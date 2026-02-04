/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.lucene.sandbox.neptune;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.Explanation;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.search.ScorerSupplier;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.Weight;
import org.apache.lucene.util.BitDocIdSet;
import org.apache.lucene.util.FixedBitSet;

/**
 * Weight implementation for Neptune graph traversal queries.
 *
 * <p>This weight executes the graph traversal during construction and caches the resulting document
 * IDs for efficient scoring. The traversal is executed once per IndexSearcher, and the results are
 * reused for all leaf contexts.
 *
 * @lucene.experimental
 */
final class NeptuneGraphWeight extends Weight {

  private final NeptuneGraphQuery graphQuery;
  private final IndexSearcher searcher;
  private final ScoreMode scoreMode;
  private final float boost;
  private Set<Integer> matchingDocIds;
  private boolean executed = false;

  /**
   * Constructs a new NeptuneGraphWeight.
   *
   * @param query the parent NeptuneGraphQuery
   * @param searcher the IndexSearcher
   * @param scoreMode the scoring mode
   * @param boost the score boost
   */
  NeptuneGraphWeight(
      NeptuneGraphQuery query, IndexSearcher searcher, ScoreMode scoreMode, float boost) {
    super(query);
    this.graphQuery = query;
    this.searcher = searcher;
    this.scoreMode = scoreMode;
    this.boost = boost;
  }

  /**
   * Executes the graph traversal and populates the matching document IDs.
   *
   * <p>This method is called lazily on first access to ensure the traversal is only executed when
   * needed.
   */
  private synchronized void executeTraversalIfNeeded() throws IOException {
    if (executed) {
      return;
    }

    NeptuneConnection connection = graphQuery.getConnection();
    GraphTraversalSpec spec = graphQuery.getTraversalSpec();
    String nodeIdField = graphQuery.getNodeIdField();

    if (connection == null || !connection.isConnected()) {
      matchingDocIds = Collections.emptySet();
      executed = true;
      return;
    }

    try {
      // Execute the graph traversal
      String gremlinQuery = spec.toGremlinQuery();
      List<GraphNode> nodes = connection.executeGremlinQuery(gremlinQuery);

      // Map the graph nodes to document IDs
      matchingDocIds = mapNodesToDocIds(nodes, nodeIdField);
    } catch (NeptuneException e) {
      // On error, return no matches rather than failing the search
      matchingDocIds = Collections.emptySet();
    }

    executed = true;
  }

  /**
   * Maps graph nodes to Lucene document IDs by looking up the node ID in the index.
   *
   * @param nodes the graph nodes to map
   * @param nodeIdField the field containing the node ID
   * @return a set of matching document IDs
   */
  private Set<Integer> mapNodesToDocIds(List<GraphNode> nodes, String nodeIdField)
      throws IOException {
    Set<Integer> docIds = new HashSet<>();

    for (GraphNode node : nodes) {
      // Create a term query to find the document with this node ID
      TermQuery termQuery = new TermQuery(new Term(nodeIdField, node.id()));
      Weight termWeight = termQuery.createWeight(searcher, ScoreMode.COMPLETE_NO_SCORES, 1.0f);

      // Search through all leaf contexts
      for (LeafReaderContext ctx : searcher.getIndexReader().leaves()) {
        ScorerSupplier scorerSupplier = termWeight.scorerSupplier(ctx);
        if (scorerSupplier != null) {
          var scorer = scorerSupplier.get(1);
          if (scorer != null) {
            DocIdSetIterator iterator = scorer.iterator();
            int doc;
            while ((doc = iterator.nextDoc()) != DocIdSetIterator.NO_MORE_DOCS) {
              // Convert leaf-relative doc ID to global doc ID
              docIds.add(ctx.docBase + doc);
            }
          }
        }
      }
    }

    return docIds;
  }

  @Override
  public ScorerSupplier scorerSupplier(LeafReaderContext context) throws IOException {
    executeTraversalIfNeeded();

    // Filter matching doc IDs to those in this leaf context
    int docBase = context.docBase;
    int maxDoc = context.reader().maxDoc();

    FixedBitSet bits = new FixedBitSet(maxDoc);
    for (Integer globalDocId : matchingDocIds) {
      int leafDocId = globalDocId - docBase;
      if (leafDocId >= 0 && leafDocId < maxDoc) {
        bits.set(leafDocId);
      }
    }

    if (bits.cardinality() == 0) {
      return null;
    }

    final NeptuneGraphScorer scorer =
        new NeptuneGraphScorer(this, new BitDocIdSet(bits).iterator(), boost);

    return new ScorerSupplier() {
      @Override
      public NeptuneGraphScorer get(long leadCost) {
        return scorer;
      }

      @Override
      public long cost() {
        return bits.cardinality();
      }
    };
  }

  @Override
  public boolean isCacheable(LeafReaderContext ctx) {
    // Graph queries depend on external state (Neptune), so not cacheable
    return false;
  }

  @Override
  public Explanation explain(LeafReaderContext context, int doc) throws IOException {
    executeTraversalIfNeeded();

    int globalDocId = context.docBase + doc;
    if (matchingDocIds.contains(globalDocId)) {
      return Explanation.match(
          boost,
          "NeptuneGraphQuery, document matched graph traversal",
          Explanation.match(
              boost,
              "boost from graph query, traversal spec: " + graphQuery.getTraversalSpec()));
    } else {
      return Explanation.noMatch("Document not found in graph traversal results");
    }
  }

  /**
   * Returns the set of matching document IDs from the graph traversal.
   *
   * @return the set of matching global document IDs
   */
  Set<Integer> getMatchingDocIds() throws IOException {
    executeTraversalIfNeeded();
    return matchingDocIds;
  }

  /**
   * Returns the score mode.
   *
   * @return the score mode
   */
  ScoreMode getScoreMode() {
    return scoreMode;
  }
}

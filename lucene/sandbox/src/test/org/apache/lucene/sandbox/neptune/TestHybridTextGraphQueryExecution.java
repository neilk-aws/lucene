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
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.Explanation;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.index.RandomIndexWriter;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Integration tests for {@link HybridTextGraphQuery} execution combining text and graph queries.
 */
public class TestHybridTextGraphQueryExecution extends LuceneTestCase {

  private Directory directory;
  private IndexReader reader;
  private IndexSearcher searcher;
  private MockNeptuneConnection mockConnection;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    directory = newDirectory();
    RandomIndexWriter writer = new RandomIndexWriter(random(), directory);

    // Index documents with both text content and node IDs
    addDoc(writer, "node-1", "Person", "Alice likes databases and graph theory");
    addDoc(writer, "node-2", "Person", "Bob enjoys machine learning and databases");
    addDoc(writer, "node-3", "Person", "Charlie works on graph algorithms");
    addDoc(writer, "node-4", "Company", "Acme Corporation develops database solutions");
    addDoc(writer, "node-5", "Person", "Diana researches knowledge graphs");
    addDoc(writer, "node-6", "Person", "Eve studies information retrieval");

    reader = writer.getReader();
    writer.close();
    searcher = newSearcher(reader);

    mockConnection = new MockNeptuneConnection("test-endpoint").preConnect();
  }

  @Override
  public void tearDown() throws Exception {
    reader.close();
    directory.close();
    super.tearDown();
  }

  private void addDoc(RandomIndexWriter writer, String nodeId, String label, String content)
      throws IOException {
    Document doc = new Document();
    doc.add(new StringField("nodeId", nodeId, Field.Store.YES));
    doc.add(new StringField("label", label, Field.Store.YES));
    doc.add(new TextField("content", content, Field.Store.NO));
    writer.addDocument(doc);
  }

  public void testBasicHybridSearch() throws IOException {
    // Text query matches "databases"
    Query textQuery = new TermQuery(new Term("content", "databases"));

    // Graph query returns nodes node-1 and node-3
    mockConnection.setDefaultQueryHandler(
        q -> List.of(new GraphNode("node-1", "Person"), new GraphNode("node-3", "Person")));

    GraphTraversalSpec spec =
        new GraphTraversalSpec.SimpleGraphTraversalSpec(
            Set.of("start-node"), List.of("KNOWS"), 2, GraphTraversalSpec.Direction.OUTGOING);
    NeptuneGraphQuery graphQuery = new NeptuneGraphQuery(spec, mockConnection, "nodeId");

    HybridTextGraphQuery hybridQuery =
        new HybridTextGraphQuery.Builder()
            .setTextQuery(textQuery)
            .setGraphQuery(graphQuery)
            .setScoreCombinationStrategy(ScoreCombinationStrategy.SUM)
            .build();

    TopDocs topDocs = searcher.search(hybridQuery, 10);

    // Should match documents that have "databases" in content
    // Text matches: node-1 (Alice), node-2 (Bob), node-4 (Acme)
    assertTrue(topDocs.totalHits.value() >= 1);
  }

  public void testHybridSearchWithMultiplyStrategy() throws IOException {
    Query textQuery = new TermQuery(new Term("content", "graph"));

    mockConnection.setDefaultQueryHandler(
        q -> List.of(new GraphNode("node-1", "Person"), new GraphNode("node-3", "Person")));

    GraphTraversalSpec spec =
        new GraphTraversalSpec.SimpleGraphTraversalSpec(
            Set.of("start-node"), List.of("KNOWS"), 2, GraphTraversalSpec.Direction.OUTGOING);
    NeptuneGraphQuery graphQuery = new NeptuneGraphQuery(spec, mockConnection, "nodeId");

    HybridTextGraphQuery hybridQuery =
        new HybridTextGraphQuery.Builder()
            .setTextQuery(textQuery)
            .setGraphQuery(graphQuery)
            .setScoreCombinationStrategy(ScoreCombinationStrategy.MULTIPLY)
            .build();

    TopDocs topDocs = searcher.search(hybridQuery, 10);

    // Text matches "graph": node-1 (Alice), node-3 (Charlie), node-5 (Diana)
    // Graph matches: node-1, node-3
    // Intersection for MULTIPLY should boost node-1 and node-3
    assertTrue(topDocs.totalHits.value() >= 1);
  }

  public void testHybridSearchWithWeightedAverage() throws IOException {
    Query textQuery = new TermQuery(new Term("content", "databases"));

    mockConnection.setDefaultQueryHandler(
        q ->
            List.of(
                new GraphNode("node-2", "Person", Map.of("name", "Bob")),
                new GraphNode("node-4", "Company")));

    GraphTraversalSpec spec =
        new GraphTraversalSpec.SimpleGraphTraversalSpec(
            Set.of("start-node"), List.of("WORKS_FOR"), 1, GraphTraversalSpec.Direction.OUTGOING);
    NeptuneGraphQuery graphQuery = new NeptuneGraphQuery(spec, mockConnection, "nodeId");

    HybridTextGraphQuery hybridQuery =
        new HybridTextGraphQuery.Builder()
            .setTextQuery(textQuery)
            .setGraphQuery(graphQuery)
            .setScoreCombinationStrategy(ScoreCombinationStrategy.WEIGHTED_AVERAGE)
            .setTextWeight(2.0f) // Text is twice as important
            .setGraphWeight(1.0f)
            .build();

    TopDocs topDocs = searcher.search(hybridQuery, 10);

    assertTrue(topDocs.totalHits.value() >= 1);
  }

  public void testHybridSearchWithMaxStrategy() throws IOException {
    Query textQuery = new TermQuery(new Term("content", "learning"));

    mockConnection.setDefaultQueryHandler(
        q ->
            List.of(
                new GraphNode("node-2", "Person"),
                new GraphNode("node-5", "Person"),
                new GraphNode("node-6", "Person")));

    GraphTraversalSpec spec =
        new GraphTraversalSpec.SimpleGraphTraversalSpec(
            Set.of("researcher-cluster"), List.of("COLLABORATES"), 3, GraphTraversalSpec.Direction.BOTH);
    NeptuneGraphQuery graphQuery = new NeptuneGraphQuery(spec, mockConnection, "nodeId");

    HybridTextGraphQuery hybridQuery =
        new HybridTextGraphQuery.Builder()
            .setTextQuery(textQuery)
            .setGraphQuery(graphQuery)
            .setScoreCombinationStrategy(ScoreCombinationStrategy.MAX)
            .build();

    TopDocs topDocs = searcher.search(hybridQuery, 10);

    // Text matches "learning": node-2 (Bob)
    assertTrue(topDocs.totalHits.value() >= 1);
  }

  public void testHybridSearchNoTextMatches() throws IOException {
    Query textQuery = new TermQuery(new Term("content", "nonexistentterm"));

    mockConnection.setDefaultQueryHandler(
        q -> List.of(new GraphNode("node-1", "Person"), new GraphNode("node-2", "Person")));

    GraphTraversalSpec spec =
        new GraphTraversalSpec.SimpleGraphTraversalSpec(
            Set.of("start-node"), List.of("KNOWS"), 2, GraphTraversalSpec.Direction.OUTGOING);
    NeptuneGraphQuery graphQuery = new NeptuneGraphQuery(spec, mockConnection, "nodeId");

    HybridTextGraphQuery hybridQuery =
        new HybridTextGraphQuery.Builder()
            .setTextQuery(textQuery)
            .setGraphQuery(graphQuery)
            .build();

    TopDocs topDocs = searcher.search(hybridQuery, 10);

    // No text matches, so no results (text is required)
    assertEquals(0, topDocs.totalHits.value());
  }

  public void testHybridSearchNoGraphMatches() throws IOException {
    Query textQuery = new TermQuery(new Term("content", "databases"));

    // Return empty graph results
    mockConnection.setDefaultQueryHandler(q -> List.of());

    GraphTraversalSpec spec =
        new GraphTraversalSpec.SimpleGraphTraversalSpec(
            Set.of("isolated-node"), List.of("KNOWS"), 1, GraphTraversalSpec.Direction.OUTGOING);
    NeptuneGraphQuery graphQuery = new NeptuneGraphQuery(spec, mockConnection, "nodeId");

    HybridTextGraphQuery hybridQuery =
        new HybridTextGraphQuery.Builder()
            .setTextQuery(textQuery)
            .setGraphQuery(graphQuery)
            .setScoreCombinationStrategy(ScoreCombinationStrategy.SUM)
            .build();

    TopDocs topDocs = searcher.search(hybridQuery, 10);

    // Text still matches, graph score will be 0
    assertTrue(topDocs.totalHits.value() >= 1);
  }

  public void testExplainHybridMatch() throws IOException {
    Query textQuery = new TermQuery(new Term("content", "databases"));

    mockConnection.setDefaultQueryHandler(q -> List.of(new GraphNode("node-1", "Person")));

    GraphTraversalSpec spec =
        new GraphTraversalSpec.SimpleGraphTraversalSpec(
            Set.of("start-node"), List.of("KNOWS"), 2, GraphTraversalSpec.Direction.OUTGOING);
    NeptuneGraphQuery graphQuery = new NeptuneGraphQuery(spec, mockConnection, "nodeId");

    HybridTextGraphQuery hybridQuery =
        new HybridTextGraphQuery.Builder()
            .setTextQuery(textQuery)
            .setGraphQuery(graphQuery)
            .setScoreCombinationStrategy(ScoreCombinationStrategy.WEIGHTED_AVERAGE)
            .setTextWeight(1.0f)
            .setGraphWeight(0.5f)
            .build();

    TopDocs topDocs = searcher.search(hybridQuery, 10);

    if (topDocs.totalHits.value() > 0) {
      Explanation explanation = searcher.explain(hybridQuery, topDocs.scoreDocs[0].doc);
      assertTrue(explanation.isMatch());
      assertTrue(explanation.getDescription().contains("HybridTextGraphQuery"));
      assertTrue(explanation.getDescription().contains("WEIGHTED_AVERAGE"));
    }
  }

  public void testExplainNoTextMatch() throws IOException {
    Query textQuery = new TermQuery(new Term("content", "nonexistent"));

    mockConnection.setDefaultQueryHandler(q -> List.of(new GraphNode("node-1", "Person")));

    GraphTraversalSpec spec =
        new GraphTraversalSpec.SimpleGraphTraversalSpec(
            Set.of("start-node"), List.of("KNOWS"), 1, GraphTraversalSpec.Direction.OUTGOING);
    NeptuneGraphQuery graphQuery = new NeptuneGraphQuery(spec, mockConnection, "nodeId");

    HybridTextGraphQuery hybridQuery =
        new HybridTextGraphQuery.Builder()
            .setTextQuery(textQuery)
            .setGraphQuery(graphQuery)
            .build();

    // Explain a document that doesn't match text
    Explanation explanation = searcher.explain(hybridQuery, 0);
    assertFalse(explanation.isMatch());
    assertTrue(explanation.getDescription().contains("text query did not match"));
  }

  public void testWeightIsCacheable() throws IOException {
    Query textQuery = new TermQuery(new Term("content", "databases"));

    GraphTraversalSpec spec =
        new GraphTraversalSpec.SimpleGraphTraversalSpec(
            Set.of("start-node"), List.of("KNOWS"), 2, GraphTraversalSpec.Direction.OUTGOING);
    NeptuneGraphQuery graphQuery = new NeptuneGraphQuery(spec, mockConnection, "nodeId");

    HybridTextGraphQuery hybridQuery =
        new HybridTextGraphQuery.Builder()
            .setTextQuery(textQuery)
            .setGraphQuery(graphQuery)
            .build();

    var weight = hybridQuery.createWeight(searcher, ScoreMode.COMPLETE, 1.0f);

    // Hybrid queries should not be cacheable (depend on external Neptune state)
    for (var ctx : reader.leaves()) {
      assertFalse(weight.isCacheable(ctx));
    }
  }

  public void testWithDifferentScoreModes() throws IOException {
    Query textQuery = new TermQuery(new Term("content", "databases"));

    mockConnection.setDefaultQueryHandler(q -> List.of(new GraphNode("node-1", "Person")));

    GraphTraversalSpec spec =
        new GraphTraversalSpec.SimpleGraphTraversalSpec(
            Set.of("start-node"), List.of("KNOWS"), 1, GraphTraversalSpec.Direction.OUTGOING);
    NeptuneGraphQuery graphQuery = new NeptuneGraphQuery(spec, mockConnection, "nodeId");

    HybridTextGraphQuery hybridQuery =
        new HybridTextGraphQuery.Builder()
            .setTextQuery(textQuery)
            .setGraphQuery(graphQuery)
            .build();

    for (ScoreMode scoreMode : ScoreMode.values()) {
      var weight = hybridQuery.createWeight(searcher, scoreMode, 1.0f);
      assertNotNull(weight);
    }
  }

  public void testRewriteWithRewritableTextQuery() throws IOException {
    // TermQuery doesn't need rewriting, but test the code path
    Query textQuery = new TermQuery(new Term("content", "databases"));

    GraphTraversalSpec spec =
        new GraphTraversalSpec.SimpleGraphTraversalSpec(
            Set.of("start-node"), List.of("KNOWS"), 1, GraphTraversalSpec.Direction.OUTGOING);
    NeptuneGraphQuery graphQuery = new NeptuneGraphQuery(spec, mockConnection, "nodeId");

    HybridTextGraphQuery hybridQuery =
        new HybridTextGraphQuery.Builder()
            .setTextQuery(textQuery)
            .setGraphQuery(graphQuery)
            .build();

    Query rewritten = hybridQuery.rewrite(searcher);

    // Should return same instance if no rewriting needed
    assertTrue(rewritten instanceof HybridTextGraphQuery);
  }

  public void testScoreOrdering() throws IOException {
    Query textQuery = new TermQuery(new Term("content", "graph"));

    // Return all graph-related nodes
    mockConnection.setDefaultQueryHandler(
        q ->
            List.of(
                new GraphNode("node-1", "Person"),
                new GraphNode("node-3", "Person"),
                new GraphNode("node-5", "Person")));

    GraphTraversalSpec spec =
        new GraphTraversalSpec.SimpleGraphTraversalSpec(
            Set.of("graph-topic"), List.of("RELATED_TO"), 2, GraphTraversalSpec.Direction.BOTH);
    NeptuneGraphQuery graphQuery = new NeptuneGraphQuery(spec, mockConnection, "nodeId");

    HybridTextGraphQuery hybridQuery =
        new HybridTextGraphQuery.Builder()
            .setTextQuery(textQuery)
            .setGraphQuery(graphQuery)
            .setScoreCombinationStrategy(ScoreCombinationStrategy.SUM)
            .build();

    TopDocs topDocs = searcher.search(hybridQuery, 10);

    if (topDocs.totalHits.value() > 1) {
      // Verify results are in decreasing score order
      for (int i = 1; i < topDocs.scoreDocs.length; i++) {
        assertTrue(topDocs.scoreDocs[i - 1].score >= topDocs.scoreDocs[i].score);
      }
    }
  }

  public void testWithGraphQueryFailure() throws IOException {
    Query textQuery = new TermQuery(new Term("content", "databases"));

    // Simulate graph query failure
    mockConnection.simulateQueryFailure(true);

    GraphTraversalSpec spec =
        new GraphTraversalSpec.SimpleGraphTraversalSpec(
            Set.of("start-node"), List.of("KNOWS"), 1, GraphTraversalSpec.Direction.OUTGOING);
    NeptuneGraphQuery graphQuery = new NeptuneGraphQuery(spec, mockConnection, "nodeId");

    HybridTextGraphQuery hybridQuery =
        new HybridTextGraphQuery.Builder()
            .setTextQuery(textQuery)
            .setGraphQuery(graphQuery)
            .build();

    // Should not throw, graph score will be 0
    TopDocs topDocs = searcher.search(hybridQuery, 10);

    // Text results should still be returned
    assertTrue(topDocs.totalHits.value() >= 1);
  }

  public void testWithDisconnectedNeptune() throws IOException {
    Query textQuery = new TermQuery(new Term("content", "databases"));

    mockConnection.disconnect();

    GraphTraversalSpec spec =
        new GraphTraversalSpec.SimpleGraphTraversalSpec(
            Set.of("start-node"), List.of("KNOWS"), 1, GraphTraversalSpec.Direction.OUTGOING);
    NeptuneGraphQuery graphQuery = new NeptuneGraphQuery(spec, mockConnection, "nodeId");

    HybridTextGraphQuery hybridQuery =
        new HybridTextGraphQuery.Builder()
            .setTextQuery(textQuery)
            .setGraphQuery(graphQuery)
            .build();

    // Should not throw, graph score will be 0
    TopDocs topDocs = searcher.search(hybridQuery, 10);

    // Text results should still be returned
    assertTrue(topDocs.totalHits.value() >= 1);
  }

  public void testAllStrategiesProduceValidScores() throws IOException {
    Query textQuery = new TermQuery(new Term("content", "databases"));

    mockConnection.setDefaultQueryHandler(
        q -> List.of(new GraphNode("node-1", "Person"), new GraphNode("node-2", "Person")));

    GraphTraversalSpec spec =
        new GraphTraversalSpec.SimpleGraphTraversalSpec(
            Set.of("start-node"), List.of("KNOWS"), 1, GraphTraversalSpec.Direction.OUTGOING);
    NeptuneGraphQuery graphQuery = new NeptuneGraphQuery(spec, mockConnection, "nodeId");

    for (ScoreCombinationStrategy strategy : ScoreCombinationStrategy.values()) {
      HybridTextGraphQuery hybridQuery =
          new HybridTextGraphQuery.Builder()
              .setTextQuery(textQuery)
              .setGraphQuery(graphQuery)
              .setScoreCombinationStrategy(strategy)
              .setTextWeight(1.0f + random().nextFloat())
              .setGraphWeight(0.5f + random().nextFloat())
              .build();

      TopDocs topDocs = searcher.search(hybridQuery, 10);

      for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
        assertTrue("Score should be finite for " + strategy, Float.isFinite(scoreDoc.score));
        assertTrue("Score should be non-negative for " + strategy, scoreDoc.score >= 0);
      }
    }
  }
}

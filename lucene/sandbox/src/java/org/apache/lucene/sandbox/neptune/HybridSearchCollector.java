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
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.search.Collector;
import org.apache.lucene.search.LeafCollector;
import org.apache.lucene.search.Scorable;
import org.apache.lucene.search.ScoreMode;

/**
 * A collector for gathering hybrid search results that include both Lucene scores and graph match
 * information.
 *
 * <p>This collector is designed to work with {@link HybridTextGraphQuery} and captures additional
 * metadata about whether each collected document matched the graph query component in addition to
 * the text query.
 *
 * <p>Example usage:
 *
 * <pre class="prettyprint">
 * HybridSearchCollector collector = new HybridSearchCollector(100);
 * searcher.search(hybridQuery, collector);
 *
 * List&lt;HybridSearchCollector.HybridHit&gt; hits = collector.getHits();
 * for (HybridHit hit : hits) {
 *     System.out.println("Doc: " + hit.docId() +
 *                        ", Score: " + hit.score() +
 *                        ", Graph Match: " + hit.matchedGraph());
 * }
 * </pre>
 *
 * @lucene.experimental
 */
public class HybridSearchCollector implements Collector {

  private final int maxHits;
  private final List<HybridHit> hits;

  /**
   * Creates a new HybridSearchCollector.
   *
   * @param maxHits the maximum number of hits to collect
   * @throws IllegalArgumentException if maxHits is less than 1
   */
  public HybridSearchCollector(int maxHits) {
    if (maxHits < 1) {
      throw new IllegalArgumentException("maxHits must be at least 1, got: " + maxHits);
    }
    this.maxHits = maxHits;
    this.hits = new ArrayList<>();
  }

  @Override
  public LeafCollector getLeafCollector(LeafReaderContext context) throws IOException {
    final int docBase = context.docBase;

    return new LeafCollector() {
      private Scorable scorer;
      private HybridTextGraphScorer hybridScorer;

      @Override
      public void setScorer(Scorable scorer) throws IOException {
        this.scorer = scorer;
        // Try to unwrap to get the hybrid scorer for additional metadata
        if (scorer instanceof HybridTextGraphScorer hts) {
          this.hybridScorer = hts;
        } else {
          this.hybridScorer = null;
        }
      }

      @Override
      public void collect(int doc) throws IOException {
        if (hits.size() >= maxHits) {
          return; // Stop collecting once we have enough hits
        }

        int globalDocId = docBase + doc;
        float score = scorer.score();
        boolean matchedGraph = hybridScorer != null && hybridScorer.matchesGraph();

        // Create and store the hit
        HybridHit hit = new HybridHit(globalDocId, score, matchedGraph);
        hits.add(hit);
      }
    };
  }

  @Override
  public ScoreMode scoreMode() {
    return ScoreMode.COMPLETE;
  }

  /**
   * Returns the collected hits.
   *
   * <p>Note: The returned list may contain fewer hits than maxHits if the query matched fewer
   * documents.
   *
   * @return the list of collected hybrid hits
   */
  public List<HybridHit> getHits() {
    return hits;
  }

  /**
   * Returns the total number of collected hits.
   *
   * @return the number of hits
   */
  public int getTotalHits() {
    return hits.size();
  }

  /**
   * Clears all collected hits.
   *
   * <p>This allows the collector to be reused for another search.
   */
  public void reset() {
    hits.clear();
  }

  /**
   * Represents a single hit from a hybrid search.
   *
   * <p>Each hit contains the document ID, combined score, and a flag indicating whether the
   * document matched the graph query component.
   *
   * @param docId the global document ID
   * @param score the combined hybrid score
   * @param matchedGraph true if the document matched the graph query
   * @lucene.experimental
   */
  public record HybridHit(int docId, float score, boolean matchedGraph) {

    /**
     * Returns a string representation of this hit.
     *
     * @return a human-readable string
     */
    @Override
    public String toString() {
      return "HybridHit{docId="
          + docId
          + ", score="
          + score
          + ", matchedGraph="
          + matchedGraph
          + "}";
    }
  }
}

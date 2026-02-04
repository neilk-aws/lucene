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
import java.util.Set;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.Scorer;

/**
 * A Scorer implementation for hybrid text + graph queries.
 *
 * <p>This scorer iterates over documents matched by the text query and combines text scores with
 * graph scores for documents that also appear in the graph traversal results.
 *
 * <p>The scoring strategy is:
 *
 * <ul>
 *   <li>Documents are matched based on the text query (text match is required)
 *   <li>If a document also matches the graph query, scores are combined using the configured
 *       strategy
 *   <li>If a document doesn't match the graph query, graph score is treated as 0
 * </ul>
 *
 * @lucene.experimental
 */
final class HybridTextGraphScorer extends Scorer {

  private final HybridTextGraphWeight weight;
  private final Scorer textScorer;
  private final Scorer graphScorer;
  private final Set<Integer> graphMatchingDocIds;
  private final int docBase;
  private final ScoreCombinationStrategy strategy;
  private final float textWeight;
  private final float graphWeight;

  /**
   * Constructs a new HybridTextGraphScorer.
   *
   * @param weight the parent weight
   * @param textScorer the scorer for the text query
   * @param graphScorer the scorer for the graph query (may be null)
   * @param graphMatchingDocIds the set of global doc IDs matching the graph query (may be null)
   * @param docBase the doc base for converting between leaf and global doc IDs
   * @param strategy the score combination strategy
   * @param textWeight the weight for text scores
   * @param graphWeight the weight for graph scores
   */
  HybridTextGraphScorer(
      HybridTextGraphWeight weight,
      Scorer textScorer,
      Scorer graphScorer,
      Set<Integer> graphMatchingDocIds,
      int docBase,
      ScoreCombinationStrategy strategy,
      float textWeight,
      float graphWeight) {
    this.weight = weight;
    this.textScorer = textScorer;
    this.graphScorer = graphScorer;
    this.graphMatchingDocIds = graphMatchingDocIds;
    this.docBase = docBase;
    this.strategy = strategy;
    this.textWeight = textWeight;
    this.graphWeight = graphWeight;
  }

  @Override
  public int docID() {
    return textScorer.docID();
  }

  @Override
  public DocIdSetIterator iterator() {
    // We iterate over text query matches
    return textScorer.iterator();
  }

  @Override
  public float getMaxScore(int upTo) throws IOException {
    // Conservative estimate: max of text max score combined with graph boost
    float textMaxScore = textScorer.getMaxScore(upTo);
    float graphMaxScore = graphScorer != null ? graphScorer.getMaxScore(upTo) : 0f;

    // Return a conservative upper bound based on the strategy
    return switch (strategy) {
      case MULTIPLY -> textMaxScore * Math.max(1f, graphMaxScore);
      case SUM -> textMaxScore + graphMaxScore;
      case MAX -> Math.max(textMaxScore, graphMaxScore);
      case WEIGHTED_AVERAGE -> {
        float totalWeight = textWeight + graphWeight;
        if (totalWeight == 0) {
          yield 0f;
        }
        yield (textMaxScore * textWeight + graphMaxScore * graphWeight) / totalWeight;
      }
    };
  }

  @Override
  public float score() throws IOException {
    // Get the text score
    float textScore = textScorer.score();

    // Check if this document also matches the graph query
    float graphScore = getGraphScore();

    // Combine scores using the configured strategy
    return strategy.combineScores(textScore, graphScore, textWeight, graphWeight);
  }

  /**
   * Gets the graph score for the current document.
   *
   * @return the graph score, or 0 if the document doesn't match the graph query
   */
  private float getGraphScore() throws IOException {
    int currentDoc = textScorer.docID();
    int globalDocId = docBase + currentDoc;

    // Check if this document is in the graph matching set
    if (graphMatchingDocIds != null && graphMatchingDocIds.contains(globalDocId)) {
      // If we have a graph scorer, get its score
      if (graphScorer != null) {
        // Try to position the graph scorer at the current document
        int graphDoc = graphScorer.docID();
        if (graphDoc < currentDoc) {
          graphDoc = graphScorer.iterator().advance(currentDoc);
        }
        if (graphDoc == currentDoc) {
          return graphScorer.score();
        }
      }
      // If we don't have a scorer but the doc is in the matching set,
      // return a default boost score
      return 1.0f;
    }

    return 0f;
  }

  /**
   * Returns the weight associated with this scorer.
   *
   * @return the HybridTextGraphWeight
   */
  HybridTextGraphWeight getWeight() {
    return weight;
  }

  /**
   * Checks if the current document matches the graph query.
   *
   * @return true if the current document is in the graph traversal results
   */
  boolean matchesGraph() {
    if (graphMatchingDocIds == null) {
      return false;
    }
    int globalDocId = docBase + textScorer.docID();
    return graphMatchingDocIds.contains(globalDocId);
  }
}

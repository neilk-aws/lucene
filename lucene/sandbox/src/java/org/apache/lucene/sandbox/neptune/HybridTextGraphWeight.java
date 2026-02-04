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
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.search.Explanation;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.search.Scorer;
import org.apache.lucene.search.ScorerSupplier;
import org.apache.lucene.search.Weight;

/**
 * Weight implementation for hybrid text + graph queries.
 *
 * <p>This weight coordinates the execution of both text and graph queries, creating a scorer that
 * combines their results according to the configured score combination strategy.
 *
 * <p>The weight operates by:
 *
 * <ol>
 *   <li>Creating a weight for the text query component
 *   <li>Creating a weight for the graph query component (which executes the Neptune traversal)
 *   <li>Combining scores from both components when scoring documents
 * </ol>
 *
 * @lucene.experimental
 */
final class HybridTextGraphWeight extends Weight {

  private final HybridTextGraphQuery hybridQuery;
  private final Weight textWeight;
  private final Weight graphWeight;
  private final ScoreMode scoreMode;
  private final float boost;

  /**
   * Constructs a new HybridTextGraphWeight.
   *
   * @param query the parent HybridTextGraphQuery
   * @param searcher the IndexSearcher
   * @param scoreMode the scoring mode
   * @param boost the score boost
   * @throws IOException if weight creation fails
   */
  HybridTextGraphWeight(
      HybridTextGraphQuery query, IndexSearcher searcher, ScoreMode scoreMode, float boost)
      throws IOException {
    super(query);
    this.hybridQuery = query;
    this.scoreMode = scoreMode;
    this.boost = boost;

    // Create weights for both component queries
    this.textWeight = query.getTextQuery().createWeight(searcher, scoreMode, boost);
    this.graphWeight = query.getGraphQuery().createWeight(searcher, scoreMode, boost);
  }

  @Override
  public ScorerSupplier scorerSupplier(LeafReaderContext context) throws IOException {
    // Get scorer suppliers for both components
    ScorerSupplier textScorerSupplier = textWeight.scorerSupplier(context);
    ScorerSupplier graphScorerSupplier = graphWeight.scorerSupplier(context);

    // If the text query has no matches in this segment, return null
    // (we require text matches as the base)
    if (textScorerSupplier == null) {
      return null;
    }

    // Get the graph matching doc IDs (may be empty but not null)
    Set<Integer> graphMatchingDocIds = null;
    if (graphWeight instanceof NeptuneGraphWeight neptuneWeight) {
      graphMatchingDocIds = neptuneWeight.getMatchingDocIds();
    }

    final Set<Integer> finalGraphMatchingDocIds = graphMatchingDocIds;

    return new ScorerSupplier() {
      @Override
      public Scorer get(long leadCost) throws IOException {
        Scorer textScorer = textScorerSupplier.get(leadCost);
        Scorer graphScorer = graphScorerSupplier != null ? graphScorerSupplier.get(leadCost) : null;

        return new HybridTextGraphScorer(
            HybridTextGraphWeight.this,
            textScorer,
            graphScorer,
            finalGraphMatchingDocIds,
            context.docBase,
            hybridQuery.getScoreCombinationStrategy(),
            hybridQuery.getTextWeight(),
            hybridQuery.getGraphWeight());
      }

      @Override
      public long cost() {
        // Cost is based on the text query since we iterate over text matches
        return textScorerSupplier.cost();
      }
    };
  }

  @Override
  public boolean isCacheable(LeafReaderContext ctx) {
    // Hybrid queries depend on external Neptune state, so not cacheable
    return false;
  }

  @Override
  public Explanation explain(LeafReaderContext context, int doc) throws IOException {
    // Get explanations from both components
    Explanation textExplanation = textWeight.explain(context, doc);
    Explanation graphExplanation = graphWeight.explain(context, doc);

    // Check if the document matches the text query
    boolean textMatches = textExplanation.isMatch();

    if (!textMatches) {
      return Explanation.noMatch(
          "HybridTextGraphQuery: no match because text query did not match",
          textExplanation,
          graphExplanation);
    }

    // Get the scores
    float textScore = textExplanation.getValue().floatValue();
    float graphScore = graphExplanation.isMatch() ? graphExplanation.getValue().floatValue() : 0f;

    // Compute the combined score
    ScoreCombinationStrategy strategy = hybridQuery.getScoreCombinationStrategy();
    float textWeight = hybridQuery.getTextWeight();
    float graphWeight = hybridQuery.getGraphWeight();
    float combinedScore = strategy.combineScores(textScore, graphScore, textWeight, graphWeight);

    // Build the explanation
    return Explanation.match(
        combinedScore,
        "HybridTextGraphQuery, combined using "
            + strategy
            + " (textWeight="
            + textWeight
            + ", graphWeight="
            + graphWeight
            + ")",
        Explanation.match(textScore, "text query score", textExplanation),
        graphExplanation.isMatch()
            ? Explanation.match(graphScore, "graph query score", graphExplanation)
            : Explanation.noMatch("graph query: no match (score=0)"));
  }

  /**
   * Returns the text weight component.
   *
   * @return the text Weight
   */
  Weight getTextWeight() {
    return textWeight;
  }

  /**
   * Returns the graph weight component.
   *
   * @return the graph Weight
   */
  Weight getGraphWeight() {
    return graphWeight;
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

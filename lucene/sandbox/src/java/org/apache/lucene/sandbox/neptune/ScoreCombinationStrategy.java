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

/**
 * Defines strategies for combining text and graph scores in hybrid queries.
 *
 * <p>When executing a {@link HybridTextGraphQuery}, documents may match both the text query and the
 * graph query. This enum defines how to combine the individual scores from each query type into a
 * final combined score.
 *
 * <p>Example usage:
 *
 * <pre class="prettyprint">
 * float textScore = 0.8f;
 * float graphScore = 0.6f;
 * float textWeight = 1.0f;
 * float graphWeight = 0.5f;
 *
 * float combined = ScoreCombinationStrategy.WEIGHTED_AVERAGE
 *     .combineScores(textScore, graphScore, textWeight, graphWeight);
 * </pre>
 *
 * @lucene.experimental
 */
public enum ScoreCombinationStrategy {

  /**
   * Multiplies the text and graph scores.
   *
   * <p>Formula: textScore * graphScore
   *
   * <p>This strategy is useful when you want documents to score highly only if they score well on
   * both queries. It penalizes documents that score poorly on either dimension.
   */
  MULTIPLY {
    @Override
    public float combineScores(
        float textScore, float graphScore, float textWeight, float graphWeight) {
      return textScore * graphScore;
    }
  },

  /**
   * Adds the text and graph scores.
   *
   * <p>Formula: textScore + graphScore
   *
   * <p>This strategy gives equal importance to both scores and is useful when you want to reward
   * documents that score well on either or both queries.
   */
  SUM {
    @Override
    public float combineScores(
        float textScore, float graphScore, float textWeight, float graphWeight) {
      return textScore + graphScore;
    }
  },

  /**
   * Returns the maximum of the text and graph scores.
   *
   * <p>Formula: max(textScore, graphScore)
   *
   * <p>This strategy is useful when you want the strongest signal to dominate, regardless of which
   * query type produced it.
   */
  MAX {
    @Override
    public float combineScores(
        float textScore, float graphScore, float textWeight, float graphWeight) {
      return Math.max(textScore, graphScore);
    }
  },

  /**
   * Computes a weighted average of the text and graph scores.
   *
   * <p>Formula: (textScore * textWeight + graphScore * graphWeight) / (textWeight + graphWeight)
   *
   * <p>This strategy allows fine-tuning the relative importance of text vs. graph relevance. If
   * weights are equal (both 1.0), this produces a simple average.
   */
  WEIGHTED_AVERAGE {
    @Override
    public float combineScores(
        float textScore, float graphScore, float textWeight, float graphWeight) {
      float totalWeight = textWeight + graphWeight;
      if (totalWeight == 0) {
        return 0;
      }
      return (textScore * textWeight + graphScore * graphWeight) / totalWeight;
    }
  };

  /**
   * Combines text and graph scores according to this strategy.
   *
   * @param textScore the score from the text query (should be non-negative)
   * @param graphScore the score from the graph query (should be non-negative)
   * @param textWeight the weight to apply to the text score (used by some strategies)
   * @param graphWeight the weight to apply to the graph score (used by some strategies)
   * @return the combined score
   */
  public abstract float combineScores(
      float textScore, float graphScore, float textWeight, float graphWeight);
}

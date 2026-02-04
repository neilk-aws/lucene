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
import java.util.Objects;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.QueryVisitor;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.search.Weight;

/**
 * A hybrid query that combines Lucene text search with Neptune graph traversals.
 *
 * <p>This query executes both a traditional Lucene text query and a Neptune graph query, then
 * combines their scores using a configurable strategy. This enables powerful hybrid search
 * scenarios where:
 *
 * <ul>
 *   <li>Documents can be found by text relevance and boosted by graph relationships
 *   <li>Graph traversal results can be filtered by text relevance
 *   <li>Text and graph signals can be combined for ranking
 * </ul>
 *
 * <p>Example usage:
 *
 * <pre class="prettyprint">
 * // Create a text query
 * Query textQuery = new TermQuery(new Term("content", "database"));
 *
 * // Create a graph traversal specification
 * GraphTraversalSpec spec = new GraphTraversalSpec.SimpleGraphTraversalSpec(
 *     Set.of("topic-graph-databases"),
 *     List.of("RELATED_TO"),
 *     2,
 *     GraphTraversalSpec.Direction.OUTGOING
 * );
 *
 * // Create a Neptune graph query
 * NeptuneGraphQuery graphQuery = new NeptuneGraphQuery(spec, connection, "nodeId");
 *
 * // Build the hybrid query
 * HybridTextGraphQuery hybridQuery = new HybridTextGraphQuery.Builder()
 *     .setTextQuery(textQuery)
 *     .setGraphQuery(graphQuery)
 *     .setScoreCombinationStrategy(ScoreCombinationStrategy.WEIGHTED_AVERAGE)
 *     .setTextWeight(1.0f)
 *     .setGraphWeight(0.5f)
 *     .build();
 *
 * // Execute the search
 * TopDocs results = searcher.search(hybridQuery, 10);
 * </pre>
 *
 * @lucene.experimental
 */
public class HybridTextGraphQuery extends Query {

  private final Query textQuery;
  private final NeptuneGraphQuery graphQuery;
  private final ScoreCombinationStrategy scoreCombinationStrategy;
  private final float textWeight;
  private final float graphWeight;

  /**
   * Constructs a new HybridTextGraphQuery.
   *
   * @param textQuery the Lucene text query to execute
   * @param graphQuery the Neptune graph query to execute
   * @param scoreCombinationStrategy the strategy for combining text and graph scores
   * @param textWeight the weight to apply to text query scores
   * @param graphWeight the weight to apply to graph query scores
   */
  private HybridTextGraphQuery(
      Query textQuery,
      NeptuneGraphQuery graphQuery,
      ScoreCombinationStrategy scoreCombinationStrategy,
      float textWeight,
      float graphWeight) {
    this.textQuery = textQuery;
    this.graphQuery = graphQuery;
    this.scoreCombinationStrategy = scoreCombinationStrategy;
    this.textWeight = textWeight;
    this.graphWeight = graphWeight;
  }

  /**
   * Returns the text query component of this hybrid query.
   *
   * @return the text query
   */
  public Query getTextQuery() {
    return textQuery;
  }

  /**
   * Returns the graph query component of this hybrid query.
   *
   * @return the Neptune graph query
   */
  public NeptuneGraphQuery getGraphQuery() {
    return graphQuery;
  }

  /**
   * Returns the score combination strategy.
   *
   * @return the strategy used to combine text and graph scores
   */
  public ScoreCombinationStrategy getScoreCombinationStrategy() {
    return scoreCombinationStrategy;
  }

  /**
   * Returns the weight applied to text query scores.
   *
   * @return the text weight
   */
  public float getTextWeight() {
    return textWeight;
  }

  /**
   * Returns the weight applied to graph query scores.
   *
   * @return the graph weight
   */
  public float getGraphWeight() {
    return graphWeight;
  }

  @Override
  public String toString(String field) {
    StringBuilder sb = new StringBuilder();
    sb.append("HybridTextGraphQuery(");
    sb.append("text=").append(textQuery.toString(field));
    sb.append(", graph=").append(graphQuery.toString(field));
    sb.append(", strategy=").append(scoreCombinationStrategy);
    sb.append(", textWeight=").append(textWeight);
    sb.append(", graphWeight=").append(graphWeight);
    sb.append(")");
    return sb.toString();
  }

  @Override
  public boolean equals(Object obj) {
    if (!sameClassAs(obj)) {
      return false;
    }
    HybridTextGraphQuery other = (HybridTextGraphQuery) obj;
    return Objects.equals(textQuery, other.textQuery)
        && Objects.equals(graphQuery, other.graphQuery)
        && scoreCombinationStrategy == other.scoreCombinationStrategy
        && Float.compare(textWeight, other.textWeight) == 0
        && Float.compare(graphWeight, other.graphWeight) == 0;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        classHash(), textQuery, graphQuery, scoreCombinationStrategy, textWeight, graphWeight);
  }

  @Override
  public void visit(QueryVisitor visitor) {
    // Visit both child queries
    Query[] subQueries = new Query[] {textQuery, graphQuery};
    for (Query subQuery : subQueries) {
      subQuery.visit(visitor);
    }
  }

  @Override
  public Query rewrite(IndexSearcher indexSearcher) throws IOException {
    // Rewrite both child queries
    Query rewrittenTextQuery = textQuery.rewrite(indexSearcher);
    Query rewrittenGraphQuery = graphQuery.rewrite(indexSearcher);

    // If neither query was rewritten, return this
    if (rewrittenTextQuery == textQuery && rewrittenGraphQuery == graphQuery) {
      return this;
    }

    // If the graph query was rewritten to something other than NeptuneGraphQuery,
    // we need to handle this case (though typically it won't be rewritten)
    if (!(rewrittenGraphQuery instanceof NeptuneGraphQuery)) {
      // Return this unchanged if graph query rewrite produces incompatible type
      return this;
    }

    // Return a new hybrid query with rewritten components
    return new HybridTextGraphQuery.Builder()
        .setTextQuery(rewrittenTextQuery)
        .setGraphQuery((NeptuneGraphQuery) rewrittenGraphQuery)
        .setScoreCombinationStrategy(scoreCombinationStrategy)
        .setTextWeight(textWeight)
        .setGraphWeight(graphWeight)
        .build();
  }

  @Override
  public Weight createWeight(IndexSearcher searcher, ScoreMode scoreMode, float boost)
      throws IOException {
    return new HybridTextGraphWeight(this, searcher, scoreMode, boost);
  }

  /**
   * A builder for constructing {@link HybridTextGraphQuery} instances.
   *
   * <p>This builder provides a fluent API for configuring all aspects of a hybrid query.
   *
   * @lucene.experimental
   */
  public static class Builder {

    private Query textQuery;
    private NeptuneGraphQuery graphQuery;
    private ScoreCombinationStrategy scoreCombinationStrategy = ScoreCombinationStrategy.SUM;
    private float textWeight = 1.0f;
    private float graphWeight = 1.0f;

    /** Creates a new Builder with default settings. */
    public Builder() {}

    /**
     * Sets the text query component.
     *
     * @param textQuery the Lucene text query to execute
     * @return this builder for chaining
     * @throws NullPointerException if textQuery is null
     */
    public Builder setTextQuery(Query textQuery) {
      this.textQuery = Objects.requireNonNull(textQuery, "textQuery must not be null");
      return this;
    }

    /**
     * Sets the graph query component.
     *
     * @param graphQuery the Neptune graph query to execute
     * @return this builder for chaining
     * @throws NullPointerException if graphQuery is null
     */
    public Builder setGraphQuery(NeptuneGraphQuery graphQuery) {
      this.graphQuery = Objects.requireNonNull(graphQuery, "graphQuery must not be null");
      return this;
    }

    /**
     * Sets the score combination strategy.
     *
     * @param strategy the strategy for combining text and graph scores
     * @return this builder for chaining
     * @throws NullPointerException if strategy is null
     */
    public Builder setScoreCombinationStrategy(ScoreCombinationStrategy strategy) {
      this.scoreCombinationStrategy =
          Objects.requireNonNull(strategy, "scoreCombinationStrategy must not be null");
      return this;
    }

    /**
     * Sets the weight for text query scores.
     *
     * <p>This weight is used by strategies that support weighted combination (e.g.,
     * WEIGHTED_AVERAGE).
     *
     * @param textWeight the weight for text scores (should be non-negative)
     * @return this builder for chaining
     * @throws IllegalArgumentException if textWeight is negative
     */
    public Builder setTextWeight(float textWeight) {
      if (textWeight < 0) {
        throw new IllegalArgumentException("textWeight must be non-negative, got: " + textWeight);
      }
      this.textWeight = textWeight;
      return this;
    }

    /**
     * Sets the weight for graph query scores.
     *
     * <p>This weight is used by strategies that support weighted combination (e.g.,
     * WEIGHTED_AVERAGE).
     *
     * @param graphWeight the weight for graph scores (should be non-negative)
     * @return this builder for chaining
     * @throws IllegalArgumentException if graphWeight is negative
     */
    public Builder setGraphWeight(float graphWeight) {
      if (graphWeight < 0) {
        throw new IllegalArgumentException("graphWeight must be non-negative, got: " + graphWeight);
      }
      this.graphWeight = graphWeight;
      return this;
    }

    /**
     * Builds a new {@link HybridTextGraphQuery} from the configured parameters.
     *
     * @return a new HybridTextGraphQuery
     * @throws IllegalStateException if textQuery or graphQuery has not been set
     */
    public HybridTextGraphQuery build() {
      if (textQuery == null) {
        throw new IllegalStateException("textQuery must be set");
      }
      if (graphQuery == null) {
        throw new IllegalStateException("graphQuery must be set");
      }
      return new HybridTextGraphQuery(
          textQuery, graphQuery, scoreCombinationStrategy, textWeight, graphWeight);
    }
  }
}

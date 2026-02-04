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

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Represents a combined result from hybrid text + graph search operations.
 *
 * <p>This class holds the results of executing both a Lucene text search and a Neptune graph
 * traversal, along with their combined relevance score. It bridges the gap between document-centric
 * search results and graph-centric traversal results.
 *
 * <p>The combined score can be computed using different strategies (e.g., multiplication, weighted
 * average, max) depending on the use case. The traversal path shows how the graph node relates to
 * other nodes in the graph.
 *
 * <p>Example usage:
 *
 * <pre class="prettyprint">
 * HybridSearchResult result = new HybridSearchResult.Builder()
 *     .luceneScore(0.85f)
 *     .graphScore(0.72f)
 *     .combinedScore(0.78f)
 *     .docId(42)
 *     .graphNode(node)
 *     .traversalPath(List.of(edge1, edge2))
 *     .build();
 * </pre>
 *
 * @lucene.experimental
 */
public final class HybridSearchResult {

  private final float luceneScore;
  private final float graphScore;
  private final float combinedScore;
  private final int docId;
  private final GraphNode graphNode;
  private final List<GraphEdge> traversalPath;

  private HybridSearchResult(Builder builder) {
    this.luceneScore = builder.luceneScore;
    this.graphScore = builder.graphScore;
    this.combinedScore = builder.combinedScore;
    this.docId = builder.docId;
    this.graphNode = builder.graphNode;
    this.traversalPath =
        builder.traversalPath == null || builder.traversalPath.isEmpty()
            ? Collections.emptyList()
            : Collections.unmodifiableList(List.copyOf(builder.traversalPath));
  }

  /**
   * Creates a new builder for constructing {@link HybridSearchResult} instances.
   *
   * @return a new builder
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Returns the Lucene text search relevance score.
   *
   * <p>This score represents how well the document matched the text query. Higher values indicate
   * better text relevance.
   *
   * @return the Lucene score (typically positive, higher is better)
   */
  public float getLuceneScore() {
    return luceneScore;
  }

  /**
   * Returns the Neptune graph traversal score.
   *
   * <p>This score represents the relevance of the result from the graph perspective, such as how
   * connected the node is or how it relates to the starting nodes.
   *
   * @return the graph score
   */
  public float getGraphScore() {
    return graphScore;
  }

  /**
   * Returns the combined score merging both Lucene and graph relevance.
   *
   * <p>The combination strategy used (multiplication, weighted sum, etc.) depends on the query
   * configuration.
   *
   * @return the combined relevance score
   */
  public float getCombinedScore() {
    return combinedScore;
  }

  /**
   * Returns the Lucene document ID.
   *
   * <p>This is the internal document ID within the Lucene index that corresponds to this result.
   *
   * @return the Lucene doc ID
   */
  public int getDocId() {
    return docId;
  }

  /**
   * Returns the graph node associated with this result.
   *
   * <p>This node represents the entity in Neptune that corresponds to the Lucene document.
   *
   * @return the graph node, or {@code null} if no graph node is associated
   */
  public GraphNode getGraphNode() {
    return graphNode;
  }

  /**
   * Returns the traversal path from the starting node(s) to this result's graph node.
   *
   * <p>The path is represented as a list of edges traversed. An empty list indicates the node was a
   * starting point or no traversal was performed.
   *
   * @return an unmodifiable list of edges in the traversal path
   */
  public List<GraphEdge> getTraversalPath() {
    return traversalPath;
  }

  /**
   * Returns the number of hops (edges) in the traversal path.
   *
   * @return the number of edges traversed to reach this result
   */
  public int getHopCount() {
    return traversalPath.size();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    HybridSearchResult that = (HybridSearchResult) o;
    return Float.compare(that.luceneScore, luceneScore) == 0
        && Float.compare(that.graphScore, graphScore) == 0
        && Float.compare(that.combinedScore, combinedScore) == 0
        && docId == that.docId
        && Objects.equals(graphNode, that.graphNode)
        && Objects.equals(traversalPath, that.traversalPath);
  }

  @Override
  public int hashCode() {
    return Objects.hash(luceneScore, graphScore, combinedScore, docId, graphNode, traversalPath);
  }

  @Override
  public String toString() {
    return "HybridSearchResult{"
        + "luceneScore="
        + luceneScore
        + ", graphScore="
        + graphScore
        + ", combinedScore="
        + combinedScore
        + ", docId="
        + docId
        + ", graphNode="
        + graphNode
        + ", traversalPath="
        + traversalPath
        + '}';
  }

  /**
   * Builder for constructing {@link HybridSearchResult} instances.
   *
   * @lucene.experimental
   */
  public static final class Builder {
    private float luceneScore;
    private float graphScore;
    private float combinedScore;
    private int docId = -1;
    private GraphNode graphNode;
    private List<GraphEdge> traversalPath;

    private Builder() {}

    /**
     * Sets the Lucene text search score.
     *
     * @param luceneScore the Lucene relevance score
     * @return this builder
     */
    public Builder luceneScore(float luceneScore) {
      this.luceneScore = luceneScore;
      return this;
    }

    /**
     * Sets the Neptune graph traversal score.
     *
     * @param graphScore the graph relevance score
     * @return this builder
     */
    public Builder graphScore(float graphScore) {
      this.graphScore = graphScore;
      return this;
    }

    /**
     * Sets the combined relevance score.
     *
     * @param combinedScore the combined score
     * @return this builder
     */
    public Builder combinedScore(float combinedScore) {
      this.combinedScore = combinedScore;
      return this;
    }

    /**
     * Sets the Lucene document ID.
     *
     * @param docId the Lucene doc ID
     * @return this builder
     */
    public Builder docId(int docId) {
      this.docId = docId;
      return this;
    }

    /**
     * Sets the graph node associated with this result.
     *
     * @param graphNode the graph node
     * @return this builder
     */
    public Builder graphNode(GraphNode graphNode) {
      this.graphNode = graphNode;
      return this;
    }

    /**
     * Sets the traversal path to the result node.
     *
     * @param traversalPath the list of edges in the path
     * @return this builder
     */
    public Builder traversalPath(List<GraphEdge> traversalPath) {
      this.traversalPath = traversalPath;
      return this;
    }

    /**
     * Builds the {@link HybridSearchResult} instance.
     *
     * @return the constructed result
     */
    public HybridSearchResult build() {
      return new HybridSearchResult(this);
    }
  }
}

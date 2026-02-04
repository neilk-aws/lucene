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
import java.util.Set;

/**
 * Abstract class representing a specification for graph traversal queries.
 *
 * <p>A traversal specification defines how to navigate through a graph database starting from a set
 * of nodes, following edges with specific labels, up to a maximum number of hops.
 *
 * <p>Subclasses may extend this to provide additional traversal parameters or different traversal
 * strategies.
 *
 * <p>Example usage:
 *
 * <pre class="prettyprint">
 * GraphTraversalSpec spec = new SimpleGraphTraversalSpec(
 *     Set.of("person-123", "person-456"),
 *     List.of("KNOWS", "FOLLOWS"),
 *     3,
 *     Direction.OUTGOING
 * );
 * </pre>
 *
 * @lucene.experimental
 */
public abstract class GraphTraversalSpec {

  /**
   * Enumeration representing the direction of edge traversal in the graph.
   *
   * @lucene.experimental
   */
  public enum Direction {
    /** Traverse only outgoing edges (from source to target) */
    OUTGOING,
    /** Traverse only incoming edges (from target to source) */
    INCOMING,
    /** Traverse edges in both directions */
    BOTH
  }

  private final Set<String> startNodeIds;
  private final List<String> edgeLabels;
  private final int maxHops;
  private final Direction direction;

  /**
   * Constructs a new GraphTraversalSpec with the specified parameters.
   *
   * @param startNodeIds the set of node IDs to start the traversal from (must not be null or empty)
   * @param edgeLabels the list of edge labels to traverse (empty list means all labels)
   * @param maxHops the maximum number of hops (must be at least 1)
   * @param direction the direction to traverse edges
   * @throws NullPointerException if any parameter is null
   * @throws IllegalArgumentException if startNodeIds is empty or maxHops is less than 1
   */
  protected GraphTraversalSpec(
      Set<String> startNodeIds, List<String> edgeLabels, int maxHops, Direction direction) {
    Objects.requireNonNull(startNodeIds, "startNodeIds must not be null");
    Objects.requireNonNull(edgeLabels, "edgeLabels must not be null");
    Objects.requireNonNull(direction, "direction must not be null");

    if (startNodeIds.isEmpty()) {
      throw new IllegalArgumentException("startNodeIds must not be empty");
    }
    if (maxHops < 1) {
      throw new IllegalArgumentException("maxHops must be at least 1, got: " + maxHops);
    }

    this.startNodeIds = Collections.unmodifiableSet(Set.copyOf(startNodeIds));
    this.edgeLabels = Collections.unmodifiableList(List.copyOf(edgeLabels));
    this.maxHops = maxHops;
    this.direction = direction;
  }

  /**
   * Returns the set of node IDs from which the traversal starts.
   *
   * @return an unmodifiable set of starting node IDs
   */
  public Set<String> getStartNodeIds() {
    return startNodeIds;
  }

  /**
   * Returns the list of edge labels to traverse.
   *
   * <p>An empty list indicates that all edge labels should be traversed.
   *
   * @return an unmodifiable list of edge labels
   */
  public List<String> getEdgeLabels() {
    return edgeLabels;
  }

  /**
   * Returns the maximum number of hops for the traversal.
   *
   * @return the maximum number of hops
   */
  public int getMaxHops() {
    return maxHops;
  }

  /**
   * Returns the direction of edge traversal.
   *
   * @return the traversal direction
   */
  public Direction getDirection() {
    return direction;
  }

  /**
   * Converts this traversal specification to a Gremlin query string.
   *
   * <p>Subclasses must implement this method to generate the appropriate Gremlin traversal for
   * their specific traversal strategy.
   *
   * @return a Gremlin query string representing this traversal
   */
  public abstract String toGremlinQuery();

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    GraphTraversalSpec that = (GraphTraversalSpec) o;
    return maxHops == that.maxHops
        && Objects.equals(startNodeIds, that.startNodeIds)
        && Objects.equals(edgeLabels, that.edgeLabels)
        && direction == that.direction;
  }

  @Override
  public int hashCode() {
    return Objects.hash(startNodeIds, edgeLabels, maxHops, direction);
  }

  @Override
  public String toString() {
    return "GraphTraversalSpec{"
        + "startNodeIds="
        + startNodeIds
        + ", edgeLabels="
        + edgeLabels
        + ", maxHops="
        + maxHops
        + ", direction="
        + direction
        + '}';
  }

  /**
   * A simple concrete implementation of GraphTraversalSpec.
   *
   * <p>This implementation provides a basic Gremlin query generation that traverses from the start
   * nodes following the specified edge labels up to the maximum number of hops.
   *
   * @lucene.experimental
   */
  public static class SimpleGraphTraversalSpec extends GraphTraversalSpec {

    /**
     * Constructs a new SimpleGraphTraversalSpec.
     *
     * @param startNodeIds the set of node IDs to start from
     * @param edgeLabels the edge labels to traverse
     * @param maxHops the maximum number of hops
     * @param direction the traversal direction
     */
    public SimpleGraphTraversalSpec(
        Set<String> startNodeIds, List<String> edgeLabels, int maxHops, Direction direction) {
      super(startNodeIds, edgeLabels, maxHops, direction);
    }

    @Override
    public String toGremlinQuery() {
      StringBuilder query = new StringBuilder("g.V(");

      // Add start node IDs
      boolean first = true;
      for (String nodeId : getStartNodeIds()) {
        if (!first) {
          query.append(", ");
        }
        query.append("'").append(escapeGremlinString(nodeId)).append("'");
        first = false;
      }
      query.append(")");

      // Add edge traversal based on direction
      String edgeStep = getEdgeStepForDirection();

      // Build the repeat step for variable-length traversal
      query.append(".repeat(");
      query.append(edgeStep);

      // Add edge label filter if specified
      if (!getEdgeLabels().isEmpty()) {
        query.append("(");
        first = true;
        for (String label : getEdgeLabels()) {
          if (!first) {
            query.append(", ");
          }
          query.append("'").append(escapeGremlinString(label)).append("'");
          first = false;
        }
        query.append(")");
      } else {
        query.append("()");
      }

      // Complete the repeat step
      query.append(").times(").append(getMaxHops()).append(")");
      query.append(".emit()");
      query.append(".dedup()");

      return query.toString();
    }

    private String getEdgeStepForDirection() {
      return switch (getDirection()) {
        case OUTGOING -> "out";
        case INCOMING -> "in";
        case BOTH -> "both";
      };
    }

    private static String escapeGremlinString(String value) {
      // Basic escaping for Gremlin string literals
      return value.replace("\\", "\\\\").replace("'", "\\'");
    }
  }
}

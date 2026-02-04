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
import java.util.Map;
import java.util.Objects;

/**
 * Represents an edge (relationship) in a Neptune graph database.
 *
 * <p>An edge connects two nodes in a property graph and represents a relationship between them.
 * Each edge has:
 *
 * <ul>
 *   <li>A unique identifier ({@link #id()})
 *   <li>A label that describes the relationship type ({@link #label()})
 *   <li>A source node ({@link #sourceNodeId()})
 *   <li>A target node ({@link #targetNodeId()})
 *   <li>A set of key-value properties ({@link #properties()})
 * </ul>
 *
 * <p>This record is immutable and thread-safe. The properties map is defensively copied and wrapped
 * in an unmodifiable view.
 *
 * <p>Example usage:
 *
 * <pre class="prettyprint">
 * GraphEdge knows = new GraphEdge(
 *     "edge-456",
 *     "KNOWS",
 *     "person-123",
 *     "person-789",
 *     Map.of("since", 2020, "closeness", 0.85)
 * );
 * </pre>
 *
 * @param id the unique identifier of the edge in the graph
 * @param label the label/type of the relationship (e.g., "KNOWS", "PURCHASED")
 * @param sourceNodeId the id of the source (outgoing) node
 * @param targetNodeId the id of the target (incoming) node
 * @param properties a map of property key-value pairs associated with the edge
 * @lucene.experimental
 */
public record GraphEdge(
    String id, String label, String sourceNodeId, String targetNodeId, Map<String, Object> properties) {

  /**
   * Constructs a new GraphEdge with the specified parameters.
   *
   * @param id the unique identifier of the edge (must not be null)
   * @param label the label of the edge (must not be null)
   * @param sourceNodeId the source node id (must not be null)
   * @param targetNodeId the target node id (must not be null)
   * @param properties the properties map (may be null, will be treated as empty)
   * @throws NullPointerException if id, label, sourceNodeId, or targetNodeId is null
   */
  public GraphEdge {
    Objects.requireNonNull(id, "id must not be null");
    Objects.requireNonNull(label, "label must not be null");
    Objects.requireNonNull(sourceNodeId, "sourceNodeId must not be null");
    Objects.requireNonNull(targetNodeId, "targetNodeId must not be null");
    // Defensive copy and wrap in unmodifiable map
    properties =
        properties == null || properties.isEmpty()
            ? Collections.emptyMap()
            : Collections.unmodifiableMap(Map.copyOf(properties));
  }

  /**
   * Constructs a new GraphEdge with the specified parameters and no properties.
   *
   * @param id the unique identifier of the edge
   * @param label the label of the edge
   * @param sourceNodeId the source node id
   * @param targetNodeId the target node id
   */
  public GraphEdge(String id, String label, String sourceNodeId, String targetNodeId) {
    this(id, label, sourceNodeId, targetNodeId, Collections.emptyMap());
  }

  /**
   * Returns the value of a property, or {@code null} if the property is not present.
   *
   * @param key the property key
   * @return the property value, or {@code null} if not present
   */
  public Object getProperty(String key) {
    return properties.get(key);
  }

  /**
   * Returns the value of a property cast to the specified type, or a default value if not present.
   *
   * @param <T> the expected type of the property value
   * @param key the property key
   * @param defaultValue the default value if the property is not present
   * @return the property value cast to type T, or the default value
   * @throws ClassCastException if the property value cannot be cast to type T
   */
  @SuppressWarnings("unchecked")
  public <T> T getProperty(String key, T defaultValue) {
    Object value = properties.get(key);
    return value != null ? (T) value : defaultValue;
  }

  /**
   * Checks whether this edge has a property with the specified key.
   *
   * @param key the property key
   * @return {@code true} if the property exists, {@code false} otherwise
   */
  public boolean hasProperty(String key) {
    return properties.containsKey(key);
  }

  /**
   * Checks whether this edge connects the specified nodes (in either direction).
   *
   * @param nodeId1 the first node id
   * @param nodeId2 the second node id
   * @return {@code true} if this edge connects the two nodes, {@code false} otherwise
   */
  public boolean connects(String nodeId1, String nodeId2) {
    return (sourceNodeId.equals(nodeId1) && targetNodeId.equals(nodeId2))
        || (sourceNodeId.equals(nodeId2) && targetNodeId.equals(nodeId1));
  }

  @Override
  public String toString() {
    return "GraphEdge{"
        + "id='"
        + id
        + '\''
        + ", label='"
        + label
        + '\''
        + ", sourceNodeId='"
        + sourceNodeId
        + '\''
        + ", targetNodeId='"
        + targetNodeId
        + '\''
        + ", properties="
        + properties
        + '}';
  }
}

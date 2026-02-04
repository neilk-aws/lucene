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
 * Represents a node (vertex) in a Neptune graph database.
 *
 * <p>A graph node is the fundamental entity in a property graph model. Each node has:
 *
 * <ul>
 *   <li>A unique identifier ({@link #id()})
 *   <li>A label that categorizes the node ({@link #label()})
 *   <li>A set of key-value properties ({@link #properties()})
 * </ul>
 *
 * <p>This record is immutable and thread-safe. The properties map is defensively copied and wrapped
 * in an unmodifiable view.
 *
 * <p>Example usage:
 *
 * <pre class="prettyprint">
 * GraphNode person = new GraphNode(
 *     "person-123",
 *     "Person",
 *     Map.of("name", "John Doe", "age", 30)
 * );
 * </pre>
 *
 * @param id the unique identifier of the node in the graph
 * @param label the label/type of the node (e.g., "Person", "Product")
 * @param properties a map of property key-value pairs associated with the node
 * @lucene.experimental
 */
public record GraphNode(String id, String label, Map<String, Object> properties) {

  /**
   * Constructs a new GraphNode with the specified id, label, and properties.
   *
   * @param id the unique identifier of the node (must not be null)
   * @param label the label of the node (must not be null)
   * @param properties the properties map (may be null, will be treated as empty)
   * @throws NullPointerException if id or label is null
   */
  public GraphNode {
    Objects.requireNonNull(id, "id must not be null");
    Objects.requireNonNull(label, "label must not be null");
    // Defensive copy and wrap in unmodifiable map
    properties =
        properties == null || properties.isEmpty()
            ? Collections.emptyMap()
            : Collections.unmodifiableMap(Map.copyOf(properties));
  }

  /**
   * Constructs a new GraphNode with the specified id and label, with no properties.
   *
   * @param id the unique identifier of the node
   * @param label the label of the node
   */
  public GraphNode(String id, String label) {
    this(id, label, Collections.emptyMap());
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
   * Checks whether this node has a property with the specified key.
   *
   * @param key the property key
   * @return {@code true} if the property exists, {@code false} otherwise
   */
  public boolean hasProperty(String key) {
    return properties.containsKey(key);
  }

  @Override
  public String toString() {
    return "GraphNode{" + "id='" + id + '\'' + ", label='" + label + '\'' + ", properties=" + properties + '}';
  }
}

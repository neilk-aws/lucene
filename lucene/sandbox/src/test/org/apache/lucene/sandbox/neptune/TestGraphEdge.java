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
import java.util.HashMap;
import java.util.Map;
import org.apache.lucene.tests.util.LuceneTestCase;

/** Unit tests for {@link GraphEdge} record. */
public class TestGraphEdge extends LuceneTestCase {

  public void testBasicConstruction() {
    GraphEdge edge = new GraphEdge("edge-123", "KNOWS", "person-1", "person-2");

    assertEquals("edge-123", edge.id());
    assertEquals("KNOWS", edge.label());
    assertEquals("person-1", edge.sourceNodeId());
    assertEquals("person-2", edge.targetNodeId());
    assertTrue(edge.properties().isEmpty());
  }

  public void testConstructionWithProperties() {
    Map<String, Object> props = new HashMap<>();
    props.put("since", 2020);
    props.put("weight", 0.85);
    props.put("active", true);

    GraphEdge edge = new GraphEdge("edge-123", "KNOWS", "person-1", "person-2", props);

    assertEquals("edge-123", edge.id());
    assertEquals("KNOWS", edge.label());
    assertEquals("person-1", edge.sourceNodeId());
    assertEquals("person-2", edge.targetNodeId());
    assertEquals(3, edge.properties().size());
    assertEquals(2020, edge.properties().get("since"));
    assertEquals(0.85, edge.properties().get("weight"));
    assertEquals(true, edge.properties().get("active"));
  }

  public void testConstructionWithNullProperties() {
    GraphEdge edge = new GraphEdge("edge-123", "KNOWS", "person-1", "person-2", null);

    assertNotNull(edge.properties());
    assertTrue(edge.properties().isEmpty());
  }

  public void testConstructionWithEmptyProperties() {
    GraphEdge edge = new GraphEdge("edge-123", "KNOWS", "person-1", "person-2", Collections.emptyMap());

    assertTrue(edge.properties().isEmpty());
  }

  public void testNullIdFails() {
    expectThrows(
        NullPointerException.class,
        () -> new GraphEdge(null, "KNOWS", "person-1", "person-2"));
  }

  public void testNullLabelFails() {
    expectThrows(
        NullPointerException.class,
        () -> new GraphEdge("edge-123", null, "person-1", "person-2"));
  }

  public void testNullSourceNodeIdFails() {
    expectThrows(
        NullPointerException.class,
        () -> new GraphEdge("edge-123", "KNOWS", null, "person-2"));
  }

  public void testNullTargetNodeIdFails() {
    expectThrows(
        NullPointerException.class,
        () -> new GraphEdge("edge-123", "KNOWS", "person-1", null));
  }

  public void testGetProperty() {
    Map<String, Object> props = Map.of("since", 2020, "weight", 0.5);
    GraphEdge edge = new GraphEdge("edge-123", "KNOWS", "person-1", "person-2", props);

    assertEquals(2020, edge.getProperty("since"));
    assertEquals(0.5, edge.getProperty("weight"));
    assertNull(edge.getProperty("nonexistent"));
  }

  public void testGetPropertyWithDefault() {
    Map<String, Object> props = Map.of("since", 2020);
    GraphEdge edge = new GraphEdge("edge-123", "KNOWS", "person-1", "person-2", props);

    assertEquals(2020, (int) edge.getProperty("since", 1900));
    assertEquals(1900, (int) edge.getProperty("year", 1900));
    assertEquals(0.5, (double) edge.getProperty("weight", 0.5), 0.001);
  }

  public void testHasProperty() {
    Map<String, Object> props = Map.of("since", 2020);
    GraphEdge edge = new GraphEdge("edge-123", "KNOWS", "person-1", "person-2", props);

    assertTrue(edge.hasProperty("since"));
    assertFalse(edge.hasProperty("nonexistent"));
  }

  public void testConnects() {
    GraphEdge edge = new GraphEdge("edge-123", "KNOWS", "person-1", "person-2");

    // Test forward direction
    assertTrue(edge.connects("person-1", "person-2"));

    // Test reverse direction
    assertTrue(edge.connects("person-2", "person-1"));

    // Test non-connected nodes
    assertFalse(edge.connects("person-1", "person-3"));
    assertFalse(edge.connects("person-3", "person-2"));
    assertFalse(edge.connects("person-3", "person-4"));
  }

  public void testPropertiesAreImmutable() {
    Map<String, Object> originalProps = new HashMap<>();
    originalProps.put("since", 2020);
    GraphEdge edge = new GraphEdge("edge-123", "KNOWS", "person-1", "person-2", originalProps);

    // Modifications to original map should not affect edge
    originalProps.put("weight", 0.5);
    assertFalse(edge.hasProperty("weight"));

    // Properties returned should be unmodifiable
    expectThrows(
        UnsupportedOperationException.class,
        () -> edge.properties().put("newKey", "value"));
  }

  public void testEquality() {
    Map<String, Object> props = Map.of("since", 2020);

    GraphEdge edge1 = new GraphEdge("edge-123", "KNOWS", "person-1", "person-2", props);
    GraphEdge edge2 = new GraphEdge("edge-123", "KNOWS", "person-1", "person-2", props);
    GraphEdge edge3 = new GraphEdge("edge-456", "KNOWS", "person-1", "person-2", props);
    GraphEdge edge4 = new GraphEdge("edge-123", "FOLLOWS", "person-1", "person-2", props);
    GraphEdge edge5 = new GraphEdge("edge-123", "KNOWS", "person-3", "person-2", props);
    GraphEdge edge6 = new GraphEdge("edge-123", "KNOWS", "person-1", "person-3", props);

    assertEquals(edge1, edge2);
    assertNotEquals(edge1, edge3);
    assertNotEquals(edge1, edge4);
    assertNotEquals(edge1, edge5);
    assertNotEquals(edge1, edge6);
  }

  public void testHashCode() {
    Map<String, Object> props = Map.of("since", 2020);

    GraphEdge edge1 = new GraphEdge("edge-123", "KNOWS", "person-1", "person-2", props);
    GraphEdge edge2 = new GraphEdge("edge-123", "KNOWS", "person-1", "person-2", props);

    assertEquals(edge1.hashCode(), edge2.hashCode());
  }

  public void testToString() {
    Map<String, Object> props = Map.of("since", 2020);
    GraphEdge edge = new GraphEdge("edge-123", "KNOWS", "person-1", "person-2", props);

    String str = edge.toString();
    assertTrue(str.contains("edge-123"));
    assertTrue(str.contains("KNOWS"));
    assertTrue(str.contains("person-1"));
    assertTrue(str.contains("person-2"));
    assertTrue(str.contains("since"));
    assertTrue(str.contains("2020"));
  }

  public void testWithVariousPropertyTypes() {
    Map<String, Object> props = new HashMap<>();
    props.put("stringProp", "value");
    props.put("intProp", 123);
    props.put("longProp", 999999999999L);
    props.put("doubleProp", 3.14159);
    props.put("booleanProp", true);

    GraphEdge edge = new GraphEdge("edge-123", "RELATION", "node-1", "node-2", props);

    assertEquals("value", edge.getProperty("stringProp"));
    assertEquals(123, edge.getProperty("intProp"));
    assertEquals(999999999999L, edge.getProperty("longProp"));
    assertEquals(3.14159, edge.getProperty("doubleProp"));
    assertEquals(true, edge.getProperty("booleanProp"));
  }

  public void testSelfLoop() {
    // Edge from node to itself
    GraphEdge selfLoop = new GraphEdge("edge-123", "REFERENCES", "node-1", "node-1");

    assertEquals("node-1", selfLoop.sourceNodeId());
    assertEquals("node-1", selfLoop.targetNodeId());
    assertTrue(selfLoop.connects("node-1", "node-1"));
  }

  public void testRandomizedProperties() {
    int numProps = random().nextInt(20);
    Map<String, Object> props = new HashMap<>();
    for (int i = 0; i < numProps; i++) {
      props.put("prop" + i, random().nextDouble());
    }

    String edgeId = "edge-" + random().nextInt(10000);
    String label = "RELATION_" + random().nextInt(100);
    String sourceId = "source-" + random().nextInt(10000);
    String targetId = "target-" + random().nextInt(10000);

    GraphEdge edge = new GraphEdge(edgeId, label, sourceId, targetId, props);

    assertEquals(edgeId, edge.id());
    assertEquals(label, edge.label());
    assertEquals(sourceId, edge.sourceNodeId());
    assertEquals(targetId, edge.targetNodeId());
    assertEquals(numProps, edge.properties().size());

    for (int i = 0; i < numProps; i++) {
      assertTrue(edge.hasProperty("prop" + i));
      assertEquals(props.get("prop" + i), edge.getProperty("prop" + i));
    }
  }

  public void testConnectsWithSameNodeId() {
    GraphEdge edge = new GraphEdge("edge-123", "KNOWS", "person-1", "person-2");

    // Same node ID for both parameters should not match unless it's a self-loop
    assertFalse(edge.connects("person-1", "person-1"));
    assertFalse(edge.connects("person-2", "person-2"));
    assertFalse(edge.connects("person-3", "person-3"));
  }

  public void testEmptyStrings() {
    // Empty strings are valid (not null)
    GraphEdge edge = new GraphEdge("", "", "", "");

    assertEquals("", edge.id());
    assertEquals("", edge.label());
    assertEquals("", edge.sourceNodeId());
    assertEquals("", edge.targetNodeId());
  }

  public void testSpecialCharacters() {
    GraphEdge edge =
        new GraphEdge("edge/123:456", "RELATED-TO#1", "node::source", "node::target");

    assertEquals("edge/123:456", edge.id());
    assertEquals("RELATED-TO#1", edge.label());
    assertEquals("node::source", edge.sourceNodeId());
    assertEquals("node::target", edge.targetNodeId());
  }
}

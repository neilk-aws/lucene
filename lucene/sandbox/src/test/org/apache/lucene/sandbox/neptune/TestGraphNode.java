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

/** Unit tests for {@link GraphNode} record. */
public class TestGraphNode extends LuceneTestCase {

  public void testBasicConstruction() {
    GraphNode node = new GraphNode("node-123", "Person");

    assertEquals("node-123", node.id());
    assertEquals("Person", node.label());
    assertTrue(node.properties().isEmpty());
  }

  public void testConstructionWithProperties() {
    Map<String, Object> props = new HashMap<>();
    props.put("name", "John Doe");
    props.put("age", 30);
    props.put("active", true);

    GraphNode node = new GraphNode("node-123", "Person", props);

    assertEquals("node-123", node.id());
    assertEquals("Person", node.label());
    assertEquals(3, node.properties().size());
    assertEquals("John Doe", node.properties().get("name"));
    assertEquals(30, node.properties().get("age"));
    assertEquals(true, node.properties().get("active"));
  }

  public void testConstructionWithNullProperties() {
    GraphNode node = new GraphNode("node-123", "Person", null);

    assertEquals("node-123", node.id());
    assertEquals("Person", node.label());
    assertNotNull(node.properties());
    assertTrue(node.properties().isEmpty());
  }

  public void testConstructionWithEmptyProperties() {
    GraphNode node = new GraphNode("node-123", "Person", Collections.emptyMap());

    assertTrue(node.properties().isEmpty());
  }

  public void testNullIdFails() {
    expectThrows(NullPointerException.class, () -> new GraphNode(null, "Person"));
  }

  public void testNullLabelFails() {
    expectThrows(NullPointerException.class, () -> new GraphNode("node-123", null));
  }

  public void testGetProperty() {
    Map<String, Object> props = Map.of("name", "John", "age", 25);
    GraphNode node = new GraphNode("node-123", "Person", props);

    assertEquals("John", node.getProperty("name"));
    assertEquals(25, node.getProperty("age"));
    assertNull(node.getProperty("nonexistent"));
  }

  public void testGetPropertyWithDefault() {
    Map<String, Object> props = Map.of("name", "John");
    GraphNode node = new GraphNode("node-123", "Person", props);

    assertEquals("John", node.getProperty("name", "Default"));
    assertEquals("Default", node.getProperty("nonexistent", "Default"));
    assertEquals(42, (int) node.getProperty("age", 42));
  }

  public void testHasProperty() {
    Map<String, Object> props = Map.of("name", "John");
    GraphNode node = new GraphNode("node-123", "Person", props);

    assertTrue(node.hasProperty("name"));
    assertFalse(node.hasProperty("nonexistent"));
  }

  public void testPropertiesAreImmutable() {
    Map<String, Object> originalProps = new HashMap<>();
    originalProps.put("name", "John");
    GraphNode node = new GraphNode("node-123", "Person", originalProps);

    // Modifications to original map should not affect node
    originalProps.put("age", 30);
    assertFalse(node.hasProperty("age"));

    // Properties returned should be unmodifiable
    expectThrows(
        UnsupportedOperationException.class,
        () -> node.properties().put("newKey", "value"));
  }

  public void testEquality() {
    Map<String, Object> props = Map.of("name", "John");

    GraphNode node1 = new GraphNode("node-123", "Person", props);
    GraphNode node2 = new GraphNode("node-123", "Person", props);
    GraphNode node3 = new GraphNode("node-456", "Person", props);
    GraphNode node4 = new GraphNode("node-123", "Company", props);
    GraphNode node5 = new GraphNode("node-123", "Person", Map.of("name", "Jane"));

    assertEquals(node1, node2);
    assertNotEquals(node1, node3);
    assertNotEquals(node1, node4);
    assertNotEquals(node1, node5);
  }

  public void testHashCode() {
    Map<String, Object> props = Map.of("name", "John");

    GraphNode node1 = new GraphNode("node-123", "Person", props);
    GraphNode node2 = new GraphNode("node-123", "Person", props);

    assertEquals(node1.hashCode(), node2.hashCode());
  }

  public void testToString() {
    Map<String, Object> props = Map.of("name", "John");
    GraphNode node = new GraphNode("node-123", "Person", props);

    String str = node.toString();
    assertTrue(str.contains("node-123"));
    assertTrue(str.contains("Person"));
    assertTrue(str.contains("name"));
    assertTrue(str.contains("John"));
  }

  public void testWithVariousPropertyTypes() {
    Map<String, Object> props = new HashMap<>();
    props.put("stringProp", "value");
    props.put("intProp", 123);
    props.put("longProp", 999999999999L);
    props.put("doubleProp", 3.14159);
    props.put("booleanProp", true);

    GraphNode node = new GraphNode("node-123", "TypeTest", props);

    assertEquals("value", node.getProperty("stringProp"));
    assertEquals(123, node.getProperty("intProp"));
    assertEquals(999999999999L, node.getProperty("longProp"));
    assertEquals(3.14159, node.getProperty("doubleProp"));
    assertEquals(true, node.getProperty("booleanProp"));
  }

  public void testRandomizedProperties() {
    // Test with random number of properties
    int numProps = random().nextInt(20);
    Map<String, Object> props = new HashMap<>();
    for (int i = 0; i < numProps; i++) {
      props.put("prop" + i, random().nextInt(1000));
    }

    String nodeId = "node-" + random().nextInt(10000);
    String label = "Label" + random().nextInt(100);

    GraphNode node = new GraphNode(nodeId, label, props);

    assertEquals(nodeId, node.id());
    assertEquals(label, node.label());
    assertEquals(numProps, node.properties().size());

    // Verify all properties were stored correctly
    for (int i = 0; i < numProps; i++) {
      assertTrue(node.hasProperty("prop" + i));
      assertEquals(props.get("prop" + i), node.getProperty("prop" + i));
    }
  }

  public void testEmptyStringId() {
    // Empty string is technically valid (not null)
    GraphNode node = new GraphNode("", "Person");
    assertEquals("", node.id());
  }

  public void testEmptyStringLabel() {
    // Empty string is technically valid (not null)
    GraphNode node = new GraphNode("node-123", "");
    assertEquals("", node.label());
  }

  public void testSpecialCharactersInIdAndLabel() {
    GraphNode node = new GraphNode("node/123:456", "Person-Type#1");
    assertEquals("node/123:456", node.id());
    assertEquals("Person-Type#1", node.label());
  }
}

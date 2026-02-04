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
import java.util.List;
import java.util.Set;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.MultiReader;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.tests.util.LuceneTestCase;

/** Unit tests for {@link NeptuneGraphQuery}. */
public class TestNeptuneGraphQuery extends LuceneTestCase {

  private MockNeptuneConnection mockConnection;
  private GraphTraversalSpec traversalSpec;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    mockConnection = new MockNeptuneConnection("test-endpoint").preConnect();
    traversalSpec =
        new GraphTraversalSpec.SimpleGraphTraversalSpec(
            Set.of("person-1", "person-2"),
            List.of("KNOWS", "FOLLOWS"),
            2,
            GraphTraversalSpec.Direction.OUTGOING);
  }

  public void testConstruction() {
    NeptuneGraphQuery query = new NeptuneGraphQuery(traversalSpec, mockConnection, "nodeId");

    assertEquals(traversalSpec, query.getTraversalSpec());
    assertEquals(mockConnection, query.getConnection());
    assertEquals("nodeId", query.getNodeIdField());
  }

  public void testConstructionWithNullTraversalSpecFails() {
    expectThrows(
        NullPointerException.class,
        () -> new NeptuneGraphQuery(null, mockConnection, "nodeId"));
  }

  public void testConstructionWithNullConnectionFails() {
    expectThrows(
        NullPointerException.class,
        () -> new NeptuneGraphQuery(traversalSpec, null, "nodeId"));
  }

  public void testConstructionWithNullNodeIdFieldFails() {
    expectThrows(
        NullPointerException.class,
        () -> new NeptuneGraphQuery(traversalSpec, mockConnection, null));
  }

  public void testToString() {
    NeptuneGraphQuery query = new NeptuneGraphQuery(traversalSpec, mockConnection, "nodeId");

    String str = query.toString("field");
    assertTrue(str.contains("NeptuneGraphQuery"));
    assertTrue(str.contains("nodeIdField=nodeId"));
    assertTrue(str.contains("person-1") || str.contains("person-2"));
    assertTrue(str.contains("KNOWS"));
    assertTrue(str.contains("maxHops=2"));
    assertTrue(str.contains("OUTGOING"));
  }

  public void testToStringWithDifferentField() {
    NeptuneGraphQuery query = new NeptuneGraphQuery(traversalSpec, mockConnection, "graphNodeId");

    String str = query.toString("content");
    assertTrue(str.contains("nodeIdField=graphNodeId"));
  }

  public void testEquals() {
    NeptuneGraphQuery query1 = new NeptuneGraphQuery(traversalSpec, mockConnection, "nodeId");
    NeptuneGraphQuery query2 = new NeptuneGraphQuery(traversalSpec, mockConnection, "nodeId");

    // Same spec and field should be equal
    assertEquals(query1, query2);

    // Same spec, different connection should still be equal
    // (connection is not part of equality)
    MockNeptuneConnection otherConnection = new MockNeptuneConnection("other-endpoint");
    NeptuneGraphQuery query3 = new NeptuneGraphQuery(traversalSpec, otherConnection, "nodeId");
    assertEquals(query1, query3);
  }

  public void testEqualsWithDifferentSpec() {
    GraphTraversalSpec otherSpec =
        new GraphTraversalSpec.SimpleGraphTraversalSpec(
            Set.of("person-99"), List.of("LIKES"), 1, GraphTraversalSpec.Direction.INCOMING);

    NeptuneGraphQuery query1 = new NeptuneGraphQuery(traversalSpec, mockConnection, "nodeId");
    NeptuneGraphQuery query2 = new NeptuneGraphQuery(otherSpec, mockConnection, "nodeId");

    assertNotEquals(query1, query2);
  }

  public void testEqualsWithDifferentNodeIdField() {
    NeptuneGraphQuery query1 = new NeptuneGraphQuery(traversalSpec, mockConnection, "nodeId");
    NeptuneGraphQuery query2 = new NeptuneGraphQuery(traversalSpec, mockConnection, "otherId");

    assertNotEquals(query1, query2);
  }

  public void testEqualsWithNull() {
    NeptuneGraphQuery query = new NeptuneGraphQuery(traversalSpec, mockConnection, "nodeId");
    assertFalse(query.equals(null));
  }

  public void testEqualsWithDifferentClass() {
    NeptuneGraphQuery query = new NeptuneGraphQuery(traversalSpec, mockConnection, "nodeId");
    assertFalse(query.equals("string"));
  }

  public void testHashCode() {
    NeptuneGraphQuery query1 = new NeptuneGraphQuery(traversalSpec, mockConnection, "nodeId");
    NeptuneGraphQuery query2 = new NeptuneGraphQuery(traversalSpec, mockConnection, "nodeId");

    assertEquals(query1.hashCode(), query2.hashCode());

    // Different spec should have different hash
    GraphTraversalSpec otherSpec =
        new GraphTraversalSpec.SimpleGraphTraversalSpec(
            Set.of("person-99"), List.of("LIKES"), 1, GraphTraversalSpec.Direction.INCOMING);
    NeptuneGraphQuery query3 = new NeptuneGraphQuery(otherSpec, mockConnection, "nodeId");
    assertNotEquals(query1.hashCode(), query3.hashCode());
  }

  public void testVisit() {
    NeptuneGraphQuery query = new NeptuneGraphQuery(traversalSpec, mockConnection, "nodeId");

    // Verify visit doesn't throw and accepts visitor
    final boolean[] visited = {false};
    query.visit(
        new org.apache.lucene.search.QueryVisitor() {
          @Override
          public void visitLeaf(Query query) {
            visited[0] = true;
          }
        });

    assertTrue(visited[0]);
  }

  public void testRewrite() throws IOException {
    NeptuneGraphQuery query = new NeptuneGraphQuery(traversalSpec, mockConnection, "nodeId");
    IndexReader reader = new MultiReader();
    IndexSearcher searcher = new IndexSearcher(reader);

    // NeptuneGraphQuery should return itself (no rewriting needed)
    Query rewritten = query.rewrite(searcher);
    assertSame(query, rewritten);
  }

  public void testCreateWeight() throws IOException {
    NeptuneGraphQuery query = new NeptuneGraphQuery(traversalSpec, mockConnection, "nodeId");
    IndexReader reader = new MultiReader();
    IndexSearcher searcher = new IndexSearcher(reader);

    var weight =
        query.createWeight(searcher, org.apache.lucene.search.ScoreMode.COMPLETE, 1.0f);
    assertNotNull(weight);
    assertTrue(weight instanceof NeptuneGraphWeight);
  }

  public void testGettersReturnCorrectValues() {
    NeptuneGraphQuery query = new NeptuneGraphQuery(traversalSpec, mockConnection, "nodeIdField");

    // Test that getters return the exact same objects
    assertSame(traversalSpec, query.getTraversalSpec());
    assertSame(mockConnection, query.getConnection());
    assertEquals("nodeIdField", query.getNodeIdField());
  }

  public void testWithDifferentDirections() {
    for (GraphTraversalSpec.Direction direction : GraphTraversalSpec.Direction.values()) {
      GraphTraversalSpec spec =
          new GraphTraversalSpec.SimpleGraphTraversalSpec(
              Set.of("node-1"), List.of("REL"), 1, direction);
      NeptuneGraphQuery query = new NeptuneGraphQuery(spec, mockConnection, "nodeId");

      assertTrue(query.toString("f").contains(direction.toString()));
    }
  }

  public void testWithVariousMaxHops() {
    for (int maxHops = 1; maxHops <= 5; maxHops++) {
      GraphTraversalSpec spec =
          new GraphTraversalSpec.SimpleGraphTraversalSpec(
              Set.of("node-1"), List.of("REL"), maxHops, GraphTraversalSpec.Direction.BOTH);
      NeptuneGraphQuery query = new NeptuneGraphQuery(spec, mockConnection, "nodeId");

      assertTrue(query.toString("f").contains("maxHops=" + maxHops));
    }
  }

  public void testWithEmptyEdgeLabels() {
    GraphTraversalSpec spec =
        new GraphTraversalSpec.SimpleGraphTraversalSpec(
            Set.of("node-1"),
            List.of(), // Empty edge labels = traverse all
            1,
            GraphTraversalSpec.Direction.OUTGOING);
    NeptuneGraphQuery query = new NeptuneGraphQuery(spec, mockConnection, "nodeId");

    assertNotNull(query);
    assertTrue(query.getTraversalSpec().getEdgeLabels().isEmpty());
  }

  public void testRandomizedQuery() {
    // Test with random parameters
    int numStartNodes = 1 + random().nextInt(5);
    Set<String> startNodes =
        Set.of(java.util.stream.IntStream.range(0, numStartNodes)
            .mapToObj(i -> "node-" + random().nextInt(1000))
            .toArray(String[]::new));

    int numEdgeLabels = random().nextInt(5);
    List<String> edgeLabels =
        java.util.stream.IntStream.range(0, numEdgeLabels)
            .mapToObj(i -> "LABEL_" + i)
            .toList();

    int maxHops = 1 + random().nextInt(10);
    GraphTraversalSpec.Direction direction =
        GraphTraversalSpec.Direction.values()[
            random().nextInt(GraphTraversalSpec.Direction.values().length)];

    GraphTraversalSpec spec =
        new GraphTraversalSpec.SimpleGraphTraversalSpec(startNodes, edgeLabels, maxHops, direction);
    String nodeIdField = "field_" + random().nextInt(100);

    NeptuneGraphQuery query = new NeptuneGraphQuery(spec, mockConnection, nodeIdField);

    assertEquals(spec, query.getTraversalSpec());
    assertEquals(nodeIdField, query.getNodeIdField());

    // Verify equality is consistent
    NeptuneGraphQuery queryCopy = new NeptuneGraphQuery(spec, mockConnection, nodeIdField);
    assertEquals(query, queryCopy);
    assertEquals(query.hashCode(), queryCopy.hashCode());
  }
}

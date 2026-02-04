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
import java.util.Set;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Tests for edge cases and error conditions in Neptune integration classes.
 *
 * <p>This test class focuses on validating proper error handling, null checks, boundary conditions,
 * and exception scenarios across the Neptune integration components.
 */
public class TestNeptuneEdgeCases extends LuceneTestCase {

  // ---- NeptuneException Tests ----

  public void testNeptuneExceptionWithMessage() {
    NeptuneException e = new NeptuneException("Test error message");
    assertEquals("Test error message", e.getMessage());
    assertNull(e.getCause());
  }

  public void testNeptuneExceptionWithMessageAndCause() {
    RuntimeException cause = new RuntimeException("Root cause");
    NeptuneException e = new NeptuneException("Test error", cause);
    assertEquals("Test error", e.getMessage());
    assertEquals(cause, e.getCause());
  }

  public void testNeptuneExceptionWithCauseOnly() {
    RuntimeException cause = new RuntimeException("Root cause");
    NeptuneException e = new NeptuneException(cause);
    assertEquals(cause, e.getCause());
  }

  public void testNeptuneExceptionFactoryMethods() {
    // Connection failed
    NeptuneException connFailed =
        NeptuneException.connectionFailed("test-endpoint", new RuntimeException("Network error"));
    assertTrue(connFailed.getMessage().contains("test-endpoint"));
    assertTrue(connFailed.getMessage().contains("Failed to connect"));

    // Query failed
    NeptuneException queryFailed =
        NeptuneException.queryFailed("g.V().limit(10)", new RuntimeException("Parse error"));
    assertTrue(queryFailed.getMessage().contains("g.V().limit(10)"));
    assertTrue(queryFailed.getMessage().contains("Failed to execute"));

    // Timeout
    NeptuneException timeout = NeptuneException.timeout("Query execution", 30000);
    assertTrue(timeout.getMessage().contains("Query execution"));
    assertTrue(timeout.getMessage().contains("30000"));
    assertTrue(timeout.getMessage().contains("timed out"));

    // Not connected
    NeptuneException notConnected = NeptuneException.notConnected();
    assertTrue(notConnected.getMessage().contains("Not connected"));
    assertTrue(notConnected.getMessage().contains("connect()"));

    // Authentication failed
    NeptuneException authFailed =
        NeptuneException.authenticationFailed(new RuntimeException("Invalid credentials"));
    assertTrue(authFailed.getMessage().contains("authentication failed"));
  }

  // ---- GraphTraversalSpec Edge Cases ----

  public void testGraphTraversalSpecWithEmptyStartNodesFails() {
    IllegalArgumentException e =
        expectThrows(
            IllegalArgumentException.class,
            () ->
                new GraphTraversalSpec.SimpleGraphTraversalSpec(
                    Collections.emptySet(),
                    List.of("KNOWS"),
                    1,
                    GraphTraversalSpec.Direction.OUTGOING));
    assertTrue(e.getMessage().contains("startNodeIds must not be empty"));
  }

  public void testGraphTraversalSpecWithZeroMaxHopsFails() {
    IllegalArgumentException e =
        expectThrows(
            IllegalArgumentException.class,
            () ->
                new GraphTraversalSpec.SimpleGraphTraversalSpec(
                    Set.of("node-1"),
                    List.of("KNOWS"),
                    0,
                    GraphTraversalSpec.Direction.OUTGOING));
    assertTrue(e.getMessage().contains("maxHops must be at least 1"));
  }

  public void testGraphTraversalSpecWithNegativeMaxHopsFails() {
    IllegalArgumentException e =
        expectThrows(
            IllegalArgumentException.class,
            () ->
                new GraphTraversalSpec.SimpleGraphTraversalSpec(
                    Set.of("node-1"),
                    List.of("KNOWS"),
                    -1,
                    GraphTraversalSpec.Direction.OUTGOING));
    assertTrue(e.getMessage().contains("maxHops must be at least 1"));
  }

  public void testGraphTraversalSpecWithNullStartNodesFails() {
    expectThrows(
        NullPointerException.class,
        () ->
            new GraphTraversalSpec.SimpleGraphTraversalSpec(
                null, List.of("KNOWS"), 1, GraphTraversalSpec.Direction.OUTGOING));
  }

  public void testGraphTraversalSpecWithNullEdgeLabelsFails() {
    expectThrows(
        NullPointerException.class,
        () ->
            new GraphTraversalSpec.SimpleGraphTraversalSpec(
                Set.of("node-1"), null, 1, GraphTraversalSpec.Direction.OUTGOING));
  }

  public void testGraphTraversalSpecWithNullDirectionFails() {
    expectThrows(
        NullPointerException.class,
        () ->
            new GraphTraversalSpec.SimpleGraphTraversalSpec(
                Set.of("node-1"), List.of("KNOWS"), 1, null));
  }

  public void testGraphTraversalSpecToGremlinWithSpecialCharacters() {
    // Test escaping of special characters in node IDs
    GraphTraversalSpec spec =
        new GraphTraversalSpec.SimpleGraphTraversalSpec(
            Set.of("node-with'quote", "node-with\\backslash"),
            List.of("LABEL'WITH'QUOTE"),
            1,
            GraphTraversalSpec.Direction.OUTGOING);

    String gremlin = spec.toGremlinQuery();

    // Verify proper escaping
    assertTrue(gremlin.contains("\\'"));
    assertTrue(gremlin.contains("\\\\"));
  }

  public void testGraphTraversalSpecEquality() {
    GraphTraversalSpec spec1 =
        new GraphTraversalSpec.SimpleGraphTraversalSpec(
            Set.of("node-1", "node-2"),
            List.of("KNOWS"),
            2,
            GraphTraversalSpec.Direction.OUTGOING);

    GraphTraversalSpec spec2 =
        new GraphTraversalSpec.SimpleGraphTraversalSpec(
            Set.of("node-2", "node-1"), // Same set, different order
            List.of("KNOWS"),
            2,
            GraphTraversalSpec.Direction.OUTGOING);

    assertEquals(spec1, spec2);
    assertEquals(spec1.hashCode(), spec2.hashCode());
  }

  // ---- MockNeptuneConnection Edge Cases ----

  public void testMockConnectionNotConnectedThrows() {
    MockNeptuneConnection mock = new MockNeptuneConnection();
    // Not connected yet

    NeptuneException e =
        expectThrows(NeptuneException.class, () -> mock.executeGremlinQuery("g.V().limit(10)"));
    assertTrue(e.getMessage().contains("Not connected"));
  }

  public void testMockConnectionSimulatedConnectionFailure() {
    MockNeptuneConnection mock = new MockNeptuneConnection().simulateConnectionFailure(true);

    NeptuneException e = expectThrows(NeptuneException.class, mock::connect);
    assertTrue(e.getMessage().contains("Failed to connect"));
  }

  public void testMockConnectionSimulatedQueryFailure() {
    MockNeptuneConnection mock =
        new MockNeptuneConnection()
            .preConnect()
            .simulateQueryFailure(true)
            .setQueryFailureMessage("Custom error message");

    NeptuneException e =
        expectThrows(NeptuneException.class, () -> mock.executeGremlinQuery("g.V()"));
    assertTrue(e.getMessage().contains("Custom error message"));
  }

  public void testMockConnectionDisconnect() {
    MockNeptuneConnection mock = new MockNeptuneConnection();
    mock.connect();
    assertTrue(mock.isConnected());

    mock.disconnect();
    assertFalse(mock.isConnected());

    // Calling disconnect again should be safe
    mock.disconnect();
    assertFalse(mock.isConnected());
  }

  public void testMockConnectionReset() {
    MockNeptuneConnection mock =
        new MockNeptuneConnection()
            .preConnect()
            .addQueryResult("query1", List.of(new GraphNode("n1", "L")))
            .simulateQueryFailure(true);

    mock.executeGremlinQuery("some query");

    mock.reset();

    assertFalse(mock.isConnected());
    assertEquals(0, mock.getQueryExecutionCount());
  }

  // ---- GraphNode Edge Cases ----

  public void testGraphNodeWithUnicodeCharacters() {
    GraphNode node = new GraphNode("节点-1", "类型", java.util.Map.of("名字", "值"));

    assertEquals("节点-1", node.id());
    assertEquals("类型", node.label());
    assertEquals("值", node.getProperty("名字"));
  }

  public void testGraphNodeGetPropertyWithIncorrectTypeCast() {
    GraphNode node = new GraphNode("node-1", "Person", java.util.Map.of("age", 30));

    // This would throw ClassCastException at runtime when cast fails
    // Testing that getProperty returns the actual type
    Object age = node.getProperty("age");
    assertTrue(age instanceof Integer);
    assertEquals(30, age);
  }

  // ---- GraphEdge Edge Cases ----

  public void testGraphEdgeConnectsWithSelf() {
    // Self-loop
    GraphEdge selfLoop = new GraphEdge("edge-1", "REFERENCES", "node-1", "node-1");
    assertTrue(selfLoop.connects("node-1", "node-1"));
  }

  public void testGraphEdgeConnectsWithWrongNodes() {
    GraphEdge edge = new GraphEdge("edge-1", "KNOWS", "a", "b");

    // Neither node is part of the edge
    assertFalse(edge.connects("c", "d"));

    // Only one node matches
    assertFalse(edge.connects("a", "c"));
    assertFalse(edge.connects("c", "b"));
  }

  // ---- HybridSearchResult Edge Cases ----

  public void testHybridSearchResultWithSpecialFloatValues() {
    // NaN and Infinity (unusual but should not throw)
    HybridSearchResult result1 =
        HybridSearchResult.builder()
            .luceneScore(Float.NaN)
            .graphScore(Float.POSITIVE_INFINITY)
            .combinedScore(Float.NEGATIVE_INFINITY)
            .build();

    assertTrue(Float.isNaN(result1.getLuceneScore()));
    assertEquals(Float.POSITIVE_INFINITY, result1.getGraphScore(), 0.0f);
    assertEquals(Float.NEGATIVE_INFINITY, result1.getCombinedScore(), 0.0f);
  }

  public void testHybridSearchResultWithMaxIntDocId() {
    HybridSearchResult result =
        HybridSearchResult.builder()
            .docId(Integer.MAX_VALUE)
            .build();

    assertEquals(Integer.MAX_VALUE, result.getDocId());
  }

  // ---- ScoreCombinationStrategy Edge Cases ----

  public void testScoreCombinationWithZeroScores() {
    assertEquals(0.0f, ScoreCombinationStrategy.MULTIPLY.combineScores(0, 0.5f, 1, 1), 0.0f);
    assertEquals(0.0f, ScoreCombinationStrategy.MULTIPLY.combineScores(0.5f, 0, 1, 1), 0.0f);
    assertEquals(0.5f, ScoreCombinationStrategy.SUM.combineScores(0, 0.5f, 1, 1), 0.0f);
    assertEquals(0.5f, ScoreCombinationStrategy.MAX.combineScores(0, 0.5f, 1, 1), 0.0f);
  }

  public void testScoreCombinationWithVerySmallValues() {
    float tiny = Float.MIN_VALUE;
    float result = ScoreCombinationStrategy.MULTIPLY.combineScores(tiny, tiny, 1, 1);
    assertEquals(0.0f, result, 0.0f); // Underflow to zero
  }

  public void testScoreCombinationWithLargeValues() {
    float large = Float.MAX_VALUE / 2;
    float sumResult = ScoreCombinationStrategy.SUM.combineScores(large, large, 1, 1);
    assertEquals(Float.MAX_VALUE, sumResult, Float.MAX_VALUE * 0.001f);
  }

  public void testWeightedAverageWithExtremeWeights() {
    // Very large text weight relative to graph
    float result1 =
        ScoreCombinationStrategy.WEIGHTED_AVERAGE.combineScores(0.8f, 0.2f, 1000000f, 0.001f);
    // Should be very close to text score
    assertEquals(0.8f, result1, 0.01f);

    // Very large graph weight relative to text
    float result2 =
        ScoreCombinationStrategy.WEIGHTED_AVERAGE.combineScores(0.8f, 0.2f, 0.001f, 1000000f);
    // Should be very close to graph score
    assertEquals(0.2f, result2, 0.01f);
  }

  // ---- General Robustness Tests ----

  public void testRandomStressTestGraphNode() {
    // Create many random nodes to verify no crashes
    for (int i = 0; i < 100; i++) {
      String id = "node-" + random().nextLong();
      String label = random().nextBoolean() ? "" : "Label" + random().nextInt();
      java.util.Map<String, Object> props = new java.util.HashMap<>();
      int numProps = random().nextInt(10);
      for (int j = 0; j < numProps; j++) {
        props.put("prop" + j, random().nextDouble());
      }

      GraphNode node = new GraphNode(id, label, props);
      assertNotNull(node.toString());
      assertEquals(id, node.id());
    }
  }

  public void testRandomStressTestGraphEdge() {
    for (int i = 0; i < 100; i++) {
      String id = "edge-" + random().nextLong();
      String label = "REL_" + random().nextInt(10);
      String source = "src-" + random().nextInt(1000);
      String target = "tgt-" + random().nextInt(1000);

      GraphEdge edge = new GraphEdge(id, label, source, target);
      assertNotNull(edge.toString());
      assertTrue(edge.connects(source, target));
    }
  }

  public void testRandomStressTestHybridSearchResult() {
    for (int i = 0; i < 100; i++) {
      HybridSearchResult result =
          HybridSearchResult.builder()
              .luceneScore(random().nextFloat())
              .graphScore(random().nextFloat())
              .combinedScore(random().nextFloat())
              .docId(random().nextInt(Integer.MAX_VALUE))
              .build();

      assertNotNull(result.toString());
      assertEquals(0, result.getHopCount()); // No path set
    }
  }

  public void testRandomStressTestScoreCombination() {
    ScoreCombinationStrategy[] strategies = ScoreCombinationStrategy.values();

    for (int i = 0; i < 1000; i++) {
      ScoreCombinationStrategy strategy = strategies[random().nextInt(strategies.length)];
      float textScore = random().nextFloat() * 100;
      float graphScore = random().nextFloat() * 100;
      float textWeight = random().nextFloat() * 10;
      float graphWeight = random().nextFloat() * 10;

      // Should not throw
      float result = strategy.combineScores(textScore, graphScore, textWeight, graphWeight);

      // Result should be a valid float (not NaN unless inputs cause it)
      if (!Float.isNaN(textScore) && !Float.isNaN(graphScore)) {
        assertTrue(
            "Result should be finite or properly infinite",
            Float.isFinite(result) || Float.isInfinite(result));
      }
    }
  }
}

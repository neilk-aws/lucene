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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.apache.lucene.tests.util.LuceneTestCase;

/** Unit tests for {@link HybridSearchResult} and its Builder. */
public class TestHybridSearchResult extends LuceneTestCase {

  public void testBuilderWithMinimalFields() {
    HybridSearchResult result = HybridSearchResult.builder().build();

    assertEquals(0.0f, result.getLuceneScore(), 0.0f);
    assertEquals(0.0f, result.getGraphScore(), 0.0f);
    assertEquals(0.0f, result.getCombinedScore(), 0.0f);
    assertEquals(-1, result.getDocId());
    assertNull(result.getGraphNode());
    assertTrue(result.getTraversalPath().isEmpty());
    assertEquals(0, result.getHopCount());
  }

  public void testBuilderWithAllFields() {
    GraphNode node = new GraphNode("node-123", "Person", Map.of("name", "John"));
    GraphEdge edge1 = new GraphEdge("edge-1", "KNOWS", "node-1", "node-123");
    GraphEdge edge2 = new GraphEdge("edge-2", "KNOWS", "node-0", "node-1");
    List<GraphEdge> path = List.of(edge2, edge1);

    HybridSearchResult result =
        HybridSearchResult.builder()
            .luceneScore(0.85f)
            .graphScore(0.72f)
            .combinedScore(0.78f)
            .docId(42)
            .graphNode(node)
            .traversalPath(path)
            .build();

    assertEquals(0.85f, result.getLuceneScore(), 0.001f);
    assertEquals(0.72f, result.getGraphScore(), 0.001f);
    assertEquals(0.78f, result.getCombinedScore(), 0.001f);
    assertEquals(42, result.getDocId());
    assertEquals(node, result.getGraphNode());
    assertEquals(2, result.getTraversalPath().size());
    assertEquals(2, result.getHopCount());
    assertEquals("edge-2", result.getTraversalPath().get(0).id());
    assertEquals("edge-1", result.getTraversalPath().get(1).id());
  }

  public void testBuilderWithNullTraversalPath() {
    HybridSearchResult result =
        HybridSearchResult.builder()
            .luceneScore(0.5f)
            .traversalPath(null)
            .build();

    assertNotNull(result.getTraversalPath());
    assertTrue(result.getTraversalPath().isEmpty());
  }

  public void testBuilderWithEmptyTraversalPath() {
    HybridSearchResult result =
        HybridSearchResult.builder()
            .luceneScore(0.5f)
            .traversalPath(Collections.emptyList())
            .build();

    assertTrue(result.getTraversalPath().isEmpty());
    assertEquals(0, result.getHopCount());
  }

  public void testTraversalPathIsImmutable() {
    List<GraphEdge> mutablePath = new ArrayList<>();
    mutablePath.add(new GraphEdge("edge-1", "KNOWS", "a", "b"));

    HybridSearchResult result =
        HybridSearchResult.builder()
            .traversalPath(mutablePath)
            .build();

    // Modifying original list should not affect result
    mutablePath.add(new GraphEdge("edge-2", "FOLLOWS", "b", "c"));
    assertEquals(1, result.getTraversalPath().size());

    // Returned path should be unmodifiable
    expectThrows(
        UnsupportedOperationException.class,
        () -> result.getTraversalPath().add(new GraphEdge("edge-3", "LIKES", "c", "d")));
  }

  public void testEquality() {
    GraphNode node = new GraphNode("node-123", "Person");
    List<GraphEdge> path = List.of(new GraphEdge("edge-1", "KNOWS", "a", "b"));

    HybridSearchResult result1 =
        HybridSearchResult.builder()
            .luceneScore(0.85f)
            .graphScore(0.72f)
            .combinedScore(0.78f)
            .docId(42)
            .graphNode(node)
            .traversalPath(path)
            .build();

    HybridSearchResult result2 =
        HybridSearchResult.builder()
            .luceneScore(0.85f)
            .graphScore(0.72f)
            .combinedScore(0.78f)
            .docId(42)
            .graphNode(node)
            .traversalPath(path)
            .build();

    HybridSearchResult result3 =
        HybridSearchResult.builder()
            .luceneScore(0.90f) // Different score
            .graphScore(0.72f)
            .combinedScore(0.78f)
            .docId(42)
            .graphNode(node)
            .traversalPath(path)
            .build();

    assertEquals(result1, result2);
    assertEquals(result1.hashCode(), result2.hashCode());
    assertNotEquals(result1, result3);
  }

  public void testEqualityWithDifferentDocId() {
    HybridSearchResult result1 =
        HybridSearchResult.builder()
            .luceneScore(0.85f)
            .docId(42)
            .build();

    HybridSearchResult result2 =
        HybridSearchResult.builder()
            .luceneScore(0.85f)
            .docId(43)
            .build();

    assertNotEquals(result1, result2);
  }

  public void testEqualityWithDifferentGraphNode() {
    HybridSearchResult result1 =
        HybridSearchResult.builder()
            .docId(42)
            .graphNode(new GraphNode("node-1", "Person"))
            .build();

    HybridSearchResult result2 =
        HybridSearchResult.builder()
            .docId(42)
            .graphNode(new GraphNode("node-2", "Person"))
            .build();

    assertNotEquals(result1, result2);
  }

  public void testEqualityWithDifferentPath() {
    HybridSearchResult result1 =
        HybridSearchResult.builder()
            .docId(42)
            .traversalPath(List.of(new GraphEdge("edge-1", "KNOWS", "a", "b")))
            .build();

    HybridSearchResult result2 =
        HybridSearchResult.builder()
            .docId(42)
            .traversalPath(List.of(new GraphEdge("edge-2", "KNOWS", "a", "b")))
            .build();

    assertNotEquals(result1, result2);
  }

  public void testEqualsWithNull() {
    HybridSearchResult result = HybridSearchResult.builder().build();
    assertFalse(result.equals(null));
  }

  public void testEqualsWithDifferentClass() {
    HybridSearchResult result = HybridSearchResult.builder().build();
    assertFalse(result.equals("string"));
  }

  public void testToString() {
    GraphNode node = new GraphNode("node-123", "Person");
    HybridSearchResult result =
        HybridSearchResult.builder()
            .luceneScore(0.85f)
            .graphScore(0.72f)
            .combinedScore(0.78f)
            .docId(42)
            .graphNode(node)
            .build();

    String str = result.toString();
    assertTrue(str.contains("0.85"));
    assertTrue(str.contains("0.72"));
    assertTrue(str.contains("0.78"));
    assertTrue(str.contains("42"));
    assertTrue(str.contains("node-123"));
  }

  public void testGetHopCount() {
    // Empty path
    HybridSearchResult result0 = HybridSearchResult.builder().build();
    assertEquals(0, result0.getHopCount());

    // Single hop
    HybridSearchResult result1 =
        HybridSearchResult.builder()
            .traversalPath(List.of(new GraphEdge("e1", "R", "a", "b")))
            .build();
    assertEquals(1, result1.getHopCount());

    // Multiple hops
    HybridSearchResult result3 =
        HybridSearchResult.builder()
            .traversalPath(
                List.of(
                    new GraphEdge("e1", "R", "a", "b"),
                    new GraphEdge("e2", "R", "b", "c"),
                    new GraphEdge("e3", "R", "c", "d")))
            .build();
    assertEquals(3, result3.getHopCount());
  }

  public void testScoreValuesCanBeNegative() {
    // Though unusual, negative scores should be allowed
    HybridSearchResult result =
        HybridSearchResult.builder()
            .luceneScore(-0.5f)
            .graphScore(-0.3f)
            .combinedScore(-0.4f)
            .build();

    assertEquals(-0.5f, result.getLuceneScore(), 0.001f);
    assertEquals(-0.3f, result.getGraphScore(), 0.001f);
    assertEquals(-0.4f, result.getCombinedScore(), 0.001f);
  }

  public void testScoreValuesCanBeZero() {
    HybridSearchResult result =
        HybridSearchResult.builder()
            .luceneScore(0.0f)
            .graphScore(0.0f)
            .combinedScore(0.0f)
            .build();

    assertEquals(0.0f, result.getLuceneScore(), 0.0f);
    assertEquals(0.0f, result.getGraphScore(), 0.0f);
    assertEquals(0.0f, result.getCombinedScore(), 0.0f);
  }

  public void testVeryLargeScores() {
    HybridSearchResult result =
        HybridSearchResult.builder()
            .luceneScore(Float.MAX_VALUE)
            .graphScore(Float.MAX_VALUE)
            .combinedScore(Float.MAX_VALUE)
            .build();

    assertEquals(Float.MAX_VALUE, result.getLuceneScore(), 0.0f);
    assertEquals(Float.MAX_VALUE, result.getGraphScore(), 0.0f);
    assertEquals(Float.MAX_VALUE, result.getCombinedScore(), 0.0f);
  }

  public void testRandomizedResult() {
    float luceneScore = random().nextFloat() * 10;
    float graphScore = random().nextFloat() * 10;
    float combinedScore = random().nextFloat() * 10;
    int docId = random().nextInt(100000);

    GraphNode node = new GraphNode("node-" + random().nextInt(), "Label" + random().nextInt(10));

    int pathLength = random().nextInt(5);
    List<GraphEdge> path = new ArrayList<>();
    for (int i = 0; i < pathLength; i++) {
      path.add(new GraphEdge("edge-" + i, "REL", "n" + i, "n" + (i + 1)));
    }

    HybridSearchResult result =
        HybridSearchResult.builder()
            .luceneScore(luceneScore)
            .graphScore(graphScore)
            .combinedScore(combinedScore)
            .docId(docId)
            .graphNode(node)
            .traversalPath(path)
            .build();

    assertEquals(luceneScore, result.getLuceneScore(), 0.001f);
    assertEquals(graphScore, result.getGraphScore(), 0.001f);
    assertEquals(combinedScore, result.getCombinedScore(), 0.001f);
    assertEquals(docId, result.getDocId());
    assertEquals(node, result.getGraphNode());
    assertEquals(pathLength, result.getHopCount());
  }

  public void testBuilderChaining() {
    // Verify builder returns same instance for chaining
    HybridSearchResult.Builder builder = HybridSearchResult.builder();
    assertSame(builder, builder.luceneScore(0.5f));
    assertSame(builder, builder.graphScore(0.5f));
    assertSame(builder, builder.combinedScore(0.5f));
    assertSame(builder, builder.docId(1));
    assertSame(builder, builder.graphNode(new GraphNode("n", "L")));
    assertSame(builder, builder.traversalPath(Collections.emptyList()));
  }
}

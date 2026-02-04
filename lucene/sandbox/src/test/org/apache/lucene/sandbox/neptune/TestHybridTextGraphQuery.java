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
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.tests.util.LuceneTestCase;

/** Unit tests for {@link HybridTextGraphQuery} and its Builder. */
public class TestHybridTextGraphQuery extends LuceneTestCase {

  private MockNeptuneConnection mockConnection;
  private Query textQuery;
  private NeptuneGraphQuery graphQuery;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    mockConnection = new MockNeptuneConnection("test-endpoint").preConnect();
    textQuery = new TermQuery(new Term("content", "search"));

    GraphTraversalSpec spec =
        new GraphTraversalSpec.SimpleGraphTraversalSpec(
            Set.of("node-1"), List.of("KNOWS"), 2, GraphTraversalSpec.Direction.OUTGOING);
    graphQuery = new NeptuneGraphQuery(spec, mockConnection, "nodeId");
  }

  public void testBuilderWithRequiredFields() {
    HybridTextGraphQuery query =
        new HybridTextGraphQuery.Builder()
            .setTextQuery(textQuery)
            .setGraphQuery(graphQuery)
            .build();

    assertEquals(textQuery, query.getTextQuery());
    assertEquals(graphQuery, query.getGraphQuery());
    assertEquals(ScoreCombinationStrategy.SUM, query.getScoreCombinationStrategy());
    assertEquals(1.0f, query.getTextWeight(), 0.001f);
    assertEquals(1.0f, query.getGraphWeight(), 0.001f);
  }

  public void testBuilderWithAllFields() {
    HybridTextGraphQuery query =
        new HybridTextGraphQuery.Builder()
            .setTextQuery(textQuery)
            .setGraphQuery(graphQuery)
            .setScoreCombinationStrategy(ScoreCombinationStrategy.WEIGHTED_AVERAGE)
            .setTextWeight(2.0f)
            .setGraphWeight(0.5f)
            .build();

    assertEquals(textQuery, query.getTextQuery());
    assertEquals(graphQuery, query.getGraphQuery());
    assertEquals(ScoreCombinationStrategy.WEIGHTED_AVERAGE, query.getScoreCombinationStrategy());
    assertEquals(2.0f, query.getTextWeight(), 0.001f);
    assertEquals(0.5f, query.getGraphWeight(), 0.001f);
  }

  public void testBuilderWithoutTextQueryFails() {
    IllegalStateException e =
        expectThrows(
            IllegalStateException.class,
            () -> new HybridTextGraphQuery.Builder().setGraphQuery(graphQuery).build());
    assertTrue(e.getMessage().contains("textQuery must be set"));
  }

  public void testBuilderWithoutGraphQueryFails() {
    IllegalStateException e =
        expectThrows(
            IllegalStateException.class,
            () -> new HybridTextGraphQuery.Builder().setTextQuery(textQuery).build());
    assertTrue(e.getMessage().contains("graphQuery must be set"));
  }

  public void testBuilderWithNullTextQueryFails() {
    expectThrows(
        NullPointerException.class,
        () -> new HybridTextGraphQuery.Builder().setTextQuery(null));
  }

  public void testBuilderWithNullGraphQueryFails() {
    expectThrows(
        NullPointerException.class,
        () -> new HybridTextGraphQuery.Builder().setGraphQuery(null));
  }

  public void testBuilderWithNullStrategyFails() {
    expectThrows(
        NullPointerException.class,
        () -> new HybridTextGraphQuery.Builder().setScoreCombinationStrategy(null));
  }

  public void testBuilderWithNegativeTextWeightFails() {
    IllegalArgumentException e =
        expectThrows(
            IllegalArgumentException.class,
            () -> new HybridTextGraphQuery.Builder().setTextWeight(-0.1f));
    assertTrue(e.getMessage().contains("textWeight must be non-negative"));
  }

  public void testBuilderWithNegativeGraphWeightFails() {
    IllegalArgumentException e =
        expectThrows(
            IllegalArgumentException.class,
            () -> new HybridTextGraphQuery.Builder().setGraphWeight(-0.1f));
    assertTrue(e.getMessage().contains("graphWeight must be non-negative"));
  }

  public void testBuilderWithZeroWeightsAllowed() {
    HybridTextGraphQuery query =
        new HybridTextGraphQuery.Builder()
            .setTextQuery(textQuery)
            .setGraphQuery(graphQuery)
            .setTextWeight(0.0f)
            .setGraphWeight(0.0f)
            .build();

    assertEquals(0.0f, query.getTextWeight(), 0.0f);
    assertEquals(0.0f, query.getGraphWeight(), 0.0f);
  }

  public void testToString() {
    HybridTextGraphQuery query =
        new HybridTextGraphQuery.Builder()
            .setTextQuery(textQuery)
            .setGraphQuery(graphQuery)
            .setScoreCombinationStrategy(ScoreCombinationStrategy.MULTIPLY)
            .setTextWeight(1.5f)
            .setGraphWeight(0.8f)
            .build();

    String str = query.toString("field");
    assertTrue(str.contains("HybridTextGraphQuery"));
    assertTrue(str.contains("text="));
    assertTrue(str.contains("graph="));
    assertTrue(str.contains("MULTIPLY"));
    assertTrue(str.contains("textWeight=1.5"));
    assertTrue(str.contains("graphWeight=0.8"));
  }

  public void testEquals() {
    HybridTextGraphQuery query1 =
        new HybridTextGraphQuery.Builder()
            .setTextQuery(textQuery)
            .setGraphQuery(graphQuery)
            .setScoreCombinationStrategy(ScoreCombinationStrategy.SUM)
            .setTextWeight(1.0f)
            .setGraphWeight(1.0f)
            .build();

    HybridTextGraphQuery query2 =
        new HybridTextGraphQuery.Builder()
            .setTextQuery(textQuery)
            .setGraphQuery(graphQuery)
            .setScoreCombinationStrategy(ScoreCombinationStrategy.SUM)
            .setTextWeight(1.0f)
            .setGraphWeight(1.0f)
            .build();

    assertEquals(query1, query2);
  }

  public void testEqualsWithDifferentTextQuery() {
    HybridTextGraphQuery query1 =
        new HybridTextGraphQuery.Builder()
            .setTextQuery(new TermQuery(new Term("f", "a")))
            .setGraphQuery(graphQuery)
            .build();

    HybridTextGraphQuery query2 =
        new HybridTextGraphQuery.Builder()
            .setTextQuery(new TermQuery(new Term("f", "b")))
            .setGraphQuery(graphQuery)
            .build();

    assertNotEquals(query1, query2);
  }

  public void testEqualsWithDifferentGraphQuery() {
    GraphTraversalSpec otherSpec =
        new GraphTraversalSpec.SimpleGraphTraversalSpec(
            Set.of("other-node"), List.of("FOLLOWS"), 1, GraphTraversalSpec.Direction.INCOMING);
    NeptuneGraphQuery otherGraphQuery =
        new NeptuneGraphQuery(otherSpec, mockConnection, "nodeId");

    HybridTextGraphQuery query1 =
        new HybridTextGraphQuery.Builder()
            .setTextQuery(textQuery)
            .setGraphQuery(graphQuery)
            .build();

    HybridTextGraphQuery query2 =
        new HybridTextGraphQuery.Builder()
            .setTextQuery(textQuery)
            .setGraphQuery(otherGraphQuery)
            .build();

    assertNotEquals(query1, query2);
  }

  public void testEqualsWithDifferentStrategy() {
    HybridTextGraphQuery query1 =
        new HybridTextGraphQuery.Builder()
            .setTextQuery(textQuery)
            .setGraphQuery(graphQuery)
            .setScoreCombinationStrategy(ScoreCombinationStrategy.SUM)
            .build();

    HybridTextGraphQuery query2 =
        new HybridTextGraphQuery.Builder()
            .setTextQuery(textQuery)
            .setGraphQuery(graphQuery)
            .setScoreCombinationStrategy(ScoreCombinationStrategy.MULTIPLY)
            .build();

    assertNotEquals(query1, query2);
  }

  public void testEqualsWithDifferentTextWeight() {
    HybridTextGraphQuery query1 =
        new HybridTextGraphQuery.Builder()
            .setTextQuery(textQuery)
            .setGraphQuery(graphQuery)
            .setTextWeight(1.0f)
            .build();

    HybridTextGraphQuery query2 =
        new HybridTextGraphQuery.Builder()
            .setTextQuery(textQuery)
            .setGraphQuery(graphQuery)
            .setTextWeight(2.0f)
            .build();

    assertNotEquals(query1, query2);
  }

  public void testEqualsWithDifferentGraphWeight() {
    HybridTextGraphQuery query1 =
        new HybridTextGraphQuery.Builder()
            .setTextQuery(textQuery)
            .setGraphQuery(graphQuery)
            .setGraphWeight(1.0f)
            .build();

    HybridTextGraphQuery query2 =
        new HybridTextGraphQuery.Builder()
            .setTextQuery(textQuery)
            .setGraphQuery(graphQuery)
            .setGraphWeight(2.0f)
            .build();

    assertNotEquals(query1, query2);
  }

  public void testHashCode() {
    HybridTextGraphQuery query1 =
        new HybridTextGraphQuery.Builder()
            .setTextQuery(textQuery)
            .setGraphQuery(graphQuery)
            .build();

    HybridTextGraphQuery query2 =
        new HybridTextGraphQuery.Builder()
            .setTextQuery(textQuery)
            .setGraphQuery(graphQuery)
            .build();

    assertEquals(query1.hashCode(), query2.hashCode());
  }

  public void testVisit() {
    HybridTextGraphQuery query =
        new HybridTextGraphQuery.Builder()
            .setTextQuery(textQuery)
            .setGraphQuery(graphQuery)
            .build();

    final int[] visitCount = {0};
    query.visit(
        new org.apache.lucene.search.QueryVisitor() {
          @Override
          public void visitLeaf(Query query) {
            visitCount[0]++;
          }

          @Override
          public void consumeTerms(Query query, Term... terms) {
            visitCount[0]++;
          }
        });

    // Should visit both text and graph queries
    assertTrue(visitCount[0] >= 1);
  }

  public void testRewriteNoChange() throws IOException {
    HybridTextGraphQuery query =
        new HybridTextGraphQuery.Builder()
            .setTextQuery(textQuery)
            .setGraphQuery(graphQuery)
            .build();

    IndexReader reader = new MultiReader();
    IndexSearcher searcher = new IndexSearcher(reader);

    Query rewritten = query.rewrite(searcher);

    // If nothing needs rewriting, should return same instance or equivalent
    assertTrue(rewritten instanceof HybridTextGraphQuery);
  }

  public void testCreateWeight() throws IOException {
    HybridTextGraphQuery query =
        new HybridTextGraphQuery.Builder()
            .setTextQuery(textQuery)
            .setGraphQuery(graphQuery)
            .build();

    IndexReader reader = new MultiReader();
    IndexSearcher searcher = new IndexSearcher(reader);

    var weight =
        query.createWeight(searcher, org.apache.lucene.search.ScoreMode.COMPLETE, 1.0f);
    assertNotNull(weight);
    assertTrue(weight instanceof HybridTextGraphWeight);
  }

  public void testAllScoreCombinationStrategies() {
    for (ScoreCombinationStrategy strategy : ScoreCombinationStrategy.values()) {
      HybridTextGraphQuery query =
          new HybridTextGraphQuery.Builder()
              .setTextQuery(textQuery)
              .setGraphQuery(graphQuery)
              .setScoreCombinationStrategy(strategy)
              .build();

      assertEquals(strategy, query.getScoreCombinationStrategy());
    }
  }

  public void testScoreCombinationStrategyMultiply() {
    float textScore = 0.8f;
    float graphScore = 0.5f;
    float expected = textScore * graphScore;

    float actual =
        ScoreCombinationStrategy.MULTIPLY.combineScores(textScore, graphScore, 1.0f, 1.0f);
    assertEquals(expected, actual, 0.001f);
  }

  public void testScoreCombinationStrategySum() {
    float textScore = 0.8f;
    float graphScore = 0.5f;
    float expected = textScore + graphScore;

    float actual = ScoreCombinationStrategy.SUM.combineScores(textScore, graphScore, 1.0f, 1.0f);
    assertEquals(expected, actual, 0.001f);
  }

  public void testScoreCombinationStrategyMax() {
    float textScore = 0.8f;
    float graphScore = 0.5f;
    float expected = Math.max(textScore, graphScore);

    float actual = ScoreCombinationStrategy.MAX.combineScores(textScore, graphScore, 1.0f, 1.0f);
    assertEquals(expected, actual, 0.001f);
  }

  public void testScoreCombinationStrategyWeightedAverage() {
    float textScore = 0.8f;
    float graphScore = 0.4f;
    float textWeight = 2.0f;
    float graphWeight = 1.0f;
    float expected = (textScore * textWeight + graphScore * graphWeight) / (textWeight + graphWeight);

    float actual =
        ScoreCombinationStrategy.WEIGHTED_AVERAGE.combineScores(
            textScore, graphScore, textWeight, graphWeight);
    assertEquals(expected, actual, 0.001f);
  }

  public void testScoreCombinationStrategyWeightedAverageWithZeroWeights() {
    float result =
        ScoreCombinationStrategy.WEIGHTED_AVERAGE.combineScores(0.8f, 0.5f, 0.0f, 0.0f);
    assertEquals(0.0f, result, 0.0f);
  }

  public void testBuilderChaining() {
    HybridTextGraphQuery.Builder builder = new HybridTextGraphQuery.Builder();
    assertSame(builder, builder.setTextQuery(textQuery));
    assertSame(builder, builder.setGraphQuery(graphQuery));
    assertSame(builder, builder.setScoreCombinationStrategy(ScoreCombinationStrategy.MAX));
    assertSame(builder, builder.setTextWeight(1.5f));
    assertSame(builder, builder.setGraphWeight(0.5f));
  }

  public void testEqualsWithNull() {
    HybridTextGraphQuery query =
        new HybridTextGraphQuery.Builder()
            .setTextQuery(textQuery)
            .setGraphQuery(graphQuery)
            .build();

    assertFalse(query.equals(null));
  }

  public void testEqualsWithDifferentClass() {
    HybridTextGraphQuery query =
        new HybridTextGraphQuery.Builder()
            .setTextQuery(textQuery)
            .setGraphQuery(graphQuery)
            .build();

    assertFalse(query.equals("string"));
  }

  public void testRandomizedWeights() {
    float textWeight = random().nextFloat() * 10;
    float graphWeight = random().nextFloat() * 10;

    HybridTextGraphQuery query =
        new HybridTextGraphQuery.Builder()
            .setTextQuery(textQuery)
            .setGraphQuery(graphQuery)
            .setTextWeight(textWeight)
            .setGraphWeight(graphWeight)
            .build();

    assertEquals(textWeight, query.getTextWeight(), 0.001f);
    assertEquals(graphWeight, query.getGraphWeight(), 0.001f);
  }

  public void testRandomizedScoreCombination() {
    float textScore = random().nextFloat();
    float graphScore = random().nextFloat();
    float textWeight = 0.1f + random().nextFloat() * 5;
    float graphWeight = 0.1f + random().nextFloat() * 5;

    // Test all strategies with random values
    for (ScoreCombinationStrategy strategy : ScoreCombinationStrategy.values()) {
      float result = strategy.combineScores(textScore, graphScore, textWeight, graphWeight);
      assertTrue("Result should be finite for " + strategy, Float.isFinite(result));
    }
  }
}

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
package org.apache.lucene.search.similarities;

import java.util.HashMap;
import java.util.Map;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestBM25FSimilarity extends LuceneTestCase {

  public void testDefaultConstructor() {
    BM25FSimilarity bm25f = new BM25FSimilarity();
    assertEquals(BM25FSimilarity.DEFAULT_K1, bm25f.getDefaultK1(), 0.0f);
    assertEquals(BM25FSimilarity.DEFAULT_B, bm25f.getDefaultB(), 0.0f);
    assertEquals(
        BM25FSimilarity.DEFAULT_FIELD_WEIGHT, bm25f.getFieldWeight("anyField"), 0.0f);
    assertEquals(BM25FSimilarity.DEFAULT_K1, bm25f.getFieldK1("anyField"), 0.0f);
    assertEquals(BM25FSimilarity.DEFAULT_B, bm25f.getFieldB("anyField"), 0.0f);
  }

  public void testFieldWeightConstructor() {
    Map<String, Float> weights = new HashMap<>();
    weights.put("title", 2.0f);
    weights.put("body", 1.0f);

    BM25FSimilarity bm25f = new BM25FSimilarity(weights);
    assertEquals(2.0f, bm25f.getFieldWeight("title"), 0.0f);
    assertEquals(1.0f, bm25f.getFieldWeight("body"), 0.0f);
    assertEquals(
        BM25FSimilarity.DEFAULT_FIELD_WEIGHT, bm25f.getFieldWeight("unknown"), 0.0f);

    // Should use defaults for k1 and b
    assertEquals(BM25FSimilarity.DEFAULT_K1, bm25f.getFieldK1("title"), 0.0f);
    assertEquals(BM25FSimilarity.DEFAULT_B, bm25f.getFieldB("title"), 0.0f);
  }

  public void testFullConstructor() {
    Map<String, Float> weights = new HashMap<>();
    weights.put("title", 2.0f);
    weights.put("body", 1.0f);

    Map<String, Float> k1Values = new HashMap<>();
    k1Values.put("title", 1.5f);
    k1Values.put("body", 1.2f);

    Map<String, Float> bValues = new HashMap<>();
    bValues.put("title", 0.5f);
    bValues.put("body", 0.75f);

    BM25FSimilarity bm25f = new BM25FSimilarity(weights, k1Values, bValues, 1.0f, 0.8f);

    assertEquals(2.0f, bm25f.getFieldWeight("title"), 0.0f);
    assertEquals(1.0f, bm25f.getFieldWeight("body"), 0.0f);
    assertEquals(1.5f, bm25f.getFieldK1("title"), 0.0f);
    assertEquals(1.2f, bm25f.getFieldK1("body"), 0.0f);
    assertEquals(0.5f, bm25f.getFieldB("title"), 0.0f);
    assertEquals(0.75f, bm25f.getFieldB("body"), 0.0f);

    // Test defaults for unknown fields
    assertEquals(1.0f, bm25f.getDefaultK1(), 0.0f);
    assertEquals(0.8f, bm25f.getDefaultB(), 0.0f);
    assertEquals(1.0f, bm25f.getFieldK1("unknown"), 0.0f);
    assertEquals(0.8f, bm25f.getFieldB("unknown"), 0.0f);
  }

  public void testIllegalNullMaps() {
    IllegalArgumentException expected =
        expectThrows(
            IllegalArgumentException.class,
            () -> {
              new BM25FSimilarity(null, new HashMap<>(), new HashMap<>());
            });
    assertTrue(expected.getMessage().contains("fieldWeights must not be null"));

    expected =
        expectThrows(
            IllegalArgumentException.class,
            () -> {
              new BM25FSimilarity(new HashMap<>(), null, new HashMap<>());
            });
    assertTrue(expected.getMessage().contains("fieldK1 must not be null"));

    expected =
        expectThrows(
            IllegalArgumentException.class,
            () -> {
              new BM25FSimilarity(new HashMap<>(), new HashMap<>(), null);
            });
    assertTrue(expected.getMessage().contains("fieldB must not be null"));
  }

  public void testIllegalWeights() {
    Map<String, Float> weights = new HashMap<>();
    weights.put("title", 0.0f); // Zero weight is illegal

    IllegalArgumentException expected =
        expectThrows(
            IllegalArgumentException.class,
            () -> {
              new BM25FSimilarity(weights);
            });
    assertTrue(expected.getMessage().contains("illegal weight for field"));

    weights.put("title", -1.0f); // Negative weight is illegal
    expected =
        expectThrows(
            IllegalArgumentException.class,
            () -> {
              new BM25FSimilarity(weights);
            });
    assertTrue(expected.getMessage().contains("illegal weight for field"));

    weights.put("title", Float.NaN); // NaN weight is illegal
    expected =
        expectThrows(
            IllegalArgumentException.class,
            () -> {
              new BM25FSimilarity(weights);
            });
    assertTrue(expected.getMessage().contains("illegal weight for field"));

    weights.put("title", null); // null weight is illegal
    expected =
        expectThrows(
            IllegalArgumentException.class,
            () -> {
              new BM25FSimilarity(weights);
            });
    assertTrue(expected.getMessage().contains("illegal weight for field"));
  }

  public void testIllegalK1() {
    Map<String, Float> k1Values = new HashMap<>();
    k1Values.put("title", Float.POSITIVE_INFINITY); // Infinity is illegal

    IllegalArgumentException expected =
        expectThrows(
            IllegalArgumentException.class,
            () -> {
              new BM25FSimilarity(new HashMap<>(), k1Values, new HashMap<>());
            });
    assertTrue(expected.getMessage().contains("illegal k1 for field"));

    k1Values.put("title", -1.0f); // Negative k1 is illegal
    expected =
        expectThrows(
            IllegalArgumentException.class,
            () -> {
              new BM25FSimilarity(new HashMap<>(), k1Values, new HashMap<>());
            });
    assertTrue(expected.getMessage().contains("illegal k1 for field"));

    k1Values.put("title", Float.NaN); // NaN k1 is illegal
    expected =
        expectThrows(
            IllegalArgumentException.class,
            () -> {
              new BM25FSimilarity(new HashMap<>(), k1Values, new HashMap<>());
            });
    assertTrue(expected.getMessage().contains("illegal k1 for field"));
  }

  public void testIllegalB() {
    Map<String, Float> bValues = new HashMap<>();
    bValues.put("title", 2.0f); // b > 1 is illegal

    IllegalArgumentException expected =
        expectThrows(
            IllegalArgumentException.class,
            () -> {
              new BM25FSimilarity(new HashMap<>(), new HashMap<>(), bValues);
            });
    assertTrue(expected.getMessage().contains("illegal b for field"));

    bValues.put("title", -0.1f); // b < 0 is illegal
    expected =
        expectThrows(
            IllegalArgumentException.class,
            () -> {
              new BM25FSimilarity(new HashMap<>(), new HashMap<>(), bValues);
            });
    assertTrue(expected.getMessage().contains("illegal b for field"));

    bValues.put("title", Float.NaN); // NaN b is illegal
    expected =
        expectThrows(
            IllegalArgumentException.class,
            () -> {
              new BM25FSimilarity(new HashMap<>(), new HashMap<>(), bValues);
            });
    assertTrue(expected.getMessage().contains("illegal b for field"));
  }

  public void testIllegalDefaultK1() {
    IllegalArgumentException expected =
        expectThrows(
            IllegalArgumentException.class,
            () -> {
              new BM25FSimilarity(new HashMap<>(), new HashMap<>(), new HashMap<>(), -1.0f, 0.75f);
            });
    assertTrue(expected.getMessage().contains("illegal default k1 value"));

    expected =
        expectThrows(
            IllegalArgumentException.class,
            () -> {
              new BM25FSimilarity(
                  new HashMap<>(), new HashMap<>(), new HashMap<>(), Float.NaN, 0.75f);
            });
    assertTrue(expected.getMessage().contains("illegal default k1 value"));

    expected =
        expectThrows(
            IllegalArgumentException.class,
            () -> {
              new BM25FSimilarity(
                  new HashMap<>(),
                  new HashMap<>(),
                  new HashMap<>(),
                  Float.POSITIVE_INFINITY,
                  0.75f);
            });
    assertTrue(expected.getMessage().contains("illegal default k1 value"));
  }

  public void testIllegalDefaultB() {
    IllegalArgumentException expected =
        expectThrows(
            IllegalArgumentException.class,
            () -> {
              new BM25FSimilarity(new HashMap<>(), new HashMap<>(), new HashMap<>(), 1.2f, -0.1f);
            });
    assertTrue(expected.getMessage().contains("illegal default b value"));

    expected =
        expectThrows(
            IllegalArgumentException.class,
            () -> {
              new BM25FSimilarity(new HashMap<>(), new HashMap<>(), new HashMap<>(), 1.2f, 1.1f);
            });
    assertTrue(expected.getMessage().contains("illegal default b value"));

    expected =
        expectThrows(
            IllegalArgumentException.class,
            () -> {
              new BM25FSimilarity(
                  new HashMap<>(), new HashMap<>(), new HashMap<>(), 1.2f, Float.NaN);
            });
    assertTrue(expected.getMessage().contains("illegal default b value"));
  }

  public void testToString() {
    Map<String, Float> weights = new HashMap<>();
    weights.put("title", 2.0f);

    BM25FSimilarity bm25f = new BM25FSimilarity(weights);
    String str = bm25f.toString();
    assertTrue(str.contains("BM25F"));
    assertTrue(str.contains("defaultK1=" + BM25FSimilarity.DEFAULT_K1));
    assertTrue(str.contains("defaultB=" + BM25FSimilarity.DEFAULT_B));
  }

  public void testImmutableMaps() {
    Map<String, Float> weights = new HashMap<>();
    weights.put("title", 2.0f);

    BM25FSimilarity bm25f = new BM25FSimilarity(weights);

    // Modifying the original map should not affect the similarity
    weights.put("title", 3.0f);
    assertEquals(2.0f, bm25f.getFieldWeight("title"), 0.0f);

    // Retrieved maps should be immutable
    Map<String, Float> retrievedWeights = bm25f.getFieldWeights();
    expectThrows(
        UnsupportedOperationException.class,
        () -> {
          retrievedWeights.put("newField", 1.0f);
        });
  }

  public void testScoringShouldFailForNow() {
    // This test verifies that scorer() method throws UnsupportedOperationException
    // This will be removed when FEAT-002 is implemented
    BM25FSimilarity bm25f = new BM25FSimilarity();
    expectThrows(
        UnsupportedOperationException.class,
        () -> {
          bm25f.scorer(1.0f, null);
        });
  }
}
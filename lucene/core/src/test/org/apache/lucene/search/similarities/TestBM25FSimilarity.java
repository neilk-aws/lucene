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

import static org.apache.lucene.tests.util.LuceneTestCase.TEST_NIGHTLY;

import org.apache.lucene.search.similarities.BM25FSimilarity.FieldConfig;
import org.apache.lucene.tests.util.LuceneTestCase;

/** Tests for BM25FSimilarity */
public class TestBM25FSimilarity extends LuceneTestCase {

  public void testDefaultConstruction() {
    BM25FSimilarity sim = new BM25FSimilarity.Builder().build();
    assertEquals(1.2f, sim.getK1(), 0.001f);
    assertEquals(0.75f, sim.getDefaultB(), 0.001f);
  }

  public void testCustomK1() {
    BM25FSimilarity sim = new BM25FSimilarity.Builder().setK1(2.0f).build();
    assertEquals(2.0f, sim.getK1(), 0.001f);
  }

  public void testCustomDefaultB() {
    BM25FSimilarity sim = new BM25FSimilarity.Builder().setDefaultB(0.5f).build();
    assertEquals(0.5f, sim.getDefaultB(), 0.001f);
  }

  public void testFieldConfig() {
    BM25FSimilarity sim =
        new BM25FSimilarity.Builder().addFieldConfig("title", 2.0f, 0.5f).build();

    FieldConfig config = sim.getFieldConfig("title");
    assertNotNull(config);
    assertEquals(2.0f, config.boost, 0.001f);
    assertEquals(0.5f, config.b, 0.001f);
  }

  public void testMultipleFieldConfigs() {
    BM25FSimilarity sim =
        new BM25FSimilarity.Builder()
            .addFieldConfig("title", 3.0f, 0.5f)
            .addFieldConfig("body", 1.0f, 0.75f)
            .addFieldConfig("anchor", 1.5f, 0.6f)
            .build();

    FieldConfig titleConfig = sim.getFieldConfig("title");
    assertEquals(3.0f, titleConfig.boost, 0.001f);
    assertEquals(0.5f, titleConfig.b, 0.001f);

    FieldConfig bodyConfig = sim.getFieldConfig("body");
    assertEquals(1.0f, bodyConfig.boost, 0.001f);
    assertEquals(0.75f, bodyConfig.b, 0.001f);

    FieldConfig anchorConfig = sim.getFieldConfig("anchor");
    assertEquals(1.5f, anchorConfig.boost, 0.001f);
    assertEquals(0.6f, anchorConfig.b, 0.001f);
  }

  public void testUnconfiguredField() {
    BM25FSimilarity sim =
        new BM25FSimilarity.Builder().addFieldConfig("title", 2.0f, 0.5f).build();

    assertNull(sim.getFieldConfig("body"));
  }

  public void testPerFieldSimilarity() {
    BM25FSimilarity sim =
        new BM25FSimilarity.Builder()
            .setK1(1.5f)
            .setDefaultB(0.8f)
            .addFieldConfig("title", 2.0f, 0.5f)
            .build();

    // Get similarity for configured field
    Similarity titleSim = sim.get("title");
    assertNotNull(titleSim);
    assertTrue(titleSim instanceof BM25Similarity);

    // Get similarity for unconfigured field (should use defaults)
    Similarity bodySim = sim.get("body");
    assertNotNull(bodySim);
    assertTrue(bodySim instanceof BM25Similarity);

    // They should be different instances with different configs
    assertNotSame(titleSim, bodySim);
  }

  public void testInvalidBoost() {
    expectThrows(
        IllegalArgumentException.class,
        () -> {
          new FieldConfig(0.0f, 0.5f);
        });

    expectThrows(
        IllegalArgumentException.class,
        () -> {
          new FieldConfig(-1.0f, 0.5f);
        });

    expectThrows(
        IllegalArgumentException.class,
        () -> {
          new FieldConfig(Float.NaN, 0.5f);
        });

    expectThrows(
        IllegalArgumentException.class,
        () -> {
          new FieldConfig(Float.POSITIVE_INFINITY, 0.5f);
        });
  }

  public void testInvalidB() {
    expectThrows(
        IllegalArgumentException.class,
        () -> {
          new FieldConfig(1.0f, -0.1f);
        });

    expectThrows(
        IllegalArgumentException.class,
        () -> {
          new FieldConfig(1.0f, 1.1f);
        });

    expectThrows(
        IllegalArgumentException.class,
        () -> {
          new FieldConfig(1.0f, Float.NaN);
        });
  }

  public void testBoundaryValues() {
    // Test b = 0 (no length normalization)
    FieldConfig config1 = new FieldConfig(1.0f, 0.0f);
    assertEquals(0.0f, config1.b, 0.0f);

    // Test b = 1 (full length normalization)
    FieldConfig config2 = new FieldConfig(1.0f, 1.0f);
    assertEquals(1.0f, config2.b, 0.0f);

    // Test very small boost
    FieldConfig config3 = new FieldConfig(0.01f, 0.5f);
    assertEquals(0.01f, config3.boost, 0.0001f);

    // Test large boost
    FieldConfig config4 = new FieldConfig(100.0f, 0.5f);
    assertEquals(100.0f, config4.boost, 0.01f);
  }

  public void testBuilderChaining() {
    BM25FSimilarity sim =
        new BM25FSimilarity.Builder()
            .setK1(2.0f)
            .setDefaultB(0.8f)
            .setDiscountOverlaps(false)
            .addFieldConfig("field1", 1.5f, 0.6f)
            .addFieldConfig("field2", 2.0f, 0.7f)
            .build();

    assertEquals(2.0f, sim.getK1(), 0.001f);
    assertEquals(0.8f, sim.getDefaultB(), 0.001f);
    assertNotNull(sim.getFieldConfig("field1"));
    assertNotNull(sim.getFieldConfig("field2"));
  }

  public void testToString() {
    BM25FSimilarity sim =
        new BM25FSimilarity.Builder()
            .setK1(1.5f)
            .setDefaultB(0.7f)
            .addFieldConfig("title", 2.0f, 0.5f)
            .addFieldConfig("body", 1.0f, 0.75f)
            .build();

    String str = sim.toString();
    assertTrue(str.contains("BM25F"));
    assertTrue(str.contains("k1=1.5"));
    assertTrue(str.contains("defaultB=0.7"));
  }

  public void testRandomConfigurations() {
    if (!TEST_NIGHTLY) {
      return; // Only run extensive random tests in nightly mode
    }

    for (int i = 0; i < 100; i++) {
      float k1 = random().nextFloat() * 3.0f; // 0 to 3.0
      float defaultB = random().nextFloat(); // 0 to 1.0
      int numFields = random().nextInt(10) + 1; // 1 to 10 fields

      BM25FSimilarity.Builder builder = new BM25FSimilarity.Builder();
      builder.setK1(k1);
      builder.setDefaultB(defaultB);
      builder.setDiscountOverlaps(random().nextBoolean());

      for (int j = 0; j < numFields; j++) {
        String fieldName = "field" + j;
        float boost = random().nextFloat() * 5.0f + 0.1f; // 0.1 to 5.1
        float b = random().nextFloat(); // 0 to 1.0
        builder.addFieldConfig(fieldName, boost, b);
      }

      BM25FSimilarity sim = builder.build();
      assertNotNull(sim);
      assertEquals(k1, sim.getK1(), 0.001f);
      assertEquals(defaultB, sim.getDefaultB(), 0.001f);

      // Verify all fields are configured
      for (int j = 0; j < numFields; j++) {
        String fieldName = "field" + j;
        assertNotNull(sim.getFieldConfig(fieldName));
      }
    }
  }
}

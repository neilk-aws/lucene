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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * BM25F Similarity - an extension of BM25 that handles multiple fields with different weights
 * and length normalization parameters.
 *
 * <p>BM25F (BM25 for Fields) is described in: Stephen Robertson, Hugo Zaragoza, and Michael
 * Taylor. "Simple BM25 extension to multiple weighted fields." In Proceedings of the thirteenth
 * ACM international conference on Information and knowledge management (CIKM '04), 2004.
 *
 * <p>Unlike standard BM25, BM25F allows:
 *
 * <ul>
 *   <li>Different field weights (boosts) for different fields
 *   <li>Different length normalization parameters (b) for different fields
 *   <li>Proper aggregation of term frequencies across multiple fields
 * </ul>
 *
 * <h2>Example Usage:</h2>
 *
 * <pre class="prettyprint">
 * // Create BM25F with default global k1 parameter
 * BM25FSimilarity.Builder builder = new BM25FSimilarity.Builder();
 *
 * // Configure per-field parameters
 * builder.addFieldConfig("title", 2.0f, 0.5f);    // Higher weight, less length normalization
 * builder.addFieldConfig("body", 1.0f, 0.75f);    // Standard weight and normalization
 * builder.addFieldConfig("anchor", 1.5f, 0.6f);   // Moderate weight and normalization
 *
 * BM25FSimilarity similarity = builder.build();
 * indexSearcher.setSimilarity(similarity);
 * </pre>
 *
 * <p>For optimal results, use with {@link
 * org.apache.lucene.queryparser.classic.BM25FQueryParser} which properly handles multi-field
 * queries.
 *
 * @see BM25Similarity
 * @see PerFieldSimilarityWrapper
 */
public class BM25FSimilarity extends PerFieldSimilarityWrapper {

  /** Global k1 parameter controlling term frequency saturation */
  private final float k1;

  /** Default b parameter for fields not explicitly configured */
  private final float defaultB;

  /** Whether to discount overlapping tokens from document length */
  private final boolean discountOverlaps;

  /** Per-field configurations mapping field name to FieldConfig */
  private final Map<String, FieldConfig> fieldConfigs;

  /** Default similarity used for fields without specific configuration */
  private final BM25Similarity defaultSimilarity;

  /**
   * Configuration for a specific field in BM25F.
   *
   * <p>Each field can have its own:
   *
   * <ul>
   *   <li><b>boost</b>: Relative importance of the field (default: 1.0)
   *   <li><b>b</b>: Length normalization parameter (0 = no normalization, 1 = full normalization)
   * </ul>
   */
  public static class FieldConfig {
    /** Field boost/weight - higher values make this field more important */
    public final float boost;

    /** Length normalization parameter for this field (0..1) */
    public final float b;

    /**
     * Creates a field configuration.
     *
     * @param boost Field weight/importance (typically 0.5 to 5.0)
     * @param b Length normalization parameter (0.0 to 1.0)
     * @throws IllegalArgumentException if parameters are out of valid range
     */
    public FieldConfig(float boost, float b) {
      if (boost <= 0 || Float.isNaN(boost) || Float.isInfinite(boost)) {
        throw new IllegalArgumentException(
            "boost must be positive and finite, got: " + boost);
      }
      if (b < 0 || b > 1 || Float.isNaN(b)) {
        throw new IllegalArgumentException("b must be between 0 and 1, got: " + b);
      }
      this.boost = boost;
      this.b = b;
    }
  }

  /**
   * Builder for constructing BM25FSimilarity with per-field configurations.
   *
   * <p>Example:
   *
   * <pre>{@code
   * BM25FSimilarity similarity = new BM25FSimilarity.Builder()
   *     .setK1(1.2f)
   *     .setDefaultB(0.75f)
   *     .addFieldConfig("title", 3.0f, 0.5f)
   *     .addFieldConfig("body", 1.0f, 0.75f)
   *     .build();
   * }</pre>
   */
  public static class Builder {
    private float k1 = 1.2f;
    private float defaultB = 0.75f;
    private boolean discountOverlaps = true;
    private final Map<String, FieldConfig> fieldConfigs = new HashMap<>();

    /**
     * Sets the global k1 parameter (term frequency saturation).
     *
     * @param k1 Controls how quickly term frequency saturates (typical: 1.2)
     * @return this builder
     */
    public Builder setK1(float k1) {
      this.k1 = k1;
      return this;
    }

    /**
     * Sets the default b parameter for fields without explicit configuration.
     *
     * @param defaultB Default length normalization (typical: 0.75)
     * @return this builder
     */
    public Builder setDefaultB(float defaultB) {
      this.defaultB = defaultB;
      return this;
    }

    /**
     * Sets whether to discount overlapping tokens from document length.
     *
     * @param discountOverlaps true to discount overlaps (default: true)
     * @return this builder
     */
    public Builder setDiscountOverlaps(boolean discountOverlaps) {
      this.discountOverlaps = discountOverlaps;
      return this;
    }

    /**
     * Adds configuration for a specific field.
     *
     * @param fieldName Name of the field
     * @param boost Field weight/importance (e.g., 2.0 = twice as important)
     * @param b Length normalization for this field (0.0 to 1.0)
     * @return this builder
     */
    public Builder addFieldConfig(String fieldName, float boost, float b) {
      fieldConfigs.put(fieldName, new FieldConfig(boost, b));
      return this;
    }

    /** Builds the BM25FSimilarity with configured parameters. */
    public BM25FSimilarity build() {
      return new BM25FSimilarity(k1, defaultB, discountOverlaps, fieldConfigs);
    }
  }

  /**
   * Constructs BM25FSimilarity with specified parameters.
   *
   * @param k1 Global k1 parameter
   * @param defaultB Default b parameter for unconfigured fields
   * @param discountOverlaps Whether to discount overlap tokens
   * @param fieldConfigs Per-field configurations
   */
  private BM25FSimilarity(
      float k1, float defaultB, boolean discountOverlaps, Map<String, FieldConfig> fieldConfigs) {
    this.k1 = k1;
    this.defaultB = defaultB;
    this.discountOverlaps = discountOverlaps;
    this.fieldConfigs = Collections.unmodifiableMap(new HashMap<>(fieldConfigs));
    this.defaultSimilarity = new BM25Similarity(k1, defaultB, discountOverlaps);
  }

  /**
   * Returns the configured k1 parameter.
   *
   * @return k1 value
   */
  public float getK1() {
    return k1;
  }

  /**
   * Returns the default b parameter.
   *
   * @return default b value
   */
  public float getDefaultB() {
    return defaultB;
  }

  /**
   * Returns the field configuration for a given field, or null if not configured.
   *
   * @param fieldName The field name
   * @return FieldConfig or null
   */
  public FieldConfig getFieldConfig(String fieldName) {
    return fieldConfigs.get(fieldName);
  }

  /**
   * Returns a BM25Similarity instance for the specified field.
   *
   * <p>If the field has specific configuration, returns a customized BM25Similarity. Otherwise,
   * returns the default BM25Similarity.
   *
   * @param fieldName The field name
   * @return BM25Similarity instance for this field
   */
  @Override
  public Similarity get(String fieldName) {
    FieldConfig config = fieldConfigs.get(fieldName);
    if (config == null) {
      return defaultSimilarity;
    }
    // Create a custom BM25Similarity with field-specific parameters
    return new FieldBM25Similarity(k1, config.b, discountOverlaps, config.boost);
  }

  /**
   * Extension of BM25Similarity that applies a field-level boost.
   *
   * <p>This internal class combines the BM25 scoring with a field-specific boost factor to
   * implement the BM25F multi-field weighting scheme.
   */
  private static class FieldBM25Similarity extends BM25Similarity {
    private final float fieldBoost;

    FieldBM25Similarity(float k1, float b, boolean discountOverlaps, float fieldBoost) {
      super(k1, b, discountOverlaps);
      this.fieldBoost = fieldBoost;
    }

    @Override
    protected float idf(long docFreq, long docCount) {
      // Apply field boost to IDF
      return super.idf(docFreq, docCount) * fieldBoost;
    }

    @Override
    public String toString() {
      return "FieldBM25(k1=" + getK1() + ",b=" + getB() + ",boost=" + fieldBoost + ")";
    }
  }

  @Override
  public String toString() {
    return "BM25F(k1=" + k1 + ",defaultB=" + defaultB + ",fields=" + fieldConfigs.size() + ")";
  }
}

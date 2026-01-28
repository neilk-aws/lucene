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
import org.apache.lucene.search.CollectionStatistics;
import org.apache.lucene.search.Explanation;
import org.apache.lucene.search.TermStatistics;

/**
 * BM25F Similarity implementation for multi-field scoring.
 *
 * <p>BM25F is an extension of the BM25 ranking algorithm that supports scoring across multiple
 * fields with field-specific parameters. Unlike standard BM25 which scores each field
 * independently, BM25F combines weighted term frequencies from multiple fields before applying the
 * BM25 formula, allowing for more nuanced multi-field ranking.
 *
 * <p>The key differences from BM25 are:
 *
 * <ul>
 *   <li><b>Field Weights:</b> Each field can have a different importance weight, allowing some
 *       fields (e.g., title) to contribute more to the final score than others (e.g., body text).
 *   <li><b>Per-field Length Normalization:</b> Each field can have its own {@code b} parameter,
 *       controlling how much document length affects term frequency normalization for that specific
 *       field.
 *   <li><b>Per-field Saturation:</b> Each field can have its own {@code k1} parameter, controlling
 *       the term frequency saturation for that field.
 *   <li><b>Combined Scoring:</b> Weighted term frequencies from all fields are aggregated before
 *       applying the BM25 scoring formula, rather than scoring fields independently and combining
 *       the results.
 * </ul>
 *
 * <p>The BM25F score is computed as:
 *
 * <pre>
 * score(q,d) = Σ(t∈q) IDF(t) × (Σ(f∈fields) w_f × tf_f,d × (k1_f + 1)) / 
 *                               (Σ(f∈fields) w_f × tf_f,d + k1_f × (1 - b_f + b_f × |d_f| / avgdl_f))
 * </pre>
 *
 * where:
 *
 * <ul>
 *   <li>{@code t} is a query term
 *   <li>{@code q} is the query
 *   <li>{@code d} is a document
 *   <li>{@code f} is a field
 *   <li>{@code w_f} is the weight for field {@code f}
 *   <li>{@code tf_f,d} is the term frequency in field {@code f} of document {@code d}
 *   <li>{@code k1_f} is the saturation parameter for field {@code f}
 *   <li>{@code b_f} is the length normalization parameter for field {@code f}
 *   <li>{@code |d_f|} is the length of field {@code f} in document {@code d}
 *   <li>{@code avgdl_f} is the average length of field {@code f} across all documents
 * </ul>
 *
 * <p><b>Usage Example:</b>
 *
 * <pre class="prettyprint">
 * // Create BM25F with field-specific weights and parameters
 * Map&lt;String, Float&gt; fieldWeights = new HashMap&lt;&gt;();
 * fieldWeights.put("title", 2.0f);    // Title is twice as important
 * fieldWeights.put("body", 1.0f);     // Body has standard weight
 * fieldWeights.put("tags", 1.5f);     // Tags are 1.5x as important
 *
 * Map&lt;String, Float&gt; fieldK1 = new HashMap&lt;&gt;();
 * fieldK1.put("title", 1.5f);  // Higher saturation for title
 * fieldK1.put("body", 1.2f);   // Standard saturation for body
 *
 * Map&lt;String, Float&gt; fieldB = new HashMap&lt;&gt;();
 * fieldB.put("title", 0.5f);   // Less length normalization for title
 * fieldB.put("body", 0.75f);   // Standard length normalization for body
 *
 * BM25FSimilarity bm25f = new BM25FSimilarity(fieldWeights, fieldK1, fieldB);
 * indexWriterConfig.setSimilarity(bm25f);
 * </pre>
 *
 * <p><b>Default Values:</b> If field-specific parameters are not provided, the following defaults
 * are used:
 *
 * <ul>
 *   <li>Default field weight: {@code 1.0}
 *   <li>Default k1: {@code 1.2}
 *   <li>Default b: {@code 0.75}
 * </ul>
 *
 * <p><b>Note:</b> BM25F requires that all queried fields are configured with this similarity at
 * index time. Mixing BM25F with other similarity implementations for the same query may produce
 * unexpected results.
 *
 * @see BM25Similarity
 * @see PerFieldSimilarityWrapper
 * @lucene.experimental
 */
public class BM25FSimilarity extends Similarity {

  /** Default k1 value: controls term frequency saturation. */
  public static final float DEFAULT_K1 = 1.2f;

  /** Default b value: controls length normalization. */
  public static final float DEFAULT_B = 0.75f;

  /** Default field weight: used when no specific weight is configured for a field. */
  public static final float DEFAULT_FIELD_WEIGHT = 1.0f;

  /** Field-specific weights (boost factors) for scoring. */
  private final Map<String, Float> fieldWeights;

  /** Field-specific k1 parameters for term frequency saturation. */
  private final Map<String, Float> fieldK1;

  /** Field-specific b parameters for length normalization. */
  private final Map<String, Float> fieldB;

  /** Default k1 value to use when a field doesn't have a specific k1 configured. */
  private final float defaultK1;

  /** Default b value to use when a field doesn't have a specific b configured. */
  private final float defaultB;

  /**
   * BM25F with custom field weights and per-field parameters.
   *
   * @param fieldWeights Map of field names to their relative weights (boost factors). Higher
   *     weights give more importance to matches in that field. Must not be null, but can be empty.
   *     Fields not in this map will use {@link #DEFAULT_FIELD_WEIGHT}.
   * @param fieldK1 Map of field names to their k1 parameters (term frequency saturation). Must not
   *     be null, but can be empty. Fields not in this map will use the default k1 value.
   * @param fieldB Map of field names to their b parameters (length normalization). Must not be
   *     null, but can be empty. Fields not in this map will use the default b value.
   * @param defaultK1 Default k1 value for fields not specified in fieldK1.
   * @param defaultB Default b value for fields not specified in fieldB.
   * @param discountOverlaps True if overlap tokens (tokens with a position of increment of zero)
   *     are discounted from the document's length.
   * @throws IllegalArgumentException if any weights are non-positive, if any k1 values are
   *     infinite or negative, if any b values are not within the range [0..1], or if any map is
   *     null.
   */
  public BM25FSimilarity(
      Map<String, Float> fieldWeights,
      Map<String, Float> fieldK1,
      Map<String, Float> fieldB,
      float defaultK1,
      float defaultB,
      boolean discountOverlaps) {
    super(discountOverlaps);

    // Validate that maps are not null
    if (fieldWeights == null) {
      throw new IllegalArgumentException("fieldWeights must not be null");
    }
    if (fieldK1 == null) {
      throw new IllegalArgumentException("fieldK1 must not be null");
    }
    if (fieldB == null) {
      throw new IllegalArgumentException("fieldB must not be null");
    }

    // Validate default k1
    if (Float.isFinite(defaultK1) == false || defaultK1 < 0) {
      throw new IllegalArgumentException(
          "illegal default k1 value: " + defaultK1 + ", must be a non-negative finite value");
    }

    // Validate default b
    if (Float.isNaN(defaultB) || defaultB < 0 || defaultB > 1) {
      throw new IllegalArgumentException(
          "illegal default b value: " + defaultB + ", must be between 0 and 1");
    }

    // Validate field weights
    for (Map.Entry<String, Float> entry : fieldWeights.entrySet()) {
      Float weight = entry.getValue();
      if (weight == null || Float.isNaN(weight) || weight <= 0) {
        throw new IllegalArgumentException(
            "illegal weight for field '"
                + entry.getKey()
                + "': "
                + weight
                + ", must be positive");
      }
    }

    // Validate field k1 values
    for (Map.Entry<String, Float> entry : fieldK1.entrySet()) {
      Float k1 = entry.getValue();
      if (k1 == null || Float.isFinite(k1) == false || k1 < 0) {
        throw new IllegalArgumentException(
            "illegal k1 for field '"
                + entry.getKey()
                + "': "
                + k1
                + ", must be a non-negative finite value");
      }
    }

    // Validate field b values
    for (Map.Entry<String, Float> entry : fieldB.entrySet()) {
      Float b = entry.getValue();
      if (b == null || Float.isNaN(b) || b < 0 || b > 1) {
        throw new IllegalArgumentException(
            "illegal b for field '" + entry.getKey() + "': " + b + ", must be between 0 and 1");
      }
    }

    // Create immutable copies of the maps
    this.fieldWeights = Collections.unmodifiableMap(new HashMap<>(fieldWeights));
    this.fieldK1 = Collections.unmodifiableMap(new HashMap<>(fieldK1));
    this.fieldB = Collections.unmodifiableMap(new HashMap<>(fieldB));
    this.defaultK1 = defaultK1;
    this.defaultB = defaultB;
  }

  /**
   * BM25F with custom field weights and per-field parameters, using default overlap token
   * handling.
   *
   * @param fieldWeights Map of field names to their relative weights (boost factors).
   * @param fieldK1 Map of field names to their k1 parameters.
   * @param fieldB Map of field names to their b parameters.
   * @param defaultK1 Default k1 value for fields not specified in fieldK1.
   * @param defaultB Default b value for fields not specified in fieldB.
   * @throws IllegalArgumentException if any parameter is invalid.
   */
  public BM25FSimilarity(
      Map<String, Float> fieldWeights,
      Map<String, Float> fieldK1,
      Map<String, Float> fieldB,
      float defaultK1,
      float defaultB) {
    this(fieldWeights, fieldK1, fieldB, defaultK1, defaultB, true);
  }

  /**
   * BM25F with custom field weights and per-field parameters, using standard defaults for k1 and
   * b.
   *
   * @param fieldWeights Map of field names to their relative weights (boost factors).
   * @param fieldK1 Map of field names to their k1 parameters.
   * @param fieldB Map of field names to their b parameters.
   * @throws IllegalArgumentException if any parameter is invalid.
   */
  public BM25FSimilarity(
      Map<String, Float> fieldWeights, Map<String, Float> fieldK1, Map<String, Float> fieldB) {
    this(fieldWeights, fieldK1, fieldB, DEFAULT_K1, DEFAULT_B, true);
  }

  /**
   * BM25F with custom field weights only, using default k1 and b for all fields.
   *
   * @param fieldWeights Map of field names to their relative weights (boost factors).
   * @throws IllegalArgumentException if any parameter is invalid.
   */
  public BM25FSimilarity(Map<String, Float> fieldWeights) {
    this(fieldWeights, new HashMap<>(), new HashMap<>(), DEFAULT_K1, DEFAULT_B, true);
  }

  /**
   * BM25F with default parameters for all fields (uniform weights, standard k1 and b).
   *
   * <p>This constructor is primarily useful as a base configuration that can be extended by
   * subclasses or when all fields should be treated equally.
   */
  public BM25FSimilarity() {
    this(new HashMap<>(), new HashMap<>(), new HashMap<>(), DEFAULT_K1, DEFAULT_B, true);
  }

  /**
   * Returns the weight (boost factor) for the specified field.
   *
   * @param fieldName the name of the field
   * @return the weight for this field, or {@link #DEFAULT_FIELD_WEIGHT} if no specific weight is
   *     configured
   */
  public float getFieldWeight(String fieldName) {
    return fieldWeights.getOrDefault(fieldName, DEFAULT_FIELD_WEIGHT);
  }

  /**
   * Returns an immutable map of all configured field weights.
   *
   * @return map of field names to their weights
   */
  public Map<String, Float> getFieldWeights() {
    return fieldWeights;
  }

  /**
   * Returns the k1 parameter for the specified field.
   *
   * @param fieldName the name of the field
   * @return the k1 parameter for this field, or the default k1 if no specific value is configured
   */
  public float getFieldK1(String fieldName) {
    return fieldK1.getOrDefault(fieldName, defaultK1);
  }

  /**
   * Returns an immutable map of all configured field-specific k1 parameters.
   *
   * @return map of field names to their k1 parameters
   */
  public Map<String, Float> getFieldK1Map() {
    return fieldK1;
  }

  /**
   * Returns the b parameter for the specified field.
   *
   * @param fieldName the name of the field
   * @return the b parameter for this field, or the default b if no specific value is configured
   */
  public float getFieldB(String fieldName) {
    return fieldB.getOrDefault(fieldName, defaultB);
  }

  /**
   * Returns an immutable map of all configured field-specific b parameters.
   *
   * @return map of field names to their b parameters
   */
  public Map<String, Float> getFieldBMap() {
    return fieldB;
  }

  /**
   * Returns the default k1 parameter used for fields without specific configuration.
   *
   * @return the default k1 value
   */
  public float getDefaultK1() {
    return defaultK1;
  }

  /**
   * Returns the default b parameter used for fields without specific configuration.
   *
   * @return the default b value
   */
  public float getDefaultB() {
    return defaultB;
  }

  @Override
  public final SimScorer scorer(
      float boost, CollectionStatistics collectionStats, TermStatistics... termStats) {
    // TODO: Implement in FEAT-002 - Core Similarity - Scoring
    // This is a placeholder implementation that will be replaced with the full BM25F scoring logic
    throw new UnsupportedOperationException(
        "BM25FSimilarity scoring not yet implemented - see FEAT-002");
  }

  @Override
  public String toString() {
    return "BM25F(defaultK1="
        + defaultK1
        + ", defaultB="
        + defaultB
        + ", fieldWeights="
        + fieldWeights.size()
        + " fields"
        + ", fieldK1="
        + fieldK1.size()
        + " fields"
        + ", fieldB="
        + fieldB.size()
        + " fields"
        + ")";
  }
}

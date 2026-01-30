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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.lucene.search.CollectionStatistics;
import org.apache.lucene.search.Explanation;
import org.apache.lucene.search.TermStatistics;
import org.apache.lucene.util.SmallFloat;

/**
 * BM25F Similarity for multi-field ranking. BM25F extends BM25 by combining term frequencies
 * across multiple fields with field-specific parameters.
 *
 * <p>This implementation allows configuration of:
 *
 * <ul>
 *   <li>Field-specific boosts (weights) - controls relative importance of fields
 *   <li>Field-specific b parameters - controls length normalization per field
 *   <li>Global k1 parameter - controls term frequency saturation
 * </ul>
 *
 * <p>The BM25F score is computed as: IDF * (combined_tf / (combined_tf + k1))
 *
 * <p>where combined_tf aggregates normalized term frequencies across fields:
 *
 * <pre>
 * combined_tf = sum over fields: (field_boost * field_tf) / (1 - b + b * field_length / avg_field_length)
 * </pre>
 *
 * <p>Reference: Hugo Zaragoza, Nick Craswell, Michael Taylor, Suchi Saria, and Stephen Robertson.
 * "Microsoft Cambridge at TREC-13: Web and HARD tracks." In Proceedings of TREC, 2004.
 */
public class BM25FSimilarity extends Similarity {
  private final float k1;
  private final Map<String, Float> fieldBoosts;
  private final Map<String, Float> fieldBParams;
  private final float defaultB;

  /**
   * Creates a BM25FSimilarity with custom field parameters.
   *
   * @param k1 Controls non-linear term frequency normalization (saturation). Typical value: 1.2
   * @param defaultB Default length normalization parameter for fields not specified. Typical value:
   *     0.75
   * @param fieldBoosts Map of field names to boost values (relative importance)
   * @param fieldBParams Map of field names to b values (length normalization per field)
   * @param discountOverlaps True if overlap tokens should be discounted from document length
   * @throws IllegalArgumentException if k1 is negative or infinite, or b values are not in [0..1]
   */
  public BM25FSimilarity(
      float k1,
      float defaultB,
      Map<String, Float> fieldBoosts,
      Map<String, Float> fieldBParams,
      boolean discountOverlaps) {
    super(discountOverlaps);
    if (Float.isFinite(k1) == false || k1 < 0) {
      throw new IllegalArgumentException(
          "illegal k1 value: " + k1 + ", must be a non-negative finite value");
    }
    if (Float.isNaN(defaultB) || defaultB < 0 || defaultB > 1) {
      throw new IllegalArgumentException(
          "illegal defaultB value: " + defaultB + ", must be between 0 and 1");
    }
    this.k1 = k1;
    this.defaultB = defaultB;
    this.fieldBoosts = fieldBoosts != null ? new HashMap<>(fieldBoosts) : new HashMap<>();
    this.fieldBParams = fieldBParams != null ? new HashMap<>(fieldBParams) : new HashMap<>();

    // Validate all b parameters
    for (Map.Entry<String, Float> entry : this.fieldBParams.entrySet()) {
      float b = entry.getValue();
      if (Float.isNaN(b) || b < 0 || b > 1) {
        throw new IllegalArgumentException(
            "illegal b value for field "
                + entry.getKey()
                + ": "
                + b
                + ", must be between 0 and 1");
      }
    }
  }

  /**
   * Creates a BM25FSimilarity with custom field parameters.
   *
   * @param k1 Controls non-linear term frequency normalization (saturation)
   * @param defaultB Default length normalization parameter
   * @param fieldBoosts Map of field names to boost values
   * @param fieldBParams Map of field names to b values
   */
  public BM25FSimilarity(
      float k1,
      float defaultB,
      Map<String, Float> fieldBoosts,
      Map<String, Float> fieldBParams) {
    this(k1, defaultB, fieldBoosts, fieldBParams, true);
  }

  /**
   * Creates a BM25FSimilarity with equal field boosts.
   *
   * @param k1 Controls non-linear term frequency normalization (saturation)
   * @param defaultB Default length normalization parameter
   */
  public BM25FSimilarity(float k1, float defaultB) {
    this(k1, defaultB, null, null, true);
  }

  /** Creates a BM25FSimilarity with default parameters (k1=1.2, b=0.75). */
  public BM25FSimilarity() {
    this(1.2f, 0.75f, null, null, true);
  }

  /**
   * Sets the boost for a specific field.
   *
   * @param field Field name
   * @param boost Boost value (relative importance of this field)
   */
  public void setFieldBoost(String field, float boost) {
    if (boost < 0) {
      throw new IllegalArgumentException("Field boost must be non-negative");
    }
    this.fieldBoosts.put(field, boost);
  }

  /**
   * Sets the b parameter for a specific field.
   *
   * @param field Field name
   * @param b Length normalization parameter for this field (0 = no normalization, 1 = full
   *     normalization)
   */
  public void setFieldBParam(String field, float b) {
    if (Float.isNaN(b) || b < 0 || b > 1) {
      throw new IllegalArgumentException("Field b parameter must be between 0 and 1");
    }
    this.fieldBParams.put(field, b);
  }

  /**
   * Gets the boost for a specific field.
   *
   * @param field Field name
   * @return Boost value, or 1.0 if not specified
   */
  public float getFieldBoost(String field) {
    return fieldBoosts.getOrDefault(field, 1.0f);
  }

  /**
   * Gets the b parameter for a specific field.
   *
   * @param field Field name
   * @return B parameter, or defaultB if not specified
   */
  public float getFieldBParam(String field) {
    return fieldBParams.getOrDefault(field, defaultB);
  }

  /** Computes IDF using standard BM25 formula. */
  protected float idf(long docFreq, long docCount) {
    return (float) Math.log(1 + (docCount - docFreq + 0.5D) / (docFreq + 0.5D));
  }

  /** Computes average field length. */
  protected float avgFieldLength(CollectionStatistics collectionStats) {
    return (float) (collectionStats.sumTotalTermFreq() / (double) collectionStats.docCount());
  }

  /** Cache of decoded bytes. */
  private static final float[] LENGTH_TABLE = new float[256];

  static {
    for (int i = 0; i < 256; i++) {
      LENGTH_TABLE[i] = SmallFloat.byte4ToInt((byte) i);
    }
  }

  /**
   * Computes IDF explanation for a single term.
   *
   * @param collectionStats Collection-level statistics
   * @param termStats Term-level statistics
   * @return Explanation of IDF computation
   */
  public Explanation idfExplain(CollectionStatistics collectionStats, TermStatistics termStats) {
    final long df = termStats.docFreq();
    final long docCount = collectionStats.docCount();
    final float idf = idf(df, docCount);
    return Explanation.match(
        idf,
        "idf, computed as log(1 + (N - n + 0.5) / (n + 0.5)) from:",
        Explanation.match(df, "n, number of documents containing term"),
        Explanation.match(docCount, "N, total number of documents with field"));
  }

  /**
   * Computes IDF explanation for a phrase (sum of term IDFs).
   *
   * @param collectionStats Collection-level statistics
   * @param termStats Array of term-level statistics
   * @return Explanation of IDF computation
   */
  public Explanation idfExplain(CollectionStatistics collectionStats, TermStatistics[] termStats) {
    double idf = 0d;
    List<Explanation> details = new ArrayList<>();
    for (final TermStatistics stat : termStats) {
      Explanation idfExplain = idfExplain(collectionStats, stat);
      details.add(idfExplain);
      idf += idfExplain.getValue().floatValue();
    }
    return Explanation.match((float) idf, "idf, sum of:", details);
  }

  @Override
  public final SimScorer scorer(
      float boost, CollectionStatistics collectionStats, TermStatistics... termStats) {
    Explanation idf =
        termStats.length == 1
            ? idfExplain(collectionStats, termStats[0])
            : idfExplain(collectionStats, termStats);
    float avgdl = avgFieldLength(collectionStats);

    String fieldName = collectionStats.field();
    float fieldBoost = getFieldBoost(fieldName);
    float fieldB = getFieldBParam(fieldName);

    // Precompute normalization cache for this field
    float[] cache = new float[256];
    for (int i = 0; i < cache.length; i++) {
      float dl = LENGTH_TABLE[i];
      cache[i] = fieldBoost / (1 - fieldB + fieldB * dl / avgdl);
    }

    return new BM25FScorer(boost, k1, idf, avgdl, cache, fieldBoost, fieldB, fieldName);
  }

  /** Scorer implementation for BM25F. */
  private static class BM25FScorer extends SimScorer {
    private final float boost;
    private final float k1;
    private final Explanation idf;
    private final float avgdl;
    private final float[] cache;
    private final float fieldBoost;
    private final float fieldB;
    private final String fieldName;
    private final float weight;

    BM25FScorer(
        float boost,
        float k1,
        Explanation idf,
        float avgdl,
        float[] cache,
        float fieldBoost,
        float fieldB,
        String fieldName) {
      this.boost = boost;
      this.k1 = k1;
      this.idf = idf;
      this.avgdl = avgdl;
      this.cache = cache;
      this.fieldBoost = fieldBoost;
      this.fieldB = fieldB;
      this.fieldName = fieldName;
      this.weight = boost * idf.getValue().floatValue();
    }

    @Override
    public float score(float freq, long encodedNorm) {
      // Get the normalized frequency for this field
      float normTF = cache[((byte) encodedNorm) & 0xFF] * freq;

      // BM25F formula: weight * normTF / (normTF + k1)
      return weight * normTF / (normTF + k1);
    }

    @Override
    public Explanation explain(Explanation freq, long encodedNorm) {
      List<Explanation> subs = new ArrayList<>();

      // Boost explanation
      if (boost != 1.0f) {
        subs.add(Explanation.match(boost, "boost"));
      }

      // IDF explanation
      subs.add(idf);

      // Field boost explanation
      if (fieldBoost != 1.0f) {
        subs.add(Explanation.match(fieldBoost, "fieldBoost for " + fieldName));
      }

      // Length normalization explanation
      float doclen = LENGTH_TABLE[((byte) encodedNorm) & 0xFF];
      subs.add(Explanation.match(fieldB, "b, length normalization parameter for " + fieldName));
      subs.add(Explanation.match(doclen, "dl, length of field " + fieldName));
      subs.add(Explanation.match(avgdl, "avgdl, average length of field " + fieldName));
      subs.add(Explanation.match(k1, "k1, term saturation parameter"));

      // Compute normalized TF
      float normTF = cache[((byte) encodedNorm) & 0xFF] * freq.getValue().floatValue();
      subs.add(
          Explanation.match(
              normTF,
              "normalizedTF, computed as (fieldBoost * freq) / (1 - b + b * dl / avgdl)"));

      // Final score
      float score = weight * normTF / (normTF + k1);
      return Explanation.match(
          score,
          "score(freq="
              + freq.getValue()
              + "), computed as (boost * idf * normalizedTF) / (normalizedTF + k1) from:",
          subs);
    }
  }

  @Override
  public String toString() {
    return "BM25F(k1=" + k1 + ",defaultB=" + defaultB + ")";
  }

  /**
   * Returns the k1 parameter.
   *
   * @return k1 value
   */
  public final float getK1() {
    return k1;
  }

  /**
   * Returns the default b parameter.
   *
   * @return default b value
   */
  public final float getDefaultB() {
    return defaultB;
  }

  /**
   * Returns a copy of the field boosts map.
   *
   * @return Map of field names to boost values
   */
  public final Map<String, Float> getFieldBoosts() {
    return new HashMap<>(fieldBoosts);
  }

  /**
   * Returns a copy of the field b parameters map.
   *
   * @return Map of field names to b values
   */
  public final Map<String, Float> getFieldBParams() {
    return new HashMap<>(fieldBParams);
  }
}

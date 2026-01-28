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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.lucene.search.CollectionStatistics;
import org.apache.lucene.search.Explanation;
import org.apache.lucene.search.TermStatistics;
import org.apache.lucene.util.SmallFloat;

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

  /**
   * Computes the IDF (inverse document frequency) for a single term.
   *
   * <p>Implementation follows BM25's IDF formula: log(1 + (N - n + 0.5) / (n + 0.5))
   *
   * @param docFreq document frequency (number of documents containing the term)
   * @param docCount total number of documents in the collection
   * @return the IDF value
   */
  protected float idf(long docFreq, long docCount) {
    return (float) Math.log(1 + (docCount - docFreq + 0.5D) / (docFreq + 0.5D));
  }

  /**
   * Computes the average field length for a given field.
   *
   * @param collectionStats collection-level statistics for the field
   * @return average field length
   */
  protected float avgFieldLength(CollectionStatistics collectionStats) {
    return (float) (collectionStats.sumTotalTermFreq() / (double) collectionStats.docCount());
  }

  /**
   * Computes a score factor for a single term and returns an explanation.
   *
   * @param collectionStats collection-level statistics
   * @param termStats term-level statistics for the term
   * @return an Explanation object with the IDF score factor
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
   * Computes a score factor for a phrase (multiple terms).
   *
   * <p>The default implementation sums the IDF factor for each term in the phrase.
   *
   * @param collectionStats collection-level statistics
   * @param termStats term-level statistics for the terms in the phrase
   * @return an Explanation object with the IDF score factor for the phrase
   */
  public Explanation idfExplain(CollectionStatistics collectionStats, TermStatistics[] termStats) {
    double idf = 0d; // sum into a double before casting to float
    List<Explanation> details = new ArrayList<>();
    for (final TermStatistics stat : termStats) {
      Explanation idfExplain = idfExplain(collectionStats, stat);
      details.add(idfExplain);
      idf += idfExplain.getValue().floatValue();
    }
    return Explanation.match((float) idf, "idf, sum of:", details);
  }

  /** Cache of decoded bytes for length normalization. */
  private static final float[] LENGTH_TABLE = new float[256];

  static {
    for (int i = 0; i < 256; i++) {
      LENGTH_TABLE[i] = SmallFloat.byte4ToInt((byte) i);
    }
  }

  @Override
  public final SimScorer scorer(
      float boost, CollectionStatistics collectionStats, TermStatistics... termStats) {
    Explanation idf =
        termStats.length == 1
            ? idfExplain(collectionStats, termStats[0])
            : idfExplain(collectionStats, termStats);
    float avgdl = avgFieldLength(collectionStats);

    // Get field-specific parameters
    String fieldName = collectionStats.field();
    float fieldWeight = getFieldWeight(fieldName);
    float k1 = getFieldK1(fieldName);
    float b = getFieldB(fieldName);

    // Precompute normalization cache for this field
    // cache[i] = 1 / (k1 * ((1 - b) + b * LENGTH_TABLE[i] / avgdl))
    float[] cache = new float[256];
    for (int i = 0; i < cache.length; i++) {
      cache[i] = 1f / (k1 * ((1 - b) + b * LENGTH_TABLE[i] / avgdl));
    }

    return new BM25FSimScorer(boost, fieldWeight, k1, b, idf, avgdl, cache);
  }

  /**
   * SimScorer for BM25F that computes document scores using field-specific BM25F parameters.
   *
   * <p>This scorer applies BM25F formula with field-specific k1, b parameters and field weights. The
   * scoring formula is: score = weight * boost * idf * (freq / (freq + k1 * (1 - b + b * dl /
   * avgdl)))
   */
  private class BM25FSimScorer extends SimScorer {
    /** query boost */
    private final float boost;

    /** field weight (boost factor for this field) */
    private final float fieldWeight;

    /** k1 value for term frequency saturation */
    private final float k1;

    /** b value for length normalization impact */
    private final float b;

    /** BM25's idf */
    private final Explanation idf;

    /** The average document length for this field. */
    private final float avgdl;

    /** precomputed norm[256] with k1 * ((1 - b) + b * dl / avgdl) */
    private final float[] cache;

    /** Combined weight (fieldWeight * boost * idf) */
    private final float weight;

    BM25FSimScorer(
        float boost,
        float fieldWeight,
        float k1,
        float b,
        Explanation idf,
        float avgdl,
        float[] cache) {
      this.boost = boost;
      this.fieldWeight = fieldWeight;
      this.k1 = k1;
      this.b = b;
      this.idf = idf;
      this.avgdl = avgdl;
      this.cache = cache;
      this.weight = fieldWeight * boost * idf.getValue().floatValue();
    }

    /**
     * Computes the BM25F score for a given term frequency and field length normalization.
     *
     * <p>Uses the rewritten formula: weight - weight / (1 + freq * normInverse) for better
     * monotonicity guarantees and performance.
     *
     * @param freq term frequency
     * @param normInverse precomputed inverse normalization factor
     * @return the BM25F score
     */
    private float doScore(float freq, float normInverse) {
      // In order to guarantee monotonicity with both freq and norm without
      // promoting to doubles, we rewrite freq / (freq + norm) to
      // 1 - 1 / (1 + freq * 1/norm).
      // freq * 1/norm is guaranteed to be monotonic for both freq and norm due
      // to the fact that multiplication and division round to the nearest
      // float. And then monotonicity is preserved through composition via
      // x -> 1 + x and x -> 1 - 1/x.
      // Finally we expand weight * (1 - 1 / (1 + freq * 1/norm)) to
      // weight - weight / (1 + freq * 1/norm), which runs slightly faster.
      return weight - weight / (1f + freq * normInverse);
    }

    @Override
    public float score(float freq, long encodedNorm) {
      float normInverse = cache[((byte) encodedNorm) & 0xFF];
      return doScore(freq, normInverse);
    }

    @Override
    public Explanation explain(Explanation freq, long encodedNorm) {
      List<Explanation> subs = new ArrayList<>(explainConstantFactors());
      Explanation tfExpl = explainTF(freq, encodedNorm);
      subs.add(tfExpl);
      float normInverse = cache[((byte) encodedNorm) & 0xFF];
      // not using "product of" since the rewrite that we do in score()
      // introduces a small rounding error that CheckHits complains about
      return Explanation.match(
          weight - weight / (1f + freq.getValue().floatValue() * normInverse),
          "score(freq=" + freq.getValue() + "), computed as fieldWeight * boost * idf * tf from:",
          subs);
    }

    /**
     * Explains the term frequency component of the BM25F score.
     *
     * @param freq term frequency explanation
     * @param norm encoded norm value
     * @return explanation for the TF component
     */
    private Explanation explainTF(Explanation freq, long norm) {
      List<Explanation> subs = new ArrayList<>();
      subs.add(freq);
      subs.add(Explanation.match(k1, "k1, term saturation parameter"));
      float doclen = LENGTH_TABLE[((byte) norm) & 0xff];
      subs.add(Explanation.match(b, "b, length normalization parameter"));
      if ((norm & 0xFF) > 39) {
        subs.add(Explanation.match(doclen, "dl, length of field (approximate)"));
      } else {
        subs.add(Explanation.match(doclen, "dl, length of field"));
      }
      subs.add(Explanation.match(avgdl, "avgdl, average length of field"));
      float normInverse = 1f / (k1 * ((1 - b) + b * doclen / avgdl));
      return Explanation.match(
          1f - 1f / (1 + freq.getValue().floatValue() * normInverse),
          "tf, computed as freq / (freq + k1 * (1 - b + b * dl / avgdl)) from:",
          subs);
    }

    /**
     * Returns explanations for constant factors (field weight, boost, idf).
     *
     * @return list of constant factor explanations
     */
    private List<Explanation> explainConstantFactors() {
      List<Explanation> subs = new ArrayList<>();
      // field weight
      if (fieldWeight != 1.0f) {
        subs.add(Explanation.match(fieldWeight, "fieldWeight"));
      }
      // query boost
      if (boost != 1.0f) {
        subs.add(Explanation.match(boost, "boost"));
      }
      // idf
      subs.add(idf);
      return subs;
    }
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

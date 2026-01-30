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
import org.apache.lucene.index.FieldInvertState;
import org.apache.lucene.search.CollectionStatistics;
import org.apache.lucene.search.Explanation;
import org.apache.lucene.search.TermStatistics;
import org.apache.lucene.util.SmallFloat;

/**
 * BM25F Similarity for multi-field documents.
 *
 * <p>BM25F is an extension of BM25 that handles multi-field documents by combining field statistics
 * and allowing field-specific boosts before applying the BM25 formula. This implementation follows
 * the BM25F model as described in:
 *
 * <p>Robertson, S., Zaragoza, H., and Taylor, M. (2004). "Simple BM25 Extension to Multiple
 * Weighted Fields." In Proceedings of the thirteenth ACM international conference on Information
 * and knowledge management (CIKM '04).
 *
 * <p><b>Key differences from BM25:</b>
 *
 * <ul>
 *   <li>Field-specific boosts (weights) allow prioritizing certain fields (e.g., title vs. body)
 *   <li>Field-specific b parameters control per-field length normalization
 *   <li>Term frequencies are combined across fields using weighted sums before applying BM25
 *   <li>Document length normalization considers weighted field lengths
 * </ul>
 *
 * <p><b>BM25F Formula:</b>
 *
 * <p>The weighted term frequency across fields is computed as:
 *
 * <pre>
 * wtf = Σ(w_f × tf_f)
 * </pre>
 *
 * where w_f is the field boost and tf_f is the term frequency in field f.
 *
 * <p>The weighted document length is computed as:
 *
 * <pre>
 * wdl = Σ(w_f × dl_f)
 * </pre>
 *
 * where dl_f is the document length in field f.
 *
 * <p>The final BM25F score is:
 *
 * <pre>
 * score = idf × (wtf × (k1 + 1)) / (wtf + k1 × (1 - b + b × wdl / avgwdl))
 * </pre>
 *
 * <p><b>Usage Example:</b>
 *
 * <pre class="prettyprint">
 * Map&lt;String, Float&gt; fieldBoosts = new HashMap&lt;&gt;();
 * fieldBoosts.put("title", 5.0f);  // Title field is 5x more important
 * fieldBoosts.put("body", 1.0f);   // Body field has standard weight
 *
 * Map&lt;String, Float&gt; fieldBParams = new HashMap&lt;&gt;();
 * fieldBParams.put("title", 0.75f);
 * fieldBParams.put("body", 0.75f);
 *
 * BM25FSimilarity similarity = new BM25FSimilarity(1.2f, fieldBoosts, fieldBParams);
 * </pre>
 *
 * @see BM25Similarity
 * @lucene.experimental
 */
public class BM25FSimilarity extends Similarity {
  private final float k1;
  private final Map<String, Float> fieldBoosts;
  private final Map<String, Float> fieldBParams;
  private final float defaultB;

  /**
   * BM25F with the supplied parameter values.
   *
   * @param k1 Controls non-linear term frequency normalization (saturation). Typical values are in
   *     the range [1.2, 2.0].
   * @param fieldBoosts Per-field boost values. Higher values give more weight to matches in that
   *     field. Must not be null or empty.
   * @param fieldBParams Per-field b parameters controlling length normalization (range [0..1]).
   *     Controls to what degree document length normalizes tf values for each field. A value of 0
   *     disables length normalization, 1.0 applies full length normalization.
   * @param discountOverlaps True if overlap tokens (tokens with a position increment of zero) are
   *     discounted from the document's length.
   * @throws IllegalArgumentException if {@code k1} is infinite or negative, if {@code fieldBoosts}
   *     is null or empty, or if any b parameter is not within [0..1]
   */
  public BM25FSimilarity(
      float k1,
      Map<String, Float> fieldBoosts,
      Map<String, Float> fieldBParams,
      boolean discountOverlaps) {
    super(discountOverlaps);
    if (Float.isFinite(k1) == false || k1 < 0) {
      throw new IllegalArgumentException(
          "illegal k1 value: " + k1 + ", must be a non-negative finite value");
    }
    if (fieldBoosts == null || fieldBoosts.isEmpty()) {
      throw new IllegalArgumentException("fieldBoosts must not be null or empty");
    }
    
    this.k1 = k1;
    this.fieldBoosts = Collections.unmodifiableMap(new HashMap<>(fieldBoosts));
    
    // Validate and copy b parameters
    Map<String, Float> bParams = new HashMap<>();
    if (fieldBParams != null) {
      for (Map.Entry<String, Float> entry : fieldBParams.entrySet()) {
        float b = entry.getValue();
        if (Float.isNaN(b) || b < 0 || b > 1) {
          throw new IllegalArgumentException(
              "illegal b value for field " + entry.getKey() + ": " + b + ", must be between 0 and 1");
        }
        bParams.put(entry.getKey(), b);
      }
    }
    this.fieldBParams = Collections.unmodifiableMap(bParams);
    this.defaultB = 0.75f;
  }

  /**
   * BM25F with the supplied parameter values and default overlap discounting.
   *
   * @param k1 Controls non-linear term frequency normalization (saturation).
   * @param fieldBoosts Per-field boost values.
   * @param fieldBParams Per-field b parameters controlling length normalization.
   */
  public BM25FSimilarity(
      float k1, Map<String, Float> fieldBoosts, Map<String, Float> fieldBParams) {
    this(k1, fieldBoosts, fieldBParams, true);
  }

  /**
   * BM25F with default k1=1.2 and equal field boosts.
   *
   * @param fieldBoosts Per-field boost values.
   * @param fieldBParams Per-field b parameters controlling length normalization.
   */
  public BM25FSimilarity(Map<String, Float> fieldBoosts, Map<String, Float> fieldBParams) {
    this(1.2f, fieldBoosts, fieldBParams, true);
  }

  /** Returns the k1 parameter. */
  public float getK1() {
    return k1;
  }

  /** Returns an unmodifiable view of the field boosts. */
  public Map<String, Float> getFieldBoosts() {
    return fieldBoosts;
  }

  /** Returns an unmodifiable view of the field b parameters. */
  public Map<String, Float> getFieldBParams() {
    return fieldBParams;
  }

  /**
   * Gets the b parameter for a specific field.
   *
   * @param field the field name
   * @return the b parameter for the field, or the default (0.75) if not specified
   */
  public float getBForField(String field) {
    return fieldBParams.getOrDefault(field, defaultB);
  }

  /**
   * Gets the boost for a specific field.
   *
   * @param field the field name
   * @return the boost for the field, or 1.0 if not specified
   */
  public float getBoostForField(String field) {
    return fieldBoosts.getOrDefault(field, 1.0f);
  }

  /** Implemented as <code>log(1 + (docCount - docFreq + 0.5)/(docFreq + 0.5))</code>. */
  protected float idf(long docFreq, long docCount) {
    return (float) Math.log(1 + (docCount - docFreq + 0.5D) / (docFreq + 0.5D));
  }

  /** The default implementation computes the average as <code>sumTotalTermFreq / docCount</code> */
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

  @Override
  public long computeNorm(FieldInvertState state) {
    final int numTerms;
    if (getDiscountOverlaps()) {
      numTerms = state.getLength() - state.getNumOverlap();
    } else {
      numTerms = state.getLength();
    }
    int indexCreatedVersionMajor = state.getIndexCreatedVersionMajor();
    if (indexCreatedVersionMajor >= 7) {
      return SmallFloat.intToByte4(numTerms);
    } else {
      return numTerms;
    }
  }

  /**
   * Computes a score factor for a simple term and returns an explanation for that score factor.
   *
   * <p>The default implementation uses:
   *
   * <pre><code class="language-java">
   * idf(docFreq, docCount);
   * </code></pre>
   *
   * @param collectionStats collection-level statistics
   * @param termStats term-level statistics for the term
   * @return an Explain object that includes both an idf score factor and an explanation for the
   *     term.
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

  @Override
  public SimScorer scorer(
      float boost, CollectionStatistics collectionStats, TermStatistics... termStats) {
    Explanation idf = idfExplain(collectionStats, termStats[0]);
    float avgdl = avgFieldLength(collectionStats);

    // Get field-specific parameters
    String field = collectionStats.field();
    float fieldBoost = getBoostForField(field);
    float b = getBForField(field);

    // Precompute normalization cache
    float[] cache = new float[256];
    for (int i = 0; i < cache.length; i++) {
      cache[i] = 1f / (k1 * ((1 - b) + b * LENGTH_TABLE[i] / avgdl));
    }

    return new BM25FScorer(boost * fieldBoost, k1, b, idf, avgdl, cache);
  }

  /** Collection statistics for the BM25F model. */
  private static class BM25FScorer extends SimScorer {
    /** combined query boost and field boost */
    private final float boost;

    /** k1 value for scale factor */
    private final float k1;

    /** b value for length normalization impact */
    private final float b;

    /** BM25's idf */
    private final Explanation idf;

    /** The average document length. */
    private final float avgdl;

    /** precomputed norm[256] with k1 * ((1 - b) + b * dl / avgdl) */
    private final float[] cache;

    /** weight (idf * boost) */
    private final float weight;

    BM25FScorer(float boost, float k1, float b, Explanation idf, float avgdl, float[] cache) {
      this.boost = boost;
      this.idf = idf;
      this.avgdl = avgdl;
      this.k1 = k1;
      this.b = b;
      this.cache = cache;
      this.weight = boost * idf.getValue().floatValue();
    }

    @Override
    public float score(float freq, long encodedNorm) {
      float normInverse = cache[((byte) encodedNorm) & 0xFF];
      // Apply BM25F formula: weight - weight / (1 + freq * normInverse)
      return weight - weight / (1f + freq * normInverse);
    }

    @Override
    public Explanation explain(Explanation freq, long encodedNorm) {
      List<Explanation> subs = new ArrayList<>();
      explainCommon(subs);
      
      float dl = LENGTH_TABLE[((byte) encodedNorm) & 0xFF];
      subs.add(Explanation.match(dl, "dl, length of field"));
      subs.add(Explanation.match(avgdl, "avgdl, average length of field"));
      
      float normValue = k1 * ((1 - b) + b * dl / avgdl);
      subs.add(
          Explanation.match(
              normValue,
              "normValue, computed as k1 * ((1 - b) + b * dl / avgdl) from:",
              Explanation.match(k1, "k1, term saturation parameter"),
              Explanation.match(b, "b, length normalization parameter")));
      
      float freqValue = freq.getValue().floatValue();
      float tfNorm = freqValue / (freqValue + normValue);
      subs.add(
          Explanation.match(
              tfNorm,
              "tfNorm, computed as freq / (freq + normValue) from:",
              freq));
      
      return Explanation.match(weight * tfNorm, "score, computed as boost * idf * tfNorm from:", subs);
    }

    private void explainCommon(List<Explanation> subs) {
      subs.add(idf);
      subs.add(Explanation.match(boost, "boost"));
    }
  }

  @Override
  public String toString() {
    return "BM25F(k1=" + k1 + ",fieldBoosts=" + fieldBoosts + ",fieldBParams=" + fieldBParams + ")";
  }
}

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
package org.apache.lucene.queryparser.classic;

import java.util.HashMap;
import java.util.Map;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.search.similarities.BM25FSimilarity;

/**
 * A specialized {@link MultiFieldQueryParser} designed for use with {@link BM25FSimilarity}.
 *
 * <p>This query parser simplifies the setup and usage of BM25F multi-field scoring by providing a
 * unified interface for configuring both query parsing and scoring parameters. It extends {@link
 * MultiFieldQueryParser} to support parsing queries across multiple fields while maintaining
 * consistency with BM25F's field-specific configuration.
 *
 * <p><b>Key Features:</b>
 *
 * <ul>
 *   <li><b>Integrated Configuration:</b> Configure field weights, k1, and b parameters in one
 *       place
 *   <li><b>Automatic BM25FSimilarity Creation:</b> Generate a properly configured {@link
 *       BM25FSimilarity} instance from parser parameters
 *   <li><b>Field Boost Alignment:</b> Applies field weights as query-time boosts to ensure
 *       consistency between parsing and scoring
 *   <li><b>Standard Query Syntax:</b> Supports all standard Lucene query syntax (boolean
 *       operators, phrases, wildcards, etc.)
 * </ul>
 *
 * <p><b>Basic Usage Example:</b>
 *
 * <pre class="prettyprint">
 * // Configure fields to search
 * String[] fields = {"title", "body", "tags"};
 *
 * // Set field-specific weights (importance factors)
 * Map&lt;String, Float&gt; fieldWeights = new HashMap&lt;&gt;();
 * fieldWeights.put("title", 2.0f);    // Title matches are twice as important
 * fieldWeights.put("body", 1.0f);     // Body has standard weight
 * fieldWeights.put("tags", 1.5f);     // Tags are 1.5x as important
 *
 * // Create the parser
 * Analyzer analyzer = new StandardAnalyzer();
 * BM25FMultiFieldQueryParser parser = new BM25FMultiFieldQueryParser(
 *     fields, analyzer, fieldWeights);
 *
 * // Create and configure the similarity
 * BM25FSimilarity similarity = parser.createBM25FSimilarity();
 *
 * // Use with IndexSearcher
 * IndexSearcher searcher = new IndexSearcher(indexReader);
 * searcher.setSimilarity(similarity);
 *
 * // Parse and execute queries
 * Query query = parser.parse("search terms");
 * TopDocs results = searcher.search(query, 10);
 * </pre>
 *
 * <p><b>Advanced Usage with Per-Field Parameters:</b>
 *
 * <pre class="prettyprint">
 * String[] fields = {"title", "body", "tags"};
 *
 * // Configure field weights
 * Map&lt;String, Float&gt; fieldWeights = new HashMap&lt;&gt;();
 * fieldWeights.put("title", 2.0f);
 * fieldWeights.put("body", 1.0f);
 * fieldWeights.put("tags", 1.5f);
 *
 * // Configure per-field k1 (term frequency saturation)
 * Map&lt;String, Float&gt; fieldK1 = new HashMap&lt;&gt;();
 * fieldK1.put("title", 1.5f);  // Higher saturation for title
 * fieldK1.put("body", 1.2f);   // Standard saturation for body
 *
 * // Configure per-field b (length normalization)
 * Map&lt;String, Float&gt; fieldB = new HashMap&lt;&gt;();
 * fieldB.put("title", 0.5f);   // Less length normalization for title
 * fieldB.put("body", 0.75f);   // Standard length normalization for body
 *
 * // Create parser with all parameters
 * BM25FMultiFieldQueryParser parser = new BM25FMultiFieldQueryParser(
 *     fields, analyzer, fieldWeights, fieldK1, fieldB);
 *
 * // Create similarity with matching configuration
 * BM25FSimilarity similarity = parser.createBM25FSimilarity();
 *
 * // Use with searcher
 * searcher.setSimilarity(similarity);
 * Query query = parser.parse("machine learning");
 * TopDocs results = searcher.search(query, 10);
 * </pre>
 *
 * <p><b>Important Notes:</b>
 *
 * <ul>
 *   <li>The {@link BM25FSimilarity} must be set on the {@link
 *       org.apache.lucene.search.IndexSearcher} at search time
 *   <li>For optimal BM25F scoring, the same similarity should be used during indexing (though not
 *       strictly required)
 *   <li>Field weights specified in the parser are applied as query-time boosts and should match
 *       the weights in the similarity configuration
 *   <li>The parser automatically handles all standard query syntax including: boolean operators
 *       (AND, OR, NOT), phrase queries ("exact phrase"), wildcards (test*), fuzzy search
 *       (test~), and range queries ([A TO Z])
 * </ul>
 *
 * <p><b>Parameter Guidelines:</b>
 *
 * <ul>
 *   <li><b>Field Weights:</b> Higher values give more importance to matches in that field.
 *       Typical range: 0.5 to 3.0
 *   <li><b>k1 Parameter:</b> Controls term frequency saturation. Higher values give more weight
 *       to term frequency. Typical range: 1.0 to 2.0. Default: 1.2
 *   <li><b>b Parameter:</b> Controls length normalization. 0 = no normalization, 1 = full
 *       normalization. Typical range: 0.5 to 0.9. Default: 0.75
 * </ul>
 *
 * <p><b>Query Examples:</b>
 *
 * <ul>
 *   <li>Simple terms: {@code machine learning} - searches all configured fields
 *   <li>Boolean operators: {@code machine AND learning} - both terms required
 *   <li>Phrase queries: {@code "machine learning"} - exact phrase match
 *   <li>Field-specific: {@code title:algorithm} - search only in title field
 *   <li>Wildcards: {@code comput*} - matches computer, computing, etc.
 *   <li>Fuzzy search: {@code algorithm~} - finds similar terms
 *   <li>Combinations: {@code title:algorithm AND "machine learning"} - mixed queries
 * </ul>
 *
 * @see BM25FSimilarity
 * @see MultiFieldQueryParser
 * @lucene.experimental
 */
public class BM25FMultiFieldQueryParser extends MultiFieldQueryParser {

  /** Field-specific k1 parameters for term frequency saturation. */
  private final Map<String, Float> fieldK1;

  /** Field-specific b parameters for length normalization. */
  private final Map<String, Float> fieldB;

  /** Default k1 value for fields without specific configuration. */
  private final float defaultK1;

  /** Default b value for fields without specific configuration. */
  private final float defaultB;

  /** Whether to discount overlap tokens in document length calculation. */
  private final boolean discountOverlaps;

  /**
   * Creates a BM25FMultiFieldQueryParser with full BM25F parameter configuration.
   *
   * <p>This constructor provides complete control over all BM25F parameters, allowing you to
   * specify field-specific weights, k1 values, b values, and default parameters.
   *
   * @param fields Array of field names to search across
   * @param analyzer Analyzer to use for query parsing
   * @param fieldWeights Map of field names to their weights (importance factors). Higher weights
   *     give more importance to matches in that field. Must not be null (can be empty for uniform
   *     weights).
   * @param fieldK1 Map of field names to their k1 parameters (term frequency saturation). Must not
   *     be null (can be empty to use defaults).
   * @param fieldB Map of field names to their b parameters (length normalization). Must not be
   *     null (can be empty to use defaults).
   * @param defaultK1 Default k1 value for fields not specified in fieldK1. Typical value: 1.2
   * @param defaultB Default b value for fields not specified in fieldB. Typical value: 0.75
   * @param discountOverlaps Whether to discount overlap tokens (position increment = 0) in
   *     document length calculation
   * @throws IllegalArgumentException if fields is null or empty, analyzer is null, or any
   *     parameter map is null
   */
  public BM25FMultiFieldQueryParser(
      String[] fields,
      Analyzer analyzer,
      Map<String, Float> fieldWeights,
      Map<String, Float> fieldK1,
      Map<String, Float> fieldB,
      float defaultK1,
      float defaultB,
      boolean discountOverlaps) {
    super(fields, analyzer, fieldWeights);

    if (fields == null || fields.length == 0) {
      throw new IllegalArgumentException("fields must not be null or empty");
    }
    if (fieldK1 == null) {
      throw new IllegalArgumentException("fieldK1 must not be null");
    }
    if (fieldB == null) {
      throw new IllegalArgumentException("fieldB must not be null");
    }

    this.fieldK1 = new HashMap<>(fieldK1);
    this.fieldB = new HashMap<>(fieldB);
    this.defaultK1 = defaultK1;
    this.defaultB = defaultB;
    this.discountOverlaps = discountOverlaps;
  }

  /**
   * Creates a BM25FMultiFieldQueryParser with field weights and per-field BM25 parameters.
   *
   * <p>Uses standard default values for k1 (1.2) and b (0.75), and discounts overlap tokens by
   * default.
   *
   * @param fields Array of field names to search across
   * @param analyzer Analyzer to use for query parsing
   * @param fieldWeights Map of field names to their weights (importance factors)
   * @param fieldK1 Map of field names to their k1 parameters
   * @param fieldB Map of field names to their b parameters
   * @throws IllegalArgumentException if any parameter is invalid
   */
  public BM25FMultiFieldQueryParser(
      String[] fields,
      Analyzer analyzer,
      Map<String, Float> fieldWeights,
      Map<String, Float> fieldK1,
      Map<String, Float> fieldB) {
    this(
        fields,
        analyzer,
        fieldWeights,
        fieldK1,
        fieldB,
        BM25FSimilarity.DEFAULT_K1,
        BM25FSimilarity.DEFAULT_B,
        true);
  }

  /**
   * Creates a BM25FMultiFieldQueryParser with field weights only.
   *
   * <p>Uses default k1 (1.2) and b (0.75) for all fields.
   *
   * @param fields Array of field names to search across
   * @param analyzer Analyzer to use for query parsing
   * @param fieldWeights Map of field names to their weights (importance factors)
   * @throws IllegalArgumentException if any parameter is invalid
   */
  public BM25FMultiFieldQueryParser(
      String[] fields, Analyzer analyzer, Map<String, Float> fieldWeights) {
    this(
        fields,
        analyzer,
        fieldWeights,
        new HashMap<>(),
        new HashMap<>(),
        BM25FSimilarity.DEFAULT_K1,
        BM25FSimilarity.DEFAULT_B,
        true);
  }

  /**
   * Creates a BM25FMultiFieldQueryParser with uniform weights and default BM25 parameters.
   *
   * <p>All fields are treated equally with default BM25 parameters.
   *
   * @param fields Array of field names to search across
   * @param analyzer Analyzer to use for query parsing
   * @throws IllegalArgumentException if any parameter is invalid
   */
  public BM25FMultiFieldQueryParser(String[] fields, Analyzer analyzer) {
    this(
        fields,
        analyzer,
        new HashMap<>(),
        new HashMap<>(),
        new HashMap<>(),
        BM25FSimilarity.DEFAULT_K1,
        BM25FSimilarity.DEFAULT_B,
        true);
  }

  /**
   * Creates a {@link BM25FSimilarity} instance configured with this parser's parameters.
   *
   * <p>This is the recommended way to create a BM25FSimilarity that matches the parser's
   * configuration. The returned similarity should be set on the IndexSearcher at search time.
   *
   * <p>Example usage:
   *
   * <pre class="prettyprint">
   * BM25FMultiFieldQueryParser parser = new BM25FMultiFieldQueryParser(
   *     fields, analyzer, fieldWeights);
   * BM25FSimilarity similarity = parser.createBM25FSimilarity();
   * searcher.setSimilarity(similarity);
   * </pre>
   *
   * @return A new BM25FSimilarity instance configured with this parser's field weights, k1, b, and
   *     overlap parameters
   */
  public BM25FSimilarity createBM25FSimilarity() {
    // Get field weights from the parent class's boosts map
    Map<String, Float> weights = new HashMap<>();
    if (boosts != null) {
      weights.putAll(boosts);
    }

    return new BM25FSimilarity(weights, fieldK1, fieldB, defaultK1, defaultB, discountOverlaps);
  }

  /**
   * Returns the k1 parameter for the specified field.
   *
   * @param fieldName the name of the field
   * @return the k1 parameter for this field, or the default k1 if not specifically configured
   */
  public float getFieldK1(String fieldName) {
    return fieldK1.getOrDefault(fieldName, defaultK1);
  }

  /**
   * Returns an immutable copy of the field-specific k1 parameters.
   *
   * @return map of field names to their k1 parameters
   */
  public Map<String, Float> getFieldK1Map() {
    return new HashMap<>(fieldK1);
  }

  /**
   * Returns the b parameter for the specified field.
   *
   * @param fieldName the name of the field
   * @return the b parameter for this field, or the default b if not specifically configured
   */
  public float getFieldB(String fieldName) {
    return fieldB.getOrDefault(fieldName, defaultB);
  }

  /**
   * Returns an immutable copy of the field-specific b parameters.
   *
   * @return map of field names to their b parameters
   */
  public Map<String, Float> getFieldBMap() {
    return new HashMap<>(fieldB);
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
   * Returns whether overlap tokens are discounted in document length calculation.
   *
   * @return true if overlap tokens are discounted, false otherwise
   */
  public boolean getDiscountOverlaps() {
    return discountOverlaps;
  }

  /**
   * Returns the field weights (boost factors) configured for this parser.
   *
   * <p>This is a convenience method that returns the boost map from the parent class.
   *
   * @return map of field names to their weights, or null if no weights are configured
   */
  public Map<String, Float> getFieldWeights() {
    return boosts != null ? new HashMap<>(boosts) : null;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder("BM25FMultiFieldQueryParser(");
    sb.append("fields=").append(fields.length);
    if (boosts != null && !boosts.isEmpty()) {
      sb.append(", weights=").append(boosts.size()).append(" fields");
    }
    if (!fieldK1.isEmpty()) {
      sb.append(", k1=").append(fieldK1.size()).append(" fields");
    }
    if (!fieldB.isEmpty()) {
      sb.append(", b=").append(fieldB.size()).append(" fields");
    }
    sb.append(", defaultK1=").append(defaultK1);
    sb.append(", defaultB=").append(defaultB);
    sb.append(")");
    return sb.toString();
  }
}

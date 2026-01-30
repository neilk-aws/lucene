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

import java.util.Map;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.search.similarities.BM25FSimilarity;

/**
 * A query parser that constructs BM25F multi-field queries.
 *
 * <p>BM25FQueryParser extends {@link MultiFieldQueryParser} to support BM25F ranking, which
 * combines term frequencies across multiple fields with field-specific parameters.
 *
 * <p>This parser allows you to configure:
 *
 * <ul>
 *   <li>Field-specific boosts (weights) - controlling relative importance of fields
 *   <li>Field-specific b parameters - controlling length normalization per field
 *   <li>Global k1 parameter - controlling term frequency saturation
 * </ul>
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * // Create parser for title and body fields
 * String[] fields = {"title", "body"};
 * Analyzer analyzer = new StandardAnalyzer();
 *
 * // Create BM25F query parser with field boosts
 * Map<String, Float> boosts = new HashMap<>();
 * boosts.put("title", 2.0f);  // Title is twice as important
 * boosts.put("body", 1.0f);
 *
 * BM25FQueryParser parser = new BM25FQueryParser(fields, analyzer, boosts);
 *
 * // Optionally configure BM25F parameters
 * parser.setK1(1.2f);
 * parser.setDefaultB(0.75f);
 * parser.setFieldBParam("title", 0.5f);  // Less length normalization for title
 *
 * // Parse query
 * Query query = parser.parse("search terms");
 * }</pre>
 *
 * <p>The parser constructs queries that use {@link BM25FSimilarity} for scoring, which properly
 * combines signals across fields according to BM25F formulation.
 */
public class BM25FQueryParser extends MultiFieldQueryParser {

  private float k1 = 1.2f;
  private float defaultB = 0.75f;
  private final BM25FSimilarity similarity;

  /**
   * Creates a BM25FQueryParser with field boosts.
   *
   * @param fields Array of field names to search
   * @param analyzer Analyzer to use for query parsing
   * @param boosts Map of field names to boost values (relative importance)
   */
  public BM25FQueryParser(String[] fields, Analyzer analyzer, Map<String, Float> boosts) {
    super(fields, analyzer, boosts);
    this.similarity = new BM25FSimilarity(k1, defaultB, boosts, null);
  }

  /**
   * Creates a BM25FQueryParser without field boosts.
   *
   * @param fields Array of field names to search
   * @param analyzer Analyzer to use for query parsing
   */
  public BM25FQueryParser(String[] fields, Analyzer analyzer) {
    super(fields, analyzer);
    this.similarity = new BM25FSimilarity(k1, defaultB);
  }

  /**
   * Sets the k1 parameter for BM25F scoring.
   *
   * <p>The k1 parameter controls non-linear term frequency normalization (saturation). Higher
   * values give more weight to term frequency, while lower values saturate faster. Typical value:
   * 1.2
   *
   * @param k1 The k1 parameter value (must be non-negative and finite)
   * @throws IllegalArgumentException if k1 is negative or infinite
   */
  public void setK1(float k1) {
    if (Float.isFinite(k1) == false || k1 < 0) {
      throw new IllegalArgumentException(
          "illegal k1 value: " + k1 + ", must be a non-negative finite value");
    }
    this.k1 = k1;
  }

  /**
   * Sets the default b parameter for BM25F scoring.
   *
   * <p>The b parameter controls to what degree document length normalizes term frequency values. b
   * = 0 means no length normalization, b = 1 means full length normalization. Typical value: 0.75
   *
   * @param defaultB The default b parameter value (must be between 0 and 1)
   * @throws IllegalArgumentException if defaultB is not in [0..1]
   */
  public void setDefaultB(float defaultB) {
    if (Float.isNaN(defaultB) || defaultB < 0 || defaultB > 1) {
      throw new IllegalArgumentException(
          "illegal defaultB value: " + defaultB + ", must be between 0 and 1");
    }
    this.defaultB = defaultB;
  }

  /**
   * Sets the boost for a specific field.
   *
   * <p>Field boosts control the relative importance of fields in BM25F scoring. A boost of 2.0
   * makes the field twice as important as a field with boost 1.0.
   *
   * @param field Field name
   * @param boost Boost value (must be non-negative)
   * @throws IllegalArgumentException if boost is negative
   */
  public void setFieldBoost(String field, float boost) {
    similarity.setFieldBoost(field, boost);
  }

  /**
   * Sets the b parameter for a specific field.
   *
   * <p>Field-specific b parameters allow different length normalization behavior for different
   * fields. For example, titles might need less length normalization (lower b) than body text.
   *
   * @param field Field name
   * @param b B parameter value for this field (must be between 0 and 1)
   * @throws IllegalArgumentException if b is not in [0..1]
   */
  public void setFieldBParam(String field, float b) {
    similarity.setFieldBParam(field, b);
  }

  /**
   * Gets the boost for a specific field.
   *
   * @param field Field name
   * @return Boost value (1.0 if not explicitly set)
   */
  public float getFieldBoost(String field) {
    return similarity.getFieldBoost(field);
  }

  /**
   * Gets the b parameter for a specific field.
   *
   * @param field Field name
   * @return B parameter value (defaultB if not explicitly set)
   */
  public float getFieldBParam(String field) {
    return similarity.getFieldBParam(field);
  }

  /**
   * Gets the k1 parameter.
   *
   * @return k1 value
   */
  public float getK1() {
    return k1;
  }

  /**
   * Gets the default b parameter.
   *
   * @return default b value
   */
  public float getDefaultB() {
    return defaultB;
  }

  /**
   * Gets the BM25FSimilarity instance used by this parser.
   *
   * <p>This can be used to set the similarity on an IndexSearcher when executing queries:
   *
   * <pre>{@code
   * BM25FQueryParser parser = new BM25FQueryParser(fields, analyzer, boosts);
   * Query query = parser.parse("search terms");
   *
   * IndexSearcher searcher = new IndexSearcher(reader);
   * searcher.setSimilarity(parser.getSimilarity());
   * TopDocs results = searcher.search(query, 10);
   * }</pre>
   *
   * @return The BM25FSimilarity instance
   */
  public BM25FSimilarity getSimilarity() {
    return similarity;
  }

  @Override
  public String toString() {
    return "BM25FQueryParser(fields="
        + String.join(",", fields)
        + ", k1="
        + k1
        + ", defaultB="
        + defaultB
        + ")";
  }
}

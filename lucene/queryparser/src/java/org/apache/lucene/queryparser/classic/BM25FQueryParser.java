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
 * A QueryParser specifically designed for use with BM25F similarity.
 *
 * <p>BM25FQueryParser extends {@link MultiFieldQueryParser} to work seamlessly with {@link
 * BM25FSimilarity}. It allows parsing queries across multiple fields with field-specific weights
 * (boosts) that are properly integrated into the BM25F scoring model.
 *
 * <p><b>Key Features:</b>
 *
 * <ul>
 *   <li>Multi-field query parsing with field-specific boosts
 *   <li>Automatic integration with BM25FSimilarity parameters
 *   <li>Support for per-field length normalization parameters
 *   <li>Proper handling of field statistics aggregation for BM25F scoring
 * </ul>
 *
 * <p><b>Usage Example:</b>
 *
 * <pre class="prettyprint">
 * // Define fields and their boosts
 * String[] fields = {"title", "body", "keywords"};
 * Map&lt;String, Float&gt; fieldBoosts = new HashMap&lt;&gt;();
 * fieldBoosts.put("title", 5.0f);     // Title is 5x more important
 * fieldBoosts.put("body", 1.0f);      // Body has standard weight
 * fieldBoosts.put("keywords", 3.0f);  // Keywords are 3x more important
 *
 * // Create the BM25F query parser
 * Analyzer analyzer = new StandardAnalyzer();
 * BM25FQueryParser parser = new BM25FQueryParser(fields, analyzer, fieldBoosts);
 *
 * // Parse a query
 * Query query = parser.parse("information retrieval");
 *
 * // Create and configure the searcher with BM25F similarity
 * Map&lt;String, Float&gt; fieldBParams = new HashMap&lt;&gt;();
 * fieldBParams.put("title", 0.75f);
 * fieldBParams.put("body", 0.75f);
 * fieldBParams.put("keywords", 0.5f);  // Less length normalization for keywords
 *
 * IndexSearcher searcher = new IndexSearcher(reader);
 * searcher.setSimilarity(new BM25FSimilarity(1.2f, fieldBoosts, fieldBParams));
 *
 * // Search
 * TopDocs results = searcher.search(query, 10);
 * </pre>
 *
 * <p><b>BM25F Theory:</b>
 *
 * <p>BM25F extends the BM25 model to handle multi-field documents by:
 *
 * <ol>
 *   <li>Combining term frequencies across fields using weighted sums (field boosts)
 *   <li>Normalizing document lengths on a per-field basis with field-specific b parameters
 *   <li>Computing a unified BM25 score using the combined statistics
 * </ol>
 *
 * <p>This approach is more principled than simply boosting individual field queries because it
 * combines field evidence before applying the BM25 scoring function, rather than after.
 *
 * <p><b>Field Boost Guidelines:</b>
 *
 * <ul>
 *   <li>Title fields: typically 3-10x weight (they're usually short and highly relevant)
 *   <li>Body/content fields: typically 1x weight (baseline)
 *   <li>Metadata fields: typically 0.5-2x weight (depending on importance)
 *   <li>Keyword/tag fields: typically 2-5x weight (usually high precision)
 * </ul>
 *
 * <p><b>Length Normalization Guidelines:</b>
 *
 * <ul>
 *   <li>Use b=0.75 for most fields (standard BM25 value)
 *   <li>Use lower b (0.3-0.5) for fields where length shouldn't penalize (e.g., keywords, tags)
 *   <li>Use higher b (0.8-1.0) for verbose fields where length indicates dilution
 * </ul>
 *
 * @see BM25FSimilarity
 * @see MultiFieldQueryParser
 * @lucene.experimental
 */
public class BM25FQueryParser extends MultiFieldQueryParser {

  /**
   * Creates a BM25FQueryParser for searching across multiple fields with BM25F scoring.
   *
   * <p>The provided field boosts should match those used in the {@link BM25FSimilarity}
   * configuration for consistent scoring.
   *
   * @param fields Array of field names to search
   * @param analyzer Analyzer to use for tokenizing query text
   * @param fieldBoosts Map of field names to boost values (weights). Higher values give more
   *     importance to matches in that field. These should match the boosts configured in
   *     BM25FSimilarity.
   * @throws IllegalArgumentException if fields is null or empty, or if fieldBoosts is null
   */
  public BM25FQueryParser(String[] fields, Analyzer analyzer, Map<String, Float> fieldBoosts) {
    super(fields, analyzer, fieldBoosts);
    if (fields == null || fields.length == 0) {
      throw new IllegalArgumentException("fields must not be null or empty");
    }
    if (fieldBoosts == null) {
      throw new IllegalArgumentException("fieldBoosts must not be null");
    }
  }

  /**
   * Creates a BM25FQueryParser with equal boosts for all fields.
   *
   * <p>All fields will have a boost of 1.0. This is suitable when all fields should be treated
   * equally, though for most applications field-specific boosts are recommended.
   *
   * @param fields Array of field names to search
   * @param analyzer Analyzer to use for tokenizing query text
   */
  public BM25FQueryParser(String[] fields, Analyzer analyzer) {
    this(fields, analyzer, createDefaultBoosts(fields));
  }

  /**
   * Creates a default boost map with all fields having boost=1.0.
   *
   * @param fields Array of field names
   * @return Map with all fields mapped to boost value 1.0
   */
  private static Map<String, Float> createDefaultBoosts(String[] fields) {
    Map<String, Float> boosts = new HashMap<>();
    for (String field : fields) {
      boosts.put(field, 1.0f);
    }
    return boosts;
  }

  /**
   * Creates a BM25FSimilarity instance configured with the field boosts from this parser.
   *
   * <p>This is a convenience method to create a matching BM25FSimilarity with the same field
   * boosts as the query parser. You can optionally customize the k1 parameter and per-field b
   * parameters.
   *
   * @param k1 The k1 parameter for BM25F (controls term frequency saturation). Typical values are
   *     1.2-2.0. Use 1.2 for standard BM25 behavior.
   * @param fieldBParams Per-field b parameters for length normalization. If null, defaults to 0.75
   *     for all fields.
   * @return A configured BM25FSimilarity instance
   */
  public BM25FSimilarity createBM25FSimilarity(float k1, Map<String, Float> fieldBParams) {
    Map<String, Float> boostsToUse = 
        (boosts != null) ? boosts : createDefaultBoosts(fields);
    
    Map<String, Float> bParamsToUse = fieldBParams;
    if (bParamsToUse == null) {
      bParamsToUse = new HashMap<>();
      for (String field : fields) {
        bParamsToUse.put(field, 0.75f);
      }
    }
    
    return new BM25FSimilarity(k1, boostsToUse, bParamsToUse);
  }

  /**
   * Creates a BM25FSimilarity instance with default k1=1.2 and b=0.75 for all fields.
   *
   * @return A configured BM25FSimilarity instance with default parameters
   */
  public BM25FSimilarity createBM25FSimilarity() {
    return createBM25FSimilarity(1.2f, null);
  }

  /**
   * Returns the field boosts configured for this parser.
   *
   * @return Map of field names to boost values, or null if using default equal boosts
   */
  public Map<String, Float> getFieldBoosts() {
    return boosts;
  }

  /**
   * Returns the fields configured for this parser.
   *
   * @return Array of field names
   */
  public String[] getFields() {
    return fields;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder("BM25FQueryParser(fields=[");
    for (int i = 0; i < fields.length; i++) {
      if (i > 0) sb.append(", ");
      sb.append(fields[i]);
      if (boosts != null && boosts.containsKey(fields[i])) {
        sb.append("^").append(boosts.get(fields[i]));
      }
    }
    sb.append("])");
    return sb.toString();
  }
}

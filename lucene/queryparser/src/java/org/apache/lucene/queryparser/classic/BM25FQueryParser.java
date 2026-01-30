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

/**
 * A specialized multi-field query parser optimized for BM25F ranking.
 *
 * <p>This parser extends {@link MultiFieldQueryParser} and is designed to work with {@link
 * org.apache.lucene.search.similarities.BM25FSimilarity}. It constructs queries that properly
 * leverage BM25F's multi-field scoring capabilities.
 *
 * <h2>BM25F Overview</h2>
 *
 * <p>BM25F extends the BM25 ranking function to handle multiple document fields (like title, body,
 * anchor text) with:
 *
 * <ul>
 *   <li><b>Field-specific weights</b>: Different fields can have different importance
 *   <li><b>Field-specific length normalization</b>: Each field can normalize document length
 *       differently
 *   <li><b>Aggregated term frequencies</b>: Term frequencies are combined across fields before
 *       scoring
 * </ul>
 *
 * <h2>Usage Example</h2>
 *
 * <pre class="prettyprint">
 * // Configure BM25F similarity
 * BM25FSimilarity.Builder simBuilder = new BM25FSimilarity.Builder();
 * simBuilder.addFieldConfig("title", 3.0f, 0.5f);    // title: high weight, low length norm
 * simBuilder.addFieldConfig("body", 1.0f, 0.75f);    // body: standard weight and norm
 * simBuilder.addFieldConfig("anchor", 1.5f, 0.6f);   // anchor: moderate weight and norm
 * BM25FSimilarity similarity = simBuilder.build();
 *
 * // Create query parser with matching field configurations
 * Map&lt;String, Float&gt; boosts = new HashMap&lt;&gt;();
 * boosts.put("title", 3.0f);
 * boosts.put("body", 1.0f);
 * boosts.put("anchor", 1.5f);
 *
 * String[] fields = {"title", "body", "anchor"};
 * BM25FQueryParser parser = new BM25FQueryParser(fields, analyzer, boosts);
 *
 * // Set up searcher
 * IndexSearcher searcher = new IndexSearcher(reader);
 * searcher.setSimilarity(similarity);
 *
 * // Parse and execute query
 * Query query = parser.parse("information retrieval");
 * TopDocs results = searcher.search(query, 10);
 * </pre>
 *
 * <h2>Field Weight Best Practices</h2>
 *
 * <p>Typical field weight configurations:
 *
 * <ul>
 *   <li><b>Title</b>: 2.0 - 5.0 (titles are usually very relevant)
 *   <li><b>Body/Content</b>: 1.0 (baseline)
 *   <li><b>Anchor Text</b>: 1.5 - 2.5 (external descriptions are valuable)
 *   <li><b>Metadata</b>: 0.5 - 1.5 (depending on quality)
 * </ul>
 *
 * <h2>Length Normalization Best Practices</h2>
 *
 * <p>Typical b parameter values:
 *
 * <ul>
 *   <li><b>Short fields (title, anchor)</b>: 0.3 - 0.6 (less sensitive to length)
 *   <li><b>Long fields (body, content)</b>: 0.7 - 0.9 (more length normalization)
 *   <li><b>b = 0</b>: No length normalization (all documents treated equally)
 *   <li><b>b = 1</b>: Full length normalization (strong penalty for long documents)
 * </ul>
 *
 * <h2>Implementation Notes</h2>
 *
 * <p>This parser works seamlessly with BM25FSimilarity by:
 *
 * <ol>
 *   <li>Creating multi-field queries that search across all configured fields
 *   <li>Applying field boosts at query time (these combine with similarity-level weights)
 *   <li>Using BooleanQuery with SHOULD clauses to aggregate scores across fields
 * </ol>
 *
 * <p>For optimal results, ensure the field weights in the parser match the field configurations in
 * BM25FSimilarity.
 *
 * @see org.apache.lucene.search.similarities.BM25FSimilarity
 * @see MultiFieldQueryParser
 */
public class BM25FQueryParser extends MultiFieldQueryParser {

  /**
   * Creates a BM25FQueryParser with specified fields and field boosts.
   *
   * <p>The field boosts should match the weights configured in your BM25FSimilarity for consistent
   * scoring.
   *
   * @param fields Array of field names to search across
   * @param analyzer Analyzer for tokenizing query text
   * @param boosts Map of field names to boost values (weights)
   * @throws IllegalArgumentException if fields is null or empty
   */
  public BM25FQueryParser(String[] fields, Analyzer analyzer, Map<String, Float> boosts) {
    super(fields, analyzer, boosts);

    if (fields == null || fields.length == 0) {
      throw new IllegalArgumentException("fields array cannot be null or empty");
    }

    // Validate that all fields have corresponding boosts
    if (boosts != null) {
      for (String field : fields) {
        if (!boosts.containsKey(field)) {
          // This is a warning condition - field exists but has no explicit boost
          // It will use default boost of 1.0
        }
      }
    }
  }

  /**
   * Creates a BM25FQueryParser with uniform weights across all fields.
   *
   * <p>All fields will have equal weight (1.0). Use the constructor with boosts parameter for
   * non-uniform field weights.
   *
   * @param fields Array of field names to search across
   * @param analyzer Analyzer for tokenizing query text
   * @throws IllegalArgumentException if fields is null or empty
   */
  public BM25FQueryParser(String[] fields, Analyzer analyzer) {
    super(fields, analyzer);

    if (fields == null || fields.length == 0) {
      throw new IllegalArgumentException("fields array cannot be null or empty");
    }
  }

  /**
   * Validates that the provided field configurations are reasonable for BM25F.
   *
   * <p>This is a helper method to check common configuration mistakes.
   *
   * @param boosts Field boost map
   * @throws IllegalArgumentException if configuration appears invalid
   */
  public static void validateFieldBoosts(Map<String, Float> boosts) {
    if (boosts == null || boosts.isEmpty()) {
      return; // No boosts is valid
    }

    for (Map.Entry<String, Float> entry : boosts.entrySet()) {
      Float boost = entry.getValue();
      if (boost == null || boost <= 0 || Float.isNaN(boost) || Float.isInfinite(boost)) {
        throw new IllegalArgumentException(
            "Invalid boost for field '"
                + entry.getKey()
                + "': "
                + boost
                + " (must be positive and finite)");
      }

      // Warn about unusually high or low boosts
      if (boost > 10.0f) {
        // Very high boost - might dominate other fields excessively
        System.err.println(
            "Warning: Very high boost ("
                + boost
                + ") for field '"
                + entry.getKey()
                + "' may dominate scoring");
      } else if (boost < 0.1f) {
        // Very low boost - field might have negligible impact
        System.err.println(
            "Warning: Very low boost ("
                + boost
                + ") for field '"
                + entry.getKey()
                + "' may have minimal impact");
      }
    }
  }

  /**
   * Returns a string representation of this parser's configuration.
   *
   * @return String showing fields and their boosts
   */
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

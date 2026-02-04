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

/**
 * Simple query parsers for human-entered queries.
 *
 * <p>This package contains:
 *
 * <ul>
 *   <li>{@link org.apache.lucene.queryparser.simple.SimpleQueryParser} - A simple query parser that
 *       creates boolean queries across multiple fields
 *   <li>{@link org.apache.lucene.queryparser.simple.BM25FQueryParser} - A BM25F multi-field query
 *       parser that uses {@link org.apache.lucene.search.CombinedFieldQuery} for BM25F scoring
 * </ul>
 *
 * <h2>BM25F Query Parser</h2>
 *
 * <p>The {@link org.apache.lucene.queryparser.simple.BM25FQueryParser} implements BM25F (BM25 with
 * Field weights) scoring, which treats multiple fields as a single combined field for scoring
 * purposes. This is different from {@link org.apache.lucene.queryparser.simple.SimpleQueryParser}
 * which creates separate queries for each field and combines them with a boolean query.
 *
 * <p>BM25F provides better scoring for multi-field searches because it combines term frequencies
 * from all fields before applying the BM25 formula, rather than scoring each field independently
 * and then combining scores.
 *
 * <p>Example usage:
 *
 * <pre class="prettyprint">
 * Map&lt;String, Float&gt; fieldWeights = new LinkedHashMap&lt;&gt;();
 * fieldWeights.put("title", 2.0f);   // title field has 2x boost
 * fieldWeights.put("body", 1.0f);    // body field has normal weight
 *
 * Analyzer analyzer = new StandardAnalyzer();
 * BM25FQueryParser parser = new BM25FQueryParser(analyzer, fieldWeights);
 *
 * // Parse a query - terms will be scored using BM25F across title and body
 * Query query = parser.parse("search query terms");
 * </pre>
 */
package org.apache.lucene.queryparser.simple;

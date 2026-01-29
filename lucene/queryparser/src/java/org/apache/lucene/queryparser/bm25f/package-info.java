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
 * A query parser that produces {@link org.apache.lucene.search.CombinedFieldQuery} instances for
 * BM25F-style scoring across multiple fields.
 *
 * <p>BM25F is an extension of BM25 that allows searching across multiple fields with field-specific
 * weights, while combining field-level statistics for more accurate relevance ranking.
 *
 * @see org.apache.lucene.queryparser.bm25f.BM25FQueryParser
 * @see org.apache.lucene.search.CombinedFieldQuery
 */
package org.apache.lucene.queryparser.bm25f;

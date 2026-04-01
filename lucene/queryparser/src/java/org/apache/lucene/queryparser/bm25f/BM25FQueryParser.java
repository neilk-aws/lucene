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
package org.apache.lucene.queryparser.bm25f;

import java.util.Map;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.queryparser.simple.SimpleQueryParser;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.CombinedFieldQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.util.BytesRef;

/**
 * A query parser that uses BM25F scoring for term-level queries across multiple weighted fields.
 *
 * <p>This parser extends {@link SimpleQueryParser} and overrides the handling of default (term)
 * queries to produce {@link CombinedFieldQuery} instances instead of per-field {@link
 * org.apache.lucene.search.TermQuery} disjunctions. {@link CombinedFieldQuery} implements BM25F
 * scoring, which treats multiple fields as a single combined field during scoring. This produces
 * more accurate relevance scores than independently scoring each field and combining the results.
 *
 * <p>For query types that {@link CombinedFieldQuery} does not support (phrases, prefix queries, and
 * fuzzy queries), this parser falls back to the standard {@link SimpleQueryParser} behavior of
 * creating per-field {@link BooleanQuery} disjunctions.
 *
 * <p>All field weights must be greater than or equal to 1.0, as required by {@link
 * CombinedFieldQuery}. The constructor validates this constraint.
 *
 * <p>Usage example:
 *
 * <pre class="prettyprint">
 * Map&lt;String, Float&gt; weights = new LinkedHashMap&lt;&gt;();
 * weights.put("title", 3.0f);
 * weights.put("body", 1.0f);
 * BM25FQueryParser parser = new BM25FQueryParser(analyzer, weights);
 * Query query = parser.parse("search terms");
 * </pre>
 *
 * @lucene.experimental
 */
public class BM25FQueryParser extends SimpleQueryParser {

  private final Map<String, Float> fieldWeights;

  /**
   * Creates a new parser searching over multiple fields with BM25F scoring.
   *
   * @param analyzer the analyzer used to tokenize query text
   * @param weights a map of field names to their BM25F weights (all weights must be &gt;= 1.0)
   * @throws IllegalArgumentException if any weight is less than 1.0
   */
  public BM25FQueryParser(Analyzer analyzer, Map<String, Float> weights) {
    this(analyzer, weights, -1);
  }

  /**
   * Creates a new parser with custom flags used to enable/disable certain features.
   *
   * @param analyzer the analyzer used to tokenize query text
   * @param weights a map of field names to their BM25F weights (all weights must be &gt;= 1.0)
   * @param flags flags to enable/disable parser features
   * @throws IllegalArgumentException if any weight is less than 1.0
   */
  public BM25FQueryParser(Analyzer analyzer, Map<String, Float> weights, int flags) {
    super(analyzer, weights, flags);
    for (Map.Entry<String, Float> entry : weights.entrySet()) {
      if (entry.getValue() < 1.0f) {
        throw new IllegalArgumentException(
            "weight for field '"
                + entry.getKey()
                + "' must be greater or equal to 1, got: "
                + entry.getValue());
      }
    }
    this.fieldWeights = weights;
  }

  @Override
  protected Query newDefaultQuery(String text) {
    // Use the first field to analyze the text via the parent QueryBuilder
    String firstField = fieldWeights.keySet().iterator().next();
    Query analyzed = createBooleanQuery(firstField, text, getDefaultOperator());
    if (analyzed == null) {
      // All tokens were filtered out (e.g., stopwords)
      return null;
    }
    if (analyzed instanceof TermQuery tq) {
      // Single term -- create a CombinedFieldQuery across all fields
      return buildCombinedFieldQuery(tq.getTerm().bytes());
    } else if (analyzed instanceof BooleanQuery bq) {
      // Multiple terms -- wrap each in a CombinedFieldQuery
      BooleanQuery.Builder builder = new BooleanQuery.Builder();
      for (BooleanClause clause : bq) {
        if (clause.query() instanceof TermQuery tq) {
          builder.add(buildCombinedFieldQuery(tq.getTerm().bytes()), clause.occur());
        } else {
          // Fallback for unexpected query types (e.g., SynonymQuery)
          builder.add(clause);
        }
      }
      return simplify(builder.build());
    } else {
      // Unexpected type -- return as-is
      return analyzed;
    }
  }

  /**
   * Builds a {@link CombinedFieldQuery} for the given term bytes across all configured fields with
   * their weights.
   */
  private CombinedFieldQuery buildCombinedFieldQuery(BytesRef termBytes) {
    CombinedFieldQuery.Builder cfqBuilder = new CombinedFieldQuery.Builder(termBytes);
    for (Map.Entry<String, Float> entry : fieldWeights.entrySet()) {
      cfqBuilder.addField(entry.getKey(), entry.getValue());
    }
    return cfqBuilder.build();
  }
}

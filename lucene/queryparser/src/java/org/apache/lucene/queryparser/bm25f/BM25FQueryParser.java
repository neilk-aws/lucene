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
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.simple.SimpleQueryParser;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.CombinedFieldQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.SynonymQuery;
import org.apache.lucene.search.TermQuery;

/**
 * A query parser that generates {@link CombinedFieldQuery} instances for BM25F multi-field scoring.
 *
 * <p>This parser extends {@link SimpleQueryParser} and overrides the default term query generation
 * to produce {@link CombinedFieldQuery} instances instead of per-field BooleanQuery+BoostQuery
 * combinations. This allows users to parse human-readable queries and get proper BM25F multi-field
 * scoring without manually constructing CombinedFieldQuery instances.
 *
 * <p>For term queries, the parser creates a {@link CombinedFieldQuery} that combines term frequency
 * across all configured fields with per-field weights. For phrase, prefix, and fuzzy queries, the
 * parser falls back to the default {@link SimpleQueryParser} behavior (per-field queries with
 * boosts) since {@link CombinedFieldQuery} only supports single terms.
 *
 * <p><b>Shared-analyzer precondition:</b> All fields passed to this parser must share the same
 * analyzer, or at minimum must produce the same tokenization behavior (same token stream). This is
 * because {@link CombinedFieldQuery} combines term frequency across fields, which only produces
 * meaningful BM25F scores when the same token stream is generated for all fields. The parser picks
 * one field for analysis and applies the resulting tokens to all configured fields. If fields use
 * different analyzers, the behavior is silently incorrect.
 *
 * <p><b>Per-term IDF model:</b> For multi-token queries, each term is wrapped in its own {@link
 * CombinedFieldQuery} with independent IDF computation. These per-term queries are then combined
 * via {@link BooleanQuery} using the configured default operator. This means that IDF is computed
 * independently for each term rather than across the full term set, which may produce different
 * rankings compared to a single multi-term query with unified IDF computation.
 *
 * <p>Usage example:
 *
 * <pre class="prettyprint">
 * Map&lt;String, Float&gt; weights = new LinkedHashMap&lt;&gt;();
 * weights.put("title", 5.0f);
 * weights.put("body", 1.0f);
 * BM25FQueryParser parser = new BM25FQueryParser(analyzer, weights);
 * Query query = parser.parse("search terms");
 * </pre>
 *
 * <p>All field weights must be greater than or equal to 1.0f, as required by {@link
 * CombinedFieldQuery}.
 *
 * @see CombinedFieldQuery
 * @see SimpleQueryParser
 */
public class BM25FQueryParser extends SimpleQueryParser {

  /**
   * Creates a new parser searching over multiple fields with different weights, with all operators
   * enabled.
   *
   * @param analyzer the analyzer used to tokenize query text
   * @param weights a map of field names to their boost weights (all weights must be &gt;= 1.0f)
   * @throws IllegalArgumentException if any weight is less than 1.0f
   */
  public BM25FQueryParser(Analyzer analyzer, Map<String, Float> weights) {
    this(analyzer, weights, -1);
  }

  /**
   * Creates a new parser with custom flags used to enable/disable certain features.
   *
   * @param analyzer the analyzer used to tokenize query text
   * @param weights a map of field names to their boost weights (all weights must be &gt;= 1.0f)
   * @param flags the flags to enable/disable parser features
   * @throws IllegalArgumentException if any weight is less than 1.0f
   */
  public BM25FQueryParser(Analyzer analyzer, Map<String, Float> weights, int flags) {
    super(analyzer, weights, flags);
    for (Map.Entry<String, Float> entry : weights.entrySet()) {
      if (entry.getValue() < 1.0f) {
        throw new IllegalArgumentException(
            "weight for field \""
                + entry.getKey()
                + "\" must be greater or equal to 1, got: "
                + entry.getValue());
      }
    }
  }

  /**
   * Generates a {@link CombinedFieldQuery} for the given text. The text is analyzed using the
   * configured analyzer, and each resulting token is wrapped in a CombinedFieldQuery that spans all
   * configured fields with their weights. Multiple tokens are combined using the default operator.
   * SynonymQuery instances produced by the analyzer are expanded into a disjunction of
   * CombinedFieldQuery instances, one per synonym alternative.
   */
  @Override
  protected Query newDefaultQuery(String text) {
    // Analyze the text using the first field (all fields must share the same analyzer
    // for CombinedFieldQuery to produce valid BM25F scores)
    String firstField = weights.keySet().iterator().next();
    Query analyzed = createBooleanQuery(firstField, text, getDefaultOperator());
    if (analyzed == null) {
      // All stop words or empty input
      return null;
    }
    if (analyzed instanceof TermQuery tq) {
      return createCombinedFieldQuery(tq.getTerm().text());
    } else if (analyzed instanceof SynonymQuery sq) {
      return expandSynonymQuery(sq);
    } else if (analyzed instanceof BooleanQuery bq) {
      BooleanQuery.Builder builder = new BooleanQuery.Builder();
      for (BooleanClause clause : bq) {
        if (clause.query() instanceof TermQuery tq) {
          builder.add(createCombinedFieldQuery(tq.getTerm().text()), clause.occur());
        } else if (clause.query() instanceof SynonymQuery sq) {
          builder.add(expandSynonymQuery(sq), clause.occur());
        } else {
          // For anything else, keep as-is
          builder.add(clause);
        }
      }
      return builder.build();
    }
    // Fallback for other query types
    return analyzed;
  }

  /**
   * Creates a {@link CombinedFieldQuery} for a single term across all configured fields with their
   * weights.
   */
  private Query createCombinedFieldQuery(String termText) {
    CombinedFieldQuery.Builder builder = new CombinedFieldQuery.Builder(termText);
    for (Map.Entry<String, Float> entry : weights.entrySet()) {
      builder.addField(entry.getKey(), entry.getValue());
    }
    return builder.build();
  }

  /**
   * Expands a {@link SynonymQuery} into a {@link BooleanQuery} (SHOULD) of {@link
   * CombinedFieldQuery} instances, one per synonym alternative. This ensures that synonym
   * alternatives receive multi-field BM25F scoring across all configured fields rather than being
   * scoped to a single field.
   */
  private Query expandSynonymQuery(SynonymQuery sq) {
    BooleanQuery.Builder builder = new BooleanQuery.Builder();
    for (Term term : sq.getTerms()) {
      builder.add(createCombinedFieldQuery(term.text()), BooleanClause.Occur.SHOULD);
    }
    return builder.build();
  }
}

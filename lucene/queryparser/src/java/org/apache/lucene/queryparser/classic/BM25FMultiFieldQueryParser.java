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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BoostQuery;
import org.apache.lucene.search.CombinedFieldQuery;
import org.apache.lucene.search.MultiPhraseQuery;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;

/**
 * A QueryParser that uses BM25F scoring for multi-field queries.
 *
 * <p>BM25F (BM25 with Field weights) is a variant of BM25 that handles multiple fields by treating
 * them as a single combined field with per-field weights. This results in more accurate scoring for
 * multi-field searches compared to the traditional approach of creating separate queries per field
 * and combining them with a BooleanQuery.
 *
 * <p>For simple term queries, this parser creates {@link CombinedFieldQuery} instances that score
 * using BM25F. For phrase queries and other complex queries (fuzzy, wildcard, prefix, range), it
 * falls back to creating a BooleanQuery with per-field queries, similar to {@link
 * MultiFieldQueryParser}.
 *
 * <p>Example usage:
 *
 * <pre class="prettyprint">
 * // Create parser for searching title and body fields
 * String[] fields = {"title", "body"};
 * BM25FMultiFieldQueryParser parser = new BM25FMultiFieldQueryParser(fields, analyzer);
 *
 * // Parse a query - terms will be scored using BM25F across both fields
 * Query query = parser.parse("search terms");
 *
 * // With field weights (title weighted 2x more than body)
 * Map&lt;String, Float&gt; boosts = new HashMap&lt;&gt;();
 * boosts.put("title", 2.0f);
 * boosts.put("body", 1.0f);
 * BM25FMultiFieldQueryParser weightedParser =
 *     new BM25FMultiFieldQueryParser(fields, analyzer, boosts);
 * </pre>
 *
 * <p>Note: BM25F requires field weights to be &gt;= 1.0. If you specify a weight less than 1.0, an
 * {@link IllegalArgumentException} will be thrown. To achieve the equivalent of a weight less than
 * 1.0, increase the weights of the other fields proportionally.
 *
 * @see CombinedFieldQuery
 * @see MultiFieldQueryParser
 * @lucene.experimental
 */
public class BM25FMultiFieldQueryParser extends QueryParser {

  /** The fields to search across */
  protected String[] fields;

  /** Field weights for BM25F scoring. All weights must be &gt;= 1.0. */
  protected Map<String, Float> boosts;

  /**
   * Creates a BM25FMultiFieldQueryParser with the specified fields and analyzer.
   *
   * <p>All fields will have equal weight (1.0) in BM25F scoring.
   *
   * @param fields the fields to search across
   * @param analyzer the analyzer to use for parsing query text
   */
  public BM25FMultiFieldQueryParser(String[] fields, Analyzer analyzer) {
    super(null, analyzer);
    this.fields = fields;
    this.boosts = null;
  }

  /**
   * Creates a BM25FMultiFieldQueryParser with the specified fields, analyzer, and field weights.
   *
   * <p>Field weights control how much each field contributes to the BM25F score. Higher weights
   * give more importance to matches in that field.
   *
   * @param fields the fields to search across
   * @param analyzer the analyzer to use for parsing query text
   * @param boosts a map of field names to their weights. All weights must be &gt;= 1.0.
   * @throws IllegalArgumentException if any weight is less than 1.0
   */
  public BM25FMultiFieldQueryParser(String[] fields, Analyzer analyzer, Map<String, Float> boosts) {
    this(fields, analyzer);
    this.boosts = boosts;
    // Validate boosts - CombinedFieldQuery requires weights >= 1.0
    if (boosts != null) {
      for (Map.Entry<String, Float> entry : boosts.entrySet()) {
        if (entry.getValue() < 1.0f) {
          throw new IllegalArgumentException(
              "BM25F requires field weights >= 1.0, but field '"
                  + entry.getKey()
                  + "' has weight "
                  + entry.getValue()
                  + ". To achieve equivalent effect, scale up other field weights instead.");
        }
      }
    }
  }

  /**
   * Gets the weight for a field.
   *
   * @param field the field name
   * @return the weight for the field, or 1.0f if no weight is specified
   */
  protected float getFieldWeight(String field) {
    if (boosts != null && boosts.containsKey(field)) {
      return boosts.get(field);
    }
    return 1.0f;
  }

  @Override
  protected Query getFieldQuery(String field, String queryText, int slop) throws ParseException {
    if (field == null) {
      // Multi-field query - need to handle phrase queries specially
      Query query = getFieldQuery(field, queryText, true);
      if (query == null) {
        return null;
      }
      // Apply slop to phrase queries
      return applySlop(query, slop);
    }
    // Single field query - delegate to parent
    Query q = super.getFieldQuery(field, queryText, true);
    return applySlop(q, slop);
  }

  /**
   * Applies slop to phrase queries.
   *
   * @param query the query to apply slop to
   * @param slop the slop value
   * @return the query with slop applied if applicable
   */
  private Query applySlop(Query query, int slop) {
    if (query instanceof PhraseQuery) {
      PhraseQuery.Builder builder = new PhraseQuery.Builder();
      builder.setSlop(slop);
      PhraseQuery pq = (PhraseQuery) query;
      org.apache.lucene.index.Term[] terms = pq.getTerms();
      int[] positions = pq.getPositions();
      for (int i = 0; i < terms.length; ++i) {
        builder.add(terms[i], positions[i]);
      }
      return builder.build();
    } else if (query instanceof MultiPhraseQuery mpq) {
      if (slop != mpq.getSlop()) {
        return new MultiPhraseQuery.Builder(mpq).setSlop(slop).build();
      }
    } else if (query instanceof BooleanQuery bq) {
      // Apply slop to all phrase queries in a boolean query
      BooleanQuery.Builder builder = new BooleanQuery.Builder();
      boolean changed = false;
      for (BooleanClause clause : bq.clauses()) {
        Query subQuery = clause.query();
        Query newSubQuery = applySlop(subQuery, slop);
        if (newSubQuery != subQuery) {
          changed = true;
        }
        builder.add(newSubQuery, clause.occur());
      }
      if (changed) {
        return builder.build();
      }
    } else if (query instanceof BoostQuery boostQuery) {
      Query subQuery = boostQuery.getQuery();
      Query newSubQuery = applySlop(subQuery, slop);
      if (newSubQuery != subQuery) {
        return new BoostQuery(newSubQuery, boostQuery.getBoost());
      }
    }
    return query;
  }

  @Override
  protected Query getFieldQuery(String field, String queryText, boolean quoted)
      throws ParseException {
    if (field == null) {
      // Multi-field query
      // First, get field queries for all fields to determine the query structure
      Map<String, Query> fieldQueries = new LinkedHashMap<>();
      for (String f : fields) {
        Query q = super.getFieldQuery(f, queryText, quoted);
        if (q != null) {
          fieldQueries.put(f, q);
        }
      }

      if (fieldQueries.isEmpty()) {
        return null; // All terms were filtered (e.g., stop words)
      }

      // Check if all queries are simple TermQueries - if so, use CombinedFieldQuery
      // Also check for BooleanQuery of TermQueries (multi-term analyzed queries)
      boolean canUseCombinedFieldQuery = true;
      boolean isMultiTermQuery = false;
      int maxTerms = 0;

      for (Query q : fieldQueries.values()) {
        if (q instanceof TermQuery) {
          maxTerms = Math.max(1, maxTerms);
        } else if (q instanceof BooleanQuery bq) {
          // Check if all clauses are TermQueries
          for (BooleanClause clause : bq.clauses()) {
            if (!(clause.query() instanceof TermQuery)) {
              canUseCombinedFieldQuery = false;
              break;
            }
          }
          if (canUseCombinedFieldQuery) {
            isMultiTermQuery = true;
            maxTerms = Math.max(bq.clauses().size(), maxTerms);
          }
        } else {
          // Phrase query or other complex query - can't use CombinedFieldQuery
          canUseCombinedFieldQuery = false;
        }
        if (!canUseCombinedFieldQuery) {
          break;
        }
      }

      if (canUseCombinedFieldQuery) {
        // Use CombinedFieldQuery for BM25F scoring
        if (isMultiTermQuery) {
          // Multiple terms - create a BooleanQuery of CombinedFieldQueries
          return buildMultiTermCombinedFieldQuery(fieldQueries, maxTerms);
        } else {
          // Single term - create a single CombinedFieldQuery
          String term = extractTerm(fieldQueries.values().iterator().next());
          if (term != null) {
            return buildCombinedFieldQuery(term);
          }
        }
      }

      // Fall back to BooleanQuery with per-field queries (for phrases, etc.)
      return buildBooleanMultiFieldQuery(fieldQueries);
    }

    // Single field query - delegate to parent
    return super.getFieldQuery(field, queryText, quoted);
  }

  /**
   * Builds a CombinedFieldQuery for a single term across all fields.
   *
   * @param term the term text
   * @return the CombinedFieldQuery
   */
  private Query buildCombinedFieldQuery(String term) {
    CombinedFieldQuery.Builder builder = new CombinedFieldQuery.Builder(term);
    for (String f : fields) {
      float weight = getFieldWeight(f);
      builder.addField(f, weight);
    }
    return builder.build();
  }

  /**
   * Builds a BooleanQuery of CombinedFieldQueries for multi-term queries.
   *
   * @param fieldQueries the per-field queries
   * @param maxTerms the maximum number of terms across all fields
   * @return the combined query
   */
  private Query buildMultiTermCombinedFieldQuery(Map<String, Query> fieldQueries, int maxTerms) {
    List<Query> termQueries = new ArrayList<>();

    for (int termNum = 0; termNum < maxTerms; termNum++) {
      String term = null;
      // Find the term for this position from any field
      for (Map.Entry<String, Query> entry : fieldQueries.entrySet()) {
        Query q = entry.getValue();
        if (q instanceof TermQuery tq) {
          if (termNum == 0) {
            term = tq.getTerm().text();
            break;
          }
        } else if (q instanceof BooleanQuery bq) {
          List<BooleanClause> clauses = bq.clauses();
          if (termNum < clauses.size()) {
            Query clauseQuery = clauses.get(termNum).query();
            if (clauseQuery instanceof TermQuery tq) {
              term = tq.getTerm().text();
              break;
            }
          }
        }
      }

      if (term != null) {
        termQueries.add(buildCombinedFieldQuery(term));
      }
    }

    if (termQueries.isEmpty()) {
      return null;
    }

    if (termQueries.size() == 1) {
      return termQueries.get(0);
    }

    // Combine with BooleanQuery
    BooleanQuery.Builder builder = newBooleanQuery();
    BooleanClause.Occur occur =
        getDefaultOperator() == Operator.AND ? BooleanClause.Occur.MUST : BooleanClause.Occur.SHOULD;
    for (Query q : termQueries) {
      builder.add(q, occur);
    }
    return builder.build();
  }

  /**
   * Extracts the term text from a TermQuery.
   *
   * @param query the query to extract from
   * @return the term text, or null if not a TermQuery
   */
  private String extractTerm(Query query) {
    if (query instanceof TermQuery tq) {
      return tq.getTerm().text();
    }
    return null;
  }

  /**
   * Builds a BooleanQuery combining per-field queries (used for phrases and complex queries).
   *
   * @param fieldQueries the per-field queries
   * @return the combined query
   */
  private Query buildBooleanMultiFieldQuery(Map<String, Query> fieldQueries) {
    BooleanQuery.Builder builder = newBooleanQuery();
    for (Map.Entry<String, Query> entry : fieldQueries.entrySet()) {
      Query q = entry.getValue();
      // Apply field boost if specified
      float weight = getFieldWeight(entry.getKey());
      if (weight != 1.0f) {
        q = new BoostQuery(q, weight);
      }
      builder.add(q, BooleanClause.Occur.SHOULD);
    }
    return builder.build();
  }

  @Override
  protected Query getFuzzyQuery(String field, String termStr, float minSimilarity)
      throws ParseException {
    if (field == null) {
      List<Query> clauses = new ArrayList<>();
      for (String f : fields) {
        Query q = super.getFuzzyQuery(f, termStr, minSimilarity);
        float weight = getFieldWeight(f);
        if (weight != 1.0f) {
          q = new BoostQuery(q, weight);
        }
        clauses.add(q);
      }
      return buildBooleanQuery(clauses);
    }
    return super.getFuzzyQuery(field, termStr, minSimilarity);
  }

  @Override
  protected Query getPrefixQuery(String field, String termStr) throws ParseException {
    if (field == null) {
      List<Query> clauses = new ArrayList<>();
      for (String f : fields) {
        Query q = super.getPrefixQuery(f, termStr);
        float weight = getFieldWeight(f);
        if (weight != 1.0f) {
          q = new BoostQuery(q, weight);
        }
        clauses.add(q);
      }
      return buildBooleanQuery(clauses);
    }
    return super.getPrefixQuery(field, termStr);
  }

  @Override
  protected Query getWildcardQuery(String field, String termStr) throws ParseException {
    if (field == null) {
      List<Query> clauses = new ArrayList<>();
      for (String f : fields) {
        Query q = super.getWildcardQuery(f, termStr);
        float weight = getFieldWeight(f);
        if (weight != 1.0f) {
          q = new BoostQuery(q, weight);
        }
        clauses.add(q);
      }
      return buildBooleanQuery(clauses);
    }
    return super.getWildcardQuery(field, termStr);
  }

  @Override
  protected Query getRangeQuery(
      String field, String part1, String part2, boolean startInclusive, boolean endInclusive)
      throws ParseException {
    if (field == null) {
      List<Query> clauses = new ArrayList<>();
      for (String f : fields) {
        Query q = super.getRangeQuery(f, part1, part2, startInclusive, endInclusive);
        float weight = getFieldWeight(f);
        if (weight != 1.0f) {
          q = new BoostQuery(q, weight);
        }
        clauses.add(q);
      }
      return buildBooleanQuery(clauses);
    }
    return super.getRangeQuery(field, part1, part2, startInclusive, endInclusive);
  }

  @Override
  protected Query getRegexpQuery(String field, String termStr) throws ParseException {
    if (field == null) {
      List<Query> clauses = new ArrayList<>();
      for (String f : fields) {
        Query q = super.getRegexpQuery(f, termStr);
        float weight = getFieldWeight(f);
        if (weight != 1.0f) {
          q = new BoostQuery(q, weight);
        }
        clauses.add(q);
      }
      return buildBooleanQuery(clauses);
    }
    return super.getRegexpQuery(field, termStr);
  }

  /**
   * Builds a BooleanQuery from a list of queries.
   *
   * @param queries the queries to combine
   * @return the combined query, or null if all queries were null
   */
  protected Query buildBooleanQuery(List<Query> queries) {
    if (queries.isEmpty()) {
      return null;
    }
    BooleanQuery.Builder builder = newBooleanQuery();
    for (Query q : queries) {
      builder.add(q, BooleanClause.Occur.SHOULD);
    }
    return builder.build();
  }
}

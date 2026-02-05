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

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.TermToBytesRefAttribute;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BoostQuery;
import org.apache.lucene.search.CombinedFieldQuery;
import org.apache.lucene.search.MultiPhraseQuery;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.util.BytesRef;

/**
 * A query parser that uses BM25F scoring for multi-field term queries.
 *
 * <p>BM25F (BM25 with Field weights) extends the BM25 ranking function to support multiple weighted
 * fields. Unlike {@link MultiFieldQueryParser} which creates independent term queries for each
 * field combined with a disjunction, this parser uses {@link CombinedFieldQuery} which treats
 * multiple fields as a single combined field for more accurate BM25F scoring.
 *
 * <p>Usage example:
 *
 * <pre class="prettyprint">
 * Map&lt;String, Float&gt; fieldWeights = new LinkedHashMap&lt;&gt;();
 * fieldWeights.put("title", 2.0f);
 * fieldWeights.put("body", 1.0f);
 * BM25FQueryParser parser = new BM25FQueryParser(fieldWeights, analyzer);
 * Query q = parser.parse("search terms");
 * </pre>
 *
 * <p>The field weights must be greater than or equal to 1.0 as required by {@link
 * CombinedFieldQuery}.
 *
 * <p><b>Important notes:</b>
 *
 * <ul>
 *   <li>All fields should use the same analyzer for consistent scoring
 *   <li>Either all fields or no fields should have norms enabled
 *   <li>Field weights must be &gt;= 1.0 (a requirement of CombinedFieldQuery)
 *   <li>For query types other than term queries (e.g., phrase, fuzzy, wildcard), the parser falls
 *       back to a disjunction of field-specific queries
 * </ul>
 *
 * @see CombinedFieldQuery
 * @see MultiFieldQueryParser
 * @lucene.experimental
 */
public class BM25FQueryParser extends QueryParser {

  /** Map of field names to their weights. Order is preserved. */
  protected final Map<String, Float> fieldWeights;

  /** Array of field names for iteration */
  protected final String[] fields;

  /**
   * Creates a BM25F query parser with the specified field weights.
   *
   * @param fieldWeights A map from field names to their weights. Weights must be &gt;= 1.0. The map
   *     iteration order determines the order of fields in generated queries. Using a {@link
   *     LinkedHashMap} is recommended to ensure consistent ordering.
   * @param analyzer The analyzer used to tokenize query text and find terms.
   * @throws IllegalArgumentException if fieldWeights is null or empty, or if any weight is less
   *     than 1.0
   */
  public BM25FQueryParser(Map<String, Float> fieldWeights, Analyzer analyzer) {
    super(null, analyzer);
    if (fieldWeights == null || fieldWeights.isEmpty()) {
      throw new IllegalArgumentException("fieldWeights must not be null or empty");
    }
    for (Map.Entry<String, Float> entry : fieldWeights.entrySet()) {
      if (entry.getValue() < 1.0f) {
        throw new IllegalArgumentException(
            "weight for field '"
                + entry.getKey()
                + "' must be >= 1.0, got: "
                + entry.getValue());
      }
    }
    this.fieldWeights = new LinkedHashMap<>(fieldWeights);
    this.fields = fieldWeights.keySet().toArray(new String[0]);
  }

  /**
   * Creates a BM25F query parser with equal weights for all fields.
   *
   * @param fields The fields to search across
   * @param analyzer The analyzer used to tokenize query text
   * @throws IllegalArgumentException if fields is null or empty
   */
  public BM25FQueryParser(String[] fields, Analyzer analyzer) {
    super(null, analyzer);
    if (fields == null || fields.length == 0) {
      throw new IllegalArgumentException("fields must not be null or empty");
    }
    this.fieldWeights = new LinkedHashMap<>();
    for (String field : fields) {
      this.fieldWeights.put(field, 1.0f);
    }
    this.fields = fields.clone();
  }

  /**
   * Returns the field weights used by this parser.
   *
   * @return An unmodifiable view would be ideal, but returns the internal map for now
   */
  public Map<String, Float> getFieldWeights() {
    return fieldWeights;
  }

  /**
   * Returns the fields used by this parser.
   *
   * @return A copy of the fields array
   */
  public String[] getFields() {
    return fields.clone();
  }

  /**
   * Generates a query for the given field and query text.
   *
   * <p>When the field is null (indicating a multi-field query), this method analyzes the query text
   * and creates either:
   *
   * <ul>
   *   <li>A {@link CombinedFieldQuery} for single-term queries (proper BM25F scoring)
   *   <li>A {@link BooleanQuery} containing multiple CombinedFieldQueries for multi-term queries
   * </ul>
   *
   * <p>When a specific field is provided, delegates to the parent class.
   */
  @Override
  protected Query getFieldQuery(String field, String queryText, boolean quoted)
      throws ParseException {
    if (field != null) {
      // Specific field requested, use standard behavior
      return super.getFieldQuery(field, queryText, quoted);
    }

    if (quoted) {
      // For quoted phrases, use multi-field phrase query
      return createMultiFieldPhraseQuery(queryText);
    }

    // Analyze the query text to extract terms
    List<BytesRef> terms = analyzeQueryText(queryText);

    if (terms.isEmpty()) {
      return null;
    }

    if (terms.size() == 1) {
      // Single term - use CombinedFieldQuery for BM25F scoring
      return createCombinedFieldQuery(terms.get(0));
    }

    // Multiple terms - create CombinedFieldQuery for each term and combine with BooleanQuery
    BooleanQuery.Builder builder = newBooleanQuery();
    BooleanClause.Occur occur =
        getDefaultOperator() == Operator.AND ? BooleanClause.Occur.MUST : BooleanClause.Occur.SHOULD;

    for (BytesRef term : terms) {
      Query termQuery = createCombinedFieldQuery(term);
      if (termQuery != null) {
        builder.add(termQuery, occur);
      }
    }

    BooleanQuery query = builder.build();
    return query.clauses().isEmpty() ? null : query;
  }

  /**
   * Generates a phrase query with optional slop.
   *
   * <p>When the field is null, creates a multi-field phrase query using a disjunction.
   */
  @Override
  protected Query getFieldQuery(String field, String queryText, int slop) throws ParseException {
    if (field != null) {
      return super.getFieldQuery(field, queryText, slop);
    }

    Query query = createMultiFieldPhraseQuery(queryText);
    if (query == null) {
      return null;
    }

    // Apply slop to the query
    return applySlopToQuery(query, slop);
  }

  /**
   * Creates a CombinedFieldQuery for the given term bytes, using all configured fields and weights.
   *
   * @param termBytes The term as bytes
   * @return A CombinedFieldQuery or null if no fields are configured
   */
  protected Query createCombinedFieldQuery(BytesRef termBytes) {
    CombinedFieldQuery.Builder builder = new CombinedFieldQuery.Builder(termBytes);
    for (Map.Entry<String, Float> entry : fieldWeights.entrySet()) {
      builder.addField(entry.getKey(), entry.getValue());
    }
    return builder.build();
  }

  /**
   * Creates a CombinedFieldQuery for the given term string, using all configured fields and
   * weights.
   *
   * @param term The term as a string
   * @return A CombinedFieldQuery or null if no fields are configured
   */
  protected Query createCombinedFieldQuery(String term) {
    CombinedFieldQuery.Builder builder = new CombinedFieldQuery.Builder(term);
    for (Map.Entry<String, Float> entry : fieldWeights.entrySet()) {
      builder.addField(entry.getKey(), entry.getValue());
    }
    return builder.build();
  }

  /**
   * Creates a multi-field phrase query by creating phrase queries for each field and combining them
   * with OR.
   *
   * @param queryText The phrase text
   * @return A BooleanQuery containing phrase queries for each field, or null if analysis produces
   *     no terms
   */
  protected Query createMultiFieldPhraseQuery(String queryText) throws ParseException {
    List<Query> phraseQueries = new ArrayList<>();

    for (Map.Entry<String, Float> entry : fieldWeights.entrySet()) {
      String fieldName = entry.getKey();
      float weight = entry.getValue();

      Query phraseQuery = super.getFieldQuery(fieldName, queryText, true);
      if (phraseQuery != null) {
        if (weight != 1.0f) {
          phraseQuery = new BoostQuery(phraseQuery, weight);
        }
        phraseQueries.add(phraseQuery);
      }
    }

    if (phraseQueries.isEmpty()) {
      return null;
    }

    if (phraseQueries.size() == 1) {
      return phraseQueries.get(0);
    }

    BooleanQuery.Builder builder = newBooleanQuery();
    for (Query q : phraseQueries) {
      builder.add(q, BooleanClause.Occur.SHOULD);
    }
    return builder.build();
  }

  /**
   * Analyzes the query text and returns the list of term bytes.
   *
   * @param queryText The query text to analyze
   * @return List of term bytes
   */
  protected List<BytesRef> analyzeQueryText(String queryText) {
    List<BytesRef> terms = new ArrayList<>();

    // Use the first field for analysis (assuming all fields use the same analyzer)
    String analyzeField = fields[0];

    try (TokenStream ts = getAnalyzer().tokenStream(analyzeField, queryText)) {
      TermToBytesRefAttribute termAtt = ts.addAttribute(TermToBytesRefAttribute.class);
      ts.reset();
      while (ts.incrementToken()) {
        terms.add(BytesRef.deepCopyOf(termAtt.getBytesRef()));
      }
      ts.end();
    } catch (IOException e) {
      // If analysis fails, treat the whole text as a single term
      terms.clear();
      terms.add(new BytesRef(queryText));
    }

    return terms;
  }

  /**
   * Applies slop to a phrase query or multi-phrase query.
   *
   * @param query The query to modify
   * @param slop The slop value
   * @return The modified query
   */
  private Query applySlopToQuery(Query query, int slop) {
    if (query instanceof PhraseQuery pq) {
      if (pq.getSlop() != slop) {
        PhraseQuery.Builder builder = new PhraseQuery.Builder();
        builder.setSlop(slop);
        org.apache.lucene.index.Term[] terms = pq.getTerms();
        int[] positions = pq.getPositions();
        for (int i = 0; i < terms.length; i++) {
          builder.add(terms[i], positions[i]);
        }
        return builder.build();
      }
    } else if (query instanceof MultiPhraseQuery mpq) {
      if (mpq.getSlop() != slop) {
        return new MultiPhraseQuery.Builder(mpq).setSlop(slop).build();
      }
    } else if (query instanceof BooleanQuery bq) {
      // Apply slop to all phrase queries in the boolean query
      BooleanQuery.Builder builder = new BooleanQuery.Builder();
      builder.setMinimumNumberShouldMatch(bq.getMinimumNumberShouldMatch());
      for (BooleanClause clause : bq.clauses()) {
        Query subQuery = applySlopToQuery(clause.query(), slop);
        builder.add(subQuery, clause.occur());
      }
      return builder.build();
    } else if (query instanceof BoostQuery boostQuery) {
      Query inner = applySlopToQuery(boostQuery.getQuery(), slop);
      if (inner != boostQuery.getQuery()) {
        return new BoostQuery(inner, boostQuery.getBoost());
      }
    }
    return query;
  }

  // Override multi-field specific methods to handle null field

  @Override
  protected Query getFuzzyQuery(String field, String termStr, float minSimilarity)
      throws ParseException {
    if (field == null) {
      return createMultiFieldQuery(
          (f) -> {
            try {
              return super.getFuzzyQuery(f, termStr, minSimilarity);
            } catch (ParseException e) {
              throw new RuntimeException(e);
            }
          });
    }
    return super.getFuzzyQuery(field, termStr, minSimilarity);
  }

  @Override
  protected Query getPrefixQuery(String field, String termStr) throws ParseException {
    if (field == null) {
      return createMultiFieldQuery(
          (f) -> {
            try {
              return super.getPrefixQuery(f, termStr);
            } catch (ParseException e) {
              throw new RuntimeException(e);
            }
          });
    }
    return super.getPrefixQuery(field, termStr);
  }

  @Override
  protected Query getWildcardQuery(String field, String termStr) throws ParseException {
    if (field == null) {
      // Handle match all case
      if ("*".equals(termStr)) {
        return newMatchAllDocsQuery();
      }
      return createMultiFieldQuery(
          (f) -> {
            try {
              return super.getWildcardQuery(f, termStr);
            } catch (ParseException e) {
              throw new RuntimeException(e);
            }
          });
    }
    return super.getWildcardQuery(field, termStr);
  }

  @Override
  protected Query getRegexpQuery(String field, String termStr) throws ParseException {
    if (field == null) {
      return createMultiFieldQuery(
          (f) -> {
            try {
              return super.getRegexpQuery(f, termStr);
            } catch (ParseException e) {
              throw new RuntimeException(e);
            }
          });
    }
    return super.getRegexpQuery(field, termStr);
  }

  @Override
  protected Query getRangeQuery(
      String field, String part1, String part2, boolean startInclusive, boolean endInclusive)
      throws ParseException {
    if (field == null) {
      return createMultiFieldQuery(
          (f) -> {
            try {
              return super.getRangeQuery(f, part1, part2, startInclusive, endInclusive);
            } catch (ParseException e) {
              throw new RuntimeException(e);
            }
          });
    }
    return super.getRangeQuery(field, part1, part2, startInclusive, endInclusive);
  }

  /**
   * Functional interface for creating queries per field.
   */
  @FunctionalInterface
  private interface QueryFactory {
    Query create(String field);
  }

  /**
   * Creates a multi-field query by applying the query factory to each field and combining with OR.
   *
   * @param factory The factory that creates a query for a given field
   * @return A BooleanQuery combining all field queries, or null if no queries were produced
   */
  private Query createMultiFieldQuery(QueryFactory factory) {
    List<Query> queries = new ArrayList<>();

    for (Map.Entry<String, Float> entry : fieldWeights.entrySet()) {
      String fieldName = entry.getKey();
      float weight = entry.getValue();

      Query query = factory.create(fieldName);
      if (query != null) {
        if (weight != 1.0f) {
          query = new BoostQuery(query, weight);
        }
        queries.add(query);
      }
    }

    if (queries.isEmpty()) {
      return null;
    }

    if (queries.size() == 1) {
      return queries.get(0);
    }

    BooleanQuery.Builder builder = newBooleanQuery();
    for (Query q : queries) {
      builder.add(q, BooleanClause.Occur.SHOULD);
    }
    return builder.build();
  }
}

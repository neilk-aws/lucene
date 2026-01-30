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
import java.util.List;
import java.util.Map;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.CombinedFieldQuery;
import org.apache.lucene.search.Query;

/**
 * A QueryParser that constructs BM25F multi-field queries using {@link CombinedFieldQuery}.
 *
 * <p>BM25F is an extension of BM25 that supports multiple fields with different weights. Instead of
 * creating a disjunction of queries across fields, this parser creates {@link CombinedFieldQuery}
 * instances that properly aggregate term frequencies across fields before scoring, as described in
 * the BM25F algorithm.
 *
 * <p>Usage example:
 *
 * <pre><code class="language-java">
 * Map&lt;String, Float&gt; fieldWeights = new HashMap&lt;&gt;();
 * fieldWeights.put("title", 2.0f);
 * fieldWeights.put("body", 1.0f);
 * BM25FQueryParser parser = new BM25FQueryParser(
 *     new String[]{"title", "body"},
 *     analyzer,
 *     fieldWeights);
 * Query query = parser.parse("machine learning");
 * </code></pre>
 *
 * <p>This will create a query that searches for "machine" and "learning" across both the "title"
 * and "body" fields, with the title field having twice the weight of the body field. The scoring
 * follows the BM25F algorithm, which aggregates term frequencies across fields before computing
 * the final BM25 score.
 *
 * @lucene.experimental
 */
public class BM25FQueryParser extends QueryParser {
  protected String[] fields;
  protected Map<String, Float> fieldWeights;

  /**
   * Creates a BM25FQueryParser with field weights.
   *
   * @param fields The fields to search across
   * @param analyzer The analyzer to use for parsing queries
   * @param fieldWeights A map of field names to weights (must be >= 1.0)
   * @throws IllegalArgumentException if any weight is less than 1.0
   */
  public BM25FQueryParser(String[] fields, Analyzer analyzer, Map<String, Float> fieldWeights) {
    super(null, analyzer);
    this.fields = fields;
    this.fieldWeights = fieldWeights;
    
    // Validate weights
    if (fieldWeights != null) {
      for (Map.Entry<String, Float> entry : fieldWeights.entrySet()) {
        if (entry.getValue() < 1.0f) {
          throw new IllegalArgumentException(
              "Field weight for '" + entry.getKey() + "' must be >= 1.0, got: " + entry.getValue());
        }
      }
    }
  }

  /**
   * Creates a BM25FQueryParser with equal weights for all fields.
   *
   * @param fields The fields to search across
   * @param analyzer The analyzer to use for parsing queries
   */
  public BM25FQueryParser(String[] fields, Analyzer analyzer) {
    this(fields, analyzer, null);
  }

  @Override
  protected Query getFieldQuery(String field, String queryText, boolean quoted)
      throws ParseException {
    if (field == null) {
      // For multi-field queries, we need to analyze the query text and create
      // CombinedFieldQuery instances for each term
      return createBM25FQuery(queryText, quoted);
    }
    // For single field queries, delegate to parent
    return super.getFieldQuery(field, queryText, quoted);
  }

  @Override
  protected Query getFieldQuery(String field, String queryText, int slop) throws ParseException {
    if (field == null) {
      // For phrase queries with slop, fall back to the standard multi-field behavior
      // CombinedFieldQuery doesn't support phrase queries yet
      return createStandardMultiFieldQuery(queryText, slop);
    }
    return super.getFieldQuery(field, queryText, slop);
  }

  /**
   * Creates a BM25F query by analyzing the query text and creating CombinedFieldQuery instances
   * for each term.
   */
  private Query createBM25FQuery(String queryText, boolean quoted) throws ParseException {
    List<String> tokens = analyzeQueryText(queryText);
    
    if (tokens.isEmpty()) {
      return null; // No tokens after analysis (e.g., all stopwords)
    }

    if (tokens.size() == 1) {
      // Single term - create a single CombinedFieldQuery
      return createCombinedFieldQuery(tokens.get(0));
    } else {
      // Multiple terms - create a BooleanQuery with CombinedFieldQuery for each term
      BooleanQuery.Builder builder = newBooleanQuery();
      for (String token : tokens) {
        Query termQuery = createCombinedFieldQuery(token);
        if (termQuery != null) {
          builder.add(termQuery, BooleanClause.Occur.SHOULD);
        }
      }
      BooleanQuery bq = builder.build();
      return bq.clauses().isEmpty() ? null : bq;
    }
  }

  /**
   * Creates a CombinedFieldQuery for a single term across all configured fields.
   */
  private Query createCombinedFieldQuery(String term) {
    CombinedFieldQuery.Builder builder = new CombinedFieldQuery.Builder(term);
    
    for (String field : fields) {
      float weight = 1.0f;
      if (fieldWeights != null && fieldWeights.containsKey(field)) {
        weight = fieldWeights.get(field);
      }
      builder.addField(field, weight);
    }
    
    return builder.build();
  }

  /**
   * Falls back to standard multi-field query behavior for complex queries that CombinedFieldQuery
   * doesn't support (e.g., phrase queries with slop).
   */
  private Query createStandardMultiFieldQuery(String queryText, int slop) throws ParseException {
    List<Query> clauses = new ArrayList<>();
    for (String field : fields) {
      Query q = super.getFieldQuery(field, queryText, slop);
      if (q != null) {
        clauses.add(q);
      }
    }
    
    if (clauses.isEmpty()) {
      return null;
    }
    
    if (clauses.size() == 1) {
      return clauses.get(0);
    }
    
    BooleanQuery.Builder builder = newBooleanQuery();
    for (Query clause : clauses) {
      builder.add(clause, BooleanClause.Occur.SHOULD);
    }
    return builder.build();
  }

  /**
   * Analyzes the query text and returns a list of tokens.
   */
  private List<String> analyzeQueryText(String text) throws ParseException {
    List<String> tokens = new ArrayList<>();
    
    try (TokenStream tokenStream = getAnalyzer().tokenStream("", text)) {
      CharTermAttribute termAttr = tokenStream.addAttribute(CharTermAttribute.class);
      tokenStream.reset();
      
      while (tokenStream.incrementToken()) {
        tokens.add(termAttr.toString());
      }
      
      tokenStream.end();
    } catch (IOException e) {
      throw new ParseException("Error analyzing query text: " + e.getMessage());
    }
    
    return tokens;
  }

  @Override
  protected Query getFuzzyQuery(String field, String termStr, float minSimilarity)
      throws ParseException {
    if (field == null) {
      // For fuzzy queries, fall back to standard multi-field behavior
      // CombinedFieldQuery doesn't support fuzzy queries
      List<Query> clauses = new ArrayList<>();
      for (String f : fields) {
        Query q = super.getFuzzyQuery(f, termStr, minSimilarity);
        if (q != null) {
          clauses.add(q);
        }
      }
      return clauses.isEmpty() ? null : getMultiFieldQuery(clauses);
    }
    return super.getFuzzyQuery(field, termStr, minSimilarity);
  }

  @Override
  protected Query getPrefixQuery(String field, String termStr) throws ParseException {
    if (field == null) {
      // For prefix queries, fall back to standard multi-field behavior
      List<Query> clauses = new ArrayList<>();
      for (String f : fields) {
        Query q = super.getPrefixQuery(f, termStr);
        if (q != null) {
          clauses.add(q);
        }
      }
      return clauses.isEmpty() ? null : getMultiFieldQuery(clauses);
    }
    return super.getPrefixQuery(field, termStr);
  }

  @Override
  protected Query getWildcardQuery(String field, String termStr) throws ParseException {
    if (field == null) {
      // For wildcard queries, fall back to standard multi-field behavior
      List<Query> clauses = new ArrayList<>();
      for (String f : fields) {
        Query q = super.getWildcardQuery(f, termStr);
        if (q != null) {
          clauses.add(q);
        }
      }
      return clauses.isEmpty() ? null : getMultiFieldQuery(clauses);
    }
    return super.getWildcardQuery(field, termStr);
  }

  @Override
  protected Query getRangeQuery(
      String field, String part1, String part2, boolean startInclusive, boolean endInclusive)
      throws ParseException {
    if (field == null) {
      // For range queries, fall back to standard multi-field behavior
      List<Query> clauses = new ArrayList<>();
      for (String f : fields) {
        Query q = super.getRangeQuery(f, part1, part2, startInclusive, endInclusive);
        if (q != null) {
          clauses.add(q);
        }
      }
      return clauses.isEmpty() ? null : getMultiFieldQuery(clauses);
    }
    return super.getRangeQuery(field, part1, part2, startInclusive, endInclusive);
  }

  @Override
  protected Query getRegexpQuery(String field, String termStr) throws ParseException {
    if (field == null) {
      // For regexp queries, fall back to standard multi-field behavior
      List<Query> clauses = new ArrayList<>();
      for (String f : fields) {
        Query q = super.getRegexpQuery(f, termStr);
        if (q != null) {
          clauses.add(q);
        }
      }
      return clauses.isEmpty() ? null : getMultiFieldQuery(clauses);
    }
    return super.getRegexpQuery(field, termStr);
  }

  /** Helper method to create a multi-field query using disjunction. */
  protected Query getMultiFieldQuery(List<Query> queries) throws ParseException {
    if (queries.isEmpty()) {
      return null;
    }
    BooleanQuery.Builder query = newBooleanQuery();
    for (Query sub : queries) {
      query.add(sub, BooleanClause.Occur.SHOULD);
    }
    return query.build();
  }

  /**
   * Parses a query across the specified fields using BM25F scoring.
   *
   * @param queryText The query string to parse
   * @param fields The fields to search
   * @param analyzer The analyzer to use
   * @param fieldWeights Optional field weights (must be >= 1.0)
   * @return The parsed query
   * @throws ParseException if parsing fails
   */
  public static Query parse(
      String queryText, String[] fields, Analyzer analyzer, Map<String, Float> fieldWeights)
      throws ParseException {
    BM25FQueryParser parser = new BM25FQueryParser(fields, analyzer, fieldWeights);
    return parser.parse(queryText);
  }

  /**
   * Parses a query across the specified fields using BM25F scoring with equal weights.
   *
   * @param queryText The query string to parse
   * @param fields The fields to search
   * @param analyzer The analyzer to use
   * @return The parsed query
   * @throws ParseException if parsing fails
   */
  public static Query parse(String queryText, String[] fields, Analyzer analyzer)
      throws ParseException {
    return parse(queryText, fields, analyzer, null);
  }
}

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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.TermToBytesRefAttribute;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BoostQuery;
import org.apache.lucene.search.CombinedFieldQuery;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.MatchNoDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.QueryBuilder;

/**
 * A query parser that produces {@link CombinedFieldQuery} instances for BM25F-style scoring across
 * multiple fields.
 *
 * <p>BM25F (BM25 with field weights) is an extension of the BM25 ranking function that supports
 * searching across multiple fields with field-specific weights. Unlike traditional multi-field
 * queries that score each field independently and combine scores, BM25F treats the weighted
 * combination of fields as a single virtual field for more accurate relevance ranking.
 *
 * <p>This parser uses {@link CombinedFieldQuery} to implement BM25F-style scoring. For each term in
 * the query, it creates a {@link CombinedFieldQuery} that searches across all configured fields
 * with their respective weights. For multi-term queries, the individual {@link CombinedFieldQuery}
 * instances are combined using a {@link BooleanQuery} with the configured default operator.
 *
 * <h2>Basic Usage</h2>
 *
 * <pre class="prettyprint">
 * // Create field weights map
 * Map&lt;String, Float&gt; fieldWeights = new LinkedHashMap&lt;&gt;();
 * fieldWeights.put("title", 5.0f);    // Title field with weight 5.0
 * fieldWeights.put("body", 1.0f);     // Body field with weight 1.0
 * fieldWeights.put("anchor", 2.0f);   // Anchor text with weight 2.0
 *
 * // Create parser with analyzer and field weights
 * BM25FQueryParser parser = new BM25FQueryParser(analyzer, fieldWeights);
 *
 * // Parse a query string
 * Query query = parser.parse("search engine optimization");
 * </pre>
 *
 * <h2>Query Processing</h2>
 *
 * <p>The parser processes query text as follows:
 *
 * <ol>
 *   <li>The query text is tokenized using the configured {@link Analyzer}.
 *   <li>For each unique term produced by the analyzer, a {@link CombinedFieldQuery} is created that
 *       searches across all configured fields with their weights.
 *   <li>If the query produces multiple terms, they are combined using a {@link BooleanQuery} with
 *       the default operator (OR by default, configurable to AND).
 *   <li>For phrase queries (text in quotes), the parser creates a {@link BooleanQuery} of phrase
 *       queries across each field, since {@link CombinedFieldQuery} only supports single terms.
 * </ol>
 *
 * <h2>Field Weights</h2>
 *
 * <p>Field weights must be &gt;= 1.0 as required by {@link CombinedFieldQuery}. Higher weights
 * increase the importance of matches in that field. For example, with weights of 5.0 for title and
 * 1.0 for body, a term match in the title will contribute more to the document's score than a match
 * in the body.
 *
 * <h2>Default Operator</h2>
 *
 * <p>The default operator determines how multiple terms in a query are combined:
 *
 * <ul>
 *   <li>{@link BooleanClause.Occur#SHOULD} (default): Documents matching any term are returned, but
 *       documents matching more terms score higher.
 *   <li>{@link BooleanClause.Occur#MUST}: Documents must match all terms.
 * </ul>
 *
 * <h2>Scoring Model</h2>
 *
 * <p>This parser leverages {@link CombinedFieldQuery}, which implements BM25F-style scoring as
 * described in <a href="http://www.staff.city.ac.uk/~sb317/papers/foundations_bm25_review.pdf">The
 * Probabilistic Relevance Framework: BM25 and Beyond</a>. The actual similarity function used (k1,
 * b parameters) is determined by the {@link org.apache.lucene.search.IndexSearcher}'s configured
 * similarity.
 *
 * <h2>Thread Safety</h2>
 *
 * <p>This class is thread-safe and can be shared across multiple threads.
 *
 * @see CombinedFieldQuery
 * @see org.apache.lucene.search.similarities.BM25Similarity
 * @lucene.experimental
 */
public class BM25FQueryParser extends QueryBuilder {

  /** Map of field names to their weights. All weights must be &gt;= 1.0. */
  protected final Map<String, Float> fieldWeights;

  /**
   * The default operator for combining terms in multi-term queries. Can be {@link
   * BooleanClause.Occur#SHOULD} (OR) or {@link BooleanClause.Occur#MUST} (AND).
   */
  protected BooleanClause.Occur defaultOperator = BooleanClause.Occur.SHOULD;

  /**
   * Creates a new BM25FQueryParser that searches a single field with default weight (1.0).
   *
   * @param analyzer the analyzer used to tokenize query text
   * @param field the field to search
   * @throws NullPointerException if analyzer or field is null
   */
  public BM25FQueryParser(Analyzer analyzer, String field) {
    this(analyzer, Collections.singletonMap(Objects.requireNonNull(field, "field"), 1.0f));
  }

  /**
   * Creates a new BM25FQueryParser that searches multiple fields with the specified weights.
   *
   * <p>The weights map specifies the relative importance of each field. Higher weights increase the
   * contribution of matches in that field to the overall score. All weights must be &gt;= 1.0 as
   * required by {@link CombinedFieldQuery}.
   *
   * @param analyzer the analyzer used to tokenize query text
   * @param fieldWeights a map of field names to their weights (all weights must be &gt;= 1.0)
   * @throws NullPointerException if analyzer or fieldWeights is null
   * @throws IllegalArgumentException if fieldWeights is empty or any weight is &lt; 1.0
   */
  public BM25FQueryParser(Analyzer analyzer, Map<String, Float> fieldWeights) {
    super(Objects.requireNonNull(analyzer, "analyzer"));
    Objects.requireNonNull(fieldWeights, "fieldWeights");

    if (fieldWeights.isEmpty()) {
      throw new IllegalArgumentException("fieldWeights must not be empty");
    }

    // Validate and copy field weights
    Map<String, Float> validatedWeights = new LinkedHashMap<>();
    for (Map.Entry<String, Float> entry : fieldWeights.entrySet()) {
      String field = entry.getKey();
      Float weight = entry.getValue();

      if (field == null) {
        throw new IllegalArgumentException("field name must not be null");
      }
      if (weight == null) {
        throw new IllegalArgumentException("weight for field '" + field + "' must not be null");
      }
      if (weight < 1.0f) {
        throw new IllegalArgumentException(
            "weight for field '"
                + field
                + "' must be >= 1.0 (was "
                + weight
                + "). CombinedFieldQuery requires weights >= 1.0");
      }

      validatedWeights.put(field, weight);
    }

    this.fieldWeights = Collections.unmodifiableMap(validatedWeights);
  }

  /**
   * Parses a query string and returns a {@link Query} suitable for BM25F-style scoring.
   *
   * <p>The query string is tokenized using the configured analyzer. For single-term queries, this
   * returns a {@link CombinedFieldQuery}. For multi-term queries, this returns a {@link
   * BooleanQuery} containing a {@link CombinedFieldQuery} for each term, combined using the default
   * operator.
   *
   * <h3>Supported Query Syntax</h3>
   *
   * <ul>
   *   <li><b>+term</b> - Required term (MUST match)
   *   <li><b>-term</b> - Prohibited term (MUST NOT match)
   *   <li><b>"phrase"</b> - Phrase query (exact sequence match across fields)
   *   <li><b>*</b> - Match all documents
   * </ul>
   *
   * <p>Special cases:
   *
   * <ul>
   *   <li>If the query string is "*", returns a {@link MatchAllDocsQuery}.
   *   <li>If the query string is empty or produces no tokens after analysis, returns a {@link
   *       MatchNoDocsQuery}.
   * </ul>
   *
   * @param queryText the query string to parse
   * @return the parsed query
   * @throws NullPointerException if queryText is null
   */
  public Query parse(String queryText) {
    Objects.requireNonNull(queryText, "queryText");

    // Handle special case of match-all query
    if ("*".equals(queryText.trim())) {
      return MatchAllDocsQuery.INSTANCE;
    }

    // Parse with operator support
    return parseWithOperators(queryText);
  }

  /**
   * Parses query text with support for operators (+, -, quotes).
   *
   * @param queryText the query text to parse
   * @return the parsed query
   */
  private Query parseWithOperators(String queryText) {
    List<Clause> clauses = new ArrayList<>();
    StringBuilder currentToken = new StringBuilder();
    BooleanClause.Occur currentOccur = defaultOperator;
    boolean inQuotes = false;
    boolean escaped = false;

    for (int i = 0; i < queryText.length(); i++) {
      char c = queryText.charAt(i);

      if (escaped) {
        currentToken.append(c);
        escaped = false;
        continue;
      }

      if (c == '\\') {
        escaped = true;
        continue;
      }

      if (c == '"') {
        if (inQuotes) {
          // End of quoted phrase
          String phrase = currentToken.toString();
          if (!phrase.isEmpty()) {
            Query phraseQuery = createPhraseQuery(phrase, 0);
            if (phraseQuery != null) {
              clauses.add(new Clause(phraseQuery, currentOccur));
            }
          }
          currentToken.setLength(0);
          currentOccur = defaultOperator;
          inQuotes = false;
        } else {
          // Start of quoted phrase - save any pending token first
          if (currentToken.length() > 0) {
            addTermClauses(currentToken.toString(), currentOccur, clauses);
            currentToken.setLength(0);
            currentOccur = defaultOperator;
          }
          inQuotes = true;
        }
        continue;
      }

      if (inQuotes) {
        currentToken.append(c);
        continue;
      }

      if (Character.isWhitespace(c)) {
        if (currentToken.length() > 0) {
          addTermClauses(currentToken.toString(), currentOccur, clauses);
          currentToken.setLength(0);
          currentOccur = defaultOperator;
        }
        continue;
      }

      if (c == '+' && currentToken.length() == 0) {
        currentOccur = BooleanClause.Occur.MUST;
        continue;
      }

      if (c == '-' && currentToken.length() == 0) {
        currentOccur = BooleanClause.Occur.MUST_NOT;
        continue;
      }

      currentToken.append(c);
    }

    // Handle any remaining token
    if (currentToken.length() > 0) {
      if (inQuotes) {
        // Unclosed quote - treat as phrase anyway
        Query phraseQuery = createPhraseQuery(currentToken.toString(), 0);
        if (phraseQuery != null) {
          clauses.add(new Clause(phraseQuery, currentOccur));
        }
      } else {
        addTermClauses(currentToken.toString(), currentOccur, clauses);
      }
    }

    if (clauses.isEmpty()) {
      return new MatchNoDocsQuery("empty query after analysis");
    }

    if (clauses.size() == 1 && clauses.get(0).occur != BooleanClause.Occur.MUST_NOT) {
      return clauses.get(0).query;
    }

    BooleanQuery.Builder builder = new BooleanQuery.Builder();
    for (Clause clause : clauses) {
      builder.add(clause.query, clause.occur);
    }
    return builder.build();
  }

  /**
   * Adds term clauses for the given text token.
   *
   * @param token the text token to analyze and add
   * @param occur the occur type for the clauses
   * @param clauses the list to add clauses to
   */
  private void addTermClauses(String token, BooleanClause.Occur occur, List<Clause> clauses) {
    String firstField = fieldWeights.keySet().iterator().next();
    List<BytesRef> terms = analyzeQueryText(firstField, token);

    for (BytesRef term : terms) {
      Query termQuery = createCombinedFieldQuery(term);
      clauses.add(new Clause(termQuery, occur));
    }
  }

  /** Internal helper class to hold a query and its occur type. */
  private static class Clause {
    final Query query;
    final BooleanClause.Occur occur;

    Clause(Query query, BooleanClause.Occur occur) {
      this.query = query;
      this.occur = occur;
    }
  }

  /**
   * Analyzes the query text using the configured analyzer and returns a list of term bytes.
   *
   * <p>This method tokenizes the query text and extracts unique terms in order of appearance.
   * Duplicate terms are preserved to maintain term frequency semantics if needed.
   *
   * @param field the field name to use for analyzer context
   * @param queryText the text to analyze
   * @return a list of term bytes extracted from the query text
   */
  protected List<BytesRef> analyzeQueryText(String field, String queryText) {
    List<BytesRef> terms = new ArrayList<>();

    try (TokenStream stream = getAnalyzer().tokenStream(field, queryText)) {
      TermToBytesRefAttribute termAtt = stream.getAttribute(TermToBytesRefAttribute.class);

      if (termAtt == null) {
        return terms;
      }

      stream.reset();
      while (stream.incrementToken()) {
        terms.add(BytesRef.deepCopyOf(termAtt.getBytesRef()));
      }
      stream.end();
    } catch (IOException e) {
      throw new RuntimeException("Error analyzing query text: " + queryText, e);
    }

    return terms;
  }

  /**
   * Creates a {@link CombinedFieldQuery} for the given term across all configured fields.
   *
   * <p>The returned query searches for the term in all configured fields with their respective
   * weights, using BM25F-style scoring.
   *
   * @param term the term bytes to search for
   * @return a CombinedFieldQuery searching for the term across all fields
   */
  protected Query createCombinedFieldQuery(BytesRef term) {
    CombinedFieldQuery.Builder builder = new CombinedFieldQuery.Builder(term);
    for (Map.Entry<String, Float> entry : fieldWeights.entrySet()) {
      builder.addField(entry.getKey(), entry.getValue());
    }
    return builder.build();
  }

  /**
   * Creates a phrase query that searches across all configured fields.
   *
   * <p>Since {@link CombinedFieldQuery} only supports single-term queries, phrase queries are
   * implemented as a {@link BooleanQuery} with SHOULD clauses for phrase queries on each individual
   * field. Each field's phrase query is optionally boosted by its configured weight.
   *
   * @param phraseText the phrase text to search for
   * @param slop the phrase slop (number of positions terms can be apart)
   * @return a BooleanQuery searching for the phrase across all fields
   */
  protected Query createPhraseQuery(String phraseText, int slop) {
    BooleanQuery.Builder builder = new BooleanQuery.Builder();

    for (Map.Entry<String, Float> entry : fieldWeights.entrySet()) {
      String field = entry.getKey();
      Float weight = entry.getValue();

      Query phraseQuery = super.createPhraseQuery(field, phraseText, slop);
      if (phraseQuery != null) {
        // Apply field weight as boost if different from 1.0
        if (weight != 1.0f) {
          phraseQuery = new BoostQuery(phraseQuery, weight);
        }
        builder.add(phraseQuery, BooleanClause.Occur.SHOULD);
      }
    }

    BooleanQuery bq = builder.build();
    if (bq.clauses().isEmpty()) {
      return null;
    }
    if (bq.clauses().size() == 1) {
      return bq.clauses().get(0).query();
    }
    return bq;
  }

  /**
   * Returns the default operator used for combining terms in multi-term queries.
   *
   * @return the default operator, either {@link BooleanClause.Occur#SHOULD} or {@link
   *     BooleanClause.Occur#MUST}
   */
  public BooleanClause.Occur getDefaultOperator() {
    return defaultOperator;
  }

  /**
   * Sets the default operator used for combining terms in multi-term queries.
   *
   * @param operator the default operator, must be either {@link BooleanClause.Occur#SHOULD} (OR) or
   *     {@link BooleanClause.Occur#MUST} (AND)
   * @throws IllegalArgumentException if operator is not SHOULD or MUST
   */
  public void setDefaultOperator(BooleanClause.Occur operator) {
    if (operator != BooleanClause.Occur.SHOULD && operator != BooleanClause.Occur.MUST) {
      throw new IllegalArgumentException(
          "invalid operator: only SHOULD or MUST are allowed, got " + operator);
    }
    this.defaultOperator = operator;
  }

  /**
   * Returns an unmodifiable view of the configured field weights.
   *
   * @return the field weights map
   */
  public Map<String, Float> getFieldWeights() {
    return fieldWeights;
  }
}

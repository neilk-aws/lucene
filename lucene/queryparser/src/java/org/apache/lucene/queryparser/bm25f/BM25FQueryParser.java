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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BoostQuery;
import org.apache.lucene.search.CombinedFieldQuery;
import org.apache.lucene.search.FuzzyQuery;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.MatchNoDocsQuery;
import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.QueryBuilder;
import org.apache.lucene.util.automaton.LevenshteinAutomata;

/**
 * A query parser that generates {@link CombinedFieldQuery} instances for BM25F multi-field scoring.
 *
 * <p>Unlike {@link org.apache.lucene.queryparser.simple.SimpleQueryParser} which creates per-field
 * queries and combines their scores, this parser generates {@link CombinedFieldQuery} instances that
 * implement BM25F scoring natively. BM25F treats multiple fields as a single combined field,
 * computing term frequencies and document lengths across all fields simultaneously. This typically
 * produces better relevance for multi-field search compared to combining per-field BM25 scores.
 *
 * <p>For term queries, the parser produces {@link CombinedFieldQuery} instances that score using the
 * BM25F formula. For query types not supported by {@link CombinedFieldQuery} (phrases, prefix
 * queries, fuzzy queries), the parser falls back to per-field scoring using {@link BooleanQuery}
 * with individual field queries, similar to {@link
 * org.apache.lucene.queryparser.simple.SimpleQueryParser}.
 *
 * <p><b>Important:</b> {@link CombinedFieldQuery} requires that all fields share the same analyzer
 * and that either all fields have norms enabled or all fields have norms disabled. Per-field
 * similarities are not supported.
 *
 * <p><b>Query Operators</b>
 *
 * <p>This parser supports the same operators as {@link
 * org.apache.lucene.queryparser.simple.SimpleQueryParser}:
 *
 * <ul>
 *   <li>'{@code +}' specifies {@code AND} operation: <code>token1+token2</code>
 *   <li>'{@code |}' specifies {@code OR} operation: <code>token1|token2</code>
 *   <li>'{@code -}' negates a single token: <code>-token0</code>
 *   <li>'{@code "}' creates phrases of terms: <code>"term1 term2 ..."</code>
 *   <li>'{@code *}' at the end of terms specifies prefix query: <code>term*</code>
 *   <li>'{@code ~}N' at the end of terms specifies fuzzy query: <code>term~1</code>
 *   <li>'{@code ~}N' at the end of phrases specifies near query: <code>"term1 term2"~5</code>
 *   <li>'{@code (}' and '{@code )}' specifies precedence: <code>token1 + (token2 | token3)</code>
 * </ul>
 *
 * <p>The {@link #setDefaultOperator default operator} is {@code OR} if no other operator is
 * specified.
 *
 * @see CombinedFieldQuery
 * @lucene.experimental
 */
public class BM25FQueryParser extends QueryBuilder {

  /** Map of fields to query against with their original weights */
  protected final Map<String, Float> weights;

  /**
   * Map of fields with weights normalized so that the minimum weight is at least 1.0, as required
   * by {@link CombinedFieldQuery}.
   */
  private final Map<String, Float> normalizedWeights;

  /** flags to the parser (to turn features on/off) */
  protected final int flags;

  /** Enables {@code AND} operator (+) */
  public static final int AND_OPERATOR = 1 << 0;

  /** Enables {@code NOT} operator (-) */
  public static final int NOT_OPERATOR = 1 << 1;

  /** Enables {@code OR} operator (|) */
  public static final int OR_OPERATOR = 1 << 2;

  /** Enables {@code PREFIX} operator (*) */
  public static final int PREFIX_OPERATOR = 1 << 3;

  /** Enables {@code PHRASE} operator (") */
  public static final int PHRASE_OPERATOR = 1 << 4;

  /** Enables {@code PRECEDENCE} operators: {@code (} and {@code )} */
  public static final int PRECEDENCE_OPERATORS = 1 << 5;

  /** Enables {@code ESCAPE} operator (\) */
  public static final int ESCAPE_OPERATOR = 1 << 6;

  /** Enables {@code WHITESPACE} operators: ' ' '\n' '\r' '\t' */
  public static final int WHITESPACE_OPERATOR = 1 << 7;

  /** Enables {@code FUZZY} operators: (~) on single terms */
  public static final int FUZZY_OPERATOR = 1 << 8;

  /** Enables {@code NEAR} operators: (~) on phrases */
  public static final int NEAR_OPERATOR = 1 << 9;

  private BooleanClause.Occur defaultOperator = BooleanClause.Occur.SHOULD;

  /** Creates a new parser searching over a single field. */
  public BM25FQueryParser(Analyzer analyzer, String field) {
    this(analyzer, Collections.singletonMap(field, 1.0F));
  }

  /** Creates a new parser searching over multiple fields with different weights. */
  public BM25FQueryParser(Analyzer analyzer, Map<String, Float> weights) {
    this(analyzer, weights, -1);
  }

  /** Creates a new parser with custom flags used to enable/disable certain features. */
  public BM25FQueryParser(Analyzer analyzer, Map<String, Float> weights, int flags) {
    super(analyzer);
    this.weights = weights;
    this.flags = flags;
    this.normalizedWeights = computeNormalizedWeights(weights);
  }

  /**
   * Normalizes field weights so that the minimum weight is at least 1.0, as required by {@link
   * CombinedFieldQuery}.
   */
  private static Map<String, Float> computeNormalizedWeights(Map<String, Float> weights) {
    float minWeight = Float.MAX_VALUE;
    for (Float w : weights.values()) {
      if (w < minWeight) {
        minWeight = w;
      }
    }
    if (minWeight >= 1f) {
      // All weights are already >= 1, use as-is
      return weights;
    }
    // Scale all weights so the minimum becomes 1.0
    float factor = 1f / minWeight;
    Map<String, Float> normalized = new LinkedHashMap<>();
    for (Map.Entry<String, Float> entry : weights.entrySet()) {
      normalized.put(entry.getKey(), entry.getValue() * factor);
    }
    return normalized;
  }

  /** Parses the query text and returns parsed query. */
  public Query parse(String queryText) {
    if ("*".equals(queryText.trim())) {
      return MatchAllDocsQuery.INSTANCE;
    }

    char[] data = queryText.toCharArray();
    char[] buffer = new char[data.length];

    State state = new State(data, buffer, 0, data.length);
    parseSubQuery(state);
    if (state.top == null) {
      return new MatchNoDocsQuery("empty string passed to query parser");
    } else {
      return state.top;
    }
  }

  private void parseSubQuery(State state) {
    while (state.index < state.length) {
      if (state.data[state.index] == '(' && (flags & PRECEDENCE_OPERATORS) != 0) {
        consumeSubQuery(state);
      } else if (state.data[state.index] == ')' && (flags & PRECEDENCE_OPERATORS) != 0) {
        ++state.index;
      } else if (state.data[state.index] == '"' && (flags & PHRASE_OPERATOR) != 0) {
        consumePhrase(state);
      } else if (state.data[state.index] == '+' && (flags & AND_OPERATOR) != 0) {
        if (state.currentOperation == null && state.top != null) {
          state.currentOperation = BooleanClause.Occur.MUST;
        }
        ++state.index;
      } else if (state.data[state.index] == '|' && (flags & OR_OPERATOR) != 0) {
        if (state.currentOperation == null && state.top != null) {
          state.currentOperation = BooleanClause.Occur.SHOULD;
        }
        ++state.index;
      } else if (state.data[state.index] == '-' && (flags & NOT_OPERATOR) != 0) {
        ++state.not;
        ++state.index;
        continue;
      } else if ((state.data[state.index] == ' '
              || state.data[state.index] == '\t'
              || state.data[state.index] == '\n'
              || state.data[state.index] == '\r')
          && (flags & WHITESPACE_OPERATOR) != 0) {
        ++state.index;
      } else {
        consumeToken(state);
      }

      state.not = 0;
    }
  }

  private void consumeSubQuery(State state) {
    assert (flags & PRECEDENCE_OPERATORS) != 0;
    int start = ++state.index;
    int precedence = 1;
    boolean escaped = false;

    while (state.index < state.length) {
      if (!escaped) {
        if (state.data[state.index] == '\\' && (flags & ESCAPE_OPERATOR) != 0) {
          escaped = true;
          ++state.index;
          continue;
        } else if (state.data[state.index] == '(') {
          ++precedence;
        } else if (state.data[state.index] == ')') {
          --precedence;
          if (precedence == 0) {
            break;
          }
        }
      }

      escaped = false;
      ++state.index;
    }

    if (state.index == state.length) {
      state.index = start;
    } else if (state.index == start) {
      state.currentOperation = null;
      ++state.index;
    } else {
      State subState = new State(state.data, state.buffer, start, state.index);
      parseSubQuery(subState);
      buildQueryTree(state, subState.top);
      ++state.index;
    }
  }

  private void consumePhrase(State state) {
    assert (flags & PHRASE_OPERATOR) != 0;
    int start = ++state.index;
    int copied = 0;
    boolean escaped = false;
    boolean hasSlop = false;

    while (state.index < state.length) {
      if (!escaped) {
        if (state.data[state.index] == '\\' && (flags & ESCAPE_OPERATOR) != 0) {
          escaped = true;
          ++state.index;
          continue;
        } else if (state.data[state.index] == '"') {
          if (state.length > (state.index + 1)
              && state.data[state.index + 1] == '~'
              && (flags & NEAR_OPERATOR) != 0) {
            state.index++;
            if (state.length > (state.index + 1)) {
              hasSlop = true;
            }
            break;
          } else {
            break;
          }
        }
      }

      escaped = false;
      state.buffer[copied++] = state.data[state.index++];
    }

    if (state.index == state.length) {
      state.index = start;
    } else if (state.index == start) {
      state.currentOperation = null;
      ++state.index;
    } else {
      String phrase = new String(state.buffer, 0, copied);
      Query branch;
      if (hasSlop) {
        branch = newPhraseQuery(phrase, parseFuzziness(state));
      } else {
        branch = newPhraseQuery(phrase, 0);
      }
      buildQueryTree(state, branch);
      ++state.index;
    }
  }

  private void consumeToken(State state) {
    int copied = 0;
    boolean escaped = false;
    boolean prefix = false;
    boolean fuzzy = false;

    while (state.index < state.length) {
      if (!escaped) {
        if (state.data[state.index] == '\\' && (flags & ESCAPE_OPERATOR) != 0) {
          escaped = true;
          prefix = false;
          ++state.index;
          continue;
        } else if (tokenFinished(state)) {
          break;
        } else if (copied > 0 && state.data[state.index] == '~' && (flags & FUZZY_OPERATOR) != 0) {
          fuzzy = true;
          break;
        }

        prefix = copied > 0 && state.data[state.index] == '*' && (flags & PREFIX_OPERATOR) != 0;
      }

      escaped = false;
      state.buffer[copied++] = state.data[state.index++];
    }

    if (copied > 0) {
      final Query branch;

      if (fuzzy && (flags & FUZZY_OPERATOR) != 0) {
        String token = new String(state.buffer, 0, copied);
        int fuzziness = parseFuzziness(state);
        fuzziness = Math.min(fuzziness, LevenshteinAutomata.MAXIMUM_SUPPORTED_DISTANCE);
        if (fuzziness == 0) {
          branch = newDefaultQuery(token);
        } else {
          branch = newFuzzyQuery(token, fuzziness);
        }
      } else if (prefix) {
        String token = new String(state.buffer, 0, copied - 1);
        branch = newPrefixQuery(token);
      } else {
        String token = new String(state.buffer, 0, copied);
        branch = newDefaultQuery(token);
      }

      buildQueryTree(state, branch);
    }
  }

  private static BooleanQuery addClause(BooleanQuery bq, Query query, BooleanClause.Occur occur) {
    BooleanQuery.Builder newBq = new BooleanQuery.Builder();
    newBq.setMinimumNumberShouldMatch(bq.getMinimumNumberShouldMatch());
    for (BooleanClause clause : bq) {
      newBq.add(clause);
    }
    newBq.add(query, occur);
    return newBq.build();
  }

  private void buildQueryTree(State state, Query branch) {
    if (branch != null) {
      if (state.not % 2 == 1) {
        BooleanQuery.Builder nq = new BooleanQuery.Builder();
        nq.add(branch, BooleanClause.Occur.MUST_NOT);
        nq.add(MatchAllDocsQuery.INSTANCE, BooleanClause.Occur.SHOULD);
        branch = nq.build();
      }

      if (state.top == null) {
        state.top = branch;
      } else {
        if (state.currentOperation == null) {
          state.currentOperation = defaultOperator;
        }

        if (state.previousOperation != state.currentOperation) {
          BooleanQuery.Builder bq = new BooleanQuery.Builder();
          bq.add(state.top, state.currentOperation);
          state.top = bq.build();
        }

        state.top = addClause((BooleanQuery) state.top, branch, state.currentOperation);
        state.previousOperation = state.currentOperation;
      }

      state.currentOperation = null;
    }
  }

  private int parseFuzziness(State state) {
    char[] slopText = new char[state.length];
    int slopLength = 0;

    if (state.data[state.index] == '~') {
      while (state.index < state.length) {
        state.index++;
        if (state.index < state.length) {
          if (tokenFinished(state)) {
            break;
          }
          slopText[slopLength] = state.data[state.index];
          slopLength++;
        }
      }
      int fuzziness = 0;
      try {
        String fuzzyString = new String(slopText, 0, slopLength);
        if (fuzzyString.isEmpty()) {
          fuzziness = 2;
        } else {
          fuzziness = Integer.parseInt(fuzzyString);
        }
      } catch (
          @SuppressWarnings("unused")
          NumberFormatException e) {
        // swallow number format exceptions parsing fuzziness
      }
      if (fuzziness < 0) {
        fuzziness = 0;
      }
      return fuzziness;
    }
    return 0;
  }

  private boolean tokenFinished(State state) {
    if ((state.data[state.index] == '"' && (flags & PHRASE_OPERATOR) != 0)
        || (state.data[state.index] == '|' && (flags & OR_OPERATOR) != 0)
        || (state.data[state.index] == '+' && (flags & AND_OPERATOR) != 0)
        || (state.data[state.index] == '(' && (flags & PRECEDENCE_OPERATORS) != 0)
        || (state.data[state.index] == ')' && (flags & PRECEDENCE_OPERATORS) != 0)
        || ((state.data[state.index] == ' '
                || state.data[state.index] == '\t'
                || state.data[state.index] == '\n'
                || state.data[state.index] == '\r')
            && (flags & WHITESPACE_OPERATOR) != 0)) {
      return true;
    }
    return false;
  }

  /**
   * Factory method to generate a term query using {@link CombinedFieldQuery} for BM25F scoring
   * across all configured fields.
   *
   * <p>The text is analyzed using the configured analyzer. For each resulting term, a {@link
   * CombinedFieldQuery} is created that scores across all fields. If the analyzer produces multiple
   * terms, they are combined with a {@link BooleanQuery} using the default operator.
   */
  protected Query newDefaultQuery(String text) {
    // Use the analyzer on one field to tokenize the text
    String analyzerField = weights.keySet().iterator().next();
    Query singleFieldQuery = createBooleanQuery(analyzerField, text, defaultOperator);
    if (singleFieldQuery == null) {
      return null; // all stopwords
    }

    // Single term -> single CombinedFieldQuery
    if (singleFieldQuery instanceof TermQuery tq) {
      return buildCombinedFieldQuery(tq.getTerm().text());
    }

    // Multiple terms -> BooleanQuery of CombinedFieldQueries
    if (singleFieldQuery instanceof BooleanQuery bq) {
      BooleanQuery.Builder builder = new BooleanQuery.Builder();
      builder.setMinimumNumberShouldMatch(bq.getMinimumNumberShouldMatch());
      for (BooleanClause clause : bq) {
        Query subQuery = clause.query();
        if (subQuery instanceof TermQuery tq) {
          builder.add(buildCombinedFieldQuery(tq.getTerm().text()), clause.occur());
        } else if (subQuery instanceof BoostQuery boostQ
            && boostQ.getQuery() instanceof TermQuery tq) {
          builder.add(buildCombinedFieldQuery(tq.getTerm().text()), clause.occur());
        } else {
          // Fallback for synonyms or other complex token types: keep as-is
          builder.add(subQuery, clause.occur());
        }
      }
      return builder.build();
    }

    // Fallback: return the single-field query
    return singleFieldQuery;
  }

  /**
   * Builds a {@link CombinedFieldQuery} for a single term across all configured fields using
   * normalized weights.
   */
  private Query buildCombinedFieldQuery(String termText) {
    CombinedFieldQuery.Builder cfqBuilder = new CombinedFieldQuery.Builder(termText);
    for (Map.Entry<String, Float> entry : normalizedWeights.entrySet()) {
      cfqBuilder.addField(entry.getKey(), entry.getValue());
    }
    return cfqBuilder.build();
  }

  /**
   * Factory method to generate a fuzzy query. Falls back to per-field scoring since {@link
   * CombinedFieldQuery} does not support fuzzy matching.
   */
  protected Query newFuzzyQuery(String text, int fuzziness) {
    BooleanQuery.Builder bq = new BooleanQuery.Builder();
    for (Map.Entry<String, Float> entry : weights.entrySet()) {
      final String fieldName = entry.getKey();
      final BytesRef term = getAnalyzer().normalize(fieldName, text);
      Query q = new FuzzyQuery(new Term(fieldName, term), fuzziness);
      float boost = entry.getValue();
      if (boost != 1f) {
        q = new BoostQuery(q, boost);
      }
      bq.add(q, BooleanClause.Occur.SHOULD);
    }
    return simplify(bq.build());
  }

  /**
   * Factory method to generate a phrase query with slop. Falls back to per-field scoring since
   * {@link CombinedFieldQuery} does not support phrase queries.
   */
  protected Query newPhraseQuery(String text, int slop) {
    BooleanQuery.Builder bq = new BooleanQuery.Builder();
    for (Map.Entry<String, Float> entry : weights.entrySet()) {
      Query q = createPhraseQuery(entry.getKey(), text, slop);
      if (q != null) {
        float boost = entry.getValue();
        if (boost != 1f) {
          q = new BoostQuery(q, boost);
        }
        bq.add(q, BooleanClause.Occur.SHOULD);
      }
    }
    return simplify(bq.build());
  }

  /**
   * Factory method to generate a prefix query. Falls back to per-field scoring since {@link
   * CombinedFieldQuery} does not support prefix queries.
   */
  protected Query newPrefixQuery(String text) {
    BooleanQuery.Builder bq = new BooleanQuery.Builder();
    for (Map.Entry<String, Float> entry : weights.entrySet()) {
      final String fieldName = entry.getKey();
      final BytesRef term = getAnalyzer().normalize(fieldName, text);
      Query q = new PrefixQuery(new Term(fieldName, term));
      float boost = entry.getValue();
      if (boost != 1f) {
        q = new BoostQuery(q, boost);
      }
      bq.add(q, BooleanClause.Occur.SHOULD);
    }
    return simplify(bq.build());
  }

  /** Helper to simplify boolean queries with 0 or 1 clause. */
  protected Query simplify(BooleanQuery bq) {
    if (bq.clauses().isEmpty()) {
      return null;
    } else if (bq.clauses().size() == 1) {
      return bq.clauses().iterator().next().query();
    } else {
      return bq;
    }
  }

  /** Returns the implicit operator setting, which will be either {@code SHOULD} or {@code MUST}. */
  public BooleanClause.Occur getDefaultOperator() {
    return defaultOperator;
  }

  /** Sets the implicit operator setting, which must be either {@code SHOULD} or {@code MUST}. */
  public void setDefaultOperator(BooleanClause.Occur operator) {
    if (operator != BooleanClause.Occur.SHOULD && operator != BooleanClause.Occur.MUST) {
      throw new IllegalArgumentException("invalid operator: only SHOULD or MUST are allowed");
    }
    this.defaultOperator = operator;
  }

  static class State {
    final char[] data;
    final char[] buffer;
    int index;
    int length;

    BooleanClause.Occur currentOperation;
    BooleanClause.Occur previousOperation;
    int not;

    Query top;

    State(char[] data, char[] buffer, int index, int length) {
      this.data = data;
      this.buffer = buffer;
      this.index = index;
      this.length = length;
    }
  }
}

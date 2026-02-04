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
package org.apache.lucene.queryparser.simple;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BoostQuery;
import org.apache.lucene.search.CombinedFieldQuery;
import org.apache.lucene.search.FuzzyQuery;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.MatchNoDocsQuery;
import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.index.Term;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.QueryBuilder;
import org.apache.lucene.util.automaton.LevenshteinAutomata;

/**
 * BM25F Multi-Field Query Parser.
 *
 * <p>This parser creates queries that use BM25F scoring across multiple fields. Unlike {@link
 * SimpleQueryParser} which creates separate queries for each field and combines them with a boolean
 * query, this parser uses {@link CombinedFieldQuery} to combine term frequencies from all fields
 * before scoring, which implements the BM25F algorithm.
 *
 * <p>BM25F (BM25 with Field weights) is a variant of BM25 that handles multiple fields by combining
 * term frequencies from all fields with field-specific boosts. This provides better scoring for
 * multi-field searches compared to the traditional approach of combining per-field scores.
 *
 * <p>The parser supports the same query operators as {@link SimpleQueryParser}:
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
 * <p>Usage example:
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
 *
 * @see CombinedFieldQuery
 * @see SimpleQueryParser
 * @lucene.experimental
 */
public class BM25FQueryParser extends QueryBuilder {
  
  /** Map of fields to query against with their weights */
  protected final Map<String, Float> fieldWeights;
  
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
  
  /**
   * Creates a new BM25F parser searching over a single field.
   *
   * @param analyzer the analyzer used for tokenizing text
   * @param field the field to search
   */
  public BM25FQueryParser(Analyzer analyzer, String field) {
    this(analyzer, Collections.singletonMap(field, 1.0f));
  }
  
  /**
   * Creates a new BM25F parser searching over multiple fields with different weights.
   *
   * <p>Field weights must be &gt;= 1.0 as required by {@link CombinedFieldQuery}.
   *
   * @param analyzer the analyzer used for tokenizing text
   * @param fieldWeights map of field names to their weights (weights must be &gt;= 1.0)
   * @throws IllegalArgumentException if any weight is less than 1.0
   */
  public BM25FQueryParser(Analyzer analyzer, Map<String, Float> fieldWeights) {
    this(analyzer, fieldWeights, -1);
  }
  
  /**
   * Creates a new BM25F parser with custom flags used to enable/disable certain features.
   *
   * <p>Field weights must be &gt;= 1.0 as required by {@link CombinedFieldQuery}.
   *
   * @param analyzer the analyzer used for tokenizing text
   * @param fieldWeights map of field names to their weights (weights must be &gt;= 1.0)
   * @param flags parser flags to enable/disable features
   * @throws IllegalArgumentException if any weight is less than 1.0
   */
  public BM25FQueryParser(Analyzer analyzer, Map<String, Float> fieldWeights, int flags) {
    super(analyzer);
    Objects.requireNonNull(fieldWeights, "fieldWeights cannot be null");
    if (fieldWeights.isEmpty()) {
      throw new IllegalArgumentException("fieldWeights cannot be empty");
    }
    // Validate all weights are >= 1.0 as required by CombinedFieldQuery
    for (Map.Entry<String, Float> entry : fieldWeights.entrySet()) {
      if (entry.getValue() < 1.0f) {
        throw new IllegalArgumentException(
            "Field weight for '" + entry.getKey() + "' must be >= 1.0, got " + entry.getValue());
      }
    }
    this.fieldWeights = new LinkedHashMap<>(fieldWeights);
    this.flags = flags;
  }
  
  /**
   * Parses the query text and returns a parsed query using BM25F scoring.
   *
   * @param queryText the query text to parse
   * @return the parsed query
   */
  public Query parse(String queryText) {
    if ("*".equals(queryText.trim())) {
      return MatchAllDocsQuery.INSTANCE;
    }
    
    char[] data = queryText.toCharArray();
    char[] buffer = new char[data.length];
    
    State state = new State(data, buffer, 0, data.length);
    parseSubQuery(state);
    if (state.top == null) {
      return new MatchNoDocsQuery("empty string passed to BM25F query parser");
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
          branch = newCombinedFieldQuery(token);
        } else {
          branch = newFuzzyQuery(token, fuzziness);
        }
      } else if (prefix) {
        String token = new String(state.buffer, 0, copied - 1);
        branch = newPrefixQuery(token);
      } else {
        String token = new String(state.buffer, 0, copied);
        branch = newCombinedFieldQuery(token);
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
      } catch (@SuppressWarnings("unused") NumberFormatException e) {
        // swallow number format exceptions
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
   * Factory method to generate a CombinedFieldQuery (BM25F) for a term.
   *
   * <p>This creates a query that scores the term using BM25F across all configured fields.
   *
   * @param text the term text
   * @return a CombinedFieldQuery for the term, or null if the term is empty after analysis
   */
  protected Query newCombinedFieldQuery(String text) {
    // First, analyze the text to get the actual term(s)
    // We use the first field for analysis since all fields should use the same analyzer
    String firstField = fieldWeights.keySet().iterator().next();
    BytesRef term = getAnalyzer().normalize(firstField, text);
    
    if (term == null || term.length == 0) {
      return null;
    }
    
    // Build a CombinedFieldQuery for this term across all fields
    CombinedFieldQuery.Builder builder = new CombinedFieldQuery.Builder(term);
    for (Map.Entry<String, Float> entry : fieldWeights.entrySet()) {
      builder.addField(entry.getKey(), entry.getValue());
    }
    
    return builder.build();
  }
  
  /**
   * Factory method to generate a fuzzy query.
   *
   * <p>For fuzzy queries, we fall back to a boolean disjunction across fields since
   * CombinedFieldQuery doesn't support fuzzy matching directly.
   *
   * @param text the term text
   * @param fuzziness the edit distance for fuzzy matching
   * @return a fuzzy query across all configured fields
   */
  protected Query newFuzzyQuery(String text, int fuzziness) {
    BooleanQuery.Builder bq = new BooleanQuery.Builder();
    for (Map.Entry<String, Float> entry : fieldWeights.entrySet()) {
      final String fieldName = entry.getKey();
      final BytesRef term = getAnalyzer().normalize(fieldName, text);
      Query q = new FuzzyQuery(new Term(fieldName, term), fuzziness);
      float weight = entry.getValue();
      if (weight != 1f) {
        q = new BoostQuery(q, weight);
      }
      bq.add(q, BooleanClause.Occur.SHOULD);
    }
    return simplify(bq.build());
  }
  
  /**
   * Factory method to generate a phrase query with slop.
   *
   * <p>For phrase queries, we fall back to a boolean disjunction across fields since
   * CombinedFieldQuery doesn't support phrase queries directly.
   *
   * @param text the phrase text
   * @param slop the slop value for the phrase
   * @return a phrase query across all configured fields
   */
  protected Query newPhraseQuery(String text, int slop) {
    BooleanQuery.Builder bq = new BooleanQuery.Builder();
    for (Map.Entry<String, Float> entry : fieldWeights.entrySet()) {
      Query q = createPhraseQuery(entry.getKey(), text, slop);
      if (q != null) {
        float weight = entry.getValue();
        if (weight != 1f) {
          q = new BoostQuery(q, weight);
        }
        bq.add(q, BooleanClause.Occur.SHOULD);
      }
    }
    return simplify(bq.build());
  }
  
  /**
   * Factory method to generate a prefix query.
   *
   * <p>For prefix queries, we fall back to a boolean disjunction across fields since
   * CombinedFieldQuery doesn't support prefix queries directly.
   *
   * @param text the prefix text
   * @return a prefix query across all configured fields
   */
  protected Query newPrefixQuery(String text) {
    BooleanQuery.Builder bq = new BooleanQuery.Builder();
    for (Map.Entry<String, Float> entry : fieldWeights.entrySet()) {
      final String fieldName = entry.getKey();
      final BytesRef term = getAnalyzer().normalize(fieldName, text);
      Query q = new PrefixQuery(new Term(fieldName, term));
      float weight = entry.getValue();
      if (weight != 1f) {
        q = new BoostQuery(q, weight);
      }
      bq.add(q, BooleanClause.Occur.SHOULD);
    }
    return simplify(bq.build());
  }
  
  /**
   * Helper to simplify boolean queries with 0 or 1 clause.
   *
   * @param bq the boolean query to simplify
   * @return the simplified query
   */
  protected Query simplify(BooleanQuery bq) {
    if (bq.clauses().isEmpty()) {
      return null;
    } else if (bq.clauses().size() == 1) {
      return bq.clauses().iterator().next().query();
    } else {
      return bq;
    }
  }
  
  /**
   * Returns the implicit operator setting, which will be either {@code SHOULD} or {@code MUST}.
   *
   * @return the default operator
   */
  public BooleanClause.Occur getDefaultOperator() {
    return defaultOperator;
  }
  
  /**
   * Sets the implicit operator setting, which must be either {@code SHOULD} or {@code MUST}.
   *
   * @param operator the default operator to use
   * @throws IllegalArgumentException if the operator is not SHOULD or MUST
   */
  public void setDefaultOperator(BooleanClause.Occur operator) {
    if (operator != BooleanClause.Occur.SHOULD && operator != BooleanClause.Occur.MUST) {
      throw new IllegalArgumentException("invalid operator: only SHOULD or MUST are allowed");
    }
    this.defaultOperator = operator;
  }
  
  /**
   * Returns the field weights used by this parser.
   *
   * @return an unmodifiable map of field names to their weights
   */
  public Map<String, Float> getFieldWeights() {
    return Collections.unmodifiableMap(fieldWeights);
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

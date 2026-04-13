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
package org.apache.lucene.sandbox.search;

import java.io.IOException;
import java.util.Map;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.TermToBytesRefAttribute;
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
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.QueryBuilder;
import org.apache.lucene.util.automaton.LevenshteinAutomata;

/**
 * A query parser that produces {@link CombinedFieldQuery} instances for BM25F scoring.
 *
 * <p>For each analyzed term, the parser creates a {@link CombinedFieldQuery} that searches the term
 * across all configured fields with their weights. Multiple terms are combined with a {@link
 * BooleanQuery} using the configured default operator.
 *
 * <p>Since {@link CombinedFieldQuery} only supports single-term queries, phrase queries, prefix
 * queries, and fuzzy queries fall back to per-field queries combined with {@link
 * BooleanClause.Occur#SHOULD}.
 *
 * <p>Supported operators:
 *
 * <ul>
 *   <li>{@code +} AND operator
 *   <li>{@code |} OR operator
 *   <li>{@code -} NOT operator
 *   <li>{@code "..."} phrase queries
 *   <li>{@code term*} prefix queries
 *   <li>{@code term~N} fuzzy queries
 *   <li>{@code (...)} grouping
 * </ul>
 *
 * @lucene.experimental
 */
public class BM25FQueryParser extends QueryBuilder {

  /** Map of field names to their weights. All weights must be &gt;= 1.0f. */
  protected final Map<String, Float> fieldWeights;

  private BooleanClause.Occur defaultOperator = BooleanClause.Occur.SHOULD;

  /**
   * Creates a new BM25FQueryParser.
   *
   * @param analyzer the analyzer used to tokenize query text
   * @param fieldWeights map of field names to weights; all weights must be &gt;= 1.0f
   * @throws IllegalArgumentException if any weight is less than 1.0f
   */
  public BM25FQueryParser(Analyzer analyzer, Map<String, Float> fieldWeights) {
    super(analyzer);
    for (Map.Entry<String, Float> entry : fieldWeights.entrySet()) {
      if (entry.getValue() < 1.0f) {
        throw new IllegalArgumentException(
            "weight for field '"
                + entry.getKey()
                + "' must be greater or equal to 1, got: "
                + entry.getValue());
      }
    }
    this.fieldWeights = fieldWeights;
  }

  /**
   * Parses the query text and returns a parsed {@link Query}.
   *
   * @param queryText the query string to parse
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
      return new MatchNoDocsQuery("empty string passed to BM25FQueryParser");
    } else {
      return state.top;
    }
  }

  /** Returns the default operator (SHOULD or MUST). */
  public BooleanClause.Occur getDefaultOperator() {
    return defaultOperator;
  }

  /**
   * Sets the default operator.
   *
   * @param operator must be either {@link BooleanClause.Occur#SHOULD} or {@link
   *     BooleanClause.Occur#MUST}
   */
  public void setDefaultOperator(BooleanClause.Occur operator) {
    if (operator != BooleanClause.Occur.SHOULD && operator != BooleanClause.Occur.MUST) {
      throw new IllegalArgumentException("invalid operator: only SHOULD or MUST are allowed");
    }
    this.defaultOperator = operator;
  }

  /**
   * Creates a {@link CombinedFieldQuery} for a single term across all configured fields.
   *
   * <p>If the analyzer produces multiple tokens, they are combined with a {@link BooleanQuery}
   * using the default operator.
   */
  protected Query newDefaultQuery(String text) {
    // Analyze the text using the first field to get tokens.
    // CombinedFieldQuery assumes all fields share the same analyzer.
    String firstField = fieldWeights.keySet().iterator().next();
    BooleanQuery.Builder bq = new BooleanQuery.Builder();
    try (TokenStream ts = getAnalyzer().tokenStream(firstField, text)) {
      TermToBytesRefAttribute termAtt = ts.getAttribute(TermToBytesRefAttribute.class);
      ts.reset();
      while (ts.incrementToken()) {
        BytesRef termBytes = BytesRef.deepCopyOf(termAtt.getBytesRef());
        CombinedFieldQuery.Builder cfqBuilder = new CombinedFieldQuery.Builder(termBytes);
        for (Map.Entry<String, Float> entry : fieldWeights.entrySet()) {
          cfqBuilder.addField(entry.getKey(), entry.getValue());
        }
        bq.add(cfqBuilder.build(), defaultOperator);
      }
      ts.end();
    } catch (IOException e) {
      throw new RuntimeException("Error analyzing query text", e);
    }
    return simplify(bq.build());
  }

  /**
   * Creates per-field phrase queries combined with SHOULD, since {@link CombinedFieldQuery} does
   * not support phrases.
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
   * Creates per-field prefix queries combined with SHOULD, since {@link CombinedFieldQuery} does
   * not support prefix queries.
   */
  protected Query newPrefixQuery(String text) {
    BooleanQuery.Builder bq = new BooleanQuery.Builder();
    for (Map.Entry<String, Float> entry : fieldWeights.entrySet()) {
      String fieldName = entry.getKey();
      BytesRef term = getAnalyzer().normalize(fieldName, text);
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
   * Creates per-field fuzzy queries combined with SHOULD, since {@link CombinedFieldQuery} does not
   * support fuzzy queries.
   */
  protected Query newFuzzyQuery(String text, int fuzziness) {
    BooleanQuery.Builder bq = new BooleanQuery.Builder();
    for (Map.Entry<String, Float> entry : fieldWeights.entrySet()) {
      String fieldName = entry.getKey();
      BytesRef term = getAnalyzer().normalize(fieldName, text);
      Query q = new FuzzyQuery(new Term(fieldName, term), fuzziness);
      float weight = entry.getValue();
      if (weight != 1f) {
        q = new BoostQuery(q, weight);
      }
      bq.add(q, BooleanClause.Occur.SHOULD);
    }
    return simplify(bq.build());
  }

  /** Simplifies a boolean query with 0 or 1 clause. */
  protected Query simplify(BooleanQuery bq) {
    if (bq.clauses().isEmpty()) {
      return null;
    } else if (bq.clauses().size() == 1) {
      return bq.clauses().iterator().next().query();
    } else {
      return bq;
    }
  }

  // ---- Parsing state machine (follows SimpleQueryParser approach) ----

  private void parseSubQuery(State state) {
    while (state.index < state.length) {
      if (state.data[state.index] == '(') {
        consumeSubQuery(state);
      } else if (state.data[state.index] == ')') {
        ++state.index;
      } else if (state.data[state.index] == '"') {
        consumePhrase(state);
      } else if (state.data[state.index] == '+') {
        if (state.currentOperation == null && state.top != null) {
          state.currentOperation = BooleanClause.Occur.MUST;
        }
        ++state.index;
      } else if (state.data[state.index] == '|') {
        if (state.currentOperation == null && state.top != null) {
          state.currentOperation = BooleanClause.Occur.SHOULD;
        }
        ++state.index;
      } else if (state.data[state.index] == '-') {
        ++state.not;
        ++state.index;
        continue;
      } else if (state.data[state.index] == ' '
          || state.data[state.index] == '\t'
          || state.data[state.index] == '\n'
          || state.data[state.index] == '\r') {
        ++state.index;
      } else {
        consumeToken(state);
      }
      state.not = 0;
    }
  }

  private void consumeSubQuery(State state) {
    int start = ++state.index;
    int precedence = 1;
    boolean escaped = false;

    while (state.index < state.length) {
      if (!escaped) {
        if (state.data[state.index] == '\\') {
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
    int start = ++state.index;
    int copied = 0;
    boolean escaped = false;
    boolean hasSlop = false;

    while (state.index < state.length) {
      if (!escaped) {
        if (state.data[state.index] == '\\') {
          escaped = true;
          ++state.index;
          continue;
        } else if (state.data[state.index] == '"') {
          if (state.length > (state.index + 1) && state.data[state.index + 1] == '~') {
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
        if (state.data[state.index] == '\\') {
          escaped = true;
          prefix = false;
          ++state.index;
          continue;
        } else if (tokenFinished(state)) {
          break;
        } else if (copied > 0 && state.data[state.index] == '~') {
          fuzzy = true;
          break;
        }
        prefix = copied > 0 && state.data[state.index] == '*';
      }
      escaped = false;
      state.buffer[copied++] = state.data[state.index++];
    }

    if (copied > 0) {
      final Query branch;
      if (fuzzy) {
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

  private boolean tokenFinished(State state) {
    return state.data[state.index] == '"'
        || state.data[state.index] == '|'
        || state.data[state.index] == '+'
        || state.data[state.index] == '('
        || state.data[state.index] == ')'
        || state.data[state.index] == ' '
        || state.data[state.index] == '\t'
        || state.data[state.index] == '\n'
        || state.data[state.index] == '\r';
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
        // swallow
      }
      if (fuzziness < 0) {
        fuzziness = 0;
      }
      return fuzziness;
    }
    return 0;
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

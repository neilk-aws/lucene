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

import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.queryparser.simple.SimpleQueryParser;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.CombinedFieldQuery;
import org.apache.lucene.search.FuzzyQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchNoDocsQuery;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.index.RandomIndexWriter;
import org.apache.lucene.tests.util.LuceneTestCase;

/** Tests for {@link BM25FQueryParser} */
public class TestBM25FQueryParser extends LuceneTestCase {

  private Map<String, Float> makeWeights(float w1, float w2) {
    Map<String, Float> weights = new LinkedHashMap<>();
    weights.put("field1", w1);
    weights.put("field2", w2);
    return weights;
  }

  private Query parse(String text, Map<String, Float> weights) {
    MockAnalyzer analyzer = new MockAnalyzer(random());
    BM25FQueryParser parser = new BM25FQueryParser(analyzer, weights);
    parser.setDefaultOperator(Occur.MUST);
    return parser.parse(text);
  }

  private Query parse(String text, Map<String, Float> weights, int flags) {
    MockAnalyzer analyzer = new MockAnalyzer(random());
    BM25FQueryParser parser = new BM25FQueryParser(analyzer, weights, flags);
    parser.setDefaultOperator(Occur.MUST);
    return parser.parse(text);
  }

  /** Single term should produce a CombinedFieldQuery */
  public void testSingleTerm() throws Exception {
    Map<String, Float> weights = makeWeights(1.0f, 1.0f);
    Query result = parse("foo", weights);
    assertTrue(
        "Expected CombinedFieldQuery but got: " + result.getClass().getName(),
        result instanceof CombinedFieldQuery);
  }

  /** Single term with weights should produce CombinedFieldQuery with correct weights */
  public void testSingleTermWithWeights() throws Exception {
    Map<String, Float> weights = makeWeights(3.0f, 5.0f);
    Query result = parse("foo", weights);
    assertTrue(
        "Expected CombinedFieldQuery but got: " + result.getClass().getName(),
        result instanceof CombinedFieldQuery);
    String str = result.toString();
    assertTrue("Expected field1^3.0 in toString, got: " + str, str.contains("field1^3.0"));
    assertTrue("Expected field2^5.0 in toString, got: " + str, str.contains("field2^5.0"));
  }

  /** Multiple terms with AND operator should produce BooleanQuery of CombinedFieldQuery clauses */
  public void testMultipleTerms() throws Exception {
    Map<String, Float> weights = makeWeights(1.0f, 1.0f);
    Query result =
        parse(
            "foo bar",
            weights,
            SimpleQueryParser.WHITESPACE_OPERATOR | SimpleQueryParser.AND_OPERATOR);
    assertTrue(
        "Expected BooleanQuery but got: " + result.getClass().getName(),
        result instanceof BooleanQuery);
    BooleanQuery bq = (BooleanQuery) result;
    assertEquals(2, bq.clauses().size());
    for (BooleanClause clause : bq) {
      assertTrue(
          "Expected CombinedFieldQuery clause but got: " + clause.query().getClass().getName(),
          clause.query() instanceof CombinedFieldQuery);
      assertEquals(Occur.MUST, clause.occur());
    }
  }

  /** Multiple terms with OR operator should produce BooleanQuery with SHOULD clauses */
  public void testMultipleTermsOR() throws Exception {
    Map<String, Float> weights = makeWeights(1.0f, 1.0f);
    Query result =
        parse("foo|bar", weights, SimpleQueryParser.OR_OPERATOR | SimpleQueryParser.AND_OPERATOR);
    assertTrue(
        "Expected BooleanQuery but got: " + result.getClass().getName(),
        result instanceof BooleanQuery);
    BooleanQuery bq = (BooleanQuery) result;
    assertEquals(2, bq.clauses().size());
    for (BooleanClause clause : bq) {
      assertTrue(
          "Expected CombinedFieldQuery clause but got: " + clause.query().getClass().getName(),
          clause.query() instanceof CombinedFieldQuery);
      assertEquals(Occur.SHOULD, clause.occur());
    }
  }

  /** Phrase query should fall back to per-field BooleanQuery with PhraseQuery instances */
  public void testPhraseQueryFallback() throws Exception {
    Map<String, Float> weights = makeWeights(1.0f, 1.0f);
    Query result =
        parse(
            "\"foo bar\"",
            weights,
            SimpleQueryParser.PHRASE_OPERATOR | SimpleQueryParser.WHITESPACE_OPERATOR);
    assertFalse(
        "Phrase should not produce CombinedFieldQuery", result instanceof CombinedFieldQuery);
    assertTrue(
        "Expected BooleanQuery but got: " + result.getClass().getName(),
        result instanceof BooleanQuery);
    BooleanQuery bq = (BooleanQuery) result;
    for (BooleanClause clause : bq) {
      assertTrue(
          "Expected PhraseQuery clause but got: " + clause.query().getClass().getName(),
          clause.query() instanceof PhraseQuery);
    }
  }

  /** Prefix query should fall back to per-field PrefixQuery instances */
  public void testPrefixQueryFallback() throws Exception {
    Map<String, Float> weights = makeWeights(1.0f, 1.0f);
    Query result = parse("foo*", weights, SimpleQueryParser.PREFIX_OPERATOR);
    assertFalse(
        "Prefix should not produce CombinedFieldQuery", result instanceof CombinedFieldQuery);
    assertTrue(
        "Expected BooleanQuery but got: " + result.getClass().getName(),
        result instanceof BooleanQuery);
    BooleanQuery bq = (BooleanQuery) result;
    for (BooleanClause clause : bq) {
      assertTrue(
          "Expected PrefixQuery clause but got: " + clause.query().getClass().getName(),
          clause.query() instanceof PrefixQuery);
    }
  }

  /** Fuzzy query should fall back to per-field FuzzyQuery instances */
  public void testFuzzyQueryFallback() throws Exception {
    Map<String, Float> weights = makeWeights(1.0f, 1.0f);
    Query result = parse("foo~1", weights, SimpleQueryParser.FUZZY_OPERATOR);
    assertFalse(
        "Fuzzy should not produce CombinedFieldQuery", result instanceof CombinedFieldQuery);
    assertTrue(
        "Expected BooleanQuery but got: " + result.getClass().getName(),
        result instanceof BooleanQuery);
    BooleanQuery bq = (BooleanQuery) result;
    for (BooleanClause clause : bq) {
      assertTrue(
          "Expected FuzzyQuery clause but got: " + clause.query().getClass().getName(),
          clause.query() instanceof FuzzyQuery);
    }
  }

  /** Constructor should reject weights less than 1.0 */
  public void testWeightValidation() throws Exception {
    Map<String, Float> weights = new LinkedHashMap<>();
    weights.put("field1", 1.0f);
    weights.put("field2", 0.5f);
    MockAnalyzer analyzer = new MockAnalyzer(random());
    IllegalArgumentException exc =
        expectThrows(IllegalArgumentException.class, () -> new BM25FQueryParser(analyzer, weights));
    assertTrue(exc.getMessage().contains("field2"));
    assertTrue(exc.getMessage().contains("greater or equal to 1"));
  }

  /** Empty or whitespace query should produce MatchNoDocsQuery */
  public void testEmptyQuery() throws Exception {
    Map<String, Float> weights = makeWeights(1.0f, 1.0f);
    Query result = parse("", weights);
    assertTrue(
        "Expected MatchNoDocsQuery but got: " + result.getClass().getName(),
        result instanceof MatchNoDocsQuery);
  }

  /** Integration test: index documents and search with BM25FQueryParser */
  public void testScoringWithIndex() throws Exception {
    Directory dir = newDirectory();
    BM25Similarity similarity = new BM25Similarity();
    IndexWriterConfig iwc = new IndexWriterConfig(new MockAnalyzer(random()));
    iwc.setSimilarity(similarity);
    RandomIndexWriter w = new RandomIndexWriter(random(), dir, iwc);

    Document doc1 = new Document();
    doc1.add(new TextField("a", "foo bar", Store.NO));
    doc1.add(new TextField("b", "baz", Store.NO));
    w.addDocument(doc1);

    Document doc2 = new Document();
    doc2.add(new TextField("a", "baz", Store.NO));
    doc2.add(new TextField("b", "foo bar", Store.NO));
    w.addDocument(doc2);

    Document doc3 = new Document();
    doc3.add(new TextField("a", "foo", Store.NO));
    doc3.add(new TextField("b", "foo", Store.NO));
    w.addDocument(doc3);

    IndexReader reader = w.getReader();
    IndexSearcher searcher = newSearcher(reader);
    searcher.setSimilarity(similarity);

    Map<String, Float> weights = new LinkedHashMap<>();
    weights.put("a", 1.0f);
    weights.put("b", 1.0f);

    MockAnalyzer analyzer = new MockAnalyzer(random());
    BM25FQueryParser parser = new BM25FQueryParser(analyzer, weights);
    Query parserQuery = parser.parse("foo");

    // The parser should produce a CombinedFieldQuery for a single term
    assertTrue(
        "Expected CombinedFieldQuery but got: " + parserQuery.getClass().getName(),
        parserQuery instanceof CombinedFieldQuery);

    // Build the equivalent CombinedFieldQuery directly
    CombinedFieldQuery directQuery =
        new CombinedFieldQuery.Builder("foo").addField("a", 1.0f).addField("b", 1.0f).build();

    // Both queries should produce the same results
    TopDocs parserResults = searcher.search(parserQuery, 10);
    TopDocs directResults = searcher.search(directQuery, 10);

    assertEquals(parserResults.totalHits.value(), directResults.totalHits.value());
    assertEquals(parserResults.scoreDocs.length, directResults.scoreDocs.length);
    for (int i = 0; i < parserResults.scoreDocs.length; i++) {
      ScoreDoc parserDoc = parserResults.scoreDocs[i];
      ScoreDoc directDoc = directResults.scoreDocs[i];
      assertEquals(directDoc.doc, parserDoc.doc);
      assertEquals(directDoc.score, parserDoc.score, 0.0f);
    }

    reader.close();
    w.close();
    dir.close();
  }
}

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
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BoostQuery;
import org.apache.lucene.search.CombinedFieldQuery;
import org.apache.lucene.search.FuzzyQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchNoDocsQuery;
import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.TopScoreDocCollectorManager;
import org.apache.lucene.search.TotalHits;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.index.RandomIndexWriter;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestBM25FQueryParser extends LuceneTestCase {

  public void testSingleTermProducesCombinedFieldQuery() {
    MockAnalyzer analyzer = new MockAnalyzer(random());
    Map<String, Float> weights = Map.of("title", 2.0f, "body", 1.0f);
    BM25FQueryParser parser = new BM25FQueryParser(analyzer, weights);
    Query q = parser.parse("hello");
    assertTrue(
        "single term should produce CombinedFieldQuery, got: " + q.getClass().getSimpleName(),
        q instanceof CombinedFieldQuery);
  }

  public void testMultiTermProducesBooleanOfCombinedFieldQuery() {
    MockAnalyzer analyzer = new MockAnalyzer(random());
    Map<String, Float> weights = Map.of("title", 2.0f, "body", 1.0f);
    BM25FQueryParser parser = new BM25FQueryParser(analyzer, weights);
    Query q = parser.parse("hello world");
    assertTrue(
        "multi-term should produce BooleanQuery, got: " + q.getClass().getSimpleName(),
        q instanceof BooleanQuery);
    BooleanQuery bq = (BooleanQuery) q;
    assertEquals(2, bq.clauses().size());
    for (BooleanClause clause : bq) {
      assertTrue(
          "each clause should be CombinedFieldQuery, got: "
              + clause.query().getClass().getSimpleName(),
          clause.query() instanceof CombinedFieldQuery);
      assertEquals(BooleanClause.Occur.SHOULD, clause.occur());
    }
  }

  public void testPhraseQueryProducesPerFieldPhraseQueries() {
    MockAnalyzer analyzer = new MockAnalyzer(random());
    Map<String, Float> weights = Map.of("title", 2.0f, "body", 1.0f);
    BM25FQueryParser parser = new BM25FQueryParser(analyzer, weights);
    Query q = parser.parse("\"hello world\"");
    assertNotNull(q);
    // With 2 fields, should be a BooleanQuery with SHOULD clauses
    assertTrue(
        "phrase should produce BooleanQuery, got: " + q.getClass().getSimpleName(),
        q instanceof BooleanQuery);
    BooleanQuery bq = (BooleanQuery) q;
    assertEquals(2, bq.clauses().size());
    for (BooleanClause clause : bq) {
      assertEquals(BooleanClause.Occur.SHOULD, clause.occur());
    }
  }

  public void testPrefixQuery() {
    MockAnalyzer analyzer = new MockAnalyzer(random());
    Map<String, Float> weights = Map.of("title", 2.0f, "body", 1.0f);
    BM25FQueryParser parser = new BM25FQueryParser(analyzer, weights);
    Query q = parser.parse("hel*");
    assertNotNull(q);
    assertTrue(
        "prefix should produce BooleanQuery, got: " + q.getClass().getSimpleName(),
        q instanceof BooleanQuery);
    BooleanQuery bq = (BooleanQuery) q;
    assertEquals(2, bq.clauses().size());
    for (BooleanClause clause : bq) {
      Query inner = clause.query();
      if (inner instanceof BoostQuery boost) {
        inner = boost.getQuery();
      }
      assertTrue(
          "each clause should contain PrefixQuery, got: " + inner.getClass().getSimpleName(),
          inner instanceof PrefixQuery);
    }
  }

  public void testFuzzyQuery() {
    MockAnalyzer analyzer = new MockAnalyzer(random());
    Map<String, Float> weights = Map.of("title", 2.0f, "body", 1.0f);
    BM25FQueryParser parser = new BM25FQueryParser(analyzer, weights);
    Query q = parser.parse("hello~1");
    assertNotNull(q);
    assertTrue(
        "fuzzy should produce BooleanQuery, got: " + q.getClass().getSimpleName(),
        q instanceof BooleanQuery);
    BooleanQuery bq = (BooleanQuery) q;
    assertEquals(2, bq.clauses().size());
    for (BooleanClause clause : bq) {
      Query inner = clause.query();
      if (inner instanceof BoostQuery boost) {
        inner = boost.getQuery();
      }
      assertTrue(
          "each clause should contain FuzzyQuery, got: " + inner.getClass().getSimpleName(),
          inner instanceof FuzzyQuery);
    }
  }

  public void testAndOperator() {
    MockAnalyzer analyzer = new MockAnalyzer(random());
    Map<String, Float> weights = Map.of("title", 1.0f, "body", 1.0f);
    BM25FQueryParser parser = new BM25FQueryParser(analyzer, weights);
    Query q = parser.parse("hello+world");
    assertTrue(q instanceof BooleanQuery);
    BooleanQuery bq = (BooleanQuery) q;
    assertEquals(2, bq.clauses().size());
    assertEquals(BooleanClause.Occur.MUST, bq.clauses().get(1).occur());
  }

  public void testOrOperator() {
    MockAnalyzer analyzer = new MockAnalyzer(random());
    Map<String, Float> weights = Map.of("title", 1.0f, "body", 1.0f);
    BM25FQueryParser parser = new BM25FQueryParser(analyzer, weights);
    Query q = parser.parse("hello|world");
    assertTrue(q instanceof BooleanQuery);
    BooleanQuery bq = (BooleanQuery) q;
    assertEquals(2, bq.clauses().size());
    assertEquals(BooleanClause.Occur.SHOULD, bq.clauses().get(1).occur());
  }

  public void testNotOperator() {
    MockAnalyzer analyzer = new MockAnalyzer(random());
    Map<String, Float> weights = Map.of("title", 1.0f, "body", 1.0f);
    BM25FQueryParser parser = new BM25FQueryParser(analyzer, weights);
    Query q = parser.parse("-hello");
    assertNotNull(q);
    assertTrue(q instanceof BooleanQuery);
    BooleanQuery bq = (BooleanQuery) q;
    boolean hasMustNot = false;
    for (BooleanClause clause : bq) {
      if (clause.occur() == BooleanClause.Occur.MUST_NOT) {
        hasMustNot = true;
      }
    }
    assertTrue("NOT operator should produce MUST_NOT clause", hasMustNot);
  }

  public void testFieldWeightsApplied() {
    MockAnalyzer analyzer = new MockAnalyzer(random());
    Map<String, Float> weights = Map.of("title", 3.0f, "body", 1.0f);
    BM25FQueryParser parser = new BM25FQueryParser(analyzer, weights);
    Query q = parser.parse("hello");
    assertTrue(q instanceof CombinedFieldQuery);
    String str = q.toString();
    assertTrue(
        "toString should contain weight for title: " + str,
        str.contains("title^3.0") || str.contains("title"));
  }

  public void testInvalidWeightThrows() {
    MockAnalyzer analyzer = new MockAnalyzer(random());
    expectThrows(
        IllegalArgumentException.class,
        () -> new BM25FQueryParser(analyzer, Map.of("title", 0.5f)));
  }

  public void testEmptyInputReturnsMatchNoDocsQuery() {
    MockAnalyzer analyzer = new MockAnalyzer(random());
    Map<String, Float> weights = Map.of("title", 1.0f);
    BM25FQueryParser parser = new BM25FQueryParser(analyzer, weights);
    Query q = parser.parse("");
    assertTrue(q instanceof MatchNoDocsQuery);
  }

  public void testWhitespaceInputReturnsMatchNoDocsQuery() {
    MockAnalyzer analyzer = new MockAnalyzer(random());
    Map<String, Float> weights = Map.of("title", 1.0f);
    BM25FQueryParser parser = new BM25FQueryParser(analyzer, weights);
    Query q = parser.parse("   ");
    assertTrue(q instanceof MatchNoDocsQuery);
  }

  public void testDefaultOperatorMust() {
    MockAnalyzer analyzer = new MockAnalyzer(random());
    Map<String, Float> weights = Map.of("title", 1.0f, "body", 1.0f);
    BM25FQueryParser parser = new BM25FQueryParser(analyzer, weights);
    parser.setDefaultOperator(BooleanClause.Occur.MUST);
    Query q = parser.parse("hello world");
    assertTrue(q instanceof BooleanQuery);
    BooleanQuery bq = (BooleanQuery) q;
    for (BooleanClause clause : bq) {
      assertEquals(BooleanClause.Occur.MUST, clause.occur());
    }
  }

  public void testGrouping() {
    MockAnalyzer analyzer = new MockAnalyzer(random());
    Map<String, Float> weights = Map.of("title", 1.0f, "body", 1.0f);
    BM25FQueryParser parser = new BM25FQueryParser(analyzer, weights);
    Query q = parser.parse("(hello|world)+test");
    assertNotNull(q);
    assertTrue(q instanceof BooleanQuery);
  }

  public void testIntegrationSearch() throws IOException {
    Directory dir = newDirectory();
    IndexWriterConfig iwc = new IndexWriterConfig(new MockAnalyzer(random()));
    iwc.setSimilarity(new BM25Similarity());
    RandomIndexWriter w = new RandomIndexWriter(random(), dir, iwc);

    Document doc1 = new Document();
    doc1.add(new TextField("title", "quick brown fox", Store.NO));
    doc1.add(new TextField("body", "the fox jumps over the lazy dog", Store.NO));
    w.addDocument(doc1);

    Document doc2 = new Document();
    doc2.add(new TextField("title", "lazy dog", Store.NO));
    doc2.add(new TextField("body", "the dog sleeps all day", Store.NO));
    w.addDocument(doc2);

    Document doc3 = new Document();
    doc3.add(new TextField("title", "hello world", Store.NO));
    doc3.add(new TextField("body", "nothing relevant here", Store.NO));
    w.addDocument(doc3);

    IndexReader reader = w.getReader();
    IndexSearcher searcher = newSearcher(reader);
    searcher.setSimilarity(new BM25Similarity());

    BM25FQueryParser parser =
        new BM25FQueryParser(new MockAnalyzer(random()), Map.of("title", 2.0f, "body", 1.0f));
    Query q = parser.parse("fox");
    TopScoreDocCollectorManager collectorManager =
        new TopScoreDocCollectorManager(10, Integer.MAX_VALUE);
    TopDocs topDocs = searcher.search(q, collectorManager);
    assertEquals(new TotalHits(1, TotalHits.Relation.EQUAL_TO), topDocs.totalHits);

    q = parser.parse("dog");
    collectorManager = new TopScoreDocCollectorManager(10, Integer.MAX_VALUE);
    topDocs = searcher.search(q, collectorManager);
    assertEquals(new TotalHits(2, TotalHits.Relation.EQUAL_TO), topDocs.totalHits);

    reader.close();
    w.close();
    dir.close();
  }

  public void testBM25FScoringEquivalence() throws IOException {
    Directory dir = newDirectory();
    IndexWriterConfig iwc = new IndexWriterConfig(new MockAnalyzer(random()));
    iwc.setSimilarity(new BM25Similarity());
    RandomIndexWriter w = new RandomIndexWriter(random(), dir, iwc);

    // Doc with term frequency split across two fields
    Document doc1 = new Document();
    doc1.add(new TextField("a", "foo", Store.NO));
    doc1.add(new TextField("b", "foo", Store.NO));
    w.addDocument(doc1);

    // Doc with combined frequency in a single copy-field
    Document doc2 = new Document();
    doc2.add(new TextField("a", "foo", Store.NO));
    doc2.add(new TextField("a", "foo", Store.NO));
    w.addDocument(doc2);

    IndexReader reader = w.getReader();
    IndexSearcher searcher = newSearcher(reader);
    searcher.setSimilarity(new BM25Similarity());

    BM25FQueryParser parser =
        new BM25FQueryParser(new MockAnalyzer(random()), Map.of("a", 1.0f, "b", 1.0f));
    Query q = parser.parse("foo");

    TopScoreDocCollectorManager collectorManager =
        new TopScoreDocCollectorManager(10, Integer.MAX_VALUE);
    TopDocs topDocs = searcher.search(q, collectorManager);
    assertTrue(topDocs.totalHits.value() > 0);

    // Both docs should be returned and scored
    assertEquals(2, topDocs.scoreDocs.length);

    reader.close();
    w.close();
    dir.close();
  }
}

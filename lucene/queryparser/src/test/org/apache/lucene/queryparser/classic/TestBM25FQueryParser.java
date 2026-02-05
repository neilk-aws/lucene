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
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BoostQuery;
import org.apache.lucene.search.CombinedFieldQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;

/** Tests for {@link BM25FQueryParser}. */
public class TestBM25FQueryParser extends LuceneTestCase {

  private Analyzer analyzer;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    analyzer = new MockAnalyzer(random());
  }

  @Override
  public void tearDown() throws Exception {
    analyzer.close();
    super.tearDown();
  }

  public void testConstructorWithWeights() {
    Map<String, Float> weights = new LinkedHashMap<>();
    weights.put("title", 2.0f);
    weights.put("body", 1.0f);

    BM25FQueryParser parser = new BM25FQueryParser(weights, analyzer);
    assertEquals(weights, parser.getFieldWeights());
    assertArrayEquals(new String[] {"title", "body"}, parser.getFields());
  }

  public void testConstructorWithFields() {
    String[] fields = {"title", "body", "author"};
    BM25FQueryParser parser = new BM25FQueryParser(fields, analyzer);

    String[] parsedFields = parser.getFields();
    assertArrayEquals(fields, parsedFields);

    Map<String, Float> weights = parser.getFieldWeights();
    assertEquals(1.0f, weights.get("title"), 0.0f);
    assertEquals(1.0f, weights.get("body"), 0.0f);
    assertEquals(1.0f, weights.get("author"), 0.0f);
  }

  public void testConstructorNullWeights() {
    expectThrows(
        IllegalArgumentException.class,
        () -> {
          new BM25FQueryParser((Map<String, Float>) null, analyzer);
        });
  }

  public void testConstructorEmptyWeights() {
    expectThrows(
        IllegalArgumentException.class,
        () -> {
          new BM25FQueryParser(new LinkedHashMap<>(), analyzer);
        });
  }

  public void testConstructorWeightTooLow() {
    Map<String, Float> weights = new LinkedHashMap<>();
    weights.put("title", 0.5f); // Less than 1.0

    expectThrows(
        IllegalArgumentException.class,
        () -> {
          new BM25FQueryParser(weights, analyzer);
        });
  }

  public void testConstructorNullFields() {
    expectThrows(
        IllegalArgumentException.class,
        () -> {
          new BM25FQueryParser((String[]) null, analyzer);
        });
  }

  public void testConstructorEmptyFields() {
    expectThrows(
        IllegalArgumentException.class,
        () -> {
          new BM25FQueryParser(new String[0], analyzer);
        });
  }

  public void testSingleTermQuery() throws ParseException {
    Map<String, Float> weights = new LinkedHashMap<>();
    weights.put("title", 2.0f);
    weights.put("body", 1.0f);

    BM25FQueryParser parser = new BM25FQueryParser(weights, analyzer);
    Query q = parser.parse("test");

    assertTrue("Expected CombinedFieldQuery, got: " + q.getClass(), q instanceof CombinedFieldQuery);
    CombinedFieldQuery cfq = (CombinedFieldQuery) q;
    String queryStr = cfq.toString();
    assertTrue("Query should contain 'title'", queryStr.contains("title"));
    assertTrue("Query should contain 'body'", queryStr.contains("body"));
    assertTrue("Query should contain 'test'", queryStr.contains("test"));
  }

  public void testMultiTermQuery() throws ParseException {
    Map<String, Float> weights = new LinkedHashMap<>();
    weights.put("title", 2.0f);
    weights.put("body", 1.0f);

    BM25FQueryParser parser = new BM25FQueryParser(weights, analyzer);
    Query q = parser.parse("hello world");

    assertTrue("Expected BooleanQuery, got: " + q.getClass(), q instanceof BooleanQuery);
    BooleanQuery bq = (BooleanQuery) q;
    assertEquals("Should have 2 clauses", 2, bq.clauses().size());

    for (BooleanClause clause : bq.clauses()) {
      assertTrue(
          "Each clause should be CombinedFieldQuery",
          clause.query() instanceof CombinedFieldQuery);
      assertEquals(BooleanClause.Occur.SHOULD, clause.occur());
    }
  }

  public void testMultiTermQueryWithAndOperator() throws ParseException {
    Map<String, Float> weights = new LinkedHashMap<>();
    weights.put("title", 2.0f);
    weights.put("body", 1.0f);

    BM25FQueryParser parser = new BM25FQueryParser(weights, analyzer);
    parser.setDefaultOperator(QueryParser.Operator.AND);
    Query q = parser.parse("hello world");

    assertTrue("Expected BooleanQuery, got: " + q.getClass(), q instanceof BooleanQuery);
    BooleanQuery bq = (BooleanQuery) q;
    assertEquals("Should have 2 clauses", 2, bq.clauses().size());

    for (BooleanClause clause : bq.clauses()) {
      assertEquals(BooleanClause.Occur.MUST, clause.occur());
    }
  }

  public void testPhraseQuery() throws ParseException {
    Map<String, Float> weights = new LinkedHashMap<>();
    weights.put("title", 2.0f);
    weights.put("body", 1.0f);

    BM25FQueryParser parser = new BM25FQueryParser(weights, analyzer);
    Query q = parser.parse("\"hello world\"");

    assertTrue("Expected BooleanQuery for phrase, got: " + q.getClass(), q instanceof BooleanQuery);
    BooleanQuery bq = (BooleanQuery) q;
    assertEquals("Should have clauses for each field", 2, bq.clauses().size());
  }

  public void testPhraseQueryWithSlop() throws ParseException {
    Map<String, Float> weights = new LinkedHashMap<>();
    weights.put("title", 2.0f);
    weights.put("body", 1.0f);

    BM25FQueryParser parser = new BM25FQueryParser(weights, analyzer);
    Query q = parser.parse("\"hello world\"~3");

    assertTrue("Expected BooleanQuery, got: " + q.getClass(), q instanceof BooleanQuery);
    // The query should contain phrase queries with slop
    String queryStr = q.toString();
    assertTrue("Query should contain slop", queryStr.contains("~3"));
  }

  public void testFieldSpecificQuery() throws ParseException {
    Map<String, Float> weights = new LinkedHashMap<>();
    weights.put("title", 2.0f);
    weights.put("body", 1.0f);

    BM25FQueryParser parser = new BM25FQueryParser(weights, analyzer);
    Query q = parser.parse("title:specific");

    // Field-specific queries should use standard TermQuery
    assertFalse("Field-specific query should not be CombinedFieldQuery", q instanceof CombinedFieldQuery);
    assertTrue(
        "Query should be for 'title' field", q.toString().startsWith("title:"));
  }

  public void testWildcardQuery() throws ParseException {
    Map<String, Float> weights = new LinkedHashMap<>();
    weights.put("title", 2.0f);
    weights.put("body", 1.0f);

    BM25FQueryParser parser = new BM25FQueryParser(weights, analyzer);
    Query q = parser.parse("test*");

    assertTrue("Expected BooleanQuery for wildcard, got: " + q.getClass(), q instanceof BooleanQuery);
    BooleanQuery bq = (BooleanQuery) q;
    assertEquals("Should have clauses for each field", 2, bq.clauses().size());
  }

  public void testPrefixQuery() throws ParseException {
    Map<String, Float> weights = new LinkedHashMap<>();
    weights.put("title", 1.0f);
    weights.put("body", 1.0f);

    BM25FQueryParser parser = new BM25FQueryParser(weights, analyzer);
    Query q = parser.parse("test*");

    String queryStr = q.toString();
    assertTrue("Query should contain prefix for title", queryStr.contains("title:test*"));
    assertTrue("Query should contain prefix for body", queryStr.contains("body:test*"));
  }

  public void testFuzzyQuery() throws ParseException {
    Map<String, Float> weights = new LinkedHashMap<>();
    weights.put("title", 2.0f);
    weights.put("body", 1.0f);

    BM25FQueryParser parser = new BM25FQueryParser(weights, analyzer);
    Query q = parser.parse("test~");

    assertTrue("Expected BooleanQuery for fuzzy, got: " + q.getClass(), q instanceof BooleanQuery);
    BooleanQuery bq = (BooleanQuery) q;
    assertEquals("Should have clauses for each field", 2, bq.clauses().size());

    // Check that weights are applied
    boolean foundBoost = false;
    for (BooleanClause clause : bq.clauses()) {
      if (clause.query() instanceof BoostQuery) {
        foundBoost = true;
        break;
      }
    }
    assertTrue("Should have boosted clauses for weighted fields", foundBoost);
  }

  public void testRangeQuery() throws ParseException {
    Map<String, Float> weights = new LinkedHashMap<>();
    weights.put("title", 2.0f);
    weights.put("body", 1.0f);

    BM25FQueryParser parser = new BM25FQueryParser(weights, analyzer);
    Query q = parser.parse("[a TO z]");

    assertTrue("Expected BooleanQuery for range, got: " + q.getClass(), q instanceof BooleanQuery);
    BooleanQuery bq = (BooleanQuery) q;
    assertEquals("Should have clauses for each field", 2, bq.clauses().size());
  }

  public void testBooleanCombination() throws ParseException {
    Map<String, Float> weights = new LinkedHashMap<>();
    weights.put("title", 2.0f);
    weights.put("body", 1.0f);

    BM25FQueryParser parser = new BM25FQueryParser(weights, analyzer);
    Query q = parser.parse("hello AND world");

    assertTrue("Expected BooleanQuery, got: " + q.getClass(), q instanceof BooleanQuery);
    BooleanQuery bq = (BooleanQuery) q;

    // Both terms should be MUST
    int mustCount = 0;
    for (BooleanClause clause : bq.clauses()) {
      if (clause.occur() == BooleanClause.Occur.MUST) {
        mustCount++;
      }
    }
    assertEquals("Should have 2 MUST clauses", 2, mustCount);
  }

  public void testSearchIntegration() throws IOException, ParseException {
    Directory dir = newDirectory();
    IndexWriterConfig iwc = new IndexWriterConfig(analyzer);
    iwc.setSimilarity(new BM25Similarity());
    IndexWriter writer = new IndexWriter(dir, iwc);

    // Add documents
    Document doc1 = new Document();
    doc1.add(new TextField("title", "quick brown fox", Field.Store.YES));
    doc1.add(new TextField("body", "the fox jumps over the lazy dog", Field.Store.YES));
    writer.addDocument(doc1);

    Document doc2 = new Document();
    doc2.add(new TextField("title", "lazy dog sleeps", Field.Store.YES));
    doc2.add(new TextField("body", "the dog is lazy and sleeps all day", Field.Store.YES));
    writer.addDocument(doc2);

    Document doc3 = new Document();
    doc3.add(new TextField("title", "fox and dog", Field.Store.YES));
    doc3.add(new TextField("body", "the quick fox and the lazy dog are friends", Field.Store.YES));
    writer.addDocument(doc3);

    writer.close();

    IndexReader reader = DirectoryReader.open(dir);
    IndexSearcher searcher = newSearcher(reader);
    searcher.setSimilarity(new BM25Similarity());

    Map<String, Float> weights = new LinkedHashMap<>();
    weights.put("title", 2.0f);
    weights.put("body", 1.0f);

    BM25FQueryParser parser = new BM25FQueryParser(weights, analyzer);

    // Test single term search
    Query q1 = parser.parse("fox");
    TopDocs results1 = searcher.search(q1, 10);
    assertTrue("Should find documents containing 'fox'", results1.totalHits.value() > 0);

    // Test multi-term search
    Query q2 = parser.parse("lazy dog");
    TopDocs results2 = searcher.search(q2, 10);
    assertTrue("Should find documents containing 'lazy dog'", results2.totalHits.value() > 0);

    // Test phrase search
    Query q3 = parser.parse("\"lazy dog\"");
    TopDocs results3 = searcher.search(q3, 10);
    assertTrue("Should find documents with phrase 'lazy dog'", results3.totalHits.value() > 0);

    reader.close();
    dir.close();
  }

  public void testScoringDifferenceFromMultiFieldParser() throws IOException, ParseException {
    Directory dir = newDirectory();
    IndexWriterConfig iwc = new IndexWriterConfig(analyzer);
    iwc.setSimilarity(new BM25Similarity());
    IndexWriter writer = new IndexWriter(dir, iwc);

    // Add documents where the term appears in different fields
    Document doc1 = new Document();
    doc1.add(new TextField("title", "test", Field.Store.YES));
    doc1.add(new TextField("body", "content without the term", Field.Store.YES));
    writer.addDocument(doc1);

    Document doc2 = new Document();
    doc2.add(new TextField("title", "other content", Field.Store.YES));
    doc2.add(new TextField("body", "test appears here", Field.Store.YES));
    writer.addDocument(doc2);

    writer.close();

    IndexReader reader = DirectoryReader.open(dir);
    IndexSearcher searcher = newSearcher(reader);
    searcher.setSimilarity(new BM25Similarity());

    Map<String, Float> weights = new LinkedHashMap<>();
    weights.put("title", 2.0f);
    weights.put("body", 1.0f);

    // BM25F parser
    BM25FQueryParser bm25fParser = new BM25FQueryParser(weights, analyzer);
    Query bm25fQuery = bm25fParser.parse("test");

    // MultiField parser for comparison
    MultiFieldQueryParser mfParser =
        new MultiFieldQueryParser(new String[] {"title", "body"}, analyzer, weights);
    Query mfQuery = mfParser.parse("test");

    // Both should return results
    TopDocs bm25fResults = searcher.search(bm25fQuery, 10);
    TopDocs mfResults = searcher.search(mfQuery, 10);

    assertEquals(
        "Both parsers should return same number of hits",
        mfResults.totalHits.value(),
        bm25fResults.totalHits.value());

    // The queries should be different types
    assertTrue("BM25F query should be CombinedFieldQuery", bm25fQuery instanceof CombinedFieldQuery);
    assertTrue("MultiField query should be BooleanQuery", mfQuery instanceof BooleanQuery);

    reader.close();
    dir.close();
  }

  public void testEmptyQuery() throws ParseException {
    Map<String, Float> weights = new LinkedHashMap<>();
    weights.put("title", 2.0f);
    weights.put("body", 1.0f);

    BM25FQueryParser parser = new BM25FQueryParser(weights, analyzer);

    // Empty query should throw ParseException
    expectThrows(
        ParseException.class,
        () -> {
          parser.parse("");
        });
  }

  public void testWhitespaceOnlyQuery() throws ParseException {
    Map<String, Float> weights = new LinkedHashMap<>();
    weights.put("title", 2.0f);
    weights.put("body", 1.0f);

    BM25FQueryParser parser = new BM25FQueryParser(weights, analyzer);

    // Whitespace-only query should throw ParseException
    expectThrows(
        ParseException.class,
        () -> {
          parser.parse("   ");
        });
  }

  public void testMatchAllQuery() throws ParseException {
    Map<String, Float> weights = new LinkedHashMap<>();
    weights.put("title", 2.0f);
    weights.put("body", 1.0f);

    BM25FQueryParser parser = new BM25FQueryParser(weights, analyzer);
    Query q = parser.parse("*:*");

    assertTrue("*:* should produce MatchAllDocsQuery", q.toString().contains("*:*"));
  }

  public void testRegexpQuery() throws ParseException {
    Map<String, Float> weights = new LinkedHashMap<>();
    weights.put("title", 2.0f);
    weights.put("body", 1.0f);

    BM25FQueryParser parser = new BM25FQueryParser(weights, analyzer);
    Query q = parser.parse("/[a-z]+/");

    assertTrue("Expected BooleanQuery for regexp, got: " + q.getClass(), q instanceof BooleanQuery);
    BooleanQuery bq = (BooleanQuery) q;
    assertEquals("Should have clauses for each field", 2, bq.clauses().size());
  }

  public void testQueryWithBoost() throws ParseException {
    Map<String, Float> weights = new LinkedHashMap<>();
    weights.put("title", 2.0f);
    weights.put("body", 1.0f);

    BM25FQueryParser parser = new BM25FQueryParser(weights, analyzer);
    Query q = parser.parse("test^2");

    assertTrue("Expected BoostQuery, got: " + q.getClass(), q instanceof BoostQuery);
    BoostQuery bq = (BoostQuery) q;
    assertEquals(2.0f, bq.getBoost(), 0.0f);
    assertTrue(
        "Inner query should be CombinedFieldQuery",
        bq.getQuery() instanceof CombinedFieldQuery);
  }

  public void testRequiredAndProhibitedTerms() throws ParseException {
    Map<String, Float> weights = new LinkedHashMap<>();
    weights.put("title", 2.0f);
    weights.put("body", 1.0f);

    BM25FQueryParser parser = new BM25FQueryParser(weights, analyzer);
    Query q = parser.parse("+required -prohibited");

    assertTrue("Expected BooleanQuery, got: " + q.getClass(), q instanceof BooleanQuery);
    BooleanQuery bq = (BooleanQuery) q;

    boolean hasMust = false;
    boolean hasMustNot = false;
    for (BooleanClause clause : bq.clauses()) {
      if (clause.occur() == BooleanClause.Occur.MUST) hasMust = true;
      if (clause.occur() == BooleanClause.Occur.MUST_NOT) hasMustNot = true;
    }
    assertTrue("Should have MUST clause", hasMust);
    assertTrue("Should have MUST_NOT clause", hasMustNot);
  }
}

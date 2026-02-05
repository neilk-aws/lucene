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
import java.util.HashMap;
import java.util.Map;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.CombinedFieldQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;

/** Tests for {@link BM25FMultiFieldQueryParser}. */
public class TestBM25FMultiFieldQueryParser extends LuceneTestCase {

  /** Test parsing a simple single term query produces CombinedFieldQuery. */
  public void testSimpleTerm() throws ParseException {
    String[] fields = {"title", "body"};
    BM25FMultiFieldQueryParser parser =
        new BM25FMultiFieldQueryParser(fields, new MockAnalyzer(random()));

    Query query = parser.parse("test");
    assertTrue(
        "Expected CombinedFieldQuery but got: " + query.getClass().getName(),
        query instanceof CombinedFieldQuery);
    assertEquals("CombinedFieldQuery((body title)(test))", query.toString());
  }

  /** Test parsing multiple terms produces BooleanQuery of CombinedFieldQueries. */
  public void testMultipleTerms() throws ParseException {
    String[] fields = {"title", "body"};
    BM25FMultiFieldQueryParser parser =
        new BM25FMultiFieldQueryParser(fields, new MockAnalyzer(random()));

    Query query = parser.parse("hello world");
    assertTrue(
        "Expected BooleanQuery but got: " + query.getClass().getName(),
        query instanceof BooleanQuery);
    BooleanQuery bq = (BooleanQuery) query;
    assertEquals(2, bq.clauses().size());

    // Both clauses should be CombinedFieldQuery
    for (BooleanClause clause : bq.clauses()) {
      assertTrue(
          "Expected CombinedFieldQuery but got: " + clause.query().getClass().getName(),
          clause.query() instanceof CombinedFieldQuery);
      assertEquals(BooleanClause.Occur.SHOULD, clause.occur());
    }
  }

  /** Test that AND operator produces MUST clauses. */
  public void testAndOperator() throws ParseException {
    String[] fields = {"title", "body"};
    BM25FMultiFieldQueryParser parser =
        new BM25FMultiFieldQueryParser(fields, new MockAnalyzer(random()));
    parser.setDefaultOperator(QueryParserBase.AND_OPERATOR);

    Query query = parser.parse("hello world");
    assertTrue(
        "Expected BooleanQuery but got: " + query.getClass().getName(),
        query instanceof BooleanQuery);
    BooleanQuery bq = (BooleanQuery) query;
    assertEquals(2, bq.clauses().size());

    // Both clauses should be MUST with AND operator
    for (BooleanClause clause : bq.clauses()) {
      assertTrue(
          "Expected CombinedFieldQuery but got: " + clause.query().getClass().getName(),
          clause.query() instanceof CombinedFieldQuery);
      assertEquals(BooleanClause.Occur.MUST, clause.occur());
    }
  }

  /** Test parsing phrase query falls back to BooleanQuery. */
  public void testPhraseQuery() throws ParseException {
    String[] fields = {"title", "body"};
    BM25FMultiFieldQueryParser parser =
        new BM25FMultiFieldQueryParser(fields, new MockAnalyzer(random()));

    Query query = parser.parse("\"hello world\"");
    assertTrue(
        "Expected BooleanQuery but got: " + query.getClass().getName(),
        query instanceof BooleanQuery);

    // Phrase queries should create per-field PhraseQueries combined with OR
    String queryStr = query.toString();
    assertTrue(
        "Expected phrase query in multiple fields but got: " + queryStr,
        queryStr.contains("body:\"hello world\"") && queryStr.contains("title:\"hello world\""));
  }

  /** Test field weights are applied to CombinedFieldQuery. */
  public void testFieldWeights() throws ParseException {
    String[] fields = {"title", "body"};
    Map<String, Float> boosts = new HashMap<>();
    boosts.put("title", 2.0f);
    boosts.put("body", 1.0f);

    BM25FMultiFieldQueryParser parser =
        new BM25FMultiFieldQueryParser(fields, new MockAnalyzer(random()), boosts);

    Query query = parser.parse("test");
    assertTrue(
        "Expected CombinedFieldQuery but got: " + query.getClass().getName(),
        query instanceof CombinedFieldQuery);
    // CombinedFieldQuery toString shows weights
    String queryStr = query.toString();
    assertTrue("Expected title^2.0 in query but got: " + queryStr, queryStr.contains("title^2.0"));
  }

  /** Test that weights less than 1.0 throw an exception. */
  public void testInvalidWeightThrowsException() {
    String[] fields = {"title", "body"};
    Map<String, Float> boosts = new HashMap<>();
    boosts.put("title", 0.5f); // Invalid: less than 1.0
    boosts.put("body", 1.0f);

    IllegalArgumentException ex =
        expectThrows(
            IllegalArgumentException.class,
            () -> new BM25FMultiFieldQueryParser(fields, new MockAnalyzer(random()), boosts));
    assertTrue(ex.getMessage().contains("BM25F requires field weights >= 1.0"));
    assertTrue(ex.getMessage().contains("title"));
  }

  /** Test wildcard query falls back to BooleanQuery. */
  public void testWildcardQuery() throws ParseException {
    String[] fields = {"title", "body"};
    BM25FMultiFieldQueryParser parser =
        new BM25FMultiFieldQueryParser(fields, new MockAnalyzer(random()));

    Query query = parser.parse("test*");
    assertTrue(
        "Expected BooleanQuery but got: " + query.getClass().getName(),
        query instanceof BooleanQuery);
    String queryStr = query.toString();
    assertTrue(
        "Expected wildcard in multiple fields but got: " + queryStr,
        queryStr.contains("body:test*") && queryStr.contains("title:test*"));
  }

  /** Test prefix query falls back to BooleanQuery. */
  public void testPrefixQuery() throws ParseException {
    String[] fields = {"title", "body"};
    BM25FMultiFieldQueryParser parser =
        new BM25FMultiFieldQueryParser(fields, new MockAnalyzer(random()));

    Query query = parser.parse("test*");
    assertTrue(
        "Expected BooleanQuery but got: " + query.getClass().getName(),
        query instanceof BooleanQuery);
  }

  /** Test fuzzy query falls back to BooleanQuery. */
  public void testFuzzyQuery() throws ParseException {
    String[] fields = {"title", "body"};
    BM25FMultiFieldQueryParser parser =
        new BM25FMultiFieldQueryParser(fields, new MockAnalyzer(random()));

    Query query = parser.parse("test~");
    assertTrue(
        "Expected BooleanQuery but got: " + query.getClass().getName(),
        query instanceof BooleanQuery);
    String queryStr = query.toString();
    assertTrue(
        "Expected fuzzy in multiple fields but got: " + queryStr,
        queryStr.contains("body:test~") && queryStr.contains("title:test~"));
  }

  /** Test range query falls back to BooleanQuery. */
  public void testRangeQuery() throws ParseException {
    String[] fields = {"title", "body"};
    BM25FMultiFieldQueryParser parser =
        new BM25FMultiFieldQueryParser(fields, new MockAnalyzer(random()));

    Query query = parser.parse("[a TO z]");
    assertTrue(
        "Expected BooleanQuery but got: " + query.getClass().getName(),
        query instanceof BooleanQuery);
    String queryStr = query.toString();
    assertTrue(
        "Expected range in multiple fields but got: " + queryStr,
        queryStr.contains("body:[a TO z]") && queryStr.contains("title:[a TO z]"));
  }

  /** Test explicit field query bypasses multi-field behavior. */
  public void testExplicitField() throws ParseException {
    String[] fields = {"title", "body"};
    BM25FMultiFieldQueryParser parser =
        new BM25FMultiFieldQueryParser(fields, new MockAnalyzer(random()));

    Query query = parser.parse("title:test");
    // Should be a simple term query on the title field only
    assertEquals("title:test", query.toString());
  }

  /** Test mixed explicit and implicit field queries. */
  public void testMixedFieldQueries() throws ParseException {
    String[] fields = {"title", "body"};
    BM25FMultiFieldQueryParser parser =
        new BM25FMultiFieldQueryParser(fields, new MockAnalyzer(random()));

    Query query = parser.parse("implicit title:explicit");
    String queryStr = query.toString();
    // Should have CombinedFieldQuery for "implicit" and term query for "title:explicit"
    assertTrue(
        "Expected combined and term query but got: " + queryStr,
        queryStr.contains("CombinedFieldQuery") && queryStr.contains("title:explicit"));
  }

  /** Test that BM25F scoring produces expected results in actual search. */
  public void testSearchWithBM25FScoring() throws IOException, ParseException {
    Directory dir = newDirectory();
    Analyzer analyzer = new MockAnalyzer(random());

    // Index some documents
    IndexWriter writer = new IndexWriter(dir, newIndexWriterConfig(analyzer));

    Document doc1 = new Document();
    doc1.add(new TextField("title", "quick brown fox", Field.Store.YES));
    doc1.add(new TextField("body", "the quick brown fox jumps", Field.Store.YES));
    writer.addDocument(doc1);

    Document doc2 = new Document();
    doc2.add(new TextField("title", "lazy dog", Field.Store.YES));
    doc2.add(new TextField("body", "the lazy dog sleeps", Field.Store.YES));
    writer.addDocument(doc2);

    Document doc3 = new Document();
    doc3.add(new TextField("title", "fox and dog", Field.Store.YES));
    doc3.add(new TextField("body", "the fox and the dog play", Field.Store.YES));
    writer.addDocument(doc3);

    writer.close();

    // Search
    IndexReader reader = DirectoryReader.open(dir);
    IndexSearcher searcher = newSearcher(reader);
    searcher.setSimilarity(new BM25Similarity());

    String[] fields = {"title", "body"};
    BM25FMultiFieldQueryParser parser = new BM25FMultiFieldQueryParser(fields, analyzer);

    Query query = parser.parse("fox");
    assertTrue(
        "Expected CombinedFieldQuery but got: " + query.getClass().getName(),
        query instanceof CombinedFieldQuery);

    TopDocs topDocs = searcher.search(query, 10);
    assertEquals("Expected 2 matching documents", 2, topDocs.totalHits.value());

    // Verify documents were found
    ScoreDoc[] hits = topDocs.scoreDocs;
    assertTrue(hits.length > 0);

    reader.close();
    dir.close();
  }

  /** Test that field weights affect ranking in actual search. */
  public void testFieldWeightsAffectRanking() throws IOException, ParseException {
    Directory dir = newDirectory();
    Analyzer analyzer = new MockAnalyzer(random());

    // Index documents where "test" appears in different fields
    IndexWriter writer = new IndexWriter(dir, newIndexWriterConfig(analyzer));

    Document doc1 = new Document();
    doc1.add(new TextField("title", "test", Field.Store.YES));
    doc1.add(new TextField("body", "other content", Field.Store.YES));
    writer.addDocument(doc1);

    Document doc2 = new Document();
    doc2.add(new TextField("title", "other content", Field.Store.YES));
    doc2.add(new TextField("body", "test", Field.Store.YES));
    writer.addDocument(doc2);

    writer.close();

    IndexReader reader = DirectoryReader.open(dir);
    IndexSearcher searcher = newSearcher(reader);
    searcher.setSimilarity(new BM25Similarity());

    String[] fields = {"title", "body"};

    // With title weighted higher, doc1 should rank first
    Map<String, Float> boosts = new HashMap<>();
    boosts.put("title", 3.0f);
    boosts.put("body", 1.0f);

    BM25FMultiFieldQueryParser parser = new BM25FMultiFieldQueryParser(fields, analyzer, boosts);
    Query query = parser.parse("test");

    TopDocs topDocs = searcher.search(query, 10);
    assertEquals(2, topDocs.totalHits.value());

    // Doc1 (with "test" in title) should rank higher
    ScoreDoc[] hits = topDocs.scoreDocs;
    assertEquals(0, hits[0].doc); // doc1 should be first

    reader.close();
    dir.close();
  }

  /** Test regexp query falls back to BooleanQuery. */
  public void testRegexpQuery() throws ParseException {
    String[] fields = {"title", "body"};
    BM25FMultiFieldQueryParser parser =
        new BM25FMultiFieldQueryParser(fields, new MockAnalyzer(random()));

    Query query = parser.parse("/te.t/");
    assertTrue(
        "Expected BooleanQuery but got: " + query.getClass().getName(),
        query instanceof BooleanQuery);
    String queryStr = query.toString();
    assertTrue(
        "Expected regexp in multiple fields but got: " + queryStr,
        queryStr.contains("body:/te.t/") && queryStr.contains("title:/te.t/"));
  }

  /** Test phrase with slop. */
  public void testPhraseWithSlop() throws ParseException {
    String[] fields = {"title", "body"};
    BM25FMultiFieldQueryParser parser =
        new BM25FMultiFieldQueryParser(fields, new MockAnalyzer(random()));

    Query query = parser.parse("\"hello world\"~2");
    String queryStr = query.toString();
    // Should have phrase queries with slop in multiple fields
    assertTrue(
        "Expected phrase with slop but got: " + queryStr,
        queryStr.contains("~2"));
  }

  /** Test query with boost. */
  public void testQueryBoost() throws ParseException {
    String[] fields = {"title", "body"};
    BM25FMultiFieldQueryParser parser =
        new BM25FMultiFieldQueryParser(fields, new MockAnalyzer(random()));

    Query query = parser.parse("test^2");
    String queryStr = query.toString();
    // Should have boosted CombinedFieldQuery
    assertTrue(
        "Expected boosted query but got: " + queryStr,
        queryStr.contains("^2.0"));
  }

  /** Test stop words handling. */
  public void testStopWords() throws ParseException {
    String[] fields = {"title", "body"};
    // Use the QPTestAnalyzer which filters out "stop"
    Analyzer analyzer = new TestQueryParser.QPTestAnalyzer();
    BM25FMultiFieldQueryParser parser = new BM25FMultiFieldQueryParser(fields, analyzer);

    Query query = parser.parse("test stop word");
    // "stop" should be filtered out
    assertNotNull(query);
    String queryStr = query.toString();
    assertFalse("Stop word should be filtered but got: " + queryStr, queryStr.contains("stop"));
  }

  /** Test empty query after stop word filtering. */
  public void testAllStopWords() throws ParseException {
    String[] fields = {"title", "body"};
    Analyzer analyzer = new TestQueryParser.QPTestAnalyzer();
    BM25FMultiFieldQueryParser parser = new BM25FMultiFieldQueryParser(fields, analyzer);

    Query query = parser.parse("stop");
    // All words filtered - should return empty BooleanQuery or match no docs query
    assertNotNull(query);
    assertEquals("", query.toString());
  }

  /** Test required and prohibited terms. */
  public void testRequiredProhibited() throws ParseException {
    String[] fields = {"title", "body"};
    BM25FMultiFieldQueryParser parser =
        new BM25FMultiFieldQueryParser(fields, new MockAnalyzer(random()));

    Query query = parser.parse("+required -prohibited optional");
    assertTrue(
        "Expected BooleanQuery but got: " + query.getClass().getName(),
        query instanceof BooleanQuery);

    BooleanQuery bq = (BooleanQuery) query;
    // Check for MUST, MUST_NOT, and SHOULD clauses
    boolean hasMust = false;
    boolean hasMustNot = false;
    boolean hasShould = false;
    for (BooleanClause clause : bq.clauses()) {
      if (clause.occur() == BooleanClause.Occur.MUST) hasMust = true;
      if (clause.occur() == BooleanClause.Occur.MUST_NOT) hasMustNot = true;
      if (clause.occur() == BooleanClause.Occur.SHOULD) hasShould = true;
    }
    assertTrue("Expected MUST clause", hasMust);
    assertTrue("Expected MUST_NOT clause", hasMustNot);
    assertTrue("Expected SHOULD clause", hasShould);
  }
}

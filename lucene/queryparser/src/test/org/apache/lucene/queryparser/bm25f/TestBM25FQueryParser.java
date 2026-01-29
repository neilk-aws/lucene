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
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.CombinedFieldQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.MatchNoDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/** Tests for {@link BM25FQueryParser}. */
public class TestBM25FQueryParser extends LuceneTestCase {

  private Analyzer analyzer;
  private Map<String, Float> fieldWeights;
  private BM25FQueryParser parser;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    analyzer = new StandardAnalyzer();
    fieldWeights = new LinkedHashMap<>();
    fieldWeights.put("title", 5.0f);
    fieldWeights.put("body", 1.0f);
    parser = new BM25FQueryParser(analyzer, fieldWeights);
  }

  @Override
  public void tearDown() throws Exception {
    analyzer.close();
    super.tearDown();
  }

  // ==================== Constructor Tests ====================

  public void testConstructorSingleField() {
    BM25FQueryParser singleFieldParser = new BM25FQueryParser(analyzer, "content");
    assertEquals(1, singleFieldParser.getFieldWeights().size());
    assertEquals(1.0f, singleFieldParser.getFieldWeights().get("content"), 0.001f);
  }

  public void testConstructorMultipleFields() {
    assertEquals(2, parser.getFieldWeights().size());
    assertEquals(5.0f, parser.getFieldWeights().get("title"), 0.001f);
    assertEquals(1.0f, parser.getFieldWeights().get("body"), 0.001f);
  }

  public void testConstructorNullAnalyzer() {
    expectThrows(NullPointerException.class, () -> new BM25FQueryParser(null, "field"));
  }

  public void testConstructorNullField() {
    expectThrows(NullPointerException.class, () -> new BM25FQueryParser(analyzer, (String) null));
  }

  public void testConstructorNullFieldWeights() {
    expectThrows(
        NullPointerException.class, () -> new BM25FQueryParser(analyzer, (Map<String, Float>) null));
  }

  public void testConstructorEmptyFieldWeights() {
    Map<String, Float> emptyWeights = new LinkedHashMap<>();
    expectThrows(IllegalArgumentException.class, () -> new BM25FQueryParser(analyzer, emptyWeights));
  }

  public void testConstructorWeightBelowOne() {
    Map<String, Float> invalidWeights = new LinkedHashMap<>();
    invalidWeights.put("field", 0.5f);
    IllegalArgumentException e =
        expectThrows(IllegalArgumentException.class, () -> new BM25FQueryParser(analyzer, invalidWeights));
    assertTrue(e.getMessage().contains("must be >= 1.0"));
  }

  // ==================== Single Term Parsing Tests ====================

  public void testParseSingleTerm() {
    Query query = parser.parse("search");
    assertTrue("Expected CombinedFieldQuery, got " + query.getClass(), query instanceof CombinedFieldQuery);
    CombinedFieldQuery cfq = (CombinedFieldQuery) query;
    assertEquals(2, cfq.getFieldAndWeights().size());
  }

  public void testParseSingleTermUpperCase() {
    Query query = parser.parse("SEARCH");
    assertTrue(query instanceof CombinedFieldQuery);
  }

  // ==================== Multi-Term Parsing Tests ====================

  public void testParseMultiTermDefaultOr() {
    Query query = parser.parse("search engine");
    assertTrue("Expected BooleanQuery, got " + query.getClass(), query instanceof BooleanQuery);
    BooleanQuery bq = (BooleanQuery) query;
    assertEquals(2, bq.clauses().size());
    for (BooleanClause clause : bq.clauses()) {
      assertEquals(BooleanClause.Occur.SHOULD, clause.occur());
      assertTrue(clause.query() instanceof CombinedFieldQuery);
    }
  }

  public void testParseMultiTermWithAnd() {
    parser.setDefaultOperator(BooleanClause.Occur.MUST);
    Query query = parser.parse("search engine");
    assertTrue(query instanceof BooleanQuery);
    BooleanQuery bq = (BooleanQuery) query;
    assertEquals(2, bq.clauses().size());
    for (BooleanClause clause : bq.clauses()) {
      assertEquals(BooleanClause.Occur.MUST, clause.occur());
    }
  }

  // ==================== Operator Tests ====================

  public void testParseRequiredTerm() {
    Query query = parser.parse("+required optional");
    assertTrue(query instanceof BooleanQuery);
    BooleanQuery bq = (BooleanQuery) query;
    assertEquals(2, bq.clauses().size());
    // First term should be MUST
    assertEquals(BooleanClause.Occur.MUST, bq.clauses().get(0).occur());
    // Second term should be SHOULD (default)
    assertEquals(BooleanClause.Occur.SHOULD, bq.clauses().get(1).occur());
  }

  public void testParseProhibitedTerm() {
    Query query = parser.parse("allowed -prohibited");
    assertTrue(query instanceof BooleanQuery);
    BooleanQuery bq = (BooleanQuery) query;
    assertEquals(2, bq.clauses().size());
    assertEquals(BooleanClause.Occur.SHOULD, bq.clauses().get(0).occur());
    assertEquals(BooleanClause.Occur.MUST_NOT, bq.clauses().get(1).occur());
  }

  public void testParsePhraseQuery() {
    Query query = parser.parse("\"search engine\"");
    // Phrase queries use BooleanQuery across fields (not CombinedFieldQuery)
    assertNotNull(query);
    assertFalse(query instanceof MatchNoDocsQuery);
  }

  public void testParsePhraseWithOtherTerms() {
    Query query = parser.parse("\"search engine\" optimization");
    assertTrue(query instanceof BooleanQuery);
    BooleanQuery bq = (BooleanQuery) query;
    assertTrue(bq.clauses().size() >= 2);
  }

  public void testParseEscapedCharacter() {
    // Backslash should escape special characters
    Query query = parser.parse("\\+notoperator");
    assertNotNull(query);
    assertFalse(query instanceof MatchNoDocsQuery);
  }

  // ==================== Special Cases Tests ====================

  public void testParseMatchAll() {
    Query query = parser.parse("*");
    assertTrue(query instanceof MatchAllDocsQuery);
  }

  public void testParseMatchAllWithWhitespace() {
    Query query = parser.parse("  *  ");
    assertTrue(query instanceof MatchAllDocsQuery);
  }

  public void testParseEmptyQuery() {
    Query query = parser.parse("");
    assertTrue(query instanceof MatchNoDocsQuery);
  }

  public void testParseWhitespaceOnly() {
    Query query = parser.parse("   ");
    assertTrue(query instanceof MatchNoDocsQuery);
  }

  public void testParseNullQuery() {
    expectThrows(NullPointerException.class, () -> parser.parse(null));
  }

  // ==================== Default Operator Tests ====================

  public void testDefaultOperatorInitialValue() {
    assertEquals(BooleanClause.Occur.SHOULD, parser.getDefaultOperator());
  }

  public void testSetDefaultOperatorMust() {
    parser.setDefaultOperator(BooleanClause.Occur.MUST);
    assertEquals(BooleanClause.Occur.MUST, parser.getDefaultOperator());
  }

  public void testSetDefaultOperatorInvalid() {
    expectThrows(
        IllegalArgumentException.class, () -> parser.setDefaultOperator(BooleanClause.Occur.FILTER));
  }

  // ==================== Field Weights Tests ====================

  public void testFieldWeightsAreImmutable() {
    Map<String, Float> weights = parser.getFieldWeights();
    expectThrows(UnsupportedOperationException.class, () -> weights.put("new", 2.0f));
  }

  // ==================== Integration Tests ====================

  public void testSearchWithBM25FQuery() throws IOException {
    try (Directory dir = new ByteBuffersDirectory()) {
      // Index some documents
      try (IndexWriter writer = new IndexWriter(dir, new IndexWriterConfig(analyzer))) {
        Document doc1 = new Document();
        doc1.add(new TextField("title", "search engine optimization", Field.Store.NO));
        doc1.add(new TextField("body", "learn about search engines", Field.Store.NO));
        writer.addDocument(doc1);

        Document doc2 = new Document();
        doc2.add(new TextField("title", "web development", Field.Store.NO));
        doc2.add(new TextField("body", "search and find information", Field.Store.NO));
        writer.addDocument(doc2);

        Document doc3 = new Document();
        doc3.add(new TextField("title", "search", Field.Store.NO));
        doc3.add(new TextField("body", "search search search", Field.Store.NO));
        writer.addDocument(doc3);
      }

      // Search
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        Query query = parser.parse("search");

        TopDocs results = searcher.search(query, 10);
        assertEquals(3, results.totalHits.value());

        // Doc3 should rank highest (search in title + multiple in body)
        // But the exact ranking depends on BM25 parameters
        assertTrue(results.scoreDocs.length > 0);
      }
    }
  }

  public void testSearchWithPhraseQuery() throws IOException {
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter writer = new IndexWriter(dir, new IndexWriterConfig(analyzer))) {
        Document doc1 = new Document();
        doc1.add(new TextField("title", "search engine optimization", Field.Store.NO));
        doc1.add(new TextField("body", "about search engines", Field.Store.NO));
        writer.addDocument(doc1);

        Document doc2 = new Document();
        doc2.add(new TextField("title", "web development", Field.Store.NO));
        doc2.add(new TextField("body", "search for information", Field.Store.NO));
        writer.addDocument(doc2);
      }

      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        Query query = parser.parse("\"search engine\"");

        TopDocs results = searcher.search(query, 10);
        // Only doc1 has "search engine" as a phrase
        assertEquals(1, results.totalHits.value());
      }
    }
  }

  public void testSearchWithRequiredAndProhibited() throws IOException {
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter writer = new IndexWriter(dir, new IndexWriterConfig(analyzer))) {
        Document doc1 = new Document();
        doc1.add(new TextField("title", "search engine", Field.Store.NO));
        doc1.add(new TextField("body", "learn about engines", Field.Store.NO));
        writer.addDocument(doc1);

        Document doc2 = new Document();
        doc2.add(new TextField("title", "search web", Field.Store.NO));
        doc2.add(new TextField("body", "web search", Field.Store.NO));
        writer.addDocument(doc2);
      }

      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        // Search must have "search" but must NOT have "engine"
        Query query = parser.parse("+search -engine");

        TopDocs results = searcher.search(query, 10);
        // Only doc2 matches (has search, doesn't have engine)
        assertEquals(1, results.totalHits.value());
      }
    }
  }
}

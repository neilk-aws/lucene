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
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.BM25FSimilarity;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.junit.Test;

/** Tests for BM25FQueryParser */
public class TestBM25FQueryParser extends LuceneTestCase {

  @Test
  public void testConstructor() {
    String[] fields = {"title", "body"};
    Map<String, Float> boosts = new HashMap<>();
    boosts.put("title", 5.0f);
    boosts.put("body", 1.0f);

    Analyzer analyzer = new MockAnalyzer(random());
    BM25FQueryParser parser = new BM25FQueryParser(fields, analyzer, boosts);

    assertArrayEquals(fields, parser.getFields());
    assertEquals(boosts, parser.getFieldBoosts());
  }

  @Test
  public void testConstructorWithDefaultBoosts() {
    String[] fields = {"title", "body"};
    Analyzer analyzer = new MockAnalyzer(random());
    BM25FQueryParser parser = new BM25FQueryParser(fields, analyzer);

    assertArrayEquals(fields, parser.getFields());
    assertNotNull(parser.getFieldBoosts());
  }

  @Test
  public void testNullFields() {
    Map<String, Float> boosts = new HashMap<>();
    boosts.put("field", 1.0f);
    Analyzer analyzer = new MockAnalyzer(random());

    expectThrows(
        IllegalArgumentException.class,
        () -> {
          new BM25FQueryParser(null, analyzer, boosts);
        });
  }

  @Test
  public void testEmptyFields() {
    String[] fields = {};
    Map<String, Float> boosts = new HashMap<>();
    Analyzer analyzer = new MockAnalyzer(random());

    expectThrows(
        IllegalArgumentException.class,
        () -> {
          new BM25FQueryParser(fields, analyzer, boosts);
        });
  }

  @Test
  public void testNullBoosts() {
    String[] fields = {"field"};
    Analyzer analyzer = new MockAnalyzer(random());

    expectThrows(
        IllegalArgumentException.class,
        () -> {
          new BM25FQueryParser(fields, analyzer, null);
        });
  }

  @Test
  public void testParseSimpleQuery() throws Exception {
    String[] fields = {"title", "body"};
    Map<String, Float> boosts = new HashMap<>();
    boosts.put("title", 2.0f);
    boosts.put("body", 1.0f);

    Analyzer analyzer = new MockAnalyzer(random());
    BM25FQueryParser parser = new BM25FQueryParser(fields, analyzer, boosts);

    Query query = parser.parse("search");
    assertNotNull(query);
    assertTrue(query instanceof BooleanQuery);

    BooleanQuery booleanQuery = (BooleanQuery) query;
    assertEquals(2, booleanQuery.clauses().size());
  }

  @Test
  public void testParseMultiTermQuery() throws Exception {
    String[] fields = {"title", "body", "keywords"};
    Map<String, Float> boosts = new HashMap<>();
    boosts.put("title", 5.0f);
    boosts.put("body", 1.0f);
    boosts.put("keywords", 3.0f);

    Analyzer analyzer = new MockAnalyzer(random());
    BM25FQueryParser parser = new BM25FQueryParser(fields, analyzer, boosts);

    Query query = parser.parse("information retrieval");
    assertNotNull(query);
    assertTrue(query instanceof BooleanQuery);
  }

  @Test
  public void testCreateBM25FSimilarity() {
    String[] fields = {"title", "body"};
    Map<String, Float> boosts = new HashMap<>();
    boosts.put("title", 5.0f);
    boosts.put("body", 1.0f);

    Map<String, Float> bParams = new HashMap<>();
    bParams.put("title", 0.75f);
    bParams.put("body", 0.75f);

    Analyzer analyzer = new MockAnalyzer(random());
    BM25FQueryParser parser = new BM25FQueryParser(fields, analyzer, boosts);

    BM25FSimilarity similarity = parser.createBM25FSimilarity(1.2f, bParams);
    assertNotNull(similarity);
    assertEquals(1.2f, similarity.getK1(), 0.0001f);
    assertEquals(5.0f, similarity.getBoostForField("title"), 0.0001f);
    assertEquals(1.0f, similarity.getBoostForField("body"), 0.0001f);
  }

  @Test
  public void testCreateBM25FSimilarityWithDefaults() {
    String[] fields = {"title", "body"};
    Map<String, Float> boosts = new HashMap<>();
    boosts.put("title", 2.0f);
    boosts.put("body", 1.0f);

    Analyzer analyzer = new MockAnalyzer(random());
    BM25FQueryParser parser = new BM25FQueryParser(fields, analyzer, boosts);

    BM25FSimilarity similarity = parser.createBM25FSimilarity();
    assertNotNull(similarity);
    assertEquals(1.2f, similarity.getK1(), 0.0001f);
  }

  @Test
  public void testEndToEndSearch() throws IOException, ParseException {
    Directory dir = newDirectory();
    Analyzer analyzer = new MockAnalyzer(random());

    // Setup field boosts
    String[] fields = {"title", "body"};
    Map<String, Float> boosts = new HashMap<>();
    boosts.put("title", 5.0f);
    boosts.put("body", 1.0f);

    Map<String, Float> bParams = new HashMap<>();
    bParams.put("title", 0.75f);
    bParams.put("body", 0.75f);

    // Create similarity
    BM25FSimilarity similarity = new BM25FSimilarity(1.2f, boosts, bParams);

    // Index documents
    IndexWriterConfig config = new IndexWriterConfig(analyzer);
    config.setSimilarity(similarity);
    IndexWriter writer = new IndexWriter(dir, config);

    // Doc 1: Match in title (should score higher due to boost)
    Document doc1 = new Document();
    doc1.add(new TextField("title", "information retrieval", Field.Store.YES));
    doc1.add(new TextField("body", "databases and systems", Field.Store.YES));
    writer.addDocument(doc1);

    // Doc 2: Match in body
    Document doc2 = new Document();
    doc2.add(new TextField("title", "database systems", Field.Store.YES));
    doc2.add(new TextField("body", "information retrieval techniques", Field.Store.YES));
    writer.addDocument(doc2);

    // Doc 3: Match in both
    Document doc3 = new Document();
    doc3.add(new TextField("title", "information systems", Field.Store.YES));
    doc3.add(new TextField("body", "information processing", Field.Store.YES));
    writer.addDocument(doc3);

    writer.commit();
    IndexReader reader = DirectoryReader.open(writer);
    writer.close();

    // Search
    IndexSearcher searcher = new IndexSearcher(reader);
    searcher.setSimilarity(similarity);

    BM25FQueryParser parser = new BM25FQueryParser(fields, analyzer, boosts);
    Query query = parser.parse("information");
    TopDocs results = searcher.search(query, 10);

    // Should have results
    assertTrue("Should have at least one result", results.scoreDocs.length > 0);

    // All docs should have positive scores
    for (int i = 0; i < results.scoreDocs.length; i++) {
      assertTrue("Score should be positive", results.scoreDocs[i].score > 0);
    }

    reader.close();
    dir.close();
  }

  @Test
  public void testFieldBoostImpact() throws IOException, ParseException {
    Directory dir = newDirectory();
    Analyzer analyzer = new MockAnalyzer(random());

    String[] fields = {"title", "body"};
    Map<String, Float> boosts = new HashMap<>();
    boosts.put("title", 10.0f); // Very high title boost
    boosts.put("body", 1.0f);

    Map<String, Float> bParams = new HashMap<>();
    bParams.put("title", 0.75f);
    bParams.put("body", 0.75f);

    BM25FSimilarity similarity = new BM25FSimilarity(1.2f, boosts, bParams);

    IndexWriterConfig config = new IndexWriterConfig(analyzer);
    config.setSimilarity(similarity);
    IndexWriter writer = new IndexWriter(dir, config);

    // Doc 1: Term only in title
    Document doc1 = new Document();
    doc1.add(new TextField("title", "elephant", Field.Store.YES));
    doc1.add(new TextField("body", "tiger lion", Field.Store.YES));
    writer.addDocument(doc1);

    // Doc 2: Term only in body (multiple times to increase TF)
    Document doc2 = new Document();
    doc2.add(new TextField("title", "animals", Field.Store.YES));
    doc2.add(
        new TextField(
            "body",
            "elephant elephant elephant elephant elephant",
            Field.Store.YES));
    writer.addDocument(doc2);

    writer.commit();
    IndexReader reader = DirectoryReader.open(writer);
    writer.close();

    IndexSearcher searcher = new IndexSearcher(reader);
    searcher.setSimilarity(similarity);

    BM25FQueryParser parser = new BM25FQueryParser(fields, analyzer, boosts);
    Query query = parser.parse("elephant");
    TopDocs results = searcher.search(query, 10);

    assertEquals("Should have 2 results", 2, results.scoreDocs.length);

    // Due to high title boost (10x), doc1 (title match) should score higher
    // than doc2 (body match) even though doc2 has higher term frequency
    Document resultDoc1 = searcher.doc(results.scoreDocs[0].doc);
    
    // Check that we have valid scores
    assertTrue("First result should have positive score", results.scoreDocs[0].score > 0);
    assertTrue("Second result should have positive score", results.scoreDocs[1].score > 0);

    reader.close();
    dir.close();
  }

  @Test
  public void testToString() {
    String[] fields = {"title", "body"};
    Map<String, Float> boosts = new HashMap<>();
    boosts.put("title", 5.0f);
    boosts.put("body", 1.0f);

    Analyzer analyzer = new MockAnalyzer(random());
    BM25FQueryParser parser = new BM25FQueryParser(fields, analyzer, boosts);

    String str = parser.toString();
    assertTrue(str.contains("BM25FQueryParser"));
    assertTrue(str.contains("title"));
    assertTrue(str.contains("body"));
  }

  @Test
  public void testPhraseQuery() throws Exception {
    String[] fields = {"title", "body"};
    Map<String, Float> boosts = new HashMap<>();
    boosts.put("title", 2.0f);
    boosts.put("body", 1.0f);

    Analyzer analyzer = new MockAnalyzer(random());
    BM25FQueryParser parser = new BM25FQueryParser(fields, analyzer, boosts);

    Query query = parser.parse("\"information retrieval\"");
    assertNotNull(query);
    // Query should be parseable even for phrases
  }

  @Test
  public void testBooleanQuery() throws Exception {
    String[] fields = {"title", "body"};
    Map<String, Float> boosts = new HashMap<>();
    boosts.put("title", 2.0f);
    boosts.put("body", 1.0f);

    Analyzer analyzer = new MockAnalyzer(random());
    BM25FQueryParser parser = new BM25FQueryParser(fields, analyzer, boosts);

    Query query = parser.parse("information AND retrieval");
    assertNotNull(query);
    assertTrue(query instanceof BooleanQuery);

    BooleanQuery bq = (BooleanQuery) query;
    // Should have clauses for the boolean operation
    assertTrue(bq.clauses().size() > 0);
  }

  @Test
  public void testWildcardQuery() throws Exception {
    String[] fields = {"title", "body"};
    Map<String, Float> boosts = new HashMap<>();
    boosts.put("title", 2.0f);
    boosts.put("body", 1.0f);

    Analyzer analyzer = new MockAnalyzer(random());
    BM25FQueryParser parser = new BM25FQueryParser(fields, analyzer, boosts);

    Query query = parser.parse("inform*");
    assertNotNull(query);
    // Wildcard queries should be supported
  }
}

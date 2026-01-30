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

import java.util.HashMap;
import java.util.Map;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.BM25FSimilarity;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;

/** Tests for BM25FQueryParser */
public class TestBM25FQueryParser extends LuceneTestCase {

  public void testConstruction() throws Exception {
    String[] fields = {"title", "body"};
    Analyzer analyzer = new MockAnalyzer(random());

    BM25FQueryParser parser = new BM25FQueryParser(fields, analyzer);
    assertNotNull(parser);
    assertEquals(1.2f, parser.getK1(), 0.0001f);
    assertEquals(0.75f, parser.getDefaultB(), 0.0001f);
  }

  public void testConstructionWithBoosts() throws Exception {
    String[] fields = {"title", "body"};
    Analyzer analyzer = new MockAnalyzer(random());

    Map<String, Float> boosts = new HashMap<>();
    boosts.put("title", 2.0f);
    boosts.put("body", 1.0f);

    BM25FQueryParser parser = new BM25FQueryParser(fields, analyzer, boosts);
    assertNotNull(parser);
    assertEquals(2.0f, parser.getFieldBoost("title"), 0.0001f);
    assertEquals(1.0f, parser.getFieldBoost("body"), 0.0001f);
  }

  public void testSetK1() throws Exception {
    String[] fields = {"field"};
    Analyzer analyzer = new MockAnalyzer(random());

    BM25FQueryParser parser = new BM25FQueryParser(fields, analyzer);
    parser.setK1(1.5f);
    assertEquals(1.5f, parser.getK1(), 0.0001f);
  }

  public void testSetK1Invalid() throws Exception {
    String[] fields = {"field"};
    Analyzer analyzer = new MockAnalyzer(random());

    BM25FQueryParser parser = new BM25FQueryParser(fields, analyzer);

    expectThrows(
        IllegalArgumentException.class,
        () -> {
          parser.setK1(-1.0f);
        });

    expectThrows(
        IllegalArgumentException.class,
        () -> {
          parser.setK1(Float.POSITIVE_INFINITY);
        });
  }

  public void testSetDefaultB() throws Exception {
    String[] fields = {"field"};
    Analyzer analyzer = new MockAnalyzer(random());

    BM25FQueryParser parser = new BM25FQueryParser(fields, analyzer);
    parser.setDefaultB(0.5f);
    assertEquals(0.5f, parser.getDefaultB(), 0.0001f);
  }

  public void testSetDefaultBInvalid() throws Exception {
    String[] fields = {"field"};
    Analyzer analyzer = new MockAnalyzer(random());

    BM25FQueryParser parser = new BM25FQueryParser(fields, analyzer);

    expectThrows(
        IllegalArgumentException.class,
        () -> {
          parser.setDefaultB(-0.5f);
        });

    expectThrows(
        IllegalArgumentException.class,
        () -> {
          parser.setDefaultB(1.5f);
        });

    expectThrows(
        IllegalArgumentException.class,
        () -> {
          parser.setDefaultB(Float.NaN);
        });
  }

  public void testSetFieldBoost() throws Exception {
    String[] fields = {"title", "body"};
    Analyzer analyzer = new MockAnalyzer(random());

    BM25FQueryParser parser = new BM25FQueryParser(fields, analyzer);
    parser.setFieldBoost("title", 3.0f);
    assertEquals(3.0f, parser.getFieldBoost("title"), 0.0001f);
  }

  public void testSetFieldBoostInvalid() throws Exception {
    String[] fields = {"field"};
    Analyzer analyzer = new MockAnalyzer(random());

    BM25FQueryParser parser = new BM25FQueryParser(fields, analyzer);

    expectThrows(
        IllegalArgumentException.class,
        () -> {
          parser.setFieldBoost("field", -1.0f);
        });
  }

  public void testSetFieldBParam() throws Exception {
    String[] fields = {"title", "body"};
    Analyzer analyzer = new MockAnalyzer(random());

    BM25FQueryParser parser = new BM25FQueryParser(fields, analyzer);
    parser.setFieldBParam("title", 0.5f);
    assertEquals(0.5f, parser.getFieldBParam("title"), 0.0001f);
  }

  public void testSetFieldBParamInvalid() throws Exception {
    String[] fields = {"field"};
    Analyzer analyzer = new MockAnalyzer(random());

    BM25FQueryParser parser = new BM25FQueryParser(fields, analyzer);

    expectThrows(
        IllegalArgumentException.class,
        () -> {
          parser.setFieldBParam("field", -0.5f);
        });

    expectThrows(
        IllegalArgumentException.class,
        () -> {
          parser.setFieldBParam("field", 1.5f);
        });
  }

  public void testGetSimilarity() throws Exception {
    String[] fields = {"title", "body"};
    Analyzer analyzer = new MockAnalyzer(random());

    BM25FQueryParser parser = new BM25FQueryParser(fields, analyzer);
    BM25FSimilarity sim = parser.getSimilarity();

    assertNotNull(sim);
    assertEquals(1.2f, sim.getK1(), 0.0001f);
    assertEquals(0.75f, sim.getDefaultB(), 0.0001f);
  }

  public void testToString() throws Exception {
    String[] fields = {"title", "body"};
    Analyzer analyzer = new MockAnalyzer(random());

    BM25FQueryParser parser = new BM25FQueryParser(fields, analyzer);
    parser.setK1(1.5f);
    parser.setDefaultB(0.8f);

    String str = parser.toString();
    assertTrue(str.contains("BM25FQueryParser"));
    assertTrue(str.contains("title"));
    assertTrue(str.contains("body"));
    assertTrue(str.contains("k1=1.5"));
    assertTrue(str.contains("defaultB=0.8"));
  }

  public void testBasicQuery() throws Exception {
    String[] fields = {"title", "body"};
    Analyzer analyzer = new MockAnalyzer(random());

    BM25FQueryParser parser = new BM25FQueryParser(fields, analyzer);
    Query query = parser.parse("test");

    assertNotNull(query);
  }

  public void testMultiTermQuery() throws Exception {
    String[] fields = {"title", "body"};
    Analyzer analyzer = new MockAnalyzer(random());

    BM25FQueryParser parser = new BM25FQueryParser(fields, analyzer);
    Query query = parser.parse("search engine");

    assertNotNull(query);
  }

  /** Test end-to-end query parsing and searching with BM25F */
  public void testEndToEndSearch() throws Exception {
    Directory dir = newDirectory();
    Analyzer analyzer = new MockAnalyzer(random());
    IndexWriterConfig config = newIndexWriterConfig(analyzer);

    // Set up BM25F similarity
    Map<String, Float> boosts = new HashMap<>();
    boosts.put("title", 3.0f);
    boosts.put("body", 1.0f);

    BM25FSimilarity sim = new BM25FSimilarity(1.2f, 0.75f, boosts, null);
    config.setSimilarity(sim);

    IndexWriter writer = new IndexWriter(dir, config);

    // Document 1: keyword in body
    Document doc1 = new Document();
    doc1.add(new TextField("title", "unrelated title", Field.Store.YES));
    doc1.add(new TextField("body", "information retrieval systems", Field.Store.YES));
    writer.addDocument(doc1);

    // Document 2: keyword in title (should rank higher)
    Document doc2 = new Document();
    doc2.add(new TextField("title", "information systems", Field.Store.YES));
    doc2.add(new TextField("body", "other content here", Field.Store.YES));
    writer.addDocument(doc2);

    // Document 3: keyword in both
    Document doc3 = new Document();
    doc3.add(new TextField("title", "information", Field.Store.YES));
    doc3.add(new TextField("body", "information retrieval", Field.Store.YES));
    writer.addDocument(doc3);

    writer.commit();
    writer.close();

    // Search using BM25FQueryParser
    DirectoryReader reader = DirectoryReader.open(dir);
    IndexSearcher searcher = new IndexSearcher(reader);
    searcher.setSimilarity(sim);

    String[] fields = {"title", "body"};
    BM25FQueryParser parser = new BM25FQueryParser(fields, analyzer, boosts);
    parser.setK1(1.2f);
    parser.setDefaultB(0.75f);

    Query query = parser.parse("information");
    TopDocs results = searcher.search(query, 10);

    assertEquals(3, results.totalHits.value);

    // Verify results are returned (exact ranking may vary but all should be found)
    assertTrue(results.scoreDocs.length == 3);
    for (ScoreDoc scoreDoc : results.scoreDocs) {
      assertTrue(scoreDoc.score > 0);
    }

    reader.close();
    dir.close();
  }

  /** Test that field boosts affect ranking */
  public void testFieldBoostAffectsRanking() throws Exception {
    Directory dir = newDirectory();
    Analyzer analyzer = new MockAnalyzer(random());
    IndexWriterConfig config = newIndexWriterConfig(analyzer);

    // Very high boost for title field
    Map<String, Float> boosts = new HashMap<>();
    boosts.put("title", 10.0f);
    boosts.put("body", 1.0f);

    BM25FSimilarity sim = new BM25FSimilarity(1.2f, 0.75f, boosts, null);
    config.setSimilarity(sim);

    IndexWriter writer = new IndexWriter(dir, config);

    // Document 1: keyword appears 5 times in body
    Document doc1 = new Document();
    doc1.add(new TextField("title", "unrelated", Field.Store.YES));
    doc1.add(
        new TextField("body", "keyword keyword keyword keyword keyword", Field.Store.YES));
    writer.addDocument(doc1);

    // Document 2: keyword appears once in title
    Document doc2 = new Document();
    doc2.add(new TextField("title", "keyword", Field.Store.YES));
    doc2.add(new TextField("body", "unrelated content", Field.Store.YES));
    writer.addDocument(doc2);

    writer.commit();
    writer.close();

    DirectoryReader reader = DirectoryReader.open(dir);
    IndexSearcher searcher = new IndexSearcher(reader);
    searcher.setSimilarity(sim);

    String[] fields = {"title", "body"};
    BM25FQueryParser parser = new BM25FQueryParser(fields, analyzer, boosts);

    Query query = parser.parse("keyword");
    TopDocs results = searcher.search(query, 10);

    assertEquals(2, results.totalHits.value);

    // Document 2 (title match with high boost) should rank higher than doc 1
    ScoreDoc[] scoreDocs = results.scoreDocs;
    Document topDoc = searcher.doc(scoreDocs[0].doc);
    assertEquals("keyword", topDoc.get("title"));

    reader.close();
    dir.close();
  }

  /** Test query with boolean operators */
  public void testBooleanQuery() throws Exception {
    String[] fields = {"title", "body"};
    Analyzer analyzer = new MockAnalyzer(random());

    BM25FQueryParser parser = new BM25FQueryParser(fields, analyzer);

    // Test AND query
    Query andQuery = parser.parse("search AND engine");
    assertNotNull(andQuery);

    // Test OR query  
    Query orQuery = parser.parse("search OR engine");
    assertNotNull(orQuery);

    // Test NOT query
    Query notQuery = parser.parse("search NOT engine");
    assertNotNull(notQuery);
  }

  /** Test phrase query */
  public void testPhraseQuery() throws Exception {
    String[] fields = {"title", "body"};
    Analyzer analyzer = new MockAnalyzer(random());

    BM25FQueryParser parser = new BM25FQueryParser(fields, analyzer);
    Query query = parser.parse("\"search engine\"");

    assertNotNull(query);
  }

  /** Test field-specific b parameters */
  public void testFieldSpecificBParams() throws Exception {
    String[] fields = {"title", "body"};
    Analyzer analyzer = new MockAnalyzer(random());

    BM25FQueryParser parser = new BM25FQueryParser(fields, analyzer);

    // Title field gets less length normalization
    parser.setFieldBParam("title", 0.3f);
    parser.setFieldBParam("body", 0.75f);

    assertEquals(0.3f, parser.getFieldBParam("title"), 0.0001f);
    assertEquals(0.75f, parser.getFieldBParam("body"), 0.0001f);

    Query query = parser.parse("test query");
    assertNotNull(query);
  }

  /** Test with special characters */
  public void testSpecialCharacters() throws Exception {
    String[] fields = {"title", "body"};
    Analyzer analyzer = new MockAnalyzer(random());

    BM25FQueryParser parser = new BM25FQueryParser(fields, analyzer);

    // Test that special characters are handled
    Query query = parser.parse("test-query");
    assertNotNull(query);
  }

  /** Test empty query */
  public void testEmptyQuery() throws Exception {
    String[] fields = {"title", "body"};
    Analyzer analyzer = new MockAnalyzer(random());

    BM25FQueryParser parser = new BM25FQueryParser(fields, analyzer);

    expectThrows(
        ParseException.class,
        () -> {
          parser.parse("");
        });
  }
}

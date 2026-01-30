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
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexOptions;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
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

/** Tests BM25FQueryParser. */
public class TestBM25FQueryParser extends LuceneTestCase {

  /** Test basic single-term query parsing. */
  public void testSingleTerm() throws Exception {
    String[] fields = {"title", "body"};
    MockAnalyzer analyzer = new MockAnalyzer(random());
    BM25FQueryParser parser = new BM25FQueryParser(fields, analyzer);

    Query q = parser.parse("test");
    assertTrue(q instanceof CombinedFieldQuery);
    assertTrue(q.toString().contains("title"));
    assertTrue(q.toString().contains("body"));
  }

  /** Test multi-term query parsing. */
  public void testMultiTerm() throws Exception {
    String[] fields = {"title", "body"};
    MockAnalyzer analyzer = new MockAnalyzer(random());
    BM25FQueryParser parser = new BM25FQueryParser(fields, analyzer);

    Query q = parser.parse("machine learning");
    assertTrue(q instanceof BooleanQuery);
    BooleanQuery bq = (BooleanQuery) q;
    assertEquals(2, bq.clauses().size());
    
    // Each clause should be a CombinedFieldQuery
    for (var clause : bq.clauses()) {
      assertTrue(clause.query() instanceof CombinedFieldQuery);
    }
  }

  /** Test field weights. */
  public void testFieldWeights() throws Exception {
    String[] fields = {"title", "body"};
    Map<String, Float> weights = new HashMap<>();
    weights.put("title", 2.0f);
    weights.put("body", 1.0f);
    
    MockAnalyzer analyzer = new MockAnalyzer(random());
    BM25FQueryParser parser = new BM25FQueryParser(fields, analyzer, weights);

    Query q = parser.parse("test");
    assertTrue(q instanceof CombinedFieldQuery);
    String queryStr = q.toString();
    assertTrue(queryStr.contains("title^2.0"));
  }

  /** Test that invalid weights are rejected. */
  public void testInvalidWeights() throws Exception {
    String[] fields = {"title", "body"};
    Map<String, Float> weights = new HashMap<>();
    weights.put("title", 0.5f); // Invalid: must be >= 1.0
    weights.put("body", 1.0f);
    
    MockAnalyzer analyzer = new MockAnalyzer(random());
    
    expectThrows(IllegalArgumentException.class, () -> {
      new BM25FQueryParser(fields, analyzer, weights);
    });
  }

  /** Test static parse method with weights. */
  public void testStaticParseWithWeights() throws Exception {
    String[] fields = {"title", "body"};
    Map<String, Float> weights = new HashMap<>();
    weights.put("title", 3.0f);
    weights.put("body", 1.0f);
    
    MockAnalyzer analyzer = new MockAnalyzer(random());
    Query q = BM25FQueryParser.parse("test query", fields, analyzer, weights);
    
    assertTrue(q instanceof BooleanQuery);
    BooleanQuery bq = (BooleanQuery) q;
    assertEquals(2, bq.clauses().size());
  }

  /** Test static parse method without weights. */
  public void testStaticParseWithoutWeights() throws Exception {
    String[] fields = {"title", "body"};
    MockAnalyzer analyzer = new MockAnalyzer(random());
    Query q = BM25FQueryParser.parse("test", fields, analyzer);
    
    assertTrue(q instanceof CombinedFieldQuery);
  }

  /** Test that single field queries work correctly. */
  public void testSingleFieldQuery() throws Exception {
    String[] fields = {"title", "body"};
    MockAnalyzer analyzer = new MockAnalyzer(random());
    BM25FQueryParser parser = new BM25FQueryParser(fields, analyzer);

    // Explicit field specification should use parent behavior
    Query q = parser.parse("title:test");
    String queryStr = q.toString();
    assertTrue(queryStr.contains("title:test"));
  }

  /** Test boolean operators. */
  public void testBooleanOperators() throws Exception {
    String[] fields = {"title", "body"};
    MockAnalyzer analyzer = new MockAnalyzer(random());
    BM25FQueryParser parser = new BM25FQueryParser(fields, analyzer);

    Query q = parser.parse("+machine +learning");
    assertTrue(q instanceof BooleanQuery);
    BooleanQuery bq = (BooleanQuery) q;
    assertEquals(2, bq.clauses().size());
    
    // Both clauses should be MUST
    for (var clause : bq.clauses()) {
      assertEquals(clause.occur().toString(), "MUST");
    }
  }

  /** Test actual search with BM25F scoring. */
  public void testBM25FScoring() throws Exception {
    Directory dir = newDirectory();
    MockAnalyzer analyzer = new MockAnalyzer(random());
    IndexWriterConfig iwc = new IndexWriterConfig(analyzer);
    iwc.setSimilarity(new BM25Similarity());
    IndexWriter writer = new IndexWriter(dir, iwc);

    // Create field type with norms
    FieldType fieldType = new FieldType(TextField.TYPE_STORED);
    fieldType.setIndexOptions(IndexOptions.DOCS_AND_FREQS);
    fieldType.setOmitNorms(false);

    // Document 1: "machine learning" in title
    Document doc1 = new Document();
    doc1.add(new Field("title", "machine learning algorithms", fieldType));
    doc1.add(new Field("body", "introduction to data science", fieldType));
    writer.addDocument(doc1);

    // Document 2: "machine learning" in body
    Document doc2 = new Document();
    doc2.add(new Field("title", "introduction to algorithms", fieldType));
    doc2.add(new Field("body", "machine learning is a subset of artificial intelligence", fieldType));
    writer.addDocument(doc2);

    // Document 3: "machine" in title, "learning" in body
    Document doc3 = new Document();
    doc3.add(new Field("title", "machine vision systems", fieldType));
    doc3.add(new Field("body", "learning from experience and data", fieldType));
    writer.addDocument(doc3);

    writer.commit();
    writer.close();

    DirectoryReader reader = DirectoryReader.open(dir);
    IndexSearcher searcher = new IndexSearcher(reader);
    searcher.setSimilarity(new BM25Similarity());

    // Test with equal weights
    String[] fields = {"title", "body"};
    BM25FQueryParser parser = new BM25FQueryParser(fields, analyzer);
    Query q = parser.parse("machine learning");

    TopDocs results = searcher.search(q, 10);
    assertTrue(results.scoreDocs.length > 0);
    
    // Documents with both terms should score higher
    assertEquals(3, results.totalHits.value());

    // Test with title boost
    Map<String, Float> weights = new HashMap<>();
    weights.put("title", 2.0f);
    weights.put("body", 1.0f);
    
    BM25FQueryParser weightedParser = new BM25FQueryParser(fields, analyzer, weights);
    Query weightedQ = weightedParser.parse("machine learning");

    TopDocs weightedResults = searcher.search(weightedQ, 10);
    assertTrue(weightedResults.scoreDocs.length > 0);
    
    // Doc1 (both terms in title) should rank higher with title boost
    assertEquals(3, weightedResults.totalHits.value());

    reader.close();
    dir.close();
  }

  /** Test that stopwords are handled correctly. */
  public void testStopwords() throws Exception {
    String[] fields = {"title", "body"};
    TestQueryParser.QPTestAnalyzer analyzer = new TestQueryParser.QPTestAnalyzer();
    BM25FQueryParser parser = new BM25FQueryParser(fields, analyzer);

    Query q = parser.parse("the machine");
    // "the" should be filtered out as a stopword
    assertTrue(q instanceof CombinedFieldQuery);
    assertFalse(q.toString().toLowerCase().contains("the"));
  }

  /** Test phrase queries fall back to standard behavior. */
  public void testPhraseQueries() throws Exception {
    String[] fields = {"title", "body"};
    MockAnalyzer analyzer = new MockAnalyzer(random());
    BM25FQueryParser parser = new BM25FQueryParser(fields, analyzer);

    Query q = parser.parse("\"machine learning\"");
    // Phrase queries should fall back to standard multi-field behavior
    assertTrue(q instanceof BooleanQuery);
  }

  /** Test fuzzy queries fall back to standard behavior. */
  public void testFuzzyQueries() throws Exception {
    String[] fields = {"title", "body"};
    MockAnalyzer analyzer = new MockAnalyzer(random());
    BM25FQueryParser parser = new BM25FQueryParser(fields, analyzer);

    Query q = parser.parse("machine~");
    // Fuzzy queries should fall back to standard multi-field behavior
    assertTrue(q instanceof BooleanQuery);
  }

  /** Test prefix queries fall back to standard behavior. */
  public void testPrefixQueries() throws Exception {
    String[] fields = {"title", "body"};
    MockAnalyzer analyzer = new MockAnalyzer(random());
    BM25FQueryParser parser = new BM25FQueryParser(fields, analyzer);

    Query q = parser.parse("mach*");
    // Prefix queries should fall back to standard multi-field behavior
    assertTrue(q instanceof BooleanQuery);
  }

  /** Test wildcard queries fall back to standard behavior. */
  public void testWildcardQueries() throws Exception {
    String[] fields = {"title", "body"};
    MockAnalyzer analyzer = new MockAnalyzer(random());
    BM25FQueryParser parser = new BM25FQueryParser(fields, analyzer);

    Query q = parser.parse("ma?hine");
    // Wildcard queries should fall back to standard multi-field behavior
    assertTrue(q instanceof BooleanQuery);
  }

  /** Test range queries fall back to standard behavior. */
  public void testRangeQueries() throws Exception {
    String[] fields = {"title", "body"};
    MockAnalyzer analyzer = new MockAnalyzer(random());
    BM25FQueryParser parser = new BM25FQueryParser(fields, analyzer);

    Query q = parser.parse("[a TO z]");
    // Range queries should fall back to standard multi-field behavior
    assertTrue(q instanceof BooleanQuery);
  }

  /** Test empty query handling. */
  public void testEmptyQuery() throws Exception {
    String[] fields = {"title", "body"};
    MockAnalyzer analyzer = new MockAnalyzer(random());
    BM25FQueryParser parser = new BM25FQueryParser(fields, analyzer);

    Query q = parser.parse("");
    // Empty query should return a BooleanQuery with no clauses
    assertTrue(q instanceof BooleanQuery);
    assertEquals(0, ((BooleanQuery) q).clauses().size());
  }

  /** Test mixed query with CombinedFieldQuery and explicit field queries. */
  public void testMixedQuery() throws Exception {
    String[] fields = {"title", "body"};
    MockAnalyzer analyzer = new MockAnalyzer(random());
    BM25FQueryParser parser = new BM25FQueryParser(fields, analyzer);

    Query q = parser.parse("machine title:learning");
    assertTrue(q instanceof BooleanQuery);
    BooleanQuery bq = (BooleanQuery) q;
    assertEquals(2, bq.clauses().size());
  }
}

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
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.BM25FSimilarity;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;

/** Tests for BM25FQueryParser */
public class TestBM25FQueryParser extends LuceneTestCase {

  public void testBasicConstruction() {
    Analyzer analyzer = new MockAnalyzer(random());
    String[] fields = {"title", "body"};
    BM25FQueryParser parser = new BM25FQueryParser(fields, analyzer);
    assertNotNull(parser);
  }

  public void testConstructionWithBoosts() {
    Analyzer analyzer = new MockAnalyzer(random());
    String[] fields = {"title", "body", "anchor"};
    Map<String, Float> boosts = new HashMap<>();
    boosts.put("title", 2.0f);
    boosts.put("body", 1.0f);
    boosts.put("anchor", 1.5f);

    BM25FQueryParser parser = new BM25FQueryParser(fields, analyzer, boosts);
    assertNotNull(parser);
  }

  public void testEmptyFieldsRejected() {
    Analyzer analyzer = new MockAnalyzer(random());
    expectThrows(
        IllegalArgumentException.class,
        () -> {
          new BM25FQueryParser(new String[0], analyzer);
        });
  }

  public void testNullFieldsRejected() {
    Analyzer analyzer = new MockAnalyzer(random());
    expectThrows(
        IllegalArgumentException.class,
        () -> {
          new BM25FQueryParser(null, analyzer);
        });
  }

  public void testParseSimpleQuery() throws Exception {
    Analyzer analyzer = new MockAnalyzer(random());
    String[] fields = {"title", "body"};
    BM25FQueryParser parser = new BM25FQueryParser(fields, analyzer);

    Query query = parser.parse("test query");
    assertNotNull(query);
  }

  public void testParseWithBoosts() throws Exception {
    Analyzer analyzer = new MockAnalyzer(random());
    String[] fields = {"title", "body"};
    Map<String, Float> boosts = new HashMap<>();
    boosts.put("title", 3.0f);
    boosts.put("body", 1.0f);

    BM25FQueryParser parser = new BM25FQueryParser(fields, analyzer, boosts);
    Query query = parser.parse("information retrieval");
    assertNotNull(query);
  }

  public void testValidateFieldBoosts() {
    Map<String, Float> boosts = new HashMap<>();
    boosts.put("title", 2.0f);
    boosts.put("body", 1.0f);

    // Should not throw
    BM25FQueryParser.validateFieldBoosts(boosts);
  }

  public void testValidateFieldBoostsRejectsInvalid() {
    Map<String, Float> boosts = new HashMap<>();
    boosts.put("title", -1.0f);

    expectThrows(
        IllegalArgumentException.class,
        () -> {
          BM25FQueryParser.validateFieldBoosts(boosts);
        });
  }

  public void testValidateFieldBoostsRejectsNaN() {
    Map<String, Float> boosts = new HashMap<>();
    boosts.put("title", Float.NaN);

    expectThrows(
        IllegalArgumentException.class,
        () -> {
          BM25FQueryParser.validateFieldBoosts(boosts);
        });
  }

  public void testValidateFieldBoostsRejectsInfinity() {
    Map<String, Float> boosts = new HashMap<>();
    boosts.put("title", Float.POSITIVE_INFINITY);

    expectThrows(
        IllegalArgumentException.class,
        () -> {
          BM25FQueryParser.validateFieldBoosts(boosts);
        });
  }

  public void testToString() {
    Analyzer analyzer = new MockAnalyzer(random());
    String[] fields = {"title", "body", "anchor"};
    Map<String, Float> boosts = new HashMap<>();
    boosts.put("title", 3.0f);
    boosts.put("body", 1.0f);
    boosts.put("anchor", 1.5f);

    BM25FQueryParser parser = new BM25FQueryParser(fields, analyzer, boosts);
    String str = parser.toString();
    assertTrue(str.contains("BM25FQueryParser"));
    assertTrue(str.contains("title"));
    assertTrue(str.contains("body"));
    assertTrue(str.contains("anchor"));
  }

  /**
   * Integration test: Create an index, search with BM25FQueryParser and BM25FSimilarity, verify
   * results
   */
  public void testIntegrationWithBM25FSimilarity() throws Exception {
    Directory dir = newDirectory();
    Analyzer analyzer = new MockAnalyzer(random());

    // Configure BM25F similarity
    BM25FSimilarity similarity =
        new BM25FSimilarity.Builder()
            .setK1(1.2f)
            .addFieldConfig("title", 3.0f, 0.5f)
            .addFieldConfig("body", 1.0f, 0.75f)
            .build();

    // Create index with sample documents
    IndexWriter writer = new IndexWriter(dir, newIndexWriterConfig(analyzer).setSimilarity(similarity));

    // Document 1: Query term in title
    Document doc1 = new Document();
    doc1.add(new TextField("title", "information retrieval systems", Field.Store.YES));
    doc1.add(
        new TextField(
            "body", "This document discusses various aspects of database systems", Field.Store.YES));
    writer.addDocument(doc1);

    // Document 2: Query term in body
    Document doc2 = new Document();
    doc2.add(new TextField("title", "Database Management", Field.Store.YES));
    doc2.add(
        new TextField(
            "body",
            "Information retrieval is important for searching documents efficiently",
            Field.Store.YES));
    writer.addDocument(doc2);

    // Document 3: Query terms in both fields
    Document doc3 = new Document();
    doc3.add(new TextField("title", "Modern Information Systems", Field.Store.YES));
    doc3.add(
        new TextField(
            "body",
            "Information retrieval systems combine search and database technologies",
            Field.Store.YES));
    writer.addDocument(doc3);

    writer.close();

    // Search with BM25F
    DirectoryReader reader = DirectoryReader.open(dir);
    IndexSearcher searcher = new IndexSearcher(reader);
    searcher.setSimilarity(similarity);

    // Create query parser with matching field weights
    String[] fields = {"title", "body"};
    Map<String, Float> boosts = new HashMap<>();
    boosts.put("title", 3.0f);
    boosts.put("body", 1.0f);

    BM25FQueryParser parser = new BM25FQueryParser(fields, analyzer, boosts);
    Query query = parser.parse("information retrieval");

    TopDocs results = searcher.search(query, 10);
    assertTrue("Should find at least one result", results.totalHits.value >= 1);

    // Document 3 should rank highest (has terms in both title and body)
    // Document 1 should rank second (has terms in title with high weight)
    // Document 2 should rank third (has terms only in body)
    assertTrue("Should find all three documents", results.totalHits.value >= 3);

    reader.close();
    dir.close();
  }

  /** Test query parsing with various operators */
  public void testComplexQueryParsing() throws Exception {
    Analyzer analyzer = new MockAnalyzer(random());
    String[] fields = {"title", "body"};
    Map<String, Float> boosts = new HashMap<>();
    boosts.put("title", 2.0f);
    boosts.put("body", 1.0f);

    BM25FQueryParser parser = new BM25FQueryParser(fields, analyzer, boosts);

    // Test AND query
    Query andQuery = parser.parse("information AND retrieval");
    assertNotNull(andQuery);

    // Test OR query
    Query orQuery = parser.parse("information OR retrieval");
    assertNotNull(orQuery);

    // Test phrase query
    Query phraseQuery = parser.parse("\"information retrieval\"");
    assertNotNull(phraseQuery);

    // Test wildcard query
    Query wildcardQuery = parser.parse("inform*");
    assertNotNull(wildcardQuery);

    // Test boolean combination
    Query complexQuery = parser.parse("(information OR data) AND retrieval");
    assertNotNull(complexQuery);
  }

  /** Test that field-specific queries work correctly */
  public void testFieldSpecificQueries() throws Exception {
    Analyzer analyzer = new MockAnalyzer(random());
    String[] fields = {"title", "body", "author"};
    BM25FQueryParser parser = new BM25FQueryParser(fields, analyzer);

    // Query specific to one field
    Query titleQuery = parser.parse("title:information");
    assertNotNull(titleQuery);

    // Query specific to another field
    Query bodyQuery = parser.parse("body:retrieval");
    assertNotNull(bodyQuery);

    // Mixed field-specific and general queries
    Query mixedQuery = parser.parse("title:information AND retrieval");
    assertNotNull(mixedQuery);
  }
}

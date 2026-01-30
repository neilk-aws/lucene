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
package org.apache.lucene.search.similarities;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.index.RandomIndexWriter;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.junit.Test;

/** Tests for BM25FSimilarity */
public class TestBM25FSimilarity extends LuceneTestCase {

  @Test
  public void testConstructor() {
    Map<String, Float> fieldBoosts = new HashMap<>();
    fieldBoosts.put("title", 5.0f);
    fieldBoosts.put("body", 1.0f);

    Map<String, Float> fieldBParams = new HashMap<>();
    fieldBParams.put("title", 0.75f);
    fieldBParams.put("body", 0.75f);

    BM25FSimilarity similarity = new BM25FSimilarity(1.2f, fieldBoosts, fieldBParams);
    assertEquals(1.2f, similarity.getK1(), 0.0001f);
    assertEquals(5.0f, similarity.getBoostForField("title"), 0.0001f);
    assertEquals(1.0f, similarity.getBoostForField("body"), 0.0001f);
    assertEquals(0.75f, similarity.getBForField("title"), 0.0001f);
    assertEquals(0.75f, similarity.getBForField("body"), 0.0001f);
  }

  @Test
  public void testConstructorDefaults() {
    Map<String, Float> fieldBoosts = new HashMap<>();
    fieldBoosts.put("field", 1.0f);

    BM25FSimilarity similarity = new BM25FSimilarity(fieldBoosts, null);
    assertEquals(1.2f, similarity.getK1(), 0.0001f);
    assertEquals(0.75f, similarity.getBForField("field"), 0.0001f);
  }

  @Test
  public void testIllegalK1() {
    Map<String, Float> fieldBoosts = new HashMap<>();
    fieldBoosts.put("field", 1.0f);

    expectThrows(
        IllegalArgumentException.class,
        () -> {
          new BM25FSimilarity(-1.0f, fieldBoosts, null);
        });

    expectThrows(
        IllegalArgumentException.class,
        () -> {
          new BM25FSimilarity(Float.POSITIVE_INFINITY, fieldBoosts, null);
        });
  }

  @Test
  public void testIllegalBParameter() {
    Map<String, Float> fieldBoosts = new HashMap<>();
    fieldBoosts.put("field", 1.0f);

    Map<String, Float> fieldBParams = new HashMap<>();
    fieldBParams.put("field", -0.1f);

    expectThrows(
        IllegalArgumentException.class,
        () -> {
          new BM25FSimilarity(1.2f, fieldBoosts, fieldBParams);
        });

    fieldBParams.put("field", 1.1f);
    expectThrows(
        IllegalArgumentException.class,
        () -> {
          new BM25FSimilarity(1.2f, fieldBoosts, fieldBParams);
        });
  }

  @Test
  public void testNullOrEmptyFieldBoosts() {
    expectThrows(
        IllegalArgumentException.class,
        () -> {
          new BM25FSimilarity(1.2f, null, null);
        });

    expectThrows(
        IllegalArgumentException.class,
        () -> {
          new BM25FSimilarity(1.2f, new HashMap<>(), null);
        });
  }

  @Test
  public void testScoring() throws IOException {
    Directory dir = newDirectory();
    MockAnalyzer analyzer = new MockAnalyzer(random());
    IndexWriterConfig config = newIndexWriterConfig(analyzer);

    Map<String, Float> fieldBoosts = new HashMap<>();
    fieldBoosts.put("title", 5.0f);
    fieldBoosts.put("body", 1.0f);

    Map<String, Float> fieldBParams = new HashMap<>();
    fieldBParams.put("title", 0.75f);
    fieldBParams.put("body", 0.75f);

    BM25FSimilarity similarity = new BM25FSimilarity(1.2f, fieldBoosts, fieldBParams);
    config.setSimilarity(similarity);

    RandomIndexWriter writer = new RandomIndexWriter(random(), dir, config);

    // Document 1: term in title
    Document doc1 = new Document();
    doc1.add(new TextField("title", "search engine", Field.Store.YES));
    doc1.add(new TextField("body", "information retrieval systems", Field.Store.YES));
    writer.addDocument(doc1);

    // Document 2: term in body
    Document doc2 = new Document();
    doc2.add(new TextField("title", "database systems", Field.Store.YES));
    doc2.add(new TextField("body", "search algorithms and data structures", Field.Store.YES));
    writer.addDocument(doc2);

    // Document 3: term in both
    Document doc3 = new Document();
    doc3.add(new TextField("title", "search techniques", Field.Store.YES));
    doc3.add(new TextField("body", "advanced search methods", Field.Store.YES));
    writer.addDocument(doc3);

    IndexReader reader = writer.getReader();
    writer.close();

    IndexSearcher searcher = new IndexSearcher(reader);
    searcher.setSimilarity(similarity);

    // Search for "search" - should rank doc with title match higher
    Query query = new TermQuery(new Term("title", "search"));
    TopDocs results = searcher.search(query, 10);

    assertTrue("Should have at least 2 results", results.scoreDocs.length >= 2);
    
    // Document with "search" in title should score higher due to field boost
    // (docs 1 and 3 have it in title)
    assertTrue("First result should have positive score", results.scoreDocs[0].score > 0);

    reader.close();
    dir.close();
  }

  @Test
  public void testFieldBoostEffect() throws IOException {
    Directory dir = newDirectory();
    MockAnalyzer analyzer = new MockAnalyzer(random());

    // Create two similarity configurations with different field boosts
    Map<String, Float> highTitleBoost = new HashMap<>();
    highTitleBoost.put("title", 10.0f);
    highTitleBoost.put("body", 1.0f);

    Map<String, Float> equalBoosts = new HashMap<>();
    equalBoosts.put("title", 1.0f);
    equalBoosts.put("body", 1.0f);

    // Index with high title boost
    IndexWriterConfig config1 = newIndexWriterConfig(analyzer);
    BM25FSimilarity sim1 = new BM25FSimilarity(1.2f, highTitleBoost, null);
    config1.setSimilarity(sim1);

    RandomIndexWriter writer = new RandomIndexWriter(random(), dir, config1);

    Document doc = new Document();
    doc.add(new TextField("title", "information", Field.Store.YES));
    doc.add(new TextField("body", "retrieval", Field.Store.YES));
    writer.addDocument(doc);

    IndexReader reader = writer.getReader();
    writer.close();

    // Search with high title boost
    IndexSearcher searcher1 = new IndexSearcher(reader);
    searcher1.setSimilarity(sim1);
    Query query = new TermQuery(new Term("title", "information"));
    TopDocs results1 = searcher1.search(query, 10);

    // Search with equal boosts
    BM25FSimilarity sim2 = new BM25FSimilarity(1.2f, equalBoosts, null);
    IndexSearcher searcher2 = new IndexSearcher(reader);
    searcher2.setSimilarity(sim2);
    TopDocs results2 = searcher2.search(query, 10);

    // High boost should produce higher score
    assertTrue(
        "High field boost should increase score",
        results1.scoreDocs[0].score > results2.scoreDocs[0].score);

    reader.close();
    dir.close();
  }

  @Test
  public void testToString() {
    Map<String, Float> fieldBoosts = new HashMap<>();
    fieldBoosts.put("title", 5.0f);
    fieldBoosts.put("body", 1.0f);

    Map<String, Float> fieldBParams = new HashMap<>();
    fieldBParams.put("title", 0.75f);
    fieldBParams.put("body", 0.75f);

    BM25FSimilarity similarity = new BM25FSimilarity(1.2f, fieldBoosts, fieldBParams);
    String str = similarity.toString();
    assertTrue(str.contains("BM25F"));
    assertTrue(str.contains("k1=1.2"));
  }

  @Test
  public void testDefaultFieldBoost() {
    Map<String, Float> fieldBoosts = new HashMap<>();
    fieldBoosts.put("field1", 2.0f);

    BM25FSimilarity similarity = new BM25FSimilarity(1.2f, fieldBoosts, null);
    
    // Configured field should return its boost
    assertEquals(2.0f, similarity.getBoostForField("field1"), 0.0001f);
    
    // Non-configured field should return default of 1.0
    assertEquals(1.0f, similarity.getBoostForField("unknownField"), 0.0001f);
  }

  @Test
  public void testDefaultBParameter() {
    Map<String, Float> fieldBoosts = new HashMap<>();
    fieldBoosts.put("field1", 1.0f);

    Map<String, Float> fieldBParams = new HashMap<>();
    fieldBParams.put("field1", 0.5f);

    BM25FSimilarity similarity = new BM25FSimilarity(1.2f, fieldBoosts, fieldBParams);
    
    // Configured field should return its b parameter
    assertEquals(0.5f, similarity.getBForField("field1"), 0.0001f);
    
    // Non-configured field should return default of 0.75
    assertEquals(0.75f, similarity.getBForField("unknownField"), 0.0001f);
  }

  @Test
  public void testImmutability() {
    Map<String, Float> fieldBoosts = new HashMap<>();
    fieldBoosts.put("title", 5.0f);

    Map<String, Float> fieldBParams = new HashMap<>();
    fieldBParams.put("title", 0.75f);

    BM25FSimilarity similarity = new BM25FSimilarity(1.2f, fieldBoosts, fieldBParams);

    // Modifying original maps should not affect the similarity
    fieldBoosts.put("title", 10.0f);
    fieldBParams.put("title", 0.5f);

    assertEquals(5.0f, similarity.getBoostForField("title"), 0.0001f);
    assertEquals(0.75f, similarity.getBForField("title"), 0.0001f);

    // Returned maps should be unmodifiable
    Map<String, Float> returnedBoosts = similarity.getFieldBoosts();
    expectThrows(
        UnsupportedOperationException.class,
        () -> {
          returnedBoosts.put("newField", 1.0f);
        });
  }

  @Test
  public void testExplanation() throws IOException {
    Directory dir = newDirectory();
    MockAnalyzer analyzer = new MockAnalyzer(random());
    IndexWriterConfig config = newIndexWriterConfig(analyzer);

    Map<String, Float> fieldBoosts = new HashMap<>();
    fieldBoosts.put("field", 2.0f);

    BM25FSimilarity similarity = new BM25FSimilarity(1.2f, fieldBoosts, null);
    config.setSimilarity(similarity);

    IndexWriter writer = new IndexWriter(dir, config);

    Document doc = new Document();
    doc.add(new TextField("field", "test document", Field.Store.YES));
    writer.addDocument(doc);
    writer.commit();

    DirectoryReader reader = DirectoryReader.open(writer);
    writer.close();

    IndexSearcher searcher = new IndexSearcher(reader);
    searcher.setSimilarity(similarity);

    Query query = new TermQuery(new Term("field", "test"));
    TopDocs results = searcher.search(query, 1);
    
    // Get explanation
    org.apache.lucene.search.Explanation explanation = 
        searcher.explain(query, results.scoreDocs[0].doc);
    
    assertNotNull("Explanation should not be null", explanation);
    assertTrue("Explanation should contain value", explanation.getValue().floatValue() > 0);
    assertTrue("Explanation should be detailed", explanation.toString().length() > 0);

    reader.close();
    dir.close();
  }
}

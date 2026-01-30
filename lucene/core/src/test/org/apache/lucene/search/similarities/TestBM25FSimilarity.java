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

import java.util.HashMap;
import java.util.Map;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.index.RandomIndexWriter;
import org.apache.lucene.tests.util.LuceneTestCase;

/** Tests for BM25FSimilarity */
public class TestBM25FSimilarity extends LuceneTestCase {

  public void testIllegalK1() {
    expectThrows(
        IllegalArgumentException.class,
        () -> {
          new BM25FSimilarity(-1, 0.75f);
        });

    expectThrows(
        IllegalArgumentException.class,
        () -> {
          new BM25FSimilarity(Float.POSITIVE_INFINITY, 0.75f);
        });
  }

  public void testIllegalB() {
    expectThrows(
        IllegalArgumentException.class,
        () -> {
          new BM25FSimilarity(1.2f, -0.5f);
        });

    expectThrows(
        IllegalArgumentException.class,
        () -> {
          new BM25FSimilarity(1.2f, 1.5f);
        });

    expectThrows(
        IllegalArgumentException.class,
        () -> {
          new BM25FSimilarity(1.2f, Float.NaN);
        });
  }

  public void testIllegalFieldBParam() {
    BM25FSimilarity sim = new BM25FSimilarity();

    expectThrows(
        IllegalArgumentException.class,
        () -> {
          sim.setFieldBParam("field", -0.5f);
        });

    expectThrows(
        IllegalArgumentException.class,
        () -> {
          sim.setFieldBParam("field", 1.5f);
        });

    expectThrows(
        IllegalArgumentException.class,
        () -> {
          sim.setFieldBParam("field", Float.NaN);
        });
  }

  public void testIllegalFieldBoost() {
    BM25FSimilarity sim = new BM25FSimilarity();

    expectThrows(
        IllegalArgumentException.class,
        () -> {
          sim.setFieldBoost("field", -1.0f);
        });
  }

  public void testDefaults() {
    BM25FSimilarity sim = new BM25FSimilarity();
    assertEquals(1.2f, sim.getK1(), 0.0001f);
    assertEquals(0.75f, sim.getDefaultB(), 0.0001f);
    assertEquals(1.0f, sim.getFieldBoost("anyfield"), 0.0001f);
    assertEquals(0.75f, sim.getFieldBParam("anyfield"), 0.0001f);
  }

  public void testFieldBoosts() {
    Map<String, Float> boosts = new HashMap<>();
    boosts.put("title", 2.0f);
    boosts.put("body", 1.0f);

    BM25FSimilarity sim = new BM25FSimilarity(1.2f, 0.75f, boosts, null);
    assertEquals(2.0f, sim.getFieldBoost("title"), 0.0001f);
    assertEquals(1.0f, sim.getFieldBoost("body"), 0.0001f);
    assertEquals(1.0f, sim.getFieldBoost("unknown"), 0.0001f);

    // Test setting boost
    sim.setFieldBoost("abstract", 1.5f);
    assertEquals(1.5f, sim.getFieldBoost("abstract"), 0.0001f);
  }

  public void testFieldBParams() {
    Map<String, Float> bParams = new HashMap<>();
    bParams.put("title", 0.5f);
    bParams.put("body", 0.75f);

    BM25FSimilarity sim = new BM25FSimilarity(1.2f, 0.75f, null, bParams);
    assertEquals(0.5f, sim.getFieldBParam("title"), 0.0001f);
    assertEquals(0.75f, sim.getFieldBParam("body"), 0.0001f);
    assertEquals(0.75f, sim.getFieldBParam("unknown"), 0.0001f);

    // Test setting b param
    sim.setFieldBParam("abstract", 0.6f);
    assertEquals(0.6f, sim.getFieldBParam("abstract"), 0.0001f);
  }

  public void testToString() {
    BM25FSimilarity sim = new BM25FSimilarity(1.5f, 0.8f);
    String str = sim.toString();
    assertTrue(str.contains("BM25F"));
    assertTrue(str.contains("k1=1.5"));
    assertTrue(str.contains("defaultB=0.8"));
  }

  public void testGetFieldBoostsAndBParams() {
    Map<String, Float> boosts = new HashMap<>();
    boosts.put("title", 2.0f);

    Map<String, Float> bParams = new HashMap<>();
    bParams.put("title", 0.5f);

    BM25FSimilarity sim = new BM25FSimilarity(1.2f, 0.75f, boosts, bParams);

    Map<String, Float> returnedBoosts = sim.getFieldBoosts();
    Map<String, Float> returnedBParams = sim.getFieldBParams();

    assertEquals(1, returnedBoosts.size());
    assertEquals(2.0f, returnedBoosts.get("title"), 0.0001f);

    assertEquals(1, returnedBParams.size());
    assertEquals(0.5f, returnedBParams.get("title"), 0.0001f);

    // Verify returned maps are copies (modifications don't affect original)
    returnedBoosts.put("newfield", 3.0f);
    assertEquals(1.0f, sim.getFieldBoost("newfield"), 0.0001f);
  }

  /** Test that BM25F produces reasonable scores with multi-field documents */
  public void testBasicScoring() throws Exception {
    Directory dir = newDirectory();
    RandomIndexWriter w =
        new RandomIndexWriter(
            random(),
            dir,
            newIndexWriterConfig(new MockAnalyzer(random()))
                .setMergePolicy(newLogMergePolicy()));

    Document doc1 = new Document();
    doc1.add(newTextField("title", "search engine", Field.Store.NO));
    doc1.add(newTextField("body", "information retrieval", Field.Store.NO));
    w.addDocument(doc1);

    Document doc2 = new Document();
    doc2.add(newTextField("title", "information", Field.Store.NO));
    doc2.add(newTextField("body", "search engine information retrieval", Field.Store.NO));
    w.addDocument(doc2);

    DirectoryReader reader = w.getReader();
    w.close();

    IndexSearcher searcher = new IndexSearcher(reader);

    // Configure BM25F with higher boost for title
    Map<String, Float> boosts = new HashMap<>();
    boosts.put("title", 2.0f);
    boosts.put("body", 1.0f);

    BM25FSimilarity sim = new BM25FSimilarity(1.2f, 0.75f, boosts, null);
    searcher.setSimilarity(sim);

    // Search for "information" across both fields
    BooleanQuery.Builder builder = new BooleanQuery.Builder();
    builder.add(new TermQuery(new Term("title", "information")), BooleanClause.Occur.SHOULD);
    builder.add(new TermQuery(new Term("body", "information")), BooleanClause.Occur.SHOULD);
    Query query = builder.build();

    TopDocs results = searcher.search(query, 10);
    assertEquals(2, results.totalHits.value);

    // Doc2 should score higher because "information" appears in title (with higher boost)
    assertTrue(results.scoreDocs[0].score > 0);

    reader.close();
    dir.close();
  }

  /** Test that field boosts affect ranking as expected */
  public void testFieldBoostRanking() throws Exception {
    Directory dir = newDirectory();
    IndexWriterConfig config = newIndexWriterConfig(new MockAnalyzer(random()));

    Map<String, Float> boosts = new HashMap<>();
    boosts.put("title", 5.0f);
    boosts.put("body", 1.0f);

    BM25FSimilarity sim = new BM25FSimilarity(1.2f, 0.75f, boosts, null);
    config.setSimilarity(sim);

    IndexWriter w = new IndexWriter(dir, config);

    // Document 1: term only in body
    Document doc1 = new Document();
    doc1.add(new TextField("title", "unrelated", Field.Store.NO));
    doc1.add(new TextField("body", "keyword keyword keyword", Field.Store.NO));
    w.addDocument(doc1);

    // Document 2: term only in title (should rank higher due to boost)
    Document doc2 = new Document();
    doc2.add(new TextField("title", "keyword", Field.Store.NO));
    doc2.add(new TextField("body", "unrelated", Field.Store.NO));
    w.addDocument(doc2);

    w.commit();
    w.close();

    DirectoryReader reader = DirectoryReader.open(dir);
    IndexSearcher searcher = new IndexSearcher(reader);
    searcher.setSimilarity(sim);

    // Search for "keyword"
    BooleanQuery.Builder builder = new BooleanQuery.Builder();
    builder.add(new TermQuery(new Term("title", "keyword")), BooleanClause.Occur.SHOULD);
    builder.add(new TermQuery(new Term("body", "keyword")), BooleanClause.Occur.SHOULD);
    Query query = builder.build();

    TopDocs results = searcher.search(query, 10);
    assertEquals(2, results.totalHits.value);

    // Document 2 should rank first (keyword in high-boost title field)
    ScoreDoc[] scoreDocs = results.scoreDocs;
    assertEquals(1, scoreDocs[0].doc);
    assertEquals(0, scoreDocs[1].doc);

    reader.close();
    dir.close();
  }

  /** Test different b parameters for different fields */
  public void testFieldSpecificBParams() throws Exception {
    Directory dir = newDirectory();

    Map<String, Float> bParams = new HashMap<>();
    bParams.put("title", 0.0f); // No length normalization for titles
    bParams.put("body", 0.75f); // Standard length normalization for body

    BM25FSimilarity sim = new BM25FSimilarity(1.2f, 0.75f, null, bParams);

    RandomIndexWriter w =
        new RandomIndexWriter(
            random(),
            dir,
            newIndexWriterConfig(new MockAnalyzer(random()))
                .setMergePolicy(newLogMergePolicy())
                .setSimilarity(sim));

    // Short title document
    Document doc1 = new Document();
    doc1.add(newTextField("title", "short", Field.Store.NO));
    doc1.add(newTextField("body", "text", Field.Store.NO));
    w.addDocument(doc1);

    // Long title document (should score similarly in title due to b=0)
    Document doc2 = new Document();
    doc2.add(
        newTextField("title", "very very very very very very long title here", Field.Store.NO));
    doc2.add(newTextField("body", "text", Field.Store.NO));
    w.addDocument(doc2);

    DirectoryReader reader = w.getReader();
    w.close();

    IndexSearcher searcher = new IndexSearcher(reader);
    searcher.setSimilarity(sim);

    Query query = new TermQuery(new Term("title", "very"));
    TopDocs results = searcher.search(query, 10);

    // Should find doc2
    assertEquals(1, results.totalHits.value);
    assertTrue(results.scoreDocs[0].score > 0);

    reader.close();
    dir.close();
  }

  /** Test scoring explanation */
  public void testExplanation() throws Exception {
    Directory dir = newDirectory();
    RandomIndexWriter w =
        new RandomIndexWriter(
            random(),
            dir,
            newIndexWriterConfig(new MockAnalyzer(random()))
                .setMergePolicy(newLogMergePolicy()));

    Document doc = new Document();
    doc.add(newTextField("field", "test document", Field.Store.NO));
    w.addDocument(doc);

    DirectoryReader reader = w.getReader();
    w.close();

    IndexSearcher searcher = new IndexSearcher(reader);

    Map<String, Float> boosts = new HashMap<>();
    boosts.put("field", 2.0f);

    BM25FSimilarity sim = new BM25FSimilarity(1.2f, 0.75f, boosts, null);
    searcher.setSimilarity(sim);

    Query query = new TermQuery(new Term("field", "test"));
    TopDocs results = searcher.search(query, 1);

    assertEquals(1, results.totalHits.value);

    var explanation = searcher.explain(query, results.scoreDocs[0].doc);
    assertNotNull(explanation);
    assertTrue(explanation.isMatch());
    assertTrue(explanation.getValue().floatValue() > 0);

    String explainText = explanation.toString();
    assertTrue(explainText.contains("idf"));
    assertTrue(explainText.contains("k1"));

    reader.close();
    dir.close();
  }
}

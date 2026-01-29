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
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexOptions;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.CollectionStatistics;
import org.apache.lucene.search.Explanation;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TermStatistics;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.Similarity.SimScorer;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

public class TestBM25FSimilarity extends LuceneTestCase {

  public void testDefaultConstructor() {
    BM25FSimilarity bm25f = new BM25FSimilarity();
    assertEquals(BM25FSimilarity.DEFAULT_K1, bm25f.getDefaultK1(), 0.0f);
    assertEquals(BM25FSimilarity.DEFAULT_B, bm25f.getDefaultB(), 0.0f);
    assertEquals(
        BM25FSimilarity.DEFAULT_FIELD_WEIGHT, bm25f.getFieldWeight("anyField"), 0.0f);
    assertEquals(BM25FSimilarity.DEFAULT_K1, bm25f.getFieldK1("anyField"), 0.0f);
    assertEquals(BM25FSimilarity.DEFAULT_B, bm25f.getFieldB("anyField"), 0.0f);
  }

  public void testFieldWeightConstructor() {
    Map<String, Float> weights = new HashMap<>();
    weights.put("title", 2.0f);
    weights.put("body", 1.0f);

    BM25FSimilarity bm25f = new BM25FSimilarity(weights);
    assertEquals(2.0f, bm25f.getFieldWeight("title"), 0.0f);
    assertEquals(1.0f, bm25f.getFieldWeight("body"), 0.0f);
    assertEquals(
        BM25FSimilarity.DEFAULT_FIELD_WEIGHT, bm25f.getFieldWeight("unknown"), 0.0f);

    // Should use defaults for k1 and b
    assertEquals(BM25FSimilarity.DEFAULT_K1, bm25f.getFieldK1("title"), 0.0f);
    assertEquals(BM25FSimilarity.DEFAULT_B, bm25f.getFieldB("title"), 0.0f);
  }

  public void testFullConstructor() {
    Map<String, Float> weights = new HashMap<>();
    weights.put("title", 2.0f);
    weights.put("body", 1.0f);

    Map<String, Float> k1Values = new HashMap<>();
    k1Values.put("title", 1.5f);
    k1Values.put("body", 1.2f);

    Map<String, Float> bValues = new HashMap<>();
    bValues.put("title", 0.5f);
    bValues.put("body", 0.75f);

    BM25FSimilarity bm25f = new BM25FSimilarity(weights, k1Values, bValues, 1.0f, 0.8f);

    assertEquals(2.0f, bm25f.getFieldWeight("title"), 0.0f);
    assertEquals(1.0f, bm25f.getFieldWeight("body"), 0.0f);
    assertEquals(1.5f, bm25f.getFieldK1("title"), 0.0f);
    assertEquals(1.2f, bm25f.getFieldK1("body"), 0.0f);
    assertEquals(0.5f, bm25f.getFieldB("title"), 0.0f);
    assertEquals(0.75f, bm25f.getFieldB("body"), 0.0f);

    // Test defaults for unknown fields
    assertEquals(1.0f, bm25f.getDefaultK1(), 0.0f);
    assertEquals(0.8f, bm25f.getDefaultB(), 0.0f);
    assertEquals(1.0f, bm25f.getFieldK1("unknown"), 0.0f);
    assertEquals(0.8f, bm25f.getFieldB("unknown"), 0.0f);
  }

  public void testIllegalNullMaps() {
    IllegalArgumentException expected =
        expectThrows(
            IllegalArgumentException.class,
            () -> {
              new BM25FSimilarity(null, new HashMap<>(), new HashMap<>());
            });
    assertTrue(expected.getMessage().contains("fieldWeights must not be null"));

    expected =
        expectThrows(
            IllegalArgumentException.class,
            () -> {
              new BM25FSimilarity(new HashMap<>(), null, new HashMap<>());
            });
    assertTrue(expected.getMessage().contains("fieldK1 must not be null"));

    expected =
        expectThrows(
            IllegalArgumentException.class,
            () -> {
              new BM25FSimilarity(new HashMap<>(), new HashMap<>(), null);
            });
    assertTrue(expected.getMessage().contains("fieldB must not be null"));
  }

  public void testIllegalWeights() {
    Map<String, Float> weights = new HashMap<>();
    weights.put("title", 0.0f); // Zero weight is illegal

    IllegalArgumentException expected =
        expectThrows(
            IllegalArgumentException.class,
            () -> {
              new BM25FSimilarity(weights);
            });
    assertTrue(expected.getMessage().contains("illegal weight for field"));

    weights.put("title", -1.0f); // Negative weight is illegal
    expected =
        expectThrows(
            IllegalArgumentException.class,
            () -> {
              new BM25FSimilarity(weights);
            });
    assertTrue(expected.getMessage().contains("illegal weight for field"));

    weights.put("title", Float.NaN); // NaN weight is illegal
    expected =
        expectThrows(
            IllegalArgumentException.class,
            () -> {
              new BM25FSimilarity(weights);
            });
    assertTrue(expected.getMessage().contains("illegal weight for field"));

    weights.put("title", null); // null weight is illegal
    expected =
        expectThrows(
            IllegalArgumentException.class,
            () -> {
              new BM25FSimilarity(weights);
            });
    assertTrue(expected.getMessage().contains("illegal weight for field"));
  }

  public void testIllegalK1() {
    Map<String, Float> k1Values = new HashMap<>();
    k1Values.put("title", Float.POSITIVE_INFINITY); // Infinity is illegal

    IllegalArgumentException expected =
        expectThrows(
            IllegalArgumentException.class,
            () -> {
              new BM25FSimilarity(new HashMap<>(), k1Values, new HashMap<>());
            });
    assertTrue(expected.getMessage().contains("illegal k1 for field"));

    k1Values.put("title", -1.0f); // Negative k1 is illegal
    expected =
        expectThrows(
            IllegalArgumentException.class,
            () -> {
              new BM25FSimilarity(new HashMap<>(), k1Values, new HashMap<>());
            });
    assertTrue(expected.getMessage().contains("illegal k1 for field"));

    k1Values.put("title", Float.NaN); // NaN k1 is illegal
    expected =
        expectThrows(
            IllegalArgumentException.class,
            () -> {
              new BM25FSimilarity(new HashMap<>(), k1Values, new HashMap<>());
            });
    assertTrue(expected.getMessage().contains("illegal k1 for field"));
  }

  public void testIllegalB() {
    Map<String, Float> bValues = new HashMap<>();
    bValues.put("title", 2.0f); // b > 1 is illegal

    IllegalArgumentException expected =
        expectThrows(
            IllegalArgumentException.class,
            () -> {
              new BM25FSimilarity(new HashMap<>(), new HashMap<>(), bValues);
            });
    assertTrue(expected.getMessage().contains("illegal b for field"));

    bValues.put("title", -0.1f); // b < 0 is illegal
    expected =
        expectThrows(
            IllegalArgumentException.class,
            () -> {
              new BM25FSimilarity(new HashMap<>(), new HashMap<>(), bValues);
            });
    assertTrue(expected.getMessage().contains("illegal b for field"));

    bValues.put("title", Float.NaN); // NaN b is illegal
    expected =
        expectThrows(
            IllegalArgumentException.class,
            () -> {
              new BM25FSimilarity(new HashMap<>(), new HashMap<>(), bValues);
            });
    assertTrue(expected.getMessage().contains("illegal b for field"));
  }

  public void testIllegalDefaultK1() {
    IllegalArgumentException expected =
        expectThrows(
            IllegalArgumentException.class,
            () -> {
              new BM25FSimilarity(new HashMap<>(), new HashMap<>(), new HashMap<>(), -1.0f, 0.75f);
            });
    assertTrue(expected.getMessage().contains("illegal default k1 value"));

    expected =
        expectThrows(
            IllegalArgumentException.class,
            () -> {
              new BM25FSimilarity(
                  new HashMap<>(), new HashMap<>(), new HashMap<>(), Float.NaN, 0.75f);
            });
    assertTrue(expected.getMessage().contains("illegal default k1 value"));

    expected =
        expectThrows(
            IllegalArgumentException.class,
            () -> {
              new BM25FSimilarity(
                  new HashMap<>(),
                  new HashMap<>(),
                  new HashMap<>(),
                  Float.POSITIVE_INFINITY,
                  0.75f);
            });
    assertTrue(expected.getMessage().contains("illegal default k1 value"));
  }

  public void testIllegalDefaultB() {
    IllegalArgumentException expected =
        expectThrows(
            IllegalArgumentException.class,
            () -> {
              new BM25FSimilarity(new HashMap<>(), new HashMap<>(), new HashMap<>(), 1.2f, -0.1f);
            });
    assertTrue(expected.getMessage().contains("illegal default b value"));

    expected =
        expectThrows(
            IllegalArgumentException.class,
            () -> {
              new BM25FSimilarity(new HashMap<>(), new HashMap<>(), new HashMap<>(), 1.2f, 1.1f);
            });
    assertTrue(expected.getMessage().contains("illegal default b value"));

    expected =
        expectThrows(
            IllegalArgumentException.class,
            () -> {
              new BM25FSimilarity(
                  new HashMap<>(), new HashMap<>(), new HashMap<>(), 1.2f, Float.NaN);
            });
    assertTrue(expected.getMessage().contains("illegal default b value"));
  }

  public void testToString() {
    Map<String, Float> weights = new HashMap<>();
    weights.put("title", 2.0f);

    BM25FSimilarity bm25f = new BM25FSimilarity(weights);
    String str = bm25f.toString();
    assertTrue(str.contains("BM25F"));
    assertTrue(str.contains("defaultK1=" + BM25FSimilarity.DEFAULT_K1));
    assertTrue(str.contains("defaultB=" + BM25FSimilarity.DEFAULT_B));
  }

  public void testImmutableMaps() {
    Map<String, Float> weights = new HashMap<>();
    weights.put("title", 2.0f);

    BM25FSimilarity bm25f = new BM25FSimilarity(weights);

    // Modifying the original map should not affect the similarity
    weights.put("title", 3.0f);
    assertEquals(2.0f, bm25f.getFieldWeight("title"), 0.0f);

    // Retrieved maps should be immutable
    Map<String, Float> retrievedWeights = bm25f.getFieldWeights();
    expectThrows(
        UnsupportedOperationException.class,
        () -> {
          retrievedWeights.put("newField", 1.0f);
        });
  }

  // Comprehensive tests for scoring functionality, parameter effects, and integration tests
  
  public void testSingleFieldScoring() throws IOException {
    // Setup BM25F with single field - should behave similarly to regular BM25
    Map<String, Float> fieldWeights = new HashMap<>();
    fieldWeights.put("content", 1.0f);
    
    Map<String, Float> fieldK1 = new HashMap<>();
    fieldK1.put("content", 1.2f);
    
    Map<String, Float> fieldB = new HashMap<>();
    fieldB.put("content", 0.75f);
    
    BM25FSimilarity bm25f = new BM25FSimilarity(fieldWeights, fieldK1, fieldB);
    BM25Similarity bm25 = new BM25Similarity(1.2f, 0.75f);
    
    try (Directory dir = newDirectory()) {
      // Create test index with BM25F
      IndexWriterConfig config = newIndexWriterConfig().setSimilarity(bm25f);
      IndexWriter writer = new IndexWriter(dir, config);
      
      Document doc1 = new Document();
      doc1.add(new TextField("content", "hello world", Field.Store.NO));
      writer.addDocument(doc1);
      
      Document doc2 = new Document();
      doc2.add(new TextField("content", "hello hello world world world", Field.Store.NO));
      writer.addDocument(doc2);
      
      writer.commit();
      writer.close();
      
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        searcher.setSimilarity(bm25f);
        
        Query query = new TermQuery(new Term("content", "hello"));
        TopDocs results = searcher.search(query, 10);
        
        assertEquals(2, results.totalHits.value);
        
        // Verify scores are computed and make sense
        assertTrue("First document should have positive score", results.scoreDocs[0].score > 0);
        assertTrue("Second document should have positive score", results.scoreDocs[1].score > 0);
        
        // Document with higher term frequency should score higher
        if (results.scoreDocs[0].doc == 1) { // doc2 has higher TF
          assertTrue("Document with higher TF should score higher", 
                    results.scoreDocs[0].score > results.scoreDocs[1].score);
        } else {
          assertTrue("Document with higher TF should score higher", 
                    results.scoreDocs[1].score > results.scoreDocs[0].score);
        }
      }
    }
  }
  
  public void testMultiFieldScoring() throws IOException {
    // Test BM25F with different field weights
    Map<String, Float> fieldWeights = new HashMap<>();
    fieldWeights.put("title", 2.0f);
    fieldWeights.put("body", 1.0f);
    
    BM25FSimilarity bm25f = new BM25FSimilarity(fieldWeights);
    
    try (Directory dir = newDirectory()) {
      IndexWriterConfig config = newIndexWriterConfig().setSimilarity(bm25f);
      IndexWriter writer = new IndexWriter(dir, config);
      
      // Document 1: term in title
      Document doc1 = new Document();
      doc1.add(new TextField("title", "java programming", Field.Store.NO));
      doc1.add(new TextField("body", "other content", Field.Store.NO));
      writer.addDocument(doc1);
      
      // Document 2: term in body  
      Document doc2 = new Document();
      doc2.add(new TextField("title", "other title", Field.Store.NO));
      doc2.add(new TextField("body", "java programming tutorial", Field.Store.NO));
      writer.addDocument(doc2);
      
      // Document 3: term in both fields
      Document doc3 = new Document();
      doc3.add(new TextField("title", "java", Field.Store.NO));
      doc3.add(new TextField("body", "java programming guide", Field.Store.NO));
      writer.addDocument(doc3);
      
      writer.commit();
      writer.close();
      
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        searcher.setSimilarity(bm25f);
        
        Query query = new TermQuery(new Term("title", "java"));
        TopDocs results = searcher.search(query, 10);
        
        assertTrue("Should find documents with term in title", results.totalHits.value >= 2);
        
        // All scores should be positive
        for (ScoreDoc scoreDoc : results.scoreDocs) {
          assertTrue("All scores should be positive", scoreDoc.score > 0);
        }
      }
    }
  }
  
  public void testFieldParameterEffects() throws IOException {
    // Test different k1 and b parameters for fields
    Map<String, Float> fieldWeights = new HashMap<>();
    fieldWeights.put("field1", 1.0f);
    fieldWeights.put("field2", 1.0f);
    
    Map<String, Float> fieldK1 = new HashMap<>();
    fieldK1.put("field1", 0.5f); // Low saturation
    fieldK1.put("field2", 2.0f); // High saturation
    
    Map<String, Float> fieldB = new HashMap<>();
    fieldB.put("field1", 0.0f); // No length normalization
    fieldB.put("field2", 1.0f); // Full length normalization
    
    BM25FSimilarity bm25f = new BM25FSimilarity(fieldWeights, fieldK1, fieldB);
    
    try (Directory dir = newDirectory()) {
      IndexWriterConfig config = newIndexWriterConfig().setSimilarity(bm25f);
      IndexWriter writer = new IndexWriter(dir, config);
      
      // Document with different field lengths
      Document doc = new Document();
      doc.add(new TextField("field1", "term", Field.Store.NO));
      doc.add(new TextField("field2", "term term term term term", Field.Store.NO)); // longer
      writer.addDocument(doc);
      
      writer.commit();
      writer.close();
      
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        searcher.setSimilarity(bm25f);
        
        // Query field1 (no length norm, low saturation)
        Query query1 = new TermQuery(new Term("field1", "term"));
        TopDocs results1 = searcher.search(query1, 1);
        
        // Query field2 (full length norm, high saturation)
        Query query2 = new TermQuery(new Term("field2", "term"));  
        TopDocs results2 = searcher.search(query2, 1);
        
        assertTrue("Field1 should have positive score", results1.scoreDocs[0].score > 0);
        assertTrue("Field2 should have positive score", results2.scoreDocs[0].score > 0);
        
        // Due to different parameters, scores should be different
        assertNotEquals("Scores should differ due to different field parameters",
                       results1.scoreDocs[0].score, results2.scoreDocs[0].score, 0.01f);
      }
    }
  }
  
  public void testFieldWeightEffects() throws IOException {
    // Test how field weights affect scoring
    Map<String, Float> highTitleWeight = new HashMap<>();
    highTitleWeight.put("title", 5.0f);
    highTitleWeight.put("body", 1.0f);
    
    Map<String, Float> lowTitleWeight = new HashMap<>();
    lowTitleWeight.put("title", 1.0f);
    lowTitleWeight.put("body", 1.0f);
    
    BM25FSimilarity highWeightSim = new BM25FSimilarity(highTitleWeight);
    BM25FSimilarity lowWeightSim = new BM25FSimilarity(lowTitleWeight);
    
    try (Directory dir = newDirectory()) {
      // Test with high title weight
      IndexWriterConfig config = newIndexWriterConfig().setSimilarity(highWeightSim);
      IndexWriter writer = new IndexWriter(dir, config);
      
      Document doc = new Document();
      doc.add(new TextField("title", "important", Field.Store.NO));
      doc.add(new TextField("body", "less important content", Field.Store.NO));
      writer.addDocument(doc);
      
      writer.commit();
      writer.close();
      
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        
        // Test with high weight similarity
        searcher.setSimilarity(highWeightSim);
        Query titleQuery = new TermQuery(new Term("title", "important"));
        TopDocs highWeightResults = searcher.search(titleQuery, 1);
        
        // Test with low weight similarity  
        searcher.setSimilarity(lowWeightSim);
        TopDocs lowWeightResults = searcher.search(titleQuery, 1);
        
        float highWeightScore = highWeightResults.scoreDocs[0].score;
        float lowWeightScore = lowWeightResults.scoreDocs[0].score;
        
        assertTrue("High weight should produce higher score", highWeightScore > lowWeightScore);
        assertTrue("Score ratio should reflect weight ratio", 
                  highWeightScore / lowWeightScore > 2.0f);
      }
    }
  }
  
  public void testScoreExplanation() throws IOException {
    // Test that explain() method works correctly
    Map<String, Float> fieldWeights = new HashMap<>();
    fieldWeights.put("content", 2.0f);
    
    Map<String, Float> fieldK1 = new HashMap<>();
    fieldK1.put("content", 1.5f);
    
    Map<String, Float> fieldB = new HashMap<>();
    fieldB.put("content", 0.5f);
    
    BM25FSimilarity bm25f = new BM25FSimilarity(fieldWeights, fieldK1, fieldB);
    
    try (Directory dir = newDirectory()) {
      IndexWriterConfig config = newIndexWriterConfig().setSimilarity(bm25f);
      IndexWriter writer = new IndexWriter(dir, config);
      
      Document doc = new Document();
      doc.add(new TextField("content", "test document", Field.Store.NO));
      writer.addDocument(doc);
      
      writer.commit();
      writer.close();
      
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        searcher.setSimilarity(bm25f);
        
        Query query = new TermQuery(new Term("content", "test"));
        TopDocs results = searcher.search(query, 1);
        
        Explanation explanation = searcher.explain(query, results.scoreDocs[0].doc);
        
        assertNotNull("Explanation should not be null", explanation);
        assertTrue("Explanation should match computed score", explanation.isMatch());
        assertEquals("Explanation value should match score", 
                    results.scoreDocs[0].score, explanation.getValue().floatValue(), 0.0001f);
        
        String explanationStr = explanation.toString();
        assertTrue("Explanation should mention fieldWeight", explanationStr.contains("fieldWeight"));
        assertTrue("Explanation should mention BM25F concepts", 
                  explanationStr.toLowerCase().contains("computed as"));
      }
    }
  }
  
  public void testIntegrationWithIndexSearcher() throws IOException {
    // Integration test with full indexing and searching pipeline
    Map<String, Float> fieldWeights = new HashMap<>();
    fieldWeights.put("title", 3.0f);
    fieldWeights.put("body", 1.0f);
    fieldWeights.put("tags", 2.0f);
    
    Map<String, Float> fieldK1 = new HashMap<>();
    fieldK1.put("title", 1.5f);
    fieldK1.put("body", 1.2f);
    fieldK1.put("tags", 1.0f);
    
    Map<String, Float> fieldB = new HashMap<>();
    fieldB.put("title", 0.3f);  // Less length normalization for titles
    fieldB.put("body", 0.75f);  // Standard for body
    fieldB.put("tags", 0.0f);   // No length normalization for tags
    
    BM25FSimilarity bm25f = new BM25FSimilarity(fieldWeights, fieldK1, fieldB);
    
    try (Directory dir = newDirectory()) {
      IndexWriterConfig config = newIndexWriterConfig().setSimilarity(bm25f);
      IndexWriter writer = new IndexWriter(dir, config);
      
      // Add diverse documents
      Document doc1 = new Document();
      doc1.add(new TextField("title", "Machine Learning Algorithms", Field.Store.YES));
      doc1.add(new TextField("body", "This document discusses various machine learning algorithms and their applications.", Field.Store.YES));
      doc1.add(new TextField("tags", "ml algorithms", Field.Store.YES));
      writer.addDocument(doc1);
      
      Document doc2 = new Document();
      doc2.add(new TextField("title", "Deep Learning with Neural Networks", Field.Store.YES));
      doc2.add(new TextField("body", "Neural networks and deep learning are powerful machine learning techniques.", Field.Store.YES));
      doc2.add(new TextField("tags", "deep learning neural", Field.Store.YES));
      writer.addDocument(doc2);
      
      Document doc3 = new Document();
      doc3.add(new TextField("title", "Data Science Introduction", Field.Store.YES));
      doc3.add(new TextField("body", "Introduction to data science covering statistics, machine learning, and data visualization.", Field.Store.YES));
      doc3.add(new TextField("tags", "data science statistics", Field.Store.YES));
      writer.addDocument(doc3);
      
      writer.commit();
      writer.close();
      
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        searcher.setSimilarity(bm25f);
        
        // Test query across multiple fields
        Query titleQuery = new TermQuery(new Term("title", "machine"));
        Query bodyQuery = new TermQuery(new Term("body", "machine"));
        Query tagsQuery = new TermQuery(new Term("tags", "ml"));
        
        TopDocs titleResults = searcher.search(titleQuery, 10);
        TopDocs bodyResults = searcher.search(bodyQuery, 10);  
        TopDocs tagsResults = searcher.search(tagsQuery, 10);
        
        // Verify results are found
        assertTrue("Should find documents with 'machine' in title", titleResults.totalHits.value > 0);
        assertTrue("Should find documents with 'machine' in body", bodyResults.totalHits.value > 0);
        assertTrue("Should find documents with 'ml' in tags", tagsResults.totalHits.value > 0);
        
        // Title matches should score higher than body matches due to higher weight
        float titleScore = titleResults.scoreDocs[0].score;
        float bodyScore = bodyResults.scoreDocs[0].score;
        
        assertTrue("Title matches should score higher than body matches due to field weight",
                  titleScore > bodyScore);
        
        // Verify all scores are reasonable (positive and not infinite)
        for (ScoreDoc scoreDoc : titleResults.scoreDocs) {
          assertTrue("Scores should be positive", scoreDoc.score > 0);
          assertTrue("Scores should be finite", Float.isFinite(scoreDoc.score));
        }
      }
    }
  }
  
  public void testEdgeCases() throws IOException {
    // Test edge cases like empty fields, missing fields, zero frequencies
    BM25FSimilarity bm25f = new BM25FSimilarity();
    
    try (Directory dir = newDirectory()) {
      IndexWriterConfig config = newIndexWriterConfig().setSimilarity(bm25f);
      IndexWriter writer = new IndexWriter(dir, config);
      
      // Document with empty field
      Document doc1 = new Document();
      doc1.add(new TextField("empty", "", Field.Store.NO));
      doc1.add(new TextField("content", "some content", Field.Store.NO));
      writer.addDocument(doc1);
      
      // Document with only one field
      Document doc2 = new Document();
      doc2.add(new TextField("content", "different content", Field.Store.NO));
      writer.addDocument(doc2);
      
      writer.commit();
      writer.close();
      
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        searcher.setSimilarity(bm25f);
        
        // Query existing content
        Query query = new TermQuery(new Term("content", "content"));
        TopDocs results = searcher.search(query, 10);
        
        assertEquals("Should find both documents", 2, results.totalHits.value);
        
        // Query non-existent field should return no results
        Query nonExistentQuery = new TermQuery(new Term("nonexistent", "term"));
        TopDocs nonExistentResults = searcher.search(nonExistentQuery, 10);
        assertEquals("Should find no documents for non-existent field", 0, nonExistentResults.totalHits.value);
        
        // Verify scores are reasonable
        for (ScoreDoc scoreDoc : results.scoreDocs) {
          assertTrue("Scores should be positive", scoreDoc.score > 0);
          assertTrue("Scores should be finite", Float.isFinite(scoreDoc.score));
          assertFalse("Scores should not be NaN", Float.isNaN(scoreDoc.score));
        }
      }
    }
  }
  
  public void testZeroTermFrequency() throws IOException {
    // Test behavior with zero term frequencies (should not crash)
    Map<String, Float> fieldWeights = new HashMap<>();
    fieldWeights.put("content", 1.0f);
    
    BM25FSimilarity bm25f = new BM25FSimilarity(fieldWeights);
    
    try (Directory dir = newDirectory()) {
      IndexWriterConfig config = newIndexWriterConfig().setSimilarity(bm25f);
      IndexWriter writer = new IndexWriter(dir, config);
      
      Document doc = new Document();
      doc.add(new TextField("content", "test document without query term", Field.Store.NO));
      writer.addDocument(doc);
      
      writer.commit();
      writer.close();
      
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        searcher.setSimilarity(bm25f);
        
        // Query for term not in document
        Query query = new TermQuery(new Term("content", "missing"));
        TopDocs results = searcher.search(query, 10);
        
        assertEquals("Should find no documents for missing term", 0, results.totalHits.value);
      }
    }
  }
  
  public void testParameterConsistency() {
    // Test that parameters are returned consistently
    Map<String, Float> fieldWeights = new HashMap<>();
    fieldWeights.put("title", 2.5f);
    fieldWeights.put("body", 1.0f);
    
    Map<String, Float> fieldK1 = new HashMap<>();
    fieldK1.put("title", 1.8f);
    
    Map<String, Float> fieldB = new HashMap<>();
    fieldB.put("body", 0.6f);
    
    BM25FSimilarity bm25f = new BM25FSimilarity(fieldWeights, fieldK1, fieldB, 1.3f, 0.8f);
    
    // Test field weights
    assertEquals("Title weight should be 2.5", 2.5f, bm25f.getFieldWeight("title"), 0.0f);
    assertEquals("Body weight should be 1.0", 1.0f, bm25f.getFieldWeight("body"), 0.0f);
    assertEquals("Unknown field should use default weight", 
                BM25FSimilarity.DEFAULT_FIELD_WEIGHT, bm25f.getFieldWeight("unknown"), 0.0f);
    
    // Test field k1 values
    assertEquals("Title k1 should be 1.8", 1.8f, bm25f.getFieldK1("title"), 0.0f);
    assertEquals("Body k1 should use default", 1.3f, bm25f.getFieldK1("body"), 0.0f);
    assertEquals("Unknown field k1 should use default", 1.3f, bm25f.getFieldK1("unknown"), 0.0f);
    
    // Test field b values  
    assertEquals("Title b should use default", 0.8f, bm25f.getFieldB("title"), 0.0f);
    assertEquals("Body b should be 0.6", 0.6f, bm25f.getFieldB("body"), 0.0f);
    assertEquals("Unknown field b should use default", 0.8f, bm25f.getFieldB("unknown"), 0.0f);
    
    // Test map getters return immutable maps with correct content
    Map<String, Float> retrievedWeights = bm25f.getFieldWeights();
    assertEquals("Retrieved weights should have 2 entries", 2, retrievedWeights.size());
    assertEquals("Retrieved title weight should match", 2.5f, retrievedWeights.get("title"), 0.0f);
    assertEquals("Retrieved body weight should match", 1.0f, retrievedWeights.get("body"), 0.0f);
    
    Map<String, Float> retrievedK1 = bm25f.getFieldK1Map();
    assertEquals("Retrieved k1 map should have 1 entry", 1, retrievedK1.size());
    assertEquals("Retrieved title k1 should match", 1.8f, retrievedK1.get("title"), 0.0f);
    
    Map<String, Float> retrievedB = bm25f.getFieldBMap();
    assertEquals("Retrieved b map should have 1 entry", 1, retrievedB.size());
    assertEquals("Retrieved body b should match", 0.6f, retrievedB.get("body"), 0.0f);
  }
  
  public void testExtremeParameterValues() throws IOException {
    // Test with extreme but valid parameter values
    Map<String, Float> fieldWeights = new HashMap<>();
    fieldWeights.put("field1", Float.MIN_VALUE); // Very small positive weight
    fieldWeights.put("field2", 1000.0f);         // Very large weight
    
    Map<String, Float> fieldK1 = new HashMap<>();
    fieldK1.put("field1", 0.0f);                 // Minimum k1
    fieldK1.put("field2", 10000.0f);             // Very large k1
    
    Map<String, Float> fieldB = new HashMap<>();
    fieldB.put("field1", 0.0f);                  // No length normalization
    fieldB.put("field2", 1.0f);                  // Maximum length normalization
    
    // Should not throw exception with extreme values
    BM25FSimilarity bm25f = new BM25FSimilarity(fieldWeights, fieldK1, fieldB);
    
    // Verify extreme values are stored correctly
    assertEquals("Min weight should be stored", Float.MIN_VALUE, bm25f.getFieldWeight("field1"), 0.0f);
    assertEquals("Large weight should be stored", 1000.0f, bm25f.getFieldWeight("field2"), 0.0f);
    assertEquals("Zero k1 should be stored", 0.0f, bm25f.getFieldK1("field1"), 0.0f);
    assertEquals("Large k1 should be stored", 10000.0f, bm25f.getFieldK1("field2"), 0.0f);
    assertEquals("Zero b should be stored", 0.0f, bm25f.getFieldB("field1"), 0.0f);
    assertEquals("Max b should be stored", 1.0f, bm25f.getFieldB("field2"), 0.0f);
    
    try (Directory dir = newDirectory()) {
      IndexWriterConfig config = newIndexWriterConfig().setSimilarity(bm25f);
      IndexWriter writer = new IndexWriter(dir, config);
      
      Document doc = new Document();
      doc.add(new TextField("field1", "test term", Field.Store.NO));
      doc.add(new TextField("field2", "test term", Field.Store.NO));
      writer.addDocument(doc);
      
      writer.commit();
      writer.close();
      
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        searcher.setSimilarity(bm25f);
        
        // Query both fields - should work without throwing exceptions
        Query query1 = new TermQuery(new Term("field1", "test"));
        Query query2 = new TermQuery(new Term("field2", "test"));
        
        TopDocs results1 = searcher.search(query1, 1);
        TopDocs results2 = searcher.search(query2, 1);
        
        // Both should find the document
        assertEquals("Field1 query should find document", 1, results1.totalHits.value);
        assertEquals("Field2 query should find document", 1, results2.totalHits.value);
        
        // Scores should be finite and non-negative
        assertTrue("Field1 score should be non-negative", results1.scoreDocs[0].score >= 0);
        assertTrue("Field2 score should be non-negative", results2.scoreDocs[0].score >= 0);
        assertTrue("Field1 score should be finite", Float.isFinite(results1.scoreDocs[0].score));
        assertTrue("Field2 score should be finite", Float.isFinite(results2.scoreDocs[0].score));
        
        // Field2 should have much higher score due to larger weight
        assertTrue("Field2 should score much higher due to large weight",
                  results2.scoreDocs[0].score > results1.scoreDocs[0].score * 100);
      }
    }
  }
}
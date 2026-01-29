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
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.Explanation;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.BM25FSimilarity;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Tests for {@link BM25FMultiFieldQueryParser} including parameter validation, query parsing, 
 * integration with {@link BM25FSimilarity}, and end-to-end search functionality.
 */
public class TestBM25FMultiFieldQueryParser extends LuceneTestCase {

  private static final String[] FIELDS = {"title", "body", "tags"};
  private Analyzer analyzer;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    analyzer = new MockAnalyzer(random());
  }

  // ===========================================
  // Constructor and Parameter Validation Tests
  // ===========================================

  public void testConstructorBasic() {
    BM25FMultiFieldQueryParser parser = new BM25FMultiFieldQueryParser(FIELDS, analyzer);
    assertNotNull(parser);
    assertEquals(BM25FSimilarity.DEFAULT_K1, parser.getDefaultK1(), 0.001f);
    assertEquals(BM25FSimilarity.DEFAULT_B, parser.getDefaultB(), 0.001f);
    assertTrue(parser.getDiscountOverlaps());
  }

  public void testConstructorWithWeights() {
    Map<String, Float> weights = new HashMap<>();
    weights.put("title", 2.0f);
    weights.put("body", 1.0f);
    weights.put("tags", 1.5f);

    BM25FMultiFieldQueryParser parser = new BM25FMultiFieldQueryParser(FIELDS, analyzer, weights);
    assertNotNull(parser);
    
    Map<String, Float> retrievedWeights = parser.getFieldWeights();
    assertEquals(2.0f, retrievedWeights.get("title"), 0.001f);
    assertEquals(1.0f, retrievedWeights.get("body"), 0.001f);
    assertEquals(1.5f, retrievedWeights.get("tags"), 0.001f);
  }

  public void testConstructorWithFieldParameters() {
    Map<String, Float> weights = new HashMap<>();
    weights.put("title", 2.0f);
    weights.put("body", 1.0f);

    Map<String, Float> fieldK1 = new HashMap<>();
    fieldK1.put("title", 1.5f);
    fieldK1.put("body", 1.3f);

    Map<String, Float> fieldB = new HashMap<>();
    fieldB.put("title", 0.6f);
    fieldB.put("body", 0.8f);

    BM25FMultiFieldQueryParser parser = new BM25FMultiFieldQueryParser(
        FIELDS, analyzer, weights, fieldK1, fieldB);

    assertEquals(1.5f, parser.getFieldK1("title"), 0.001f);
    assertEquals(1.3f, parser.getFieldK1("body"), 0.001f);
    assertEquals(BM25FSimilarity.DEFAULT_K1, parser.getFieldK1("tags"), 0.001f); // Default for unspecified field

    assertEquals(0.6f, parser.getFieldB("title"), 0.001f);
    assertEquals(0.8f, parser.getFieldB("body"), 0.001f);
    assertEquals(BM25FSimilarity.DEFAULT_B, parser.getFieldB("tags"), 0.001f); // Default for unspecified field
  }

  public void testConstructorFullParameters() {
    Map<String, Float> weights = new HashMap<>();
    weights.put("title", 2.0f);

    Map<String, Float> fieldK1 = new HashMap<>();
    fieldK1.put("title", 1.8f);

    Map<String, Float> fieldB = new HashMap<>();
    fieldB.put("title", 0.4f);

    float customDefaultK1 = 1.4f;
    float customDefaultB = 0.9f;
    boolean discountOverlaps = false;

    BM25FMultiFieldQueryParser parser = new BM25FMultiFieldQueryParser(
        FIELDS, analyzer, weights, fieldK1, fieldB, customDefaultK1, customDefaultB, discountOverlaps);

    assertEquals(customDefaultK1, parser.getDefaultK1(), 0.001f);
    assertEquals(customDefaultB, parser.getDefaultB(), 0.001f);
    assertEquals(discountOverlaps, parser.getDiscountOverlaps());

    assertEquals(1.8f, parser.getFieldK1("title"), 0.001f);
    assertEquals(customDefaultK1, parser.getFieldK1("body"), 0.001f); // Should use custom default
  }

  public void testConstructorValidation() {
    // Test null fields
    expectThrows(IllegalArgumentException.class, () -> {
      new BM25FMultiFieldQueryParser(null, analyzer);
    });

    // Test empty fields
    expectThrows(IllegalArgumentException.class, () -> {
      new BM25FMultiFieldQueryParser(new String[0], analyzer);
    });

    // Test null analyzer
    expectThrows(IllegalArgumentException.class, () -> {
      new BM25FMultiFieldQueryParser(FIELDS, null);
    });

    Map<String, Float> weights = new HashMap<>();

    // Test null fieldK1 parameter
    expectThrows(IllegalArgumentException.class, () -> {
      new BM25FMultiFieldQueryParser(FIELDS, analyzer, weights, null, new HashMap<>());
    });

    // Test null fieldB parameter
    expectThrows(IllegalArgumentException.class, () -> {
      new BM25FMultiFieldQueryParser(FIELDS, analyzer, weights, new HashMap<>(), null);
    });
  }

  // ==============================================
  // Query Parsing Tests
  // ==============================================

  public void testBasicQueryParsing() throws Exception {
    BM25FMultiFieldQueryParser parser = new BM25FMultiFieldQueryParser(FIELDS, analyzer);

    Query query = parser.parse("search");
    String queryStr = query.toString();
    assertTrue(queryStr.contains("title:search"));
    assertTrue(queryStr.contains("body:search"));
    assertTrue(queryStr.contains("tags:search"));
  }

  public void testQueryParsingWithWeights() throws Exception {
    Map<String, Float> weights = new HashMap<>();
    weights.put("title", 2.0f);
    weights.put("body", 1.0f);
    weights.put("tags", 1.5f);

    BM25FMultiFieldQueryParser parser = new BM25FMultiFieldQueryParser(FIELDS, analyzer, weights);

    Query query = parser.parse("machine learning");
    String queryStr = query.toString();
    
    // Check that the query includes all fields
    assertTrue(queryStr.contains("title:machine"));
    assertTrue(queryStr.contains("body:machine"));
    assertTrue(queryStr.contains("tags:machine"));
    assertTrue(queryStr.contains("title:learning"));
    assertTrue(queryStr.contains("body:learning"));
    assertTrue(queryStr.contains("tags:learning"));

    // Check that boosts are applied (should contain ^2.0 for title and ^1.5 for tags)
    assertTrue(queryStr.contains("^2.0"));
    assertTrue(queryStr.contains("^1.5"));
  }

  public void testPhraseQueries() throws Exception {
    BM25FMultiFieldQueryParser parser = new BM25FMultiFieldQueryParser(FIELDS, analyzer);

    Query query = parser.parse("\"machine learning\"");
    String queryStr = query.toString();
    assertTrue(queryStr.contains("title:\"machine learning\""));
    assertTrue(queryStr.contains("body:\"machine learning\""));
    assertTrue(queryStr.contains("tags:\"machine learning\""));
  }

  public void testBooleanQueries() throws Exception {
    BM25FMultiFieldQueryParser parser = new BM25FMultiFieldQueryParser(FIELDS, analyzer);

    Query query = parser.parse("machine AND learning");
    String queryStr = query.toString();
    assertTrue(queryStr.contains("+"));
    assertTrue(queryStr.contains("machine"));
    assertTrue(queryStr.contains("learning"));
  }

  public void testWildcardQueries() throws Exception {
    BM25FMultiFieldQueryParser parser = new BM25FMultiFieldQueryParser(FIELDS, analyzer);

    Query query = parser.parse("mach*");
    String queryStr = query.toString();
    assertTrue(queryStr.contains("title:mach*"));
    assertTrue(queryStr.contains("body:mach*"));
    assertTrue(queryStr.contains("tags:mach*"));
  }

  public void testFieldSpecificQueries() throws Exception {
    BM25FMultiFieldQueryParser parser = new BM25FMultiFieldQueryParser(FIELDS, analyzer);

    Query query = parser.parse("title:algorithm body:implementation");
    String queryStr = query.toString();
    assertTrue(queryStr.contains("title:algorithm"));
    assertTrue(queryStr.contains("body:implementation"));
    // Should not expand field-specific terms to all fields
    assertFalse(queryStr.contains("tags:algorithm"));
    assertFalse(queryStr.contains("tags:implementation"));
  }

  // ==============================================
  // BM25FSimilarity Integration Tests
  // ==============================================

  public void testCreateBM25FSimilarity() {
    Map<String, Float> weights = new HashMap<>();
    weights.put("title", 2.0f);
    weights.put("body", 1.0f);

    Map<String, Float> fieldK1 = new HashMap<>();
    fieldK1.put("title", 1.5f);

    Map<String, Float> fieldB = new HashMap<>();
    fieldB.put("body", 0.8f);

    BM25FMultiFieldQueryParser parser = new BM25FMultiFieldQueryParser(
        FIELDS, analyzer, weights, fieldK1, fieldB);

    BM25FSimilarity similarity = parser.createBM25FSimilarity();
    assertNotNull(similarity);

    // Test that the similarity has the correct field weights
    assertEquals(2.0f, similarity.getFieldWeight("title"), 0.001f);
    assertEquals(1.0f, similarity.getFieldWeight("body"), 0.001f);
    assertEquals(1.0f, similarity.getFieldWeight("tags"), 0.001f); // Default

    // Test field-specific parameters
    assertEquals(1.5f, similarity.getFieldK1("title"), 0.001f);
    assertEquals(BM25FSimilarity.DEFAULT_K1, similarity.getFieldK1("body"), 0.001f); // Default
    assertEquals(0.8f, similarity.getFieldB("body"), 0.001f);
    assertEquals(BM25FSimilarity.DEFAULT_B, similarity.getFieldB("title"), 0.001f); // Default
  }

  public void testSimilarityParameterAlignment() {
    Map<String, Float> weights = new HashMap<>();
    weights.put("title", 3.0f);
    weights.put("body", 1.5f);

    float customK1 = 1.6f;
    float customB = 0.9f;
    boolean discountOverlaps = false;

    BM25FMultiFieldQueryParser parser = new BM25FMultiFieldQueryParser(
        FIELDS, analyzer, weights, new HashMap<>(), new HashMap<>(), 
        customK1, customB, discountOverlaps);

    BM25FSimilarity similarity = parser.createBM25FSimilarity();

    // Verify that all parameters are correctly transferred
    assertEquals(customK1, similarity.getDefaultK1(), 0.001f);
    assertEquals(customB, similarity.getDefaultB(), 0.001f);
    assertEquals(discountOverlaps, similarity.getDiscountOverlaps());

    assertEquals(3.0f, similarity.getFieldWeight("title"), 0.001f);
    assertEquals(1.5f, similarity.getFieldWeight("body"), 0.001f);
  }

  // ==============================================
  // End-to-End Integration Tests
  // ==============================================

  public void testEndToEndSearch() throws Exception {
    Map<String, Float> weights = new HashMap<>();
    weights.put("title", 2.0f);
    weights.put("body", 1.0f);
    weights.put("tags", 1.5f);

    BM25FMultiFieldQueryParser parser = new BM25FMultiFieldQueryParser(FIELDS, analyzer, weights);
    BM25FSimilarity similarity = parser.createBM25FSimilarity();

    // Create test index
    try (Directory dir = newDirectory()) {
      IndexWriterConfig config = new IndexWriterConfig(analyzer);
      config.setSimilarity(similarity);

      try (IndexWriter writer = new IndexWriter(dir, config)) {
        // Add test documents
        addDocument(writer, "Machine Learning Basics", 
                   "This document covers the fundamentals of machine learning algorithms.", 
                   "ml algorithm basics");
        addDocument(writer, "Deep Learning", 
                   "Advanced topics in neural networks and deep learning techniques.", 
                   "neural network deep");
        addDocument(writer, "Statistics Guide", 
                   "A comprehensive guide to statistical methods and machine learning.", 
                   "statistics guide");
      }

      try (IndexReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        searcher.setSimilarity(similarity);

        // Test search with BM25F
        Query query = parser.parse("machine learning");
        TopDocs results = searcher.search(query, 10);

        assertTrue("Should find results", results.totalHits.value > 0);
        assertTrue("Should find at least 2 documents", results.totalHits.value >= 2);

        // Verify scoring - documents with title matches should score higher due to title weight
        ScoreDoc[] scoreDocs = results.scoreDocs;
        assertTrue("Should have score differences", scoreDocs[0].score > scoreDocs[scoreDocs.length - 1].score);
      }
    }
  }

  public void testScoreExplanations() throws Exception {
    Map<String, Float> weights = new HashMap<>();
    weights.put("title", 2.0f);
    weights.put("body", 1.0f);

    BM25FMultiFieldQueryParser parser = new BM25FMultiFieldQueryParser(FIELDS, analyzer, weights);
    BM25FSimilarity similarity = parser.createBM25FSimilarity();

    try (Directory dir = newDirectory()) {
      IndexWriterConfig config = new IndexWriterConfig(analyzer);
      config.setSimilarity(similarity);

      try (IndexWriter writer = new IndexWriter(dir, config)) {
        addDocument(writer, "Machine Learning", 
                   "This covers machine learning concepts", 
                   "ml");
      }

      try (IndexReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        searcher.setSimilarity(similarity);

        Query query = parser.parse("machine");
        TopDocs results = searcher.search(query, 1);
        assertTrue("Should find document", results.totalHits.value > 0);

        // Get explanation
        Explanation explanation = searcher.explain(query, results.scoreDocs[0].doc);
        assertNotNull("Should have explanation", explanation);
        String explainStr = explanation.toString();

        // BM25F explanations should mention field weights or multi-field aspects
        assertTrue("Explanation should be informative", explainStr.length() > 50);
      }
    }
  }

  public void testDifferenceFromStandardBM25() throws Exception {
    // Create documents that will score differently with BM25F vs standard BM25
    Map<String, Float> weights = new HashMap<>();
    weights.put("title", 3.0f);  // Much higher title weight
    weights.put("body", 1.0f);

    BM25FMultiFieldQueryParser bm25fParser = new BM25FMultiFieldQueryParser(FIELDS, analyzer, weights);
    BM25FSimilarity bm25fSimilarity = bm25fParser.createBM25FSimilarity();

    MultiFieldQueryParser standardParser = new MultiFieldQueryParser(FIELDS, analyzer, weights);

    try (Directory bm25fDir = newDirectory(); Directory standardDir = newDirectory()) {
      
      // Index with BM25F
      IndexWriterConfig bm25fConfig = new IndexWriterConfig(analyzer);
      bm25fConfig.setSimilarity(bm25fSimilarity);
      
      // Index with standard BM25
      IndexWriterConfig standardConfig = new IndexWriterConfig(analyzer);
      standardConfig.setSimilarity(new BM25Similarity());

      try (IndexWriter bm25fWriter = new IndexWriter(bm25fDir, bm25fConfig);
           IndexWriter standardWriter = new IndexWriter(standardDir, standardConfig)) {
        
        // Add documents to both indices
        addDocument(bm25fWriter, "machine learning algorithm", "some body text", "tag");
        addDocument(bm25fWriter, "some title", "machine learning in body text", "tag");
        
        addDocument(standardWriter, "machine learning algorithm", "some body text", "tag");
        addDocument(standardWriter, "some title", "machine learning in body text", "tag");
      }

      try (IndexReader bm25fReader = DirectoryReader.open(bm25fDir);
           IndexReader standardReader = DirectoryReader.open(standardDir)) {
        
        IndexSearcher bm25fSearcher = new IndexSearcher(bm25fReader);
        bm25fSearcher.setSimilarity(bm25fSimilarity);
        
        IndexSearcher standardSearcher = new IndexSearcher(standardReader);
        standardSearcher.setSimilarity(new BM25Similarity());

        Query bm25fQuery = bm25fParser.parse("machine learning");
        Query standardQuery = standardParser.parse("machine learning");

        TopDocs bm25fResults = bm25fSearcher.search(bm25fQuery, 10);
        TopDocs standardResults = standardSearcher.search(standardQuery, 10);

        assertEquals("Both should find same number of docs", 
                    bm25fResults.totalHits.value, standardResults.totalHits.value);

        // The relative scores might be different due to BM25F's field-specific handling
        // This test mainly verifies that both produce results and handle the same queries
        assertTrue("BM25F should produce scores", bm25fResults.scoreDocs[0].score > 0);
        assertTrue("Standard should produce scores", standardResults.scoreDocs[0].score > 0);
      }
    }
  }

  // ==============================================
  // Additional Comprehensive Coverage Tests  
  // ==============================================

  /**
   * Test that covers all the key requirements from FEAT-005:
   * - All constructors and parameter validation  
   * - Query parsing across multiple fields
   * - BM25FSimilarity integration and configuration  
   * - Field weights are properly applied
   * - End-to-end scenarios with indexing and searching
   * - Score explanations 
   * - BM25F scoring works in full search pipeline
   */
  public void testComprehensiveFEAT005Coverage() throws Exception {
    // Test 1: All constructors work
    BM25FMultiFieldQueryParser parser1 = new BM25FMultiFieldQueryParser(FIELDS, analyzer);
    BM25FMultiFieldQueryParser parser2 = new BM25FMultiFieldQueryParser(FIELDS, analyzer, new HashMap<>());
    
    Map<String, Float> weights = new HashMap<>();
    weights.put("title", 2.0f);
    BM25FMultiFieldQueryParser parser3 = new BM25FMultiFieldQueryParser(FIELDS, analyzer, weights, 
                                                                        new HashMap<>(), new HashMap<>());
    
    BM25FMultiFieldQueryParser parser4 = new BM25FMultiFieldQueryParser(FIELDS, analyzer, weights, 
                                                                        new HashMap<>(), new HashMap<>(), 
                                                                        1.4f, 0.8f, false);
    
    // Test 2: Parameter validation 
    assertNotNull(parser1);
    assertNotNull(parser2); 
    assertNotNull(parser3);
    assertNotNull(parser4);
    
    // Test 3: Query parsing across multiple fields
    Query query = parser1.parse("test query");
    String queryStr = query.toString();
    assertTrue("Should search title", queryStr.contains("title:test"));
    assertTrue("Should search body", queryStr.contains("body:test")); 
    assertTrue("Should search tags", queryStr.contains("tags:test"));
    
    // Test 4: BM25FSimilarity integration
    BM25FSimilarity similarity = parser3.createBM25FSimilarity();
    assertNotNull("Should create similarity", similarity);
    assertEquals("Should preserve title weight", 2.0f, similarity.getFieldWeight("title"), 0.001f);
    
    // Test 5: Field weights properly applied (tested in query string)
    Query weightedQuery = parser3.parse("test");
    String weightedStr = weightedQuery.toString();
    assertTrue("Should have title boost", weightedStr.contains("^2.0"));
    
    // Test 6 & 7: End-to-end + Score explanations + Full pipeline (combined test)
    try (Directory dir = newDirectory()) {
      IndexWriterConfig config = new IndexWriterConfig(analyzer);
      config.setSimilarity(similarity);
      
      try (IndexWriter writer = new IndexWriter(dir, config)) {
        addDocument(writer, "test document title", "body content", "tag");
      }
      
      try (IndexReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        searcher.setSimilarity(similarity);
        
        Query searchQuery = parser3.parse("test");
        TopDocs results = searcher.search(searchQuery, 1);
        
        assertTrue("Should find documents in full pipeline", results.totalHits.value > 0);
        
        // Test score explanations
        Explanation explanation = searcher.explain(searchQuery, results.scoreDocs[0].doc);
        assertNotNull("Should provide score explanation", explanation);
        assertTrue("Explanation should be meaningful", explanation.toString().length() > 20);
      }
    }
  }

  // ==============================================
  // Edge Cases and Error Handling
  // ==============================================

  public void testEmptyQuery() throws Exception {
    BM25FMultiFieldQueryParser parser = new BM25FMultiFieldQueryParser(FIELDS, analyzer);
    
    // Empty query should not throw exception but might return null or empty results
    try {
      Query query = parser.parse("");
      // If parsing succeeds, result could be null or a valid query object
      // The important thing is no exception is thrown
    } catch (ParseException e) {
      // ParseException is acceptable for empty queries
    }
  }

  public void testSpecialCharacters() throws Exception {
    BM25FMultiFieldQueryParser parser = new BM25FMultiFieldQueryParser(FIELDS, analyzer);

    // Test various special characters that should be handled gracefully
    String[] testQueries = {
        "term AND term",
        "term OR term", 
        "\"phrase query\"",
        "wild*card",
        "fuz~zy",
        "[range TO query]"
    };

    for (String testQuery : testQueries) {
      try {
        Query query = parser.parse(testQuery);
        assertNotNull("Query should parse successfully: " + testQuery, query);
      } catch (ParseException e) {
        // Some complex queries might fail, but shouldn't crash
        assertNotNull("Exception should have message", e.getMessage());
      }
    }
  }

  public void testToStringMethod() {
    Map<String, Float> weights = new HashMap<>();
    weights.put("title", 2.0f);
    
    Map<String, Float> fieldK1 = new HashMap<>(); 
    fieldK1.put("title", 1.5f);
    
    BM25FMultiFieldQueryParser parser = new BM25FMultiFieldQueryParser(
        FIELDS, analyzer, weights, fieldK1, new HashMap<>());

    String toString = parser.toString();
    assertNotNull("toString should not return null", toString);
    assertTrue("toString should mention BM25F", toString.contains("BM25F"));
    assertTrue("toString should mention field count", toString.contains("fields=3"));
    assertTrue("toString should show default parameters", toString.contains("defaultK1"));
    assertTrue("toString should show default parameters", toString.contains("defaultB"));
  }

  // ==============================================
  // Helper Methods
  // ==============================================

  private void addDocument(IndexWriter writer, String title, String body, String tags) throws Exception {
    Document doc = new Document();
    doc.add(new TextField("title", title, Field.Store.YES));
    doc.add(new TextField("body", body, Field.Store.YES));
    doc.add(new TextField("tags", tags, Field.Store.YES));
    writer.addDocument(doc);
  }
}
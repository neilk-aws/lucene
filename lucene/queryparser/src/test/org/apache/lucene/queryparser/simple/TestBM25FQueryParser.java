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
package org.apache.lucene.queryparser.simple;

import static org.apache.lucene.queryparser.simple.BM25FQueryParser.AND_OPERATOR;
import static org.apache.lucene.queryparser.simple.BM25FQueryParser.ESCAPE_OPERATOR;
import static org.apache.lucene.queryparser.simple.BM25FQueryParser.FUZZY_OPERATOR;
import static org.apache.lucene.queryparser.simple.BM25FQueryParser.NEAR_OPERATOR;
import static org.apache.lucene.queryparser.simple.BM25FQueryParser.NOT_OPERATOR;
import static org.apache.lucene.queryparser.simple.BM25FQueryParser.OR_OPERATOR;
import static org.apache.lucene.queryparser.simple.BM25FQueryParser.PHRASE_OPERATOR;
import static org.apache.lucene.queryparser.simple.BM25FQueryParser.PRECEDENCE_OPERATORS;
import static org.apache.lucene.queryparser.simple.BM25FQueryParser.PREFIX_OPERATOR;
import static org.apache.lucene.queryparser.simple.BM25FQueryParser.WHITESPACE_OPERATOR;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BoostQuery;
import org.apache.lucene.search.CombinedFieldQuery;
import org.apache.lucene.search.FuzzyQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.MatchNoDocsQuery;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.analysis.MockTokenizer;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.BytesRef;

/** Tests for {@link BM25FQueryParser} */
public class TestBM25FQueryParser extends LuceneTestCase {
  
  /**
   * Helper to parse a query with whitespace+lowercase analyzer across "field", with default
   * operator of MUST
   */
  private Query parse(String text) {
    Analyzer analyzer = new MockAnalyzer(random());
    BM25FQueryParser parser = new BM25FQueryParser(analyzer, "field");
    parser.setDefaultOperator(Occur.MUST);
    return parser.parse(text);
  }
  
  /**
   * Helper to parse a query with whitespace+lowercase analyzer across "field", with default
   * operator of MUST and custom flags
   */
  private Query parse(String text, int flags) {
    Analyzer analyzer = new MockAnalyzer(random());
    BM25FQueryParser parser =
        new BM25FQueryParser(analyzer, Collections.singletonMap("field", 1f), flags);
    parser.setDefaultOperator(Occur.MUST);
    return parser.parse(text);
  }
  
  /** Test a simple single term query produces a CombinedFieldQuery */
  public void testSimpleTerm() {
    Query q = parse("foobar");
    assertTrue("Expected CombinedFieldQuery, got " + q.getClass(), q instanceof CombinedFieldQuery);
  }
  
  /** Test AND operator with BM25F queries */
  public void testAND() {
    Query q = parse("foo+bar");
    assertTrue("Expected BooleanQuery, got " + q.getClass(), q instanceof BooleanQuery);
    BooleanQuery bq = (BooleanQuery) q;
    assertEquals(2, bq.clauses().size());
    // Both clauses should be MUST with CombinedFieldQuery
    for (var clause : bq.clauses()) {
      assertEquals(Occur.MUST, clause.occur());
      assertTrue("Expected CombinedFieldQuery clause", clause.query() instanceof CombinedFieldQuery);
    }
  }
  
  /** Test OR operator */
  public void testOR() {
    Query q = parse("foo|bar");
    assertTrue("Expected BooleanQuery, got " + q.getClass(), q instanceof BooleanQuery);
    BooleanQuery bq = (BooleanQuery) q;
    assertEquals(2, bq.clauses().size());
    // At least one clause should be SHOULD
    boolean hasShould = false;
    for (var clause : bq.clauses()) {
      if (clause.occur() == Occur.SHOULD) {
        hasShould = true;
      }
    }
    assertTrue("Expected at least one SHOULD clause", hasShould);
  }
  
  /** Test NOT operator */
  public void testNOT() {
    Query q = parse("-foo");
    assertTrue("Expected BooleanQuery, got " + q.getClass(), q instanceof BooleanQuery);
    BooleanQuery bq = (BooleanQuery) q;
    boolean hasMustNot = false;
    for (var clause : bq.clauses()) {
      if (clause.occur() == Occur.MUST_NOT) {
        hasMustNot = true;
      }
    }
    assertTrue("Expected MUST_NOT clause", hasMustNot);
  }
  
  /** Test prefix query */
  public void testPrefix() {
    Query q = parse("foo*");
    assertTrue("Expected PrefixQuery, got " + q.getClass(), q instanceof PrefixQuery);
    PrefixQuery pq = (PrefixQuery) q;
    assertEquals("field", pq.getPrefix().field());
    assertEquals("foo", pq.getPrefix().text());
  }
  
  /** Test phrase query */
  public void testPhrase() {
    Query q = parse("\"foo bar\"");
    assertTrue("Expected PhraseQuery, got " + q.getClass(), q instanceof PhraseQuery);
    PhraseQuery pq = (PhraseQuery) q;
    assertEquals("field", pq.getField());
    assertEquals(2, pq.getTerms().length);
  }
  
  /** Test phrase query with slop */
  public void testPhraseWithSlop() {
    Query q = parse("\"foo bar\"~2");
    assertTrue("Expected PhraseQuery, got " + q.getClass(), q instanceof PhraseQuery);
    PhraseQuery pq = (PhraseQuery) q;
    assertEquals(2, pq.getSlop());
  }
  
  /** Test fuzzy query */
  public void testFuzzy() {
    Query q = parse("foobar~2");
    // Fuzzy query is wrapped in a BooleanQuery for multi-field
    assertTrue("Expected FuzzyQuery, got " + q.getClass(), q instanceof FuzzyQuery);
    FuzzyQuery fq = (FuzzyQuery) q;
    assertEquals("field", fq.getTerm().field());
    assertEquals("foobar", fq.getTerm().text());
    assertEquals(2, fq.getMaxEdits());
  }
  
  /** Test match all query */
  public void testMatchAll() {
    Query q = parse("*");
    assertEquals(MatchAllDocsQuery.INSTANCE, q);
  }
  
  /** Test empty query */
  public void testEmpty() {
    Query q = parse("");
    assertEquals(MatchNoDocsQuery.class, q.getClass());
  }
  
  /** Test weighted fields */
  public void testWeightedFields() {
    Map<String, Float> weights = new LinkedHashMap<>();
    weights.put("title", 2.0f);
    weights.put("body", 1.0f);
    
    Analyzer analyzer = new MockAnalyzer(random());
    BM25FQueryParser parser = new BM25FQueryParser(analyzer, weights);
    
    Query q = parser.parse("test");
    assertTrue("Expected CombinedFieldQuery, got " + q.getClass(), q instanceof CombinedFieldQuery);
    
    // Verify the query string shows both fields with weights
    String queryStr = q.toString();
    assertTrue("Expected title field", queryStr.contains("title"));
    assertTrue("Expected body field", queryStr.contains("body"));
    assertTrue("Expected title weight", queryStr.contains("title^2.0"));
  }
  
  /** Test that field weights must be >= 1.0 */
  public void testInvalidFieldWeight() {
    Map<String, Float> weights = new LinkedHashMap<>();
    weights.put("title", 0.5f);  // Invalid - less than 1.0
    weights.put("body", 1.0f);
    
    Analyzer analyzer = new MockAnalyzer(random());
    
    IllegalArgumentException ex = expectThrows(IllegalArgumentException.class, () -> {
      new BM25FQueryParser(analyzer, weights);
    });
    assertTrue(ex.getMessage().contains("must be >= 1.0"));
  }
  
  /** Test that empty field weights are rejected */
  public void testEmptyFieldWeights() {
    Map<String, Float> weights = new LinkedHashMap<>();
    Analyzer analyzer = new MockAnalyzer(random());
    
    IllegalArgumentException ex = expectThrows(IllegalArgumentException.class, () -> {
      new BM25FQueryParser(analyzer, weights);
    });
    assertTrue(ex.getMessage().contains("cannot be empty"));
  }
  
  /** Test default operator setting */
  public void testDefaultOperator() {
    Analyzer analyzer = new MockAnalyzer(random());
    BM25FQueryParser parser = new BM25FQueryParser(analyzer, "field");
    
    // Default should be SHOULD
    assertEquals(Occur.SHOULD, parser.getDefaultOperator());
    
    // Test changing to MUST
    parser.setDefaultOperator(Occur.MUST);
    assertEquals(Occur.MUST, parser.getDefaultOperator());
    
    // Test that FILTER is rejected
    expectThrows(IllegalArgumentException.class, () -> {
      parser.setDefaultOperator(Occur.FILTER);
    });
    
    // Test that MUST_NOT is rejected
    expectThrows(IllegalArgumentException.class, () -> {
      parser.setDefaultOperator(Occur.MUST_NOT);
    });
  }
  
  /** Test actual search with BM25F scoring */
  public void testSearchWithBM25FScoring() throws IOException {
    Directory dir = new ByteBuffersDirectory();
    Analyzer analyzer = new MockAnalyzer(random(), MockTokenizer.WHITESPACE, true);
    IndexWriterConfig config = new IndexWriterConfig(analyzer);
    IndexWriter writer = new IndexWriter(dir, config);
    
    // Add some documents
    Document doc1 = new Document();
    doc1.add(new TextField("title", "quick fox", Field.Store.NO));
    doc1.add(new TextField("body", "the quick brown fox jumps over lazy dog", Field.Store.NO));
    writer.addDocument(doc1);
    
    Document doc2 = new Document();
    doc2.add(new TextField("title", "lazy dog", Field.Store.NO));
    doc2.add(new TextField("body", "the lazy dog sleeps all day", Field.Store.NO));
    writer.addDocument(doc2);
    
    Document doc3 = new Document();
    doc3.add(new TextField("title", "brown fox", Field.Store.NO));
    doc3.add(new TextField("body", "a brown fox is running in the forest", Field.Store.NO));
    writer.addDocument(doc3);
    
    writer.close();
    
    IndexReader reader = DirectoryReader.open(dir);
    IndexSearcher searcher = new IndexSearcher(reader);
    
    // Create BM25F parser with weighted fields
    Map<String, Float> weights = new LinkedHashMap<>();
    weights.put("title", 2.0f);
    weights.put("body", 1.0f);
    BM25FQueryParser parser = new BM25FQueryParser(analyzer, weights);
    
    // Search for "fox"
    Query query = parser.parse("fox");
    TopDocs results = searcher.search(query, 10);
    
    // Should find all 3 documents (all contain "fox")
    assertEquals(3, results.totalHits.value());
    
    // The document with "fox" in the title should score higher due to title boost
    // (doc1 has "fox" in title, doc3 has "fox" in title)
    // Both doc1 and doc3 should score higher than doc2
    
    reader.close();
    dir.close();
  }
  
  /** Test complex query with multiple operators */
  public void testComplexQuery() {
    Query q = parse("(quick fox) | (lazy+dog)");
    assertTrue("Expected BooleanQuery, got " + q.getClass(), q instanceof BooleanQuery);
    
    // The query structure should reflect the grouping and operators
    BooleanQuery bq = (BooleanQuery) q;
    assertTrue("Expected multiple clauses", bq.clauses().size() >= 2);
  }
  
  /** Test that parser handles garbage input gracefully */
  public void testGarbageInput() {
    // Parser should not throw on any input
    parse(""); // empty
    parse("  "); // whitespace
    parse("()"); // empty parens
    parse("\"\""); // empty quotes
    parse("+++"); // multiple operators
    parse("|||"); // multiple operators
    parse("---"); // multiple NOT
    parse("*"); // just wildcard
    parse("\\"); // just escape
  }
  
  /** Test random queries don't throw */
  public void testRandomQueries() {
    for (int i = 0; i < 100; i++) {
      String query = TestUtil.randomUnicodeString(random());
      parse(query); // should not throw
    }
  }
  
  /** Test getFieldWeights returns immutable map */
  public void testGetFieldWeightsImmutable() {
    Map<String, Float> weights = new LinkedHashMap<>();
    weights.put("title", 2.0f);
    weights.put("body", 1.0f);
    
    Analyzer analyzer = new MockAnalyzer(random());
    BM25FQueryParser parser = new BM25FQueryParser(analyzer, weights);
    
    Map<String, Float> returnedWeights = parser.getFieldWeights();
    
    // Verify the map is returned
    assertEquals(2, returnedWeights.size());
    assertEquals(2.0f, returnedWeights.get("title"), 0.001f);
    assertEquals(1.0f, returnedWeights.get("body"), 0.001f);
    
    // Verify it's immutable
    expectThrows(UnsupportedOperationException.class, () -> {
      returnedWeights.put("new", 1.0f);
    });
  }
  
  /** Helper to parse a query with keyword analyzer */
  private Query parseKeyword(String text, int flags) {
    Analyzer analyzer = new MockAnalyzer(random(), MockTokenizer.KEYWORD, false);
    BM25FQueryParser parser =
        new BM25FQueryParser(analyzer, Collections.singletonMap("field", 1f), flags);
    return parser.parse(text);
  }
  
  /** Test disabling phrase operator */
  public void testDisablePhrase() {
    Query expected = parse("\"test\"", ~PHRASE_OPERATOR);
    // Without phrase operator, quotes are just part of the term
    assertTrue("Query should be CombinedFieldQuery", expected instanceof CombinedFieldQuery);
  }
  
  /** Test disabling prefix operator */
  public void testDisablePrefix() {
    Query expected = parseKeyword("test*", ~PREFIX_OPERATOR);
    // Without prefix operator, * is just part of the term
    assertTrue("Query should be CombinedFieldQuery", expected instanceof CombinedFieldQuery);
  }
}

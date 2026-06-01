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
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.CombinedFieldQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchNoDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.TopScoreDocCollectorManager;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.index.RandomIndexWriter;
import org.apache.lucene.tests.util.LuceneTestCase;

/** Tests for {@link BM25FQueryParser} */
public class TestBM25FQueryParser extends LuceneTestCase {

  /** Test parsing a single term produces a CombinedFieldQuery with all configured fields. */
  public void testSingleTerm() {
    Map<String, Float> weights = new LinkedHashMap<>();
    weights.put("title", 5f);
    weights.put("body", 1f);
    BM25FQueryParser parser = new BM25FQueryParser(new MockAnalyzer(random()), weights);
    Query result = parser.parse("foo");

    CombinedFieldQuery expected =
        new CombinedFieldQuery.Builder("foo").addField("title", 5f).addField("body", 1f).build();
    assertEquals(expected, result);
  }

  /** Test parsing multiple terms with default OR operator. */
  public void testMultipleTermsOR() {
    Map<String, Float> weights = new LinkedHashMap<>();
    weights.put("title", 5f);
    weights.put("body", 1f);
    BM25FQueryParser parser = new BM25FQueryParser(new MockAnalyzer(random()), weights);
    Query result = parser.parse("foo bar");

    CombinedFieldQuery cfqFoo =
        new CombinedFieldQuery.Builder("foo").addField("title", 5f).addField("body", 1f).build();
    CombinedFieldQuery cfqBar =
        new CombinedFieldQuery.Builder("bar").addField("title", 5f).addField("body", 1f).build();
    BooleanQuery expected =
        new BooleanQuery.Builder()
            .add(cfqFoo, BooleanClause.Occur.SHOULD)
            .add(cfqBar, BooleanClause.Occur.SHOULD)
            .build();
    assertEquals(expected, result);
  }

  /** Test parsing multiple terms with AND as the default operator. */
  public void testMultipleTermsAND() {
    Map<String, Float> weights = new LinkedHashMap<>();
    weights.put("title", 5f);
    weights.put("body", 1f);
    BM25FQueryParser parser = new BM25FQueryParser(new MockAnalyzer(random()), weights);
    parser.setDefaultOperator(BooleanClause.Occur.MUST);
    Query result = parser.parse("foo bar");

    CombinedFieldQuery cfqFoo =
        new CombinedFieldQuery.Builder("foo").addField("title", 5f).addField("body", 1f).build();
    CombinedFieldQuery cfqBar =
        new CombinedFieldQuery.Builder("bar").addField("title", 5f).addField("body", 1f).build();
    BooleanQuery expected =
        new BooleanQuery.Builder()
            .add(cfqFoo, BooleanClause.Occur.MUST)
            .add(cfqBar, BooleanClause.Occur.MUST)
            .build();
    assertEquals(expected, result);
  }

  /** Test explicit AND operator (+) between terms. */
  public void testANDOperator() {
    Map<String, Float> weights = new LinkedHashMap<>();
    weights.put("title", 5f);
    weights.put("body", 1f);
    BM25FQueryParser parser = new BM25FQueryParser(new MockAnalyzer(random()), weights);
    Query result = parser.parse("foo+bar");

    CombinedFieldQuery cfqFoo =
        new CombinedFieldQuery.Builder("foo").addField("title", 5f).addField("body", 1f).build();
    CombinedFieldQuery cfqBar =
        new CombinedFieldQuery.Builder("bar").addField("title", 5f).addField("body", 1f).build();
    BooleanQuery expected =
        new BooleanQuery.Builder()
            .add(cfqFoo, BooleanClause.Occur.MUST)
            .add(cfqBar, BooleanClause.Occur.MUST)
            .build();
    assertEquals(expected, result);
  }

  /** Test explicit OR operator (|) between terms. */
  public void testOROperator() {
    Map<String, Float> weights = new LinkedHashMap<>();
    weights.put("title", 5f);
    weights.put("body", 1f);
    BM25FQueryParser parser = new BM25FQueryParser(new MockAnalyzer(random()), weights);
    parser.setDefaultOperator(BooleanClause.Occur.MUST);
    Query result = parser.parse("foo|bar");

    CombinedFieldQuery cfqFoo =
        new CombinedFieldQuery.Builder("foo").addField("title", 5f).addField("body", 1f).build();
    CombinedFieldQuery cfqBar =
        new CombinedFieldQuery.Builder("bar").addField("title", 5f).addField("body", 1f).build();
    BooleanQuery expected =
        new BooleanQuery.Builder()
            .add(cfqFoo, BooleanClause.Occur.SHOULD)
            .add(cfqBar, BooleanClause.Occur.SHOULD)
            .build();
    assertEquals(expected, result);
  }

  /** Test NOT operator (-) negating a term. */
  public void testNOTOperator() {
    Map<String, Float> weights = new LinkedHashMap<>();
    weights.put("title", 5f);
    weights.put("body", 1f);
    BM25FQueryParser parser = new BM25FQueryParser(new MockAnalyzer(random()), weights);
    Query result = parser.parse("-foo bar");

    CombinedFieldQuery cfqFoo =
        new CombinedFieldQuery.Builder("foo").addField("title", 5f).addField("body", 1f).build();
    CombinedFieldQuery cfqBar =
        new CombinedFieldQuery.Builder("bar").addField("title", 5f).addField("body", 1f).build();
    BooleanQuery expected =
        new BooleanQuery.Builder()
            .add(cfqFoo, BooleanClause.Occur.MUST_NOT)
            .add(cfqBar, BooleanClause.Occur.SHOULD)
            .build();
    assertEquals(expected, result);
  }

  /** Test that phrase queries fall back to per-field behavior from SimpleQueryParser. */
  public void testPhraseQuery() {
    Map<String, Float> weights = new LinkedHashMap<>();
    weights.put("title", 5f);
    weights.put("body", 1f);
    BM25FQueryParser parser = new BM25FQueryParser(new MockAnalyzer(random()), weights);
    Query result = parser.parse("\"foo bar\"");

    // Phrase queries should not be CombinedFieldQuery since it only supports single terms.
    // It should use the default SimpleQueryParser behavior (per-field phrase queries).
    assertFalse(
        "Phrase queries should not produce CombinedFieldQuery",
        result instanceof CombinedFieldQuery);
    assertTrue(
        "Phrase queries should produce BooleanQuery with per-field clauses",
        result instanceof BooleanQuery);
  }

  /** Test that prefix queries fall back to per-field behavior from SimpleQueryParser. */
  public void testPrefixQuery() {
    Map<String, Float> weights = new LinkedHashMap<>();
    weights.put("title", 5f);
    weights.put("body", 1f);
    BM25FQueryParser parser = new BM25FQueryParser(new MockAnalyzer(random()), weights);
    Query result = parser.parse("foo*");

    // Prefix queries should not be CombinedFieldQuery since it only supports exact terms.
    assertFalse(
        "Prefix queries should not produce CombinedFieldQuery",
        result instanceof CombinedFieldQuery);
  }

  /** Test that fuzzy queries fall back to per-field behavior from SimpleQueryParser. */
  public void testFuzzyQuery() {
    Map<String, Float> weights = new LinkedHashMap<>();
    weights.put("title", 5f);
    weights.put("body", 1f);
    BM25FQueryParser parser = new BM25FQueryParser(new MockAnalyzer(random()), weights);
    Query result = parser.parse("foo~1");

    // Fuzzy queries should not be CombinedFieldQuery since it only supports exact terms.
    assertFalse(
        "Fuzzy queries should not produce CombinedFieldQuery",
        result instanceof CombinedFieldQuery);
  }

  /** Test that field weights are correctly passed to CombinedFieldQuery. */
  public void testFieldWeights() {
    Map<String, Float> weights = new LinkedHashMap<>();
    weights.put("title", 3f);
    weights.put("body", 2f);
    weights.put("author", 1f);
    BM25FQueryParser parser = new BM25FQueryParser(new MockAnalyzer(random()), weights);
    Query result = parser.parse("test");

    CombinedFieldQuery expected =
        new CombinedFieldQuery.Builder("test")
            .addField("title", 3f)
            .addField("body", 2f)
            .addField("author", 1f)
            .build();
    assertEquals(expected, result);
  }

  /** Test that weights less than 1.0 throw IllegalArgumentException. */
  public void testInvalidWeights() {
    Map<String, Float> weights = new LinkedHashMap<>();
    weights.put("title", 5f);
    weights.put("body", 0.5f);

    IllegalArgumentException e =
        expectThrows(
            IllegalArgumentException.class,
            () -> new BM25FQueryParser(new MockAnalyzer(random()), weights));
    assertTrue(e.getMessage().contains("body"));
    assertTrue(e.getMessage().contains("greater or equal to 1"));
  }

  /** Test that empty string returns MatchNoDocsQuery. */
  public void testEmptyQuery() {
    Map<String, Float> weights = new LinkedHashMap<>();
    weights.put("title", 5f);
    weights.put("body", 1f);
    BM25FQueryParser parser = new BM25FQueryParser(new MockAnalyzer(random()), weights);
    Query result = parser.parse("");

    assertTrue(
        "Empty query should produce MatchNoDocsQuery", result instanceof MatchNoDocsQuery);
  }

  /** Test that a single field still works correctly. */
  public void testSingleField() {
    Map<String, Float> weights = new LinkedHashMap<>();
    weights.put("content", 1f);
    BM25FQueryParser parser = new BM25FQueryParser(new MockAnalyzer(random()), weights);
    Query result = parser.parse("hello");

    CombinedFieldQuery expected =
        new CombinedFieldQuery.Builder("hello").addField("content", 1f).build();
    assertEquals(expected, result);
  }

  /** Test set default operator to MUST. */
  public void testSetDefaultOperator() {
    Map<String, Float> weights = new LinkedHashMap<>();
    weights.put("title", 5f);
    weights.put("body", 1f);
    BM25FQueryParser parser = new BM25FQueryParser(new MockAnalyzer(random()), weights);
    parser.setDefaultOperator(BooleanClause.Occur.MUST);

    Query result = parser.parse("hello world");
    CombinedFieldQuery cfqHello =
        new CombinedFieldQuery.Builder("hello")
            .addField("title", 5f)
            .addField("body", 1f)
            .build();
    CombinedFieldQuery cfqWorld =
        new CombinedFieldQuery.Builder("world")
            .addField("title", 5f)
            .addField("body", 1f)
            .build();
    BooleanQuery expected =
        new BooleanQuery.Builder()
            .add(cfqHello, BooleanClause.Occur.MUST)
            .add(cfqWorld, BooleanClause.Occur.MUST)
            .build();
    assertEquals(expected, result);
  }

  /**
   * Integration test: create an index, search with BM25FQueryParser, and verify results match
   * manually-constructed CombinedFieldQuery.
   */
  public void testSearchIntegration() throws IOException {
    Directory dir = newDirectory();
    RandomIndexWriter w = new RandomIndexWriter(random(), dir);

    Document doc1 = new Document();
    doc1.add(new TextField("title", "quick brown fox", Field.Store.NO));
    doc1.add(new TextField("body", "the fox jumped over the lazy dog", Field.Store.NO));
    w.addDocument(doc1);

    Document doc2 = new Document();
    doc2.add(new TextField("title", "lazy dog", Field.Store.NO));
    doc2.add(new TextField("body", "the quick brown fox", Field.Store.NO));
    w.addDocument(doc2);

    Document doc3 = new Document();
    doc3.add(new TextField("title", "unrelated content", Field.Store.NO));
    doc3.add(new TextField("body", "nothing interesting here", Field.Store.NO));
    w.addDocument(doc3);

    IndexReader reader = w.getReader();
    IndexSearcher searcher = newSearcher(reader);

    Map<String, Float> weights = new LinkedHashMap<>();
    weights.put("title", 5f);
    weights.put("body", 1f);

    // Parse a query using BM25FQueryParser
    BM25FQueryParser parser = new BM25FQueryParser(new MockAnalyzer(random()), weights);
    Query parsedQuery = parser.parse("fox");

    // Manually construct the equivalent CombinedFieldQuery
    CombinedFieldQuery manualQuery =
        new CombinedFieldQuery.Builder("fox").addField("title", 5f).addField("body", 1f).build();

    // Verify both queries produce the same results and scores
    assertEquals(parsedQuery, manualQuery);

    TopScoreDocCollectorManager collectorManager =
        new TopScoreDocCollectorManager(10, Integer.MAX_VALUE);
    TopDocs parsedResults = searcher.search(parsedQuery, collectorManager);

    TopScoreDocCollectorManager collectorManager2 =
        new TopScoreDocCollectorManager(10, Integer.MAX_VALUE);
    TopDocs manualResults = searcher.search(manualQuery, collectorManager2);

    assertEquals(parsedResults.totalHits, manualResults.totalHits);
    assertEquals(parsedResults.scoreDocs.length, manualResults.scoreDocs.length);
    for (int i = 0; i < parsedResults.scoreDocs.length; i++) {
      assertEquals(parsedResults.scoreDocs[i].score, manualResults.scoreDocs[i].score, 0.0f);
      assertEquals(parsedResults.scoreDocs[i].doc, manualResults.scoreDocs[i].doc);
    }

    reader.close();
    w.close();
    dir.close();
  }
}

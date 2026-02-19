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

import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.CombinedFieldQuery;
import org.apache.lucene.search.FuzzyQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.MatchNoDocsQuery;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.index.RandomIndexWriter;
import org.apache.lucene.tests.util.LuceneTestCase;

/** Tests for {@link BM25FQueryParser} */
public class TestBM25FQueryParser extends LuceneTestCase {

  /**
   * Helper to parse a query with a single field "field" and default operator MUST. MockAnalyzer
   * lowercases by default.
   */
  private Query parse(String text) {
    Analyzer analyzer = new MockAnalyzer(random());
    BM25FQueryParser parser = new BM25FQueryParser(analyzer, "field");
    parser.setDefaultOperator(Occur.MUST);
    return parser.parse(text);
  }

  /** Helper to parse a query with custom field weights and default operator MUST. */
  private Query parseWeighted(String text, Map<String, Float> weights) {
    Analyzer analyzer = new MockAnalyzer(random());
    BM25FQueryParser parser = new BM25FQueryParser(analyzer, weights);
    parser.setDefaultOperator(Occur.MUST);
    return parser.parse(text);
  }

  /** test a single term produces a CombinedFieldQuery */
  public void testSingleTerm() throws Exception {
    CombinedFieldQuery expected =
        new CombinedFieldQuery.Builder("foobar").addField("field", 1.0f).build();

    assertEquals(expected, parse("foobar"));
  }

  /** test a single term with two fields produces a CombinedFieldQuery spanning both fields */
  public void testSingleTermMultiField() throws Exception {
    Map<String, Float> weights = new LinkedHashMap<>();
    weights.put("field0", 1.0f);
    weights.put("field1", 1.0f);

    // CombinedFieldQuery uses TreeMap internally, so fields are alphabetically ordered
    CombinedFieldQuery expected =
        new CombinedFieldQuery.Builder("foobar")
            .addField("field0", 1.0f)
            .addField("field1", 1.0f)
            .build();

    assertEquals(expected, parseWeighted("foobar", weights));
  }

  /** test multi-term with default operator MUST produces BooleanQuery of CombinedFieldQueries */
  public void testMultiTermAND() throws Exception {
    CombinedFieldQuery fooQuery =
        new CombinedFieldQuery.Builder("foo").addField("field", 1.0f).build();
    CombinedFieldQuery barQuery =
        new CombinedFieldQuery.Builder("bar").addField("field", 1.0f).build();

    BooleanQuery.Builder expected = new BooleanQuery.Builder();
    expected.add(fooQuery, Occur.MUST);
    expected.add(barQuery, Occur.MUST);

    assertEquals(expected.build(), parse("foo bar"));
  }

  /** test OR operator produces BooleanQuery with SHOULD clauses of CombinedFieldQueries */
  public void testMultiTermOR() throws Exception {
    CombinedFieldQuery fooQuery =
        new CombinedFieldQuery.Builder("foo").addField("field", 1.0f).build();
    CombinedFieldQuery barQuery =
        new CombinedFieldQuery.Builder("bar").addField("field", 1.0f).build();

    BooleanQuery.Builder expected = new BooleanQuery.Builder();
    expected.add(fooQuery, Occur.SHOULD);
    expected.add(barQuery, Occur.SHOULD);

    assertEquals(expected.build(), parse("foo|bar"));
  }

  /** test NOT operator produces MUST_NOT + MatchAllDocsQuery */
  public void testNOT() throws Exception {
    CombinedFieldQuery fooQuery =
        new CombinedFieldQuery.Builder("foo").addField("field", 1.0f).build();

    BooleanQuery.Builder inner = new BooleanQuery.Builder();
    inner.add(fooQuery, Occur.MUST_NOT);
    inner.add(MatchAllDocsQuery.INSTANCE, Occur.SHOULD);

    assertEquals(inner.build(), parse("-foo"));
  }

  /** test phrase query falls back to per-field PhraseQuery, not CombinedFieldQuery */
  public void testPhrase() throws Exception {
    // With single field, simplify() unwraps the single-clause BooleanQuery
    PhraseQuery expected = new PhraseQuery("field", "foo", "bar");

    assertEquals(expected, parse("\"foo bar\""));
  }

  /** test prefix query falls back to per-field PrefixQuery */
  public void testPrefix() throws Exception {
    // With single field, simplify() unwraps the single-clause BooleanQuery
    PrefixQuery expected = new PrefixQuery(new Term("field", "foo"));

    assertEquals(expected, parse("foo*"));
  }

  /** test fuzzy query falls back to per-field FuzzyQuery */
  public void testFuzzy() throws Exception {
    // With single field, simplify() unwraps the single-clause BooleanQuery
    FuzzyQuery expected = new FuzzyQuery(new Term("field", "foo"), 2);

    assertEquals(expected, parse("foo~2"));
  }

  /** test weighted fields produce CombinedFieldQuery with those weights */
  public void testWeightedFields() throws Exception {
    Map<String, Float> weights = new LinkedHashMap<>();
    weights.put("field0", 5.0f);
    weights.put("field1", 10.0f);

    // Weights are >= 1 so no normalization needed
    CombinedFieldQuery expected =
        new CombinedFieldQuery.Builder("foo")
            .addField("field0", 5.0f)
            .addField("field1", 10.0f)
            .build();

    assertEquals(expected, parseWeighted("foo", weights));
  }

  /** test precedence with parentheses produces correct query tree */
  public void testPrecedence() throws Exception {
    CombinedFieldQuery fooQuery =
        new CombinedFieldQuery.Builder("foo").addField("field", 1.0f).build();
    CombinedFieldQuery barQuery =
        new CombinedFieldQuery.Builder("bar").addField("field", 1.0f).build();
    CombinedFieldQuery bazQuery =
        new CombinedFieldQuery.Builder("baz").addField("field", 1.0f).build();

    // "(foo|bar)+baz" -> BooleanQuery(MUST: [BooleanQuery(SHOULD: foo, bar), baz])
    BooleanQuery.Builder inner = new BooleanQuery.Builder();
    inner.add(fooQuery, Occur.SHOULD);
    inner.add(barQuery, Occur.SHOULD);

    BooleanQuery.Builder outer = new BooleanQuery.Builder();
    outer.add(inner.build(), Occur.MUST);
    outer.add(bazQuery, Occur.MUST);

    assertEquals(outer.build(), parse("(foo|bar)+baz"));
  }

  /** test empty query returns MatchNoDocsQuery */
  public void testEmptyQuery() throws Exception {
    assertEquals(MatchNoDocsQuery.INSTANCE, parse(""));
  }

  /** test * returns MatchAllDocsQuery */
  public void testMatchAll() throws Exception {
    assertEquals(MatchAllDocsQuery.INSTANCE, parse("*"));
    assertEquals(MatchAllDocsQuery.INSTANCE, parse(" *   "));
  }

  /** test that weights below 1.0 are normalized so minimum is 1.0 */
  public void testWeightNormalization() throws Exception {
    Map<String, Float> weights = new LinkedHashMap<>();
    weights.put("a", 0.5f);
    weights.put("b", 1.0f);

    // minWeight=0.5, factor=2.0 -> normalized weights: a=1.0, b=2.0
    CombinedFieldQuery expected =
        new CombinedFieldQuery.Builder("foo").addField("a", 1.0f).addField("b", 2.0f).build();

    assertEquals(expected, parseWeighted("foo", weights));
  }

  /** integration test: create an index, search with BM25FQueryParser, verify results */
  public void testIntegrationSearch() throws Exception {
    Directory dir = newDirectory();
    Analyzer analyzer = new MockAnalyzer(random());
    IndexWriterConfig iwc = newIndexWriterConfig(analyzer);
    RandomIndexWriter iw = new RandomIndexWriter(random(), dir, iwc);

    Document doc1 = new Document();
    doc1.add(new TextField("title", "lucene search engine", TextField.Store.NO));
    doc1.add(new TextField("body", "apache lucene is a search library", TextField.Store.NO));
    iw.addDocument(doc1);

    Document doc2 = new Document();
    doc2.add(new TextField("title", "solr platform", TextField.Store.NO));
    doc2.add(new TextField("body", "solr is built on lucene", TextField.Store.NO));
    iw.addDocument(doc2);

    Document doc3 = new Document();
    doc3.add(new TextField("title", "unrelated document", TextField.Store.NO));
    doc3.add(new TextField("body", "nothing relevant here", TextField.Store.NO));
    iw.addDocument(doc3);

    IndexReader reader = iw.getReader();
    iw.close();

    IndexSearcher searcher = newSearcher(reader);

    Map<String, Float> weights = new LinkedHashMap<>();
    weights.put("title", 2.0f);
    weights.put("body", 1.0f);

    BM25FQueryParser parser = new BM25FQueryParser(analyzer, weights);
    Query query = parser.parse("lucene");

    // The query should be a CombinedFieldQuery
    assertTrue(
        "Expected CombinedFieldQuery but got: " + query.getClass().getName(),
        query instanceof CombinedFieldQuery);

    TopDocs topDocs = searcher.search(query, 10);
    // doc1 has "lucene" in both title and body, doc2 has "lucene" in body
    assertEquals(2, topDocs.totalHits.value());

    reader.close();
    dir.close();
  }

  /** test that default operator can be get/set */
  public void testDefaultOperator() throws Exception {
    Analyzer analyzer = new MockAnalyzer(random());
    BM25FQueryParser parser = new BM25FQueryParser(analyzer, "field");

    // Default is SHOULD
    assertEquals(Occur.SHOULD, parser.getDefaultOperator());

    parser.setDefaultOperator(Occur.MUST);
    assertEquals(Occur.MUST, parser.getDefaultOperator());

    // Invalid operator should throw
    expectThrows(
        IllegalArgumentException.class, () -> parser.setDefaultOperator(Occur.MUST_NOT));
  }

  /** test phrase query with multiple fields falls back to BooleanQuery of PhraseQueries */
  public void testPhraseMultiField() throws Exception {
    Map<String, Float> weights = new LinkedHashMap<>();
    weights.put("field0", 1.0f);
    weights.put("field1", 1.0f);

    PhraseQuery pq0 = new PhraseQuery("field0", "foo", "bar");
    PhraseQuery pq1 = new PhraseQuery("field1", "foo", "bar");

    BooleanQuery.Builder expected = new BooleanQuery.Builder();
    expected.add(pq0, Occur.SHOULD);
    expected.add(pq1, Occur.SHOULD);

    assertEquals(expected.build(), parseWeighted("\"foo bar\"", weights));
  }
}

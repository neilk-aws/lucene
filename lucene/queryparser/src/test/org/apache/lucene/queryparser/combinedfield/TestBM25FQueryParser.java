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
package org.apache.lucene.queryparser.combinedfield;

import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.CombinedFieldQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.MatchNoDocsQuery;
import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.index.RandomIndexWriter;
import org.apache.lucene.tests.util.LuceneTestCase;

/** Tests for {@link BM25FQueryParser} */
public class TestBM25FQueryParser extends LuceneTestCase {

  private BM25FQueryParser createParser(Map<String, Float> weights) {
    Analyzer analyzer = new MockAnalyzer(random());
    return new BM25FQueryParser(analyzer, weights);
  }

  /** test a simple term produces a CombinedFieldQuery */
  public void testSimpleTerm() throws Exception {
    Map<String, Float> weights = new LinkedHashMap<>();
    weights.put("field1", 1.0f);
    weights.put("field2", 1.0f);
    BM25FQueryParser parser = createParser(weights);

    Query result = parser.parse("foobar");
    assertTrue(
        "Expected CombinedFieldQuery but got " + result.getClass().getSimpleName(),
        result instanceof CombinedFieldQuery);
  }

  /** test multiple terms with default OR operator */
  public void testMultipleTermsOr() throws Exception {
    Map<String, Float> weights = new LinkedHashMap<>();
    weights.put("field1", 1.0f);
    weights.put("field2", 1.0f);
    BM25FQueryParser parser = createParser(weights);

    Query result = parser.parse("foo bar");
    assertTrue(
        "Expected BooleanQuery but got " + result.getClass().getSimpleName(),
        result instanceof BooleanQuery);

    BooleanQuery bq = (BooleanQuery) result;
    assertEquals(2, bq.clauses().size());
    for (BooleanClause clause : bq) {
      assertEquals(BooleanClause.Occur.SHOULD, clause.occur());
      assertTrue(
          "Expected CombinedFieldQuery clause but got "
              + clause.query().getClass().getSimpleName(),
          clause.query() instanceof CombinedFieldQuery);
    }
  }

  /** test multiple terms with AND operator */
  public void testMultipleTermsAnd() throws Exception {
    Map<String, Float> weights = new LinkedHashMap<>();
    weights.put("field1", 1.0f);
    weights.put("field2", 1.0f);
    BM25FQueryParser parser = createParser(weights);
    parser.setDefaultOperator(BooleanClause.Occur.MUST);

    Query result = parser.parse("foo bar");
    assertTrue(
        "Expected BooleanQuery but got " + result.getClass().getSimpleName(),
        result instanceof BooleanQuery);

    BooleanQuery bq = (BooleanQuery) result;
    assertEquals(2, bq.clauses().size());
    for (BooleanClause clause : bq) {
      assertEquals(BooleanClause.Occur.MUST, clause.occur());
      assertTrue(
          "Expected CombinedFieldQuery clause but got "
              + clause.query().getClass().getSimpleName(),
          clause.query() instanceof CombinedFieldQuery);
    }
  }

  /** test field weights are reflected in the query */
  public void testFieldWeights() throws Exception {
    Map<String, Float> weights = new LinkedHashMap<>();
    weights.put("field1", 3.0f);
    weights.put("field2", 1.0f);
    BM25FQueryParser parser = createParser(weights);

    Query result = parser.parse("foo");
    assertTrue(
        "Expected CombinedFieldQuery but got " + result.getClass().getSimpleName(),
        result instanceof CombinedFieldQuery);
    assertTrue(
        "Expected toString to contain field1^3.0 but got: " + result.toString(),
        result.toString().contains("field1^3.0"));
  }

  /** test phrase query falls back to per-field queries */
  public void testPhrase() throws Exception {
    Map<String, Float> weights = new LinkedHashMap<>();
    weights.put("field1", 1.0f);
    weights.put("field2", 1.0f);
    BM25FQueryParser parser = createParser(weights);

    Query result = parser.parse("\"foo bar\"");
    assertFalse(
        "Phrase query should not produce CombinedFieldQuery",
        result instanceof CombinedFieldQuery);
  }

  /** test prefix query falls back to per-field queries */
  public void testPrefix() throws Exception {
    Map<String, Float> weights = new LinkedHashMap<>();
    weights.put("field1", 1.0f);
    weights.put("field2", 1.0f);
    BM25FQueryParser parser = createParser(weights);

    Query result = parser.parse("foobar*");
    assertFalse(
        "Prefix query should not produce CombinedFieldQuery",
        result instanceof CombinedFieldQuery);

    // The result should contain PrefixQuery somewhere in the tree
    if (result instanceof BooleanQuery) {
      boolean foundPrefix = false;
      for (BooleanClause clause : (BooleanQuery) result) {
        if (clause.query() instanceof PrefixQuery) {
          foundPrefix = true;
          break;
        }
      }
      assertTrue("Expected at least one PrefixQuery clause", foundPrefix);
    } else {
      assertTrue(
          "Expected PrefixQuery but got " + result.getClass().getSimpleName(),
          result instanceof PrefixQuery);
    }
  }

  /** test NOT operator */
  public void testNot() throws Exception {
    Map<String, Float> weights = new LinkedHashMap<>();
    weights.put("field1", 1.0f);
    weights.put("field2", 1.0f);
    BM25FQueryParser parser = createParser(weights);

    Query result = parser.parse("-foo");
    assertTrue(
        "Expected BooleanQuery but got " + result.getClass().getSimpleName(),
        result instanceof BooleanQuery);

    BooleanQuery bq = (BooleanQuery) result;
    boolean foundMustNot = false;
    for (BooleanClause clause : bq) {
      if (clause.occur() == BooleanClause.Occur.MUST_NOT) {
        foundMustNot = true;
        break;
      }
    }
    assertTrue("Expected at least one MUST_NOT clause", foundMustNot);
  }

  /** test OR operator */
  public void testOrOperator() throws Exception {
    Map<String, Float> weights = new LinkedHashMap<>();
    weights.put("field1", 1.0f);
    weights.put("field2", 1.0f);
    BM25FQueryParser parser = createParser(weights);

    Query result = parser.parse("foo|bar");
    assertTrue(
        "Expected BooleanQuery but got " + result.getClass().getSimpleName(),
        result instanceof BooleanQuery);

    BooleanQuery bq = (BooleanQuery) result;
    assertEquals(2, bq.clauses().size());
    for (BooleanClause clause : bq) {
      assertEquals(BooleanClause.Occur.SHOULD, clause.occur());
    }
  }

  /** test empty input produces MatchNoDocsQuery */
  public void testEmpty() throws Exception {
    Map<String, Float> weights = new LinkedHashMap<>();
    weights.put("field1", 1.0f);
    weights.put("field2", 1.0f);
    BM25FQueryParser parser = createParser(weights);

    Query result = parser.parse("");
    assertTrue(
        "Expected MatchNoDocsQuery but got " + result.getClass().getSimpleName(),
        result instanceof MatchNoDocsQuery);
  }

  /** test asterisk produces MatchAllDocsQuery */
  public void testMatchAll() throws Exception {
    Map<String, Float> weights = new LinkedHashMap<>();
    weights.put("field1", 1.0f);
    weights.put("field2", 1.0f);
    BM25FQueryParser parser = createParser(weights);

    Query result = parser.parse("*");
    assertTrue(
        "Expected MatchAllDocsQuery but got " + result.getClass().getSimpleName(),
        result instanceof MatchAllDocsQuery);
  }

  /** test that weight below 1.0f throws IllegalArgumentException */
  public void testInvalidWeight() throws Exception {
    Map<String, Float> weights = new LinkedHashMap<>();
    weights.put("field1", 0.5f);

    expectThrows(
        IllegalArgumentException.class,
        () -> {
          Analyzer analyzer = new MockAnalyzer(random());
          new BM25FQueryParser(analyzer, weights);
        });
  }

  /** integration test: index a document and search with BM25FQueryParser */
  public void testSearchIntegration() throws Exception {
    Directory dir = newDirectory();
    RandomIndexWriter writer = new RandomIndexWriter(random(), dir);

    Document doc = new Document();
    doc.add(new TextField("title", "test document", Store.NO));
    doc.add(new TextField("body", "this is a test", Store.NO));
    writer.addDocument(doc);

    IndexReader reader = writer.getReader();
    IndexSearcher searcher = new IndexSearcher(reader);

    Map<String, Float> weights = new LinkedHashMap<>();
    weights.put("title", 1.0f);
    weights.put("body", 1.0f);
    BM25FQueryParser parser = createParser(weights);

    Query query = parser.parse("test");
    TopDocs topDocs = searcher.search(query, 10);
    assertEquals(1, topDocs.totalHits.value());

    reader.close();
    writer.close();
    dir.close();
  }
}

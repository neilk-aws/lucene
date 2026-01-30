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
package org.apache.lucene.demo.bm25f;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.queryparser.classic.BM25FQueryParser;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.BM25FSimilarity;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

/**
 * Demonstrates BM25F multi-field query parsing and ranking.
 *
 * <p>This example shows how to:
 *
 * <ul>
 *   <li>Create a BM25F similarity with field-specific parameters
 *   <li>Index documents with multiple fields
 *   <li>Parse and execute multi-field queries with BM25F ranking
 *   <li>Retrieve and display ranked results
 * </ul>
 */
public class BM25FDemo {

  /**
   * Main demonstration method.
   *
   * @param args Command line arguments (not used)
   * @throws IOException If an I/O error occurs
   * @throws ParseException If query parsing fails
   */
  public static void main(String[] args) throws IOException, ParseException {
    // Create a temporary directory for the index
    String indexPath = System.getProperty("java.io.tmpdir") + "/bm25f-demo-index";
    Directory directory = FSDirectory.open(Paths.get(indexPath));

    // Step 1: Configure BM25F with field-specific parameters
    System.out.println("=== BM25F Multi-Field Query Parser Demo ===\n");
    System.out.println("Step 1: Configuring BM25F similarity");

    Map<String, Float> fieldBoosts = new HashMap<>();
    fieldBoosts.put("title", 3.0f); // Title is 3x more important than body
    fieldBoosts.put("abstract", 2.0f); // Abstract is 2x more important
    fieldBoosts.put("body", 1.0f); // Body has standard importance

    Map<String, Float> fieldBParams = new HashMap<>();
    fieldBParams.put("title", 0.5f); // Less length normalization for titles
    fieldBParams.put("abstract", 0.6f); // Moderate length normalization for abstracts
    fieldBParams.put("body", 0.75f); // Standard length normalization for body

    BM25FSimilarity similarity =
        new BM25FSimilarity(1.2f, 0.75f, fieldBoosts, fieldBParams);

    System.out.println("  k1 = " + similarity.getK1());
    System.out.println("  Field boosts: " + fieldBoosts);
    System.out.println("  Field b parameters: " + fieldBParams);
    System.out.println();

    // Step 2: Create an index with sample documents
    System.out.println("Step 2: Indexing sample documents");
    indexDocuments(directory, similarity);
    System.out.println();

    // Step 3: Create BM25F query parser
    System.out.println("Step 3: Creating BM25F query parser");
    StandardAnalyzer analyzer = new StandardAnalyzer();
    String[] fields = {"title", "abstract", "body"};

    BM25FQueryParser parser = new BM25FQueryParser(fields, analyzer, fieldBoosts);
    parser.setK1(1.2f);
    parser.setDefaultB(0.75f);

    // Set field-specific b parameters
    for (Map.Entry<String, Float> entry : fieldBParams.entrySet()) {
      parser.setFieldBParam(entry.getKey(), entry.getValue());
    }

    System.out.println("  Parser configuration: " + parser.toString());
    System.out.println();

    // Step 4: Execute queries
    System.out.println("Step 4: Executing queries with BM25F ranking");
    System.out.println();

    String[] queries = {
      "machine learning",
      "neural networks",
      "information retrieval",
      "\"deep learning\"",
      "search AND ranking"
    };

    DirectoryReader reader = DirectoryReader.open(directory);
    IndexSearcher searcher = new IndexSearcher(reader);
    searcher.setSimilarity(similarity);

    for (String queryString : queries) {
      executeQuery(searcher, parser, queryString);
    }

    // Clean up
    reader.close();
    directory.close();
    analyzer.close();

    System.out.println("\n=== Demo Complete ===");
    System.out.println("Index location: " + indexPath);
  }

  /**
   * Indexes sample documents about computer science topics.
   *
   * @param directory The directory to store the index
   * @param similarity The BM25F similarity to use
   * @throws IOException If an I/O error occurs
   */
  private static void indexDocuments(Directory directory, BM25FSimilarity similarity)
      throws IOException {
    IndexWriterConfig config = new IndexWriterConfig(new StandardAnalyzer());
    config.setSimilarity(similarity);
    config.setOpenMode(IndexWriterConfig.OpenMode.CREATE);

    IndexWriter writer = new IndexWriter(directory, config);

    // Document 1: Machine Learning
    Document doc1 = new Document();
    doc1.add(new TextField("title", "Introduction to Machine Learning", Field.Store.YES));
    doc1.add(
        new TextField(
            "abstract",
            "Machine learning is a subset of artificial intelligence that enables systems to learn from data.",
            Field.Store.YES));
    doc1.add(
        new TextField(
            "body",
            "Machine learning algorithms build models based on sample data, known as training data, "
                + "to make predictions or decisions without being explicitly programmed. Machine learning "
                + "is closely related to computational statistics and mathematical optimization.",
            Field.Store.YES));
    writer.addDocument(doc1);

    // Document 2: Deep Learning
    Document doc2 = new Document();
    doc2.add(new TextField("title", "Deep Learning and Neural Networks", Field.Store.YES));
    doc2.add(
        new TextField(
            "abstract",
            "Deep learning uses neural networks with multiple layers to learn representations of data.",
            Field.Store.YES));
    doc2.add(
        new TextField(
            "body",
            "Deep learning is part of machine learning methods based on artificial neural networks. "
                + "Learning can be supervised, semi-supervised or unsupervised. Deep neural networks, "
                + "recurrent neural networks, and convolutional neural networks have been applied to "
                + "computer vision, speech recognition, and natural language processing.",
            Field.Store.YES));
    writer.addDocument(doc2);

    // Document 3: Information Retrieval
    Document doc3 = new Document();
    doc3.add(new TextField("title", "Information Retrieval Systems", Field.Store.YES));
    doc3.add(
        new TextField(
            "abstract",
            "Information retrieval deals with obtaining relevant information from large collections.",
            Field.Store.YES));
    doc3.add(
        new TextField(
            "body",
            "Information retrieval is the activity of obtaining information system resources that are "
                + "relevant to an information need. Search engines are the most visible information retrieval "
                + "applications. Information retrieval systems use ranking algorithms to order results by relevance.",
            Field.Store.YES));
    writer.addDocument(doc3);

    // Document 4: Search Engine Ranking
    Document doc4 = new Document();
    doc4.add(new TextField("title", "Search Engine Ranking Algorithms", Field.Store.YES));
    doc4.add(
        new TextField(
            "abstract",
            "Ranking algorithms determine the order of search results based on relevance.",
            Field.Store.YES));
    doc4.add(
        new TextField(
            "body",
            "Search engines use various ranking algorithms including BM25, TF-IDF, and learning to rank "
                + "methods. BM25 is a probabilistic ranking function that considers term frequency and "
                + "document length. Modern search engines often use machine learning for ranking.",
            Field.Store.YES));
    writer.addDocument(doc4);

    // Document 5: Neural Networks
    Document doc5 = new Document();
    doc5.add(new TextField("title", "Artificial Neural Networks", Field.Store.YES));
    doc5.add(
        new TextField(
            "abstract",
            "Neural networks are computing systems inspired by biological neural networks.",
            Field.Store.YES));
    doc5.add(
        new TextField(
            "body",
            "Artificial neural networks consist of nodes that receive input, process it, and pass output to "
                + "other nodes. Neural networks learn by adjusting connection weights based on training examples. "
                + "They are used in pattern recognition, classification, and regression tasks.",
            Field.Store.YES));
    writer.addDocument(doc5);

    writer.commit();
    writer.close();

    System.out.println("  Indexed 5 documents with multiple fields");
  }

  /**
   * Executes a query and displays the results.
   *
   * @param searcher The index searcher
   * @param parser The BM25F query parser
   * @param queryString The query string to parse and execute
   * @throws IOException If an I/O error occurs
   * @throws ParseException If query parsing fails
   */
  private static void executeQuery(
      IndexSearcher searcher, BM25FQueryParser parser, String queryString)
      throws IOException, ParseException {
    System.out.println("Query: \"" + queryString + "\"");

    Query query = parser.parse(queryString);
    System.out.println("  Parsed query: " + query.toString());

    TopDocs results = searcher.search(query, 5);
    System.out.println("  Found " + results.totalHits.value + " results");

    if (results.scoreDocs.length > 0) {
      System.out.println("  Top results:");
      for (int i = 0; i < results.scoreDocs.length; i++) {
        ScoreDoc scoreDoc = results.scoreDocs[i];
        Document doc = searcher.doc(scoreDoc.doc);
        System.out.printf(
            "    %d. [Score: %.4f] %s%n", i + 1, scoreDoc.score, doc.get("title"));

        // Optionally show explanation for top result
        if (i == 0) {
          var explanation = searcher.explain(query, scoreDoc.doc);
          System.out.println("       Explanation: " + explanation.getDescription());
        }
      }
    }
    System.out.println();
  }
}

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
import org.apache.lucene.analysis.Analyzer;
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
import org.apache.lucene.store.MMapDirectory;

/**
 * Demonstrates the use of BM25F multi-field ranking in Apache Lucene.
 *
 * <p>This example shows how to:
 *
 * <ol>
 *   <li>Configure BM25FSimilarity with field-specific weights and parameters
 *   <li>Create an index with multiple fields (title, body, anchor text)
 *   <li>Use BM25FQueryParser to search across multiple fields
 *   <li>Retrieve and display ranked results
 * </ol>
 *
 * <h2>What is BM25F?</h2>
 *
 * <p>BM25F is an extension of the BM25 ranking function that properly handles documents with
 * multiple fields. Unlike standard BM25 which treats each field independently, BM25F:
 *
 * <ul>
 *   <li><b>Aggregates term frequencies</b> across fields before scoring
 *   <li><b>Applies field-specific weights</b> to emphasize important fields (e.g., titles)
 *   <li><b>Uses field-specific length normalization</b> to handle fields of different lengths
 * </ul>
 *
 * <h2>When to Use BM25F</h2>
 *
 * <p>BM25F is particularly effective for:
 *
 * <ul>
 *   <li>Document retrieval where some fields are more important (e.g., title vs. body)
 *   <li>Web search where you have title, body, anchor text, and metadata
 *   <li>E-commerce search with product names, descriptions, and reviews
 *   <li>Academic search with title, abstract, and full text
 * </ul>
 *
 * <h2>Running This Demo</h2>
 *
 * <pre>
 * java org.apache.lucene.demo.bm25f.BM25FDemo [index-path] [query]
 * </pre>
 *
 * <p>Example:
 *
 * <pre>
 * java org.apache.lucene.demo.bm25f.BM25FDemo /tmp/bm25f-index "information retrieval"
 * </pre>
 */
public class BM25FDemo {

  /**
   * Main demonstration program.
   *
   * @param args Command line arguments: [index-path] [query]
   */
  public static void main(String[] args) throws IOException, ParseException {
    String indexPath = args.length > 0 ? args[0] : "/tmp/bm25f-demo-index";
    String queryString = args.length > 1 ? args[1] : "search engines";

    System.out.println("BM25F Multi-Field Ranking Demo");
    System.out.println("================================\n");

    // Step 1: Create and configure BM25F similarity
    System.out.println("Step 1: Configuring BM25F Similarity");
    BM25FSimilarity similarity = createBM25FSimilarity();
    System.out.println("  " + similarity);
    System.out.println();

    // Step 2: Create sample index
    System.out.println("Step 2: Creating sample index at: " + indexPath);
    Directory directory = MMapDirectory.open(Paths.get(indexPath));
    createSampleIndex(directory, similarity);
    System.out.println("  Created index with sample documents\n");

    // Step 3: Search the index
    System.out.println("Step 3: Searching for: \"" + queryString + "\"");
    searchIndex(directory, similarity, queryString);

    directory.close();
  }

  /**
   * Creates and configures a BM25FSimilarity with field-specific parameters.
   *
   * <p>Field configurations:
   *
   * <ul>
   *   <li><b>title</b>: weight=3.0, b=0.5 (high importance, low length normalization)
   *   <li><b>body</b>: weight=1.0, b=0.75 (standard importance and normalization)
   *   <li><b>anchor</b>: weight=2.0, b=0.6 (moderate importance and normalization)
   * </ul>
   *
   * @return Configured BM25FSimilarity instance
   */
  private static BM25FSimilarity createBM25FSimilarity() {
    return new BM25FSimilarity.Builder()
        .setK1(1.2f) // Standard BM25 k1 parameter
        .setDefaultB(0.75f) // Default length normalization
        .addFieldConfig("title", 3.0f, 0.5f) // Title: high weight, less length sensitivity
        .addFieldConfig("body", 1.0f, 0.75f) // Body: standard weight and normalization
        .addFieldConfig("anchor", 2.0f, 0.6f) // Anchor: moderate weight and normalization
        .build();
  }

  /**
   * Creates a sample index with documents containing multiple fields.
   *
   * @param directory Directory to store the index
   * @param similarity Similarity function to use
   */
  private static void createSampleIndex(Directory directory, BM25FSimilarity similarity)
      throws IOException {
    Analyzer analyzer = new StandardAnalyzer();
    IndexWriterConfig config = new IndexWriterConfig(analyzer);
    config.setSimilarity(similarity);

    IndexWriter writer = new IndexWriter(directory, config);

    // Document 1: Web search focused
    Document doc1 = new Document();
    doc1.add(new TextField("title", "Introduction to Search Engines", Field.Store.YES));
    doc1.add(
        new TextField(
            "body",
            "Search engines are information retrieval systems designed to help users find "
                + "information on the World Wide Web. They use sophisticated algorithms to crawl, "
                + "index, and rank web pages based on relevance to search queries.",
            Field.Store.YES));
    doc1.add(new TextField("anchor", "search engines tutorial web indexing", Field.Store.YES));
    writer.addDocument(doc1);

    // Document 2: Information retrieval focused
    Document doc2 = new Document();
    doc2.add(new TextField("title", "Information Retrieval Systems", Field.Store.YES));
    doc2.add(
        new TextField(
            "body",
            "Information retrieval is the science of searching for information in documents and "
                + "databases. Modern IR systems use ranking functions like BM25 to determine the "
                + "relevance of documents to user queries. Key concepts include indexing, query "
                + "processing, and relevance ranking.",
            Field.Store.YES));
    doc2.add(
        new TextField("anchor", "information retrieval ranking algorithms", Field.Store.YES));
    writer.addDocument(doc2);

    // Document 3: Database focused
    Document doc3 = new Document();
    doc3.add(new TextField("title", "Database Management Systems", Field.Store.YES));
    doc3.add(
        new TextField(
            "body",
            "Database management systems (DBMS) are software systems used to store, retrieve, and "
                + "manage data. While different from search engines, modern databases often "
                + "incorporate full-text search capabilities for finding information within "
                + "structured data.",
            Field.Store.YES));
    doc3.add(new TextField("anchor", "database systems data management", Field.Store.YES));
    writer.addDocument(doc3);

    // Document 4: Machine learning and search
    Document doc4 = new Document();
    doc4.add(new TextField("title", "Machine Learning for Search", Field.Store.YES));
    doc4.add(
        new TextField(
            "body",
            "Modern search engines increasingly use machine learning to improve ranking quality. "
                + "Learning to rank algorithms can automatically optimize ranking functions based "
                + "on user behavior data. This represents a significant advancement over "
                + "traditional information retrieval methods.",
            Field.Store.YES));
    doc4.add(
        new TextField("anchor", "machine learning search ranking optimization", Field.Store.YES));
    writer.addDocument(doc4);

    // Document 5: Practical search implementation
    Document doc5 = new Document();
    doc5.add(new TextField("title", "Building Search Applications", Field.Store.YES));
    doc5.add(
        new TextField(
            "body",
            "Implementing a search application requires careful consideration of indexing "
                + "strategies, query parsing, and ranking algorithms. Apache Lucene provides a "
                + "powerful foundation for building search engines with support for various "
                + "similarity functions including BM25 and its variants.",
            Field.Store.YES));
    doc5.add(
        new TextField(
            "anchor", "search implementation lucene tutorial apache", Field.Store.YES));
    writer.addDocument(doc5);

    writer.close();
    System.out.println("  Indexed 5 sample documents");
  }

  /**
   * Searches the index using BM25FQueryParser and displays results.
   *
   * @param directory Directory containing the index
   * @param similarity Similarity function to use
   * @param queryString Query string to search for
   */
  private static void searchIndex(
      Directory directory, BM25FSimilarity similarity, String queryString)
      throws IOException, ParseException {
    Analyzer analyzer = new StandardAnalyzer();

    // Configure query parser with field weights matching similarity configuration
    String[] fields = {"title", "body", "anchor"};
    Map<String, Float> boosts = new HashMap<>();
    boosts.put("title", 3.0f);
    boosts.put("body", 1.0f);
    boosts.put("anchor", 2.0f);

    BM25FQueryParser parser = new BM25FQueryParser(fields, analyzer, boosts);
    Query query = parser.parse(queryString);

    System.out.println("  Parsed query: " + query);
    System.out.println();

    // Execute search
    DirectoryReader reader = DirectoryReader.open(directory);
    IndexSearcher searcher = new IndexSearcher(reader);
    searcher.setSimilarity(similarity);

    TopDocs results = searcher.search(query, 10);

    // Display results
    System.out.println("Found " + results.totalHits.value + " results:\n");
    System.out.println("Rank  Score    Title");
    System.out.println("----  ------   -----");

    int rank = 1;
    for (ScoreDoc scoreDoc : results.scoreDocs) {
      Document doc = searcher.doc(scoreDoc.doc);
      String title = doc.get("title");
      float score = scoreDoc.score;

      System.out.printf("%2d    %.4f   %s%n", rank++, score, title);
    }

    System.out.println();

    // Show detailed information for top result
    if (results.scoreDocs.length > 0) {
      System.out.println("Top result details:");
      Document topDoc = searcher.doc(results.scoreDocs[0].doc);
      System.out.println("  Title: " + topDoc.get("title"));
      System.out.println("  Body: " + truncate(topDoc.get("body"), 150));
      System.out.println("  Anchor: " + topDoc.get("anchor"));
      System.out.println("  Score: " + results.scoreDocs[0].score);
    }

    reader.close();
  }

  /** Truncates a string to the specified length, adding ellipsis if needed. */
  private static String truncate(String text, int maxLength) {
    if (text == null) return "";
    if (text.length() <= maxLength) return text;
    return text.substring(0, maxLength) + "...";
  }
}

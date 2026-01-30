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
package org.apache.lucene.demo;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexOptions;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.queryparser.classic.BM25FQueryParser;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

/**
 * Demonstrates the usage of {@link BM25FQueryParser} for multi-field BM25F search.
 *
 * <p>This example indexes a collection of documents with title, body, and author fields, then
 * demonstrates how to search across multiple fields using BM25F scoring with different field
 * weights.
 */
public class BM25FSearchDemo {

  /**
   * Demonstrates BM25F search with field weights.
   *
   * @param args Command line arguments (not used)
   */
  public static void main(String[] args) throws IOException, ParseException {
    // Create an in-memory index
    Directory directory = FSDirectory.open(Paths.get("bm25f-demo-index"));
    Analyzer analyzer = new StandardAnalyzer();

    // Index some sample documents
    indexDocuments(directory, analyzer);

    // Search the index using BM25F
    searchWithBM25F(directory, analyzer);

    directory.close();
  }

  /** Indexes sample documents about machine learning topics. */
  private static void indexDocuments(Directory directory, Analyzer analyzer) throws IOException {
    IndexWriterConfig config = new IndexWriterConfig(analyzer);
    config.setSimilarity(new BM25Similarity());
    IndexWriter writer = new IndexWriter(directory, config);

    // Create field type with norms enabled (required for BM25)
    FieldType fieldType = new FieldType();
    fieldType.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS);
    fieldType.setStored(true);
    fieldType.setTokenized(true);
    fieldType.setOmitNorms(false); // Important: norms must be enabled

    // Document 1: Focus on machine learning
    Document doc1 = new Document();
    doc1.add(new Field("title", "Introduction to Machine Learning", fieldType));
    doc1.add(
        new Field(
            "body",
            "Machine learning is a subset of artificial intelligence that focuses on "
                + "algorithms and statistical models. It enables computers to learn from data "
                + "without being explicitly programmed.",
            fieldType));
    doc1.add(new Field("author", "John Smith", fieldType));
    writer.addDocument(doc1);

    // Document 2: Focus on deep learning
    Document doc2 = new Document();
    doc2.add(new Field("title", "Deep Learning and Neural Networks", fieldType));
    doc2.add(
        new Field(
            "body",
            "Deep learning is a specialized branch of machine learning that uses neural networks "
                + "with multiple layers. These networks can learn hierarchical representations "
                + "of data.",
            fieldType));
    doc2.add(new Field("author", "Jane Doe", fieldType));
    writer.addDocument(doc2);

    // Document 3: Focus on natural language processing
    Document doc3 = new Document();
    doc3.add(new Field("title", "Natural Language Processing Techniques", fieldType));
    doc3.add(
        new Field(
            "body",
            "Natural language processing combines linguistics and machine learning to help "
                + "computers understand human language. It's used in applications like "
                + "chatbots and translation.",
            fieldType));
    doc3.add(new Field("author", "Alice Johnson", fieldType));
    writer.addDocument(doc3);

    // Document 4: Focus on computer vision
    Document doc4 = new Document();
    doc4.add(new Field("title", "Computer Vision and Image Recognition", fieldType));
    doc4.add(
        new Field(
            "body",
            "Computer vision enables machines to interpret visual information from the world. "
                + "Modern computer vision systems use deep learning and convolutional neural "
                + "networks.",
            fieldType));
    doc4.add(new Field("author", "Bob Wilson", fieldType));
    writer.addDocument(doc4);

    // Document 5: Overview document
    Document doc5 = new Document();
    doc5.add(new Field("title", "Artificial Intelligence Overview", fieldType));
    doc5.add(
        new Field(
            "body",
            "Artificial intelligence encompasses many subfields including machine learning, "
                + "natural language processing, computer vision, and robotics. Each field has "
                + "its own techniques and applications.",
            fieldType));
    doc5.add(new Field("author", "Carol Davis", fieldType));
    writer.addDocument(doc5);

    writer.commit();
    writer.close();

    System.out.println("Indexed 5 documents about AI and machine learning.\n");
  }

  /** Demonstrates searching with BM25F using different configurations. */
  private static void searchWithBM25F(Directory directory, Analyzer analyzer)
      throws IOException, ParseException {
    DirectoryReader reader = DirectoryReader.open(directory);
    IndexSearcher searcher = new IndexSearcher(reader);
    searcher.setSimilarity(new BM25Similarity());

    String queryString = "machine learning";

    System.out.println("=".repeat(80));
    System.out.println("Query: \"" + queryString + "\"");
    System.out.println("=".repeat(80));

    // Example 1: Basic BM25F search with equal weights
    System.out.println("\n1. BM25F Search with Equal Weights (title and body):");
    System.out.println("-".repeat(80));
    String[] fields = {"title", "body"};
    Query query1 = BM25FQueryParser.parse(queryString, fields, analyzer);
    System.out.println("Query: " + query1.toString());
    printResults(searcher, query1, 3);

    // Example 2: BM25F search with title boost
    System.out.println("\n2. BM25F Search with Title Boost (title=2.0, body=1.0):");
    System.out.println("-".repeat(80));
    Map<String, Float> weights = new HashMap<>();
    weights.put("title", 2.0f);
    weights.put("body", 1.0f);
    Query query2 = BM25FQueryParser.parse(queryString, fields, analyzer, weights);
    System.out.println("Query: " + query2.toString());
    printResults(searcher, query2, 3);

    // Example 3: BM25F search with heavy title boost
    System.out.println("\n3. BM25F Search with Heavy Title Boost (title=5.0, body=1.0):");
    System.out.println("-".repeat(80));
    Map<String, Float> heavyWeights = new HashMap<>();
    heavyWeights.put("title", 5.0f);
    heavyWeights.put("body", 1.0f);
    Query query3 = BM25FQueryParser.parse(queryString, fields, analyzer, heavyWeights);
    System.out.println("Query: " + query3.toString());
    printResults(searcher, query3, 3);

    // Example 4: Search across three fields
    System.out.println("\n4. BM25F Search across Three Fields (title, body, author):");
    System.out.println("-".repeat(80));
    String[] threeFields = {"title", "body", "author"};
    Query query4 = BM25FQueryParser.parse("learning", threeFields, analyzer);
    System.out.println("Query: " + query4.toString());
    printResults(searcher, query4, 3);

    // Example 5: Complex multi-term query
    System.out.println("\n5. BM25F Search with Multi-term Query:");
    System.out.println("-".repeat(80));
    String complexQuery = "deep learning neural networks";
    Query query5 = BM25FQueryParser.parse(complexQuery, fields, analyzer, weights);
    System.out.println("Query: " + query5.toString());
    printResults(searcher, query5, 3);

    reader.close();
  }

  /** Prints the top search results. */
  private static void printResults(IndexSearcher searcher, Query query, int numResults)
      throws IOException {
    TopDocs results = searcher.search(query, numResults);
    System.out.println("Found " + results.totalHits.value() + " documents.\n");

    if (results.scoreDocs.length == 0) {
      System.out.println("No results found.");
      return;
    }

    for (int i = 0; i < results.scoreDocs.length; i++) {
      ScoreDoc scoreDoc = results.scoreDocs[i];
      Document doc = searcher.doc(scoreDoc.doc);

      System.out.println("Rank " + (i + 1) + ":");
      System.out.println("  Score: " + scoreDoc.score);
      System.out.println("  Title: " + doc.get("title"));
      System.out.println("  Author: " + doc.get("author"));
      System.out.println("  Body (first 100 chars): " + truncate(doc.get("body"), 100));
      System.out.println();
    }
  }

  /** Truncates a string to the specified length and adds ellipsis. */
  private static String truncate(String str, int length) {
    if (str.length() <= length) {
      return str;
    }
    return str.substring(0, length) + "...";
  }
}

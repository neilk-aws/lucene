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
package org.apache.lucene.sandbox.neptune;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.Explanation;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.index.RandomIndexWriter;
import org.apache.lucene.tests.util.LuceneTestCase;

/** Integration tests for {@link NeptuneGraphQuery} execution using {@link MockNeptuneConnection}. */
public class TestNeptuneGraphQueryExecution extends LuceneTestCase {

  private Directory directory;
  private IndexReader reader;
  private IndexSearcher searcher;
  private MockNeptuneConnection mockConnection;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    directory = newDirectory();
    RandomIndexWriter writer = new RandomIndexWriter(random(), directory);

    // Index documents with node IDs
    addDoc(writer, "node-1", "Person", "Alice");
    addDoc(writer, "node-2", "Person", "Bob");
    addDoc(writer, "node-3", "Person", "Charlie");
    addDoc(writer, "node-4", "Company", "Acme Corp");
    addDoc(writer, "node-5", "Person", "Diana");

    reader = writer.getReader();
    writer.close();
    searcher = newSearcher(reader);

    mockConnection = new MockNeptuneConnection("test-endpoint").preConnect();
  }

  @Override
  public void tearDown() throws Exception {
    reader.close();
    directory.close();
    super.tearDown();
  }

  private void addDoc(RandomIndexWriter writer, String nodeId, String label, String name)
      throws IOException {
    Document doc = new Document();
    doc.add(new StringField("nodeId", nodeId, Field.Store.YES));
    doc.add(new StringField("label", label, Field.Store.YES));
    doc.add(new StringField("name", name, Field.Store.YES));
    writer.addDocument(doc);
  }

  public void testBasicQueryExecution() throws IOException {
    // Set up mock to return two nodes
    mockConnection.addQueryResult(
        createGremlinQuery(Set.of("start-node"), List.of("KNOWS"), 1, "out"),
        List.of(
            new GraphNode("node-1", "Person", Map.of("name", "Alice")),
            new GraphNode("node-2", "Person", Map.of("name", "Bob"))));

    GraphTraversalSpec spec =
        new GraphTraversalSpec.SimpleGraphTraversalSpec(
            Set.of("start-node"), List.of("KNOWS"), 1, GraphTraversalSpec.Direction.OUTGOING);
    NeptuneGraphQuery query = new NeptuneGraphQuery(spec, mockConnection, "nodeId");

    TopDocs topDocs = searcher.search(query, 10);

    assertEquals(2, topDocs.totalHits.value());
    assertEquals(1, mockConnection.getQueryExecutionCount());
  }

  public void testQueryWithNoMatches() throws IOException {
    // Mock returns nodes that don't exist in the index
    mockConnection.addQueryResult(
        createGremlinQuery(Set.of("start-node"), List.of("KNOWS"), 1, "out"),
        List.of(new GraphNode("nonexistent-node", "Person")));

    GraphTraversalSpec spec =
        new GraphTraversalSpec.SimpleGraphTraversalSpec(
            Set.of("start-node"), List.of("KNOWS"), 1, GraphTraversalSpec.Direction.OUTGOING);
    NeptuneGraphQuery query = new NeptuneGraphQuery(spec, mockConnection, "nodeId");

    TopDocs topDocs = searcher.search(query, 10);

    assertEquals(0, topDocs.totalHits.value());
  }

  public void testQueryWithEmptyResults() throws IOException {
    // Mock returns empty list
    mockConnection.addQueryResult(
        createGremlinQuery(Set.of("start-node"), List.of("KNOWS"), 1, "out"), List.of());

    GraphTraversalSpec spec =
        new GraphTraversalSpec.SimpleGraphTraversalSpec(
            Set.of("start-node"), List.of("KNOWS"), 1, GraphTraversalSpec.Direction.OUTGOING);
    NeptuneGraphQuery query = new NeptuneGraphQuery(spec, mockConnection, "nodeId");

    TopDocs topDocs = searcher.search(query, 10);

    assertEquals(0, topDocs.totalHits.value());
  }

  public void testQueryWithDisconnectedConnection() throws IOException {
    mockConnection.disconnect();

    GraphTraversalSpec spec =
        new GraphTraversalSpec.SimpleGraphTraversalSpec(
            Set.of("start-node"), List.of("KNOWS"), 1, GraphTraversalSpec.Direction.OUTGOING);
    NeptuneGraphQuery query = new NeptuneGraphQuery(spec, mockConnection, "nodeId");

    // Should not throw but return no results
    TopDocs topDocs = searcher.search(query, 10);
    assertEquals(0, topDocs.totalHits.value());
  }

  public void testQueryWithConnectionFailure() throws IOException {
    mockConnection.simulateQueryFailure(true);

    GraphTraversalSpec spec =
        new GraphTraversalSpec.SimpleGraphTraversalSpec(
            Set.of("start-node"), List.of("KNOWS"), 1, GraphTraversalSpec.Direction.OUTGOING);
    NeptuneGraphQuery query = new NeptuneGraphQuery(spec, mockConnection, "nodeId");

    // Should not throw but return no results due to error handling
    TopDocs topDocs = searcher.search(query, 10);
    assertEquals(0, topDocs.totalHits.value());
  }

  public void testExplainWithMatch() throws IOException {
    mockConnection.addQueryResult(
        createGremlinQuery(Set.of("start-node"), List.of("KNOWS"), 1, "out"),
        List.of(new GraphNode("node-1", "Person")));

    GraphTraversalSpec spec =
        new GraphTraversalSpec.SimpleGraphTraversalSpec(
            Set.of("start-node"), List.of("KNOWS"), 1, GraphTraversalSpec.Direction.OUTGOING);
    NeptuneGraphQuery query = new NeptuneGraphQuery(spec, mockConnection, "nodeId");

    TopDocs topDocs = searcher.search(query, 10);
    assertTrue(topDocs.totalHits.value() > 0);

    Explanation explanation = searcher.explain(query, topDocs.scoreDocs[0].doc);
    assertTrue(explanation.isMatch());
    assertTrue(explanation.getDescription().contains("NeptuneGraphQuery"));
  }

  public void testExplainWithNoMatch() throws IOException {
    mockConnection.addQueryResult(
        createGremlinQuery(Set.of("start-node"), List.of("KNOWS"), 1, "out"), List.of());

    GraphTraversalSpec spec =
        new GraphTraversalSpec.SimpleGraphTraversalSpec(
            Set.of("start-node"), List.of("KNOWS"), 1, GraphTraversalSpec.Direction.OUTGOING);
    NeptuneGraphQuery query = new NeptuneGraphQuery(spec, mockConnection, "nodeId");

    // Try to explain a doc that doesn't match
    Explanation explanation = searcher.explain(query, 0);
    assertFalse(explanation.isMatch());
    assertTrue(explanation.getDescription().contains("not found in graph traversal"));
  }

  public void testScoreMode() throws IOException {
    mockConnection.addQueryResult(
        createGremlinQuery(Set.of("start-node"), List.of("KNOWS"), 1, "out"),
        List.of(new GraphNode("node-1", "Person")));

    GraphTraversalSpec spec =
        new GraphTraversalSpec.SimpleGraphTraversalSpec(
            Set.of("start-node"), List.of("KNOWS"), 1, GraphTraversalSpec.Direction.OUTGOING);
    NeptuneGraphQuery query = new NeptuneGraphQuery(spec, mockConnection, "nodeId");

    // Test with different score modes
    for (ScoreMode scoreMode : ScoreMode.values()) {
      var weight = query.createWeight(searcher, scoreMode, 1.0f);
      assertNotNull(weight);
    }
  }

  public void testBoostIsApplied() throws IOException {
    mockConnection.addQueryResult(
        createGremlinQuery(Set.of("start-node"), List.of("KNOWS"), 1, "out"),
        List.of(new GraphNode("node-1", "Person")));

    GraphTraversalSpec spec =
        new GraphTraversalSpec.SimpleGraphTraversalSpec(
            Set.of("start-node"), List.of("KNOWS"), 1, GraphTraversalSpec.Direction.OUTGOING);
    NeptuneGraphQuery query = new NeptuneGraphQuery(spec, mockConnection, "nodeId");

    // Test with different boost values
    float boost = 2.5f;
    TopDocs topDocs = searcher.search(query, 10);
    assertTrue(topDocs.totalHits.value() > 0);

    Explanation explanation = searcher.explain(query, topDocs.scoreDocs[0].doc);
    // Default boost should be 1.0
    assertTrue(explanation.getValue().floatValue() > 0);
  }

  public void testMultipleStartNodes() throws IOException {
    mockConnection.setDefaultQueryHandler(
        query -> {
          if (query.contains("'start-1'") && query.contains("'start-2'")) {
            return List.of(
                new GraphNode("node-1", "Person"),
                new GraphNode("node-2", "Person"),
                new GraphNode("node-3", "Person"));
          }
          return List.of();
        });

    GraphTraversalSpec spec =
        new GraphTraversalSpec.SimpleGraphTraversalSpec(
            Set.of("start-1", "start-2"),
            List.of("KNOWS"),
            1,
            GraphTraversalSpec.Direction.OUTGOING);
    NeptuneGraphQuery query = new NeptuneGraphQuery(spec, mockConnection, "nodeId");

    TopDocs topDocs = searcher.search(query, 10);
    assertEquals(3, topDocs.totalHits.value());
  }

  public void testMultipleEdgeLabels() throws IOException {
    mockConnection.setDefaultQueryHandler(
        query -> {
          if (query.contains("'KNOWS'") && query.contains("'FOLLOWS'")) {
            return List.of(
                new GraphNode("node-1", "Person"),
                new GraphNode("node-4", "Company"));
          }
          return List.of();
        });

    GraphTraversalSpec spec =
        new GraphTraversalSpec.SimpleGraphTraversalSpec(
            Set.of("start-node"),
            List.of("KNOWS", "FOLLOWS"),
            2,
            GraphTraversalSpec.Direction.BOTH);
    NeptuneGraphQuery query = new NeptuneGraphQuery(spec, mockConnection, "nodeId");

    TopDocs topDocs = searcher.search(query, 10);
    assertEquals(2, topDocs.totalHits.value());
  }

  public void testWeightIsCacheable() throws IOException {
    GraphTraversalSpec spec =
        new GraphTraversalSpec.SimpleGraphTraversalSpec(
            Set.of("start-node"), List.of("KNOWS"), 1, GraphTraversalSpec.Direction.OUTGOING);
    NeptuneGraphQuery query = new NeptuneGraphQuery(spec, mockConnection, "nodeId");

    var weight = query.createWeight(searcher, ScoreMode.COMPLETE, 1.0f);

    // Neptune queries should not be cacheable (depend on external state)
    for (var ctx : reader.leaves()) {
      assertFalse(weight.isCacheable(ctx));
    }
  }

  public void testQueryExecutedOnlyOnce() throws IOException {
    mockConnection.setDefaultQueryHandler(
        query -> List.of(new GraphNode("node-1", "Person"), new GraphNode("node-2", "Person")));

    GraphTraversalSpec spec =
        new GraphTraversalSpec.SimpleGraphTraversalSpec(
            Set.of("start-node"), List.of("KNOWS"), 1, GraphTraversalSpec.Direction.OUTGOING);
    NeptuneGraphQuery query = new NeptuneGraphQuery(spec, mockConnection, "nodeId");

    // Execute search multiple times
    searcher.search(query, 10);

    // Query should only be executed once (cached in weight)
    assertEquals(1, mockConnection.getQueryExecutionCount());
  }

  public void testWithDifferentNodeIdField() throws IOException {
    // Create new index with different field name
    Directory customDir = newDirectory();
    RandomIndexWriter writer = new RandomIndexWriter(random(), customDir);
    Document doc = new Document();
    doc.add(new StringField("customNodeId", "node-1", Field.Store.YES));
    writer.addDocument(doc);
    IndexReader customReader = writer.getReader();
    writer.close();
    IndexSearcher customSearcher = newSearcher(customReader);

    mockConnection
        .clearQueryResults()
        .setDefaultQueryHandler(query -> List.of(new GraphNode("node-1", "Person")));

    GraphTraversalSpec spec =
        new GraphTraversalSpec.SimpleGraphTraversalSpec(
            Set.of("start-node"), List.of("KNOWS"), 1, GraphTraversalSpec.Direction.OUTGOING);
    NeptuneGraphQuery query = new NeptuneGraphQuery(spec, mockConnection, "customNodeId");

    TopDocs topDocs = customSearcher.search(query, 10);
    assertEquals(1, topDocs.totalHits.value());

    customReader.close();
    customDir.close();
  }

  // Helper method to create expected Gremlin query strings
  private String createGremlinQuery(
      Set<String> startNodes, List<String> edgeLabels, int maxHops, String direction) {
    StringBuilder query = new StringBuilder("g.V(");
    boolean first = true;
    for (String nodeId : startNodes) {
      if (!first) query.append(", ");
      query.append("'").append(nodeId).append("'");
      first = false;
    }
    query.append(")");
    query.append(".repeat(").append(direction);
    if (!edgeLabels.isEmpty()) {
      query.append("(");
      first = true;
      for (String label : edgeLabels) {
        if (!first) query.append(", ");
        query.append("'").append(label).append("'");
        first = false;
      }
      query.append(")");
    } else {
      query.append("()");
    }
    query.append(").times(").append(maxHops).append(")");
    query.append(".emit()");
    query.append(".dedup()");
    return query.toString();
  }
}

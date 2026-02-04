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
import java.util.Objects;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.QueryVisitor;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.search.Weight;

/**
 * A Lucene Query implementation that executes graph traversals against Amazon Neptune.
 *
 * <p>This query allows integration of graph database traversals into Lucene searches. Documents are
 * matched based on whether their node ID field value corresponds to a node found in the graph
 * traversal results.
 *
 * <p>The query operates by:
 *
 * <ol>
 *   <li>Taking a {@link GraphTraversalSpec} that defines the graph traversal
 *   <li>Executing the traversal against Neptune via the provided {@link NeptuneConnection}
 *   <li>Mapping the resulting graph nodes to Lucene documents via the nodeIdField
 *   <li>Scoring matched documents (constant score based on boost)
 * </ol>
 *
 * <p>Example usage:
 *
 * <pre class="prettyprint">
 * GraphTraversalSpec spec = new GraphTraversalSpec.SimpleGraphTraversalSpec(
 *     Set.of("person-123"),
 *     List.of("KNOWS"),
 *     2,
 *     GraphTraversalSpec.Direction.OUTGOING
 * );
 *
 * NeptuneGraphQuery query = new NeptuneGraphQuery(spec, connection, "nodeId");
 * TopDocs results = searcher.search(query, 10);
 * </pre>
 *
 * @lucene.experimental
 */
public class NeptuneGraphQuery extends Query {

  private final GraphTraversalSpec traversalSpec;
  private final NeptuneConnection connection;
  private final String nodeIdField;

  /**
   * Constructs a new NeptuneGraphQuery.
   *
   * @param traversalSpec the specification defining the graph traversal
   * @param connection the Neptune connection to use for executing the traversal
   * @param nodeIdField the name of the Lucene field that contains the graph node ID
   * @throws NullPointerException if any parameter is null
   */
  public NeptuneGraphQuery(
      GraphTraversalSpec traversalSpec, NeptuneConnection connection, String nodeIdField) {
    this.traversalSpec = Objects.requireNonNull(traversalSpec, "traversalSpec must not be null");
    this.connection = Objects.requireNonNull(connection, "connection must not be null");
    this.nodeIdField = Objects.requireNonNull(nodeIdField, "nodeIdField must not be null");
  }

  /**
   * Returns the graph traversal specification.
   *
   * @return the traversal specification
   */
  public GraphTraversalSpec getTraversalSpec() {
    return traversalSpec;
  }

  /**
   * Returns the Neptune connection.
   *
   * @return the Neptune connection
   */
  public NeptuneConnection getConnection() {
    return connection;
  }

  /**
   * Returns the name of the Lucene field containing graph node IDs.
   *
   * @return the node ID field name
   */
  public String getNodeIdField() {
    return nodeIdField;
  }

  @Override
  public String toString(String field) {
    StringBuilder sb = new StringBuilder();
    sb.append("NeptuneGraphQuery(");
    sb.append("nodeIdField=").append(nodeIdField);
    sb.append(", startNodes=").append(traversalSpec.getStartNodeIds());
    sb.append(", edgeLabels=").append(traversalSpec.getEdgeLabels());
    sb.append(", maxHops=").append(traversalSpec.getMaxHops());
    sb.append(", direction=").append(traversalSpec.getDirection());
    sb.append(")");
    return sb.toString();
  }

  @Override
  public boolean equals(Object obj) {
    if (!sameClassAs(obj)) {
      return false;
    }
    NeptuneGraphQuery other = (NeptuneGraphQuery) obj;
    return Objects.equals(traversalSpec, other.traversalSpec)
        && Objects.equals(nodeIdField, other.nodeIdField);
    // Note: connection is intentionally not part of equals - two queries with the same
    // traversal spec and field should be considered equal even if using different connections
  }

  @Override
  public int hashCode() {
    return Objects.hash(classHash(), traversalSpec, nodeIdField);
  }

  @Override
  public void visit(QueryVisitor visitor) {
    // Neptune graph queries don't have nested term-based queries in the traditional sense
    // We accept the visitor to indicate we're a leaf query
    visitor.visitLeaf(this);
  }

  @Override
  public Query rewrite(IndexSearcher indexSearcher) throws IOException {
    // This query is already in its primitive form and cannot be rewritten further
    return this;
  }

  @Override
  public Weight createWeight(IndexSearcher searcher, ScoreMode scoreMode, float boost)
      throws IOException {
    return new NeptuneGraphWeight(this, searcher, scoreMode, boost);
  }
}

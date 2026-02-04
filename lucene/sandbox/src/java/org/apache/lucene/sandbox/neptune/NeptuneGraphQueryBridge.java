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
import java.util.Set;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;

/**
 * Interface defining the bridge between Lucene queries and Neptune graph traversals.
 *
 * <p>The NeptuneGraphQueryBridge provides methods to:
 *
 * <ul>
 *   <li>Translate Lucene queries to Gremlin traversal queries
 *   <li>Execute graph traversals against a Neptune connection
 *   <li>Map graph query results back to Lucene document IDs
 * </ul>
 *
 * <p>This interface serves as the primary abstraction layer between Lucene's query model and
 * Neptune's graph query model, enabling hybrid text and graph searches.
 *
 * <p>Example usage:
 *
 * <pre class="prettyprint">
 * NeptuneGraphQueryBridge bridge = new DefaultNeptuneGraphQueryBridge();
 * String gremlinQuery = bridge.translateToGremlin(luceneQuery);
 * List&lt;GraphNode&gt; nodes = bridge.executeTraversal(gremlinQuery, connection);
 * Set&lt;Integer&gt; docIds = bridge.mapResultsToDocIds(nodes, searcher, "nodeId");
 * </pre>
 *
 * @lucene.experimental
 */
public interface NeptuneGraphQueryBridge {

  /**
   * Translates a Lucene query into a Gremlin traversal query string.
   *
   * <p>This method analyzes the Lucene query structure and generates an equivalent Gremlin
   * traversal that can be executed against a Neptune database.
   *
   * <p>Not all Lucene queries can be directly translated to Gremlin. Implementations may throw
   * {@link UnsupportedOperationException} for query types that cannot be translated.
   *
   * @param luceneQuery the Lucene query to translate
   * @return a Gremlin traversal query string
   * @throws NullPointerException if luceneQuery is null
   * @throws UnsupportedOperationException if the query type cannot be translated to Gremlin
   */
  String translateToGremlin(Query luceneQuery);

  /**
   * Executes a Gremlin traversal query against a Neptune connection.
   *
   * <p>The connection must be established before calling this method. The results are returned as a
   * list of {@link GraphNode} objects representing the vertices found by the traversal.
   *
   * @param gremlinQuery the Gremlin traversal query to execute
   * @param connection the Neptune connection to use for execution
   * @return a list of GraphNode objects representing the traversal results
   * @throws NullPointerException if gremlinQuery or connection is null
   * @throws IllegalStateException if the connection is not established
   * @throws NeptuneException if the query execution fails
   */
  List<GraphNode> executeTraversal(String gremlinQuery, NeptuneConnection connection);

  /**
   * Maps a list of graph nodes to their corresponding Lucene document IDs.
   *
   * <p>This method looks up each graph node's ID in the Lucene index to find the corresponding
   * document. The nodeIdField parameter specifies which field in the Lucene index contains the
   * graph node ID.
   *
   * <p>Nodes that do not have a corresponding Lucene document are silently skipped.
   *
   * @param nodes the list of graph nodes to map
   * @param searcher the IndexSearcher to use for lookups
   * @param nodeIdField the name of the Lucene field containing the graph node ID
   * @return a set of Lucene document IDs corresponding to the graph nodes
   * @throws NullPointerException if any parameter is null
   * @throws IOException if an I/O error occurs during the lookup
   */
  Set<Integer> mapResultsToDocIds(List<GraphNode> nodes, IndexSearcher searcher, String nodeIdField)
      throws IOException;

  /**
   * Executes a graph traversal specification and returns the resulting nodes.
   *
   * <p>This is a convenience method that combines query generation and execution.
   *
   * @param spec the traversal specification
   * @param connection the Neptune connection to use
   * @return a list of GraphNode objects representing the traversal results
   * @throws NullPointerException if spec or connection is null
   * @throws IllegalStateException if the connection is not established
   * @throws NeptuneException if the query execution fails
   */
  default List<GraphNode> executeTraversal(GraphTraversalSpec spec, NeptuneConnection connection) {
    String gremlinQuery = spec.toGremlinQuery();
    return executeTraversal(gremlinQuery, connection);
  }
}

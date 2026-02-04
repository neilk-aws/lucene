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

import java.util.List;

/**
 * Interface defining the contract for Neptune database connectivity.
 *
 * <p>Implementations of this interface provide the ability to connect to an Amazon Neptune graph
 * database cluster and execute Gremlin queries. The connection lifecycle is managed through the
 * {@link #connect()}, {@link #disconnect()}, and {@link #isConnected()} methods.
 *
 * <p>Example usage:
 *
 * <pre class="prettyprint">
 * NeptuneConnectionConfig config = NeptuneConnectionConfig.builder()
 *     .endpoint("my-neptune-cluster.cluster-xxx.region.neptune.amazonaws.com")
 *     .port(8182)
 *     .region("us-east-1")
 *     .build();
 *
 * NeptuneConnection connection = ...; // obtain implementation
 * connection.connect();
 * try {
 *     List&lt;GraphNode&gt; nodes = connection.executeGremlinQuery("g.V().hasLabel('person').limit(10)");
 *     // process results
 * } finally {
 *     connection.disconnect();
 * }
 * </pre>
 *
 * @lucene.experimental
 */
public interface NeptuneConnection {

  /**
   * Establishes a connection to the Neptune cluster.
   *
   * <p>This method must be called before executing any queries. If the connection is already
   * established, this method may be a no-op or may re-establish the connection depending on the
   * implementation.
   *
   * @throws NeptuneException if the connection cannot be established
   */
  void connect() throws NeptuneException;

  /**
   * Closes the connection to the Neptune cluster.
   *
   * <p>This method releases any resources associated with the connection. After calling this
   * method, {@link #isConnected()} should return {@code false}. Calling this method on an already
   * disconnected connection should be safe and have no effect.
   *
   * @throws NeptuneException if an error occurs while closing the connection
   */
  void disconnect() throws NeptuneException;

  /**
   * Checks whether the connection to Neptune is currently established.
   *
   * @return {@code true} if connected to Neptune, {@code false} otherwise
   */
  boolean isConnected();

  /**
   * Executes a Gremlin query against the Neptune database and returns the results as graph nodes.
   *
   * <p>The query string should be a valid Gremlin traversal that returns vertices. The results are
   * converted to {@link GraphNode} objects.
   *
   * @param query the Gremlin query string to execute
   * @return a list of {@link GraphNode} objects representing the query results
   * @throws NeptuneException if the query execution fails or the connection is not established
   * @throws IllegalStateException if called when not connected
   */
  List<GraphNode> executeGremlinQuery(String query) throws NeptuneException;

  /**
   * Returns the cluster endpoint URL for this connection.
   *
   * <p>This is the Neptune cluster endpoint that was configured for this connection.
   *
   * @return the Neptune cluster endpoint URL
   */
  String getClusterEndpoint();
}

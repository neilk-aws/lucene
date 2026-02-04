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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Mock implementation of {@link NeptuneConnection} for testing purposes.
 *
 * <p>This mock allows tests to simulate Neptune connectivity without requiring a real Neptune
 * cluster. It supports configurable query results, connection state, and error simulation.
 *
 * <p>Example usage:
 *
 * <pre class="prettyprint">
 * MockNeptuneConnection mock = new MockNeptuneConnection("mock-endpoint");
 * mock.addQueryResult("g.V('person-1')", List.of(new GraphNode("person-1", "Person")));
 * mock.connect();
 * List&lt;GraphNode&gt; results = mock.executeGremlinQuery("g.V('person-1')");
 * </pre>
 */
public class MockNeptuneConnection implements NeptuneConnection {

  private final String endpoint;
  private boolean connected;
  private final Map<String, List<GraphNode>> queryResults;
  private final List<String> executedQueries;
  private Function<String, List<GraphNode>> defaultQueryHandler;
  private boolean simulateConnectionFailure;
  private boolean simulateQueryFailure;
  private String queryFailureMessage;
  private long queryDelayMs;

  /**
   * Constructs a new MockNeptuneConnection with the specified endpoint.
   *
   * @param endpoint the mock endpoint URL
   */
  public MockNeptuneConnection(String endpoint) {
    this.endpoint = endpoint;
    this.connected = false;
    this.queryResults = new HashMap<>();
    this.executedQueries = new ArrayList<>();
    this.simulateConnectionFailure = false;
    this.simulateQueryFailure = false;
    this.queryDelayMs = 0;
  }

  /**
   * Constructs a new MockNeptuneConnection with a default endpoint.
   */
  public MockNeptuneConnection() {
    this("mock-neptune-cluster.cluster-xxx.region.neptune.amazonaws.com");
  }

  @Override
  public void connect() throws NeptuneException {
    if (simulateConnectionFailure) {
      throw NeptuneException.connectionFailed(endpoint, new RuntimeException("Simulated connection failure"));
    }
    connected = true;
  }

  @Override
  public void disconnect() throws NeptuneException {
    connected = false;
  }

  @Override
  public boolean isConnected() {
    return connected;
  }

  @Override
  public List<GraphNode> executeGremlinQuery(String query) throws NeptuneException {
    if (!connected) {
      throw NeptuneException.notConnected();
    }

    if (simulateQueryFailure) {
      throw NeptuneException.queryFailed(query,
          new RuntimeException(queryFailureMessage != null ? queryFailureMessage : "Simulated query failure"));
    }

    // Simulate query delay if configured
    if (queryDelayMs > 0) {
      try {
        Thread.sleep(queryDelayMs);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new NeptuneException("Query interrupted", e);
      }
    }

    executedQueries.add(query);

    // Check for exact match in registered results
    if (queryResults.containsKey(query)) {
      return new ArrayList<>(queryResults.get(query));
    }

    // Try default handler if available
    if (defaultQueryHandler != null) {
      return defaultQueryHandler.apply(query);
    }

    // Return empty list for unregistered queries
    return new ArrayList<>();
  }

  @Override
  public String getClusterEndpoint() {
    return endpoint;
  }

  // ---- Test utility methods ----

  /**
   * Adds a query result mapping.
   *
   * @param query the Gremlin query string
   * @param results the results to return for this query
   * @return this instance for chaining
   */
  public MockNeptuneConnection addQueryResult(String query, List<GraphNode> results) {
    queryResults.put(query, new ArrayList<>(results));
    return this;
  }

  /**
   * Sets a default query handler for queries not explicitly registered.
   *
   * @param handler a function that takes a query string and returns results
   * @return this instance for chaining
   */
  public MockNeptuneConnection setDefaultQueryHandler(Function<String, List<GraphNode>> handler) {
    this.defaultQueryHandler = handler;
    return this;
  }

  /**
   * Returns the list of executed queries.
   *
   * @return a list of all executed query strings
   */
  public List<String> getExecutedQueries() {
    return new ArrayList<>(executedQueries);
  }

  /**
   * Returns the number of queries executed.
   *
   * @return the query execution count
   */
  public int getQueryExecutionCount() {
    return executedQueries.size();
  }

  /**
   * Clears the list of executed queries.
   *
   * @return this instance for chaining
   */
  public MockNeptuneConnection clearExecutedQueries() {
    executedQueries.clear();
    return this;
  }

  /**
   * Configures the mock to simulate connection failures.
   *
   * @param simulate whether to simulate connection failures
   * @return this instance for chaining
   */
  public MockNeptuneConnection simulateConnectionFailure(boolean simulate) {
    this.simulateConnectionFailure = simulate;
    return this;
  }

  /**
   * Configures the mock to simulate query failures.
   *
   * @param simulate whether to simulate query failures
   * @return this instance for chaining
   */
  public MockNeptuneConnection simulateQueryFailure(boolean simulate) {
    this.simulateQueryFailure = simulate;
    return this;
  }

  /**
   * Sets the error message for simulated query failures.
   *
   * @param message the error message
   * @return this instance for chaining
   */
  public MockNeptuneConnection setQueryFailureMessage(String message) {
    this.queryFailureMessage = message;
    return this;
  }

  /**
   * Sets a delay to simulate slow queries.
   *
   * @param delayMs the delay in milliseconds
   * @return this instance for chaining
   */
  public MockNeptuneConnection setQueryDelay(long delayMs) {
    this.queryDelayMs = delayMs;
    return this;
  }

  /**
   * Pre-connects the mock (sets connected state without calling connect()).
   *
   * @return this instance for chaining
   */
  public MockNeptuneConnection preConnect() {
    this.connected = true;
    return this;
  }

  /**
   * Clears all registered query results.
   *
   * @return this instance for chaining
   */
  public MockNeptuneConnection clearQueryResults() {
    queryResults.clear();
    return this;
  }

  /**
   * Resets the mock to its initial state.
   *
   * @return this instance for chaining
   */
  public MockNeptuneConnection reset() {
    connected = false;
    queryResults.clear();
    executedQueries.clear();
    defaultQueryHandler = null;
    simulateConnectionFailure = false;
    simulateQueryFailure = false;
    queryFailureMessage = null;
    queryDelayMs = 0;
    return this;
  }
}

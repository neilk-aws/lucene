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

/**
 * Exception thrown when Neptune-specific errors occur during graph database operations.
 *
 * <p>This exception wraps errors that may occur when:
 *
 * <ul>
 *   <li>Establishing or closing connections to Neptune
 *   <li>Executing Gremlin queries
 *   <li>Processing query results
 *   <li>Authentication or authorization failures
 *   <li>Network connectivity issues
 *   <li>Query timeouts
 * </ul>
 *
 * <p>The exception extends {@link RuntimeException} to allow for cleaner integration with Lucene's
 * query execution pipeline, which generally does not expect checked exceptions from custom query
 * implementations.
 *
 * @lucene.experimental
 */
public class NeptuneException extends RuntimeException {

  /**
   * Constructs a new Neptune exception with the specified detail message.
   *
   * @param message the detail message describing the error
   */
  public NeptuneException(String message) {
    super(message);
  }

  /**
   * Constructs a new Neptune exception with the specified detail message and cause.
   *
   * @param message the detail message describing the error
   * @param cause the underlying cause of this exception
   */
  public NeptuneException(String message, Throwable cause) {
    super(message, cause);
  }

  /**
   * Constructs a new Neptune exception with the specified cause.
   *
   * @param cause the underlying cause of this exception
   */
  public NeptuneException(Throwable cause) {
    super(cause);
  }

  /**
   * Creates a connection failure exception.
   *
   * @param endpoint the Neptune endpoint that failed to connect
   * @param cause the underlying cause
   * @return a new NeptuneException indicating connection failure
   */
  public static NeptuneException connectionFailed(String endpoint, Throwable cause) {
    return new NeptuneException("Failed to connect to Neptune cluster at: " + endpoint, cause);
  }

  /**
   * Creates a query execution failure exception.
   *
   * @param query the Gremlin query that failed
   * @param cause the underlying cause
   * @return a new NeptuneException indicating query failure
   */
  public static NeptuneException queryFailed(String query, Throwable cause) {
    return new NeptuneException("Failed to execute Gremlin query: " + query, cause);
  }

  /**
   * Creates a timeout exception.
   *
   * @param operation the operation that timed out
   * @param timeoutMs the timeout value in milliseconds
   * @return a new NeptuneException indicating timeout
   */
  public static NeptuneException timeout(String operation, long timeoutMs) {
    return new NeptuneException(operation + " timed out after " + timeoutMs + "ms");
  }

  /**
   * Creates a not connected exception.
   *
   * @return a new NeptuneException indicating the connection is not established
   */
  public static NeptuneException notConnected() {
    return new NeptuneException("Not connected to Neptune. Call connect() first.");
  }

  /**
   * Creates an authentication failure exception.
   *
   * @param cause the underlying cause
   * @return a new NeptuneException indicating authentication failure
   */
  public static NeptuneException authenticationFailed(Throwable cause) {
    return new NeptuneException("Neptune authentication failed", cause);
  }
}

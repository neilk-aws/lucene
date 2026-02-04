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

import java.time.Duration;
import java.util.Objects;

/**
 * Configuration settings for connecting to an Amazon Neptune graph database cluster.
 *
 * <p>This class encapsulates all the parameters needed to establish a connection to Neptune,
 * including the cluster endpoint, port, AWS region, authentication settings, and timeout
 * configurations.
 *
 * <p>Use the {@link Builder} to create instances of this class:
 *
 * <pre class="prettyprint">
 * NeptuneConnectionConfig config = NeptuneConnectionConfig.builder()
 *     .endpoint("my-neptune-cluster.cluster-xxx.region.neptune.amazonaws.com")
 *     .port(8182)
 *     .region("us-east-1")
 *     .iamAuthEnabled(true)
 *     .connectionTimeout(Duration.ofSeconds(30))
 *     .readTimeout(Duration.ofMinutes(5))
 *     .build();
 * </pre>
 *
 * @lucene.experimental
 */
public final class NeptuneConnectionConfig {

  /** Default Neptune port */
  public static final int DEFAULT_PORT = 8182;

  /** Default connection timeout */
  public static final Duration DEFAULT_CONNECTION_TIMEOUT = Duration.ofSeconds(30);

  /** Default read timeout */
  public static final Duration DEFAULT_READ_TIMEOUT = Duration.ofMinutes(5);

  private final String endpoint;
  private final int port;
  private final String region;
  private final boolean iamAuthEnabled;
  private final Duration connectionTimeout;
  private final Duration readTimeout;

  private NeptuneConnectionConfig(Builder builder) {
    this.endpoint = Objects.requireNonNull(builder.endpoint, "endpoint must not be null");
    this.port = builder.port;
    this.region = builder.region;
    this.iamAuthEnabled = builder.iamAuthEnabled;
    this.connectionTimeout = builder.connectionTimeout;
    this.readTimeout = builder.readTimeout;
  }

  /**
   * Creates a new {@link Builder} for constructing {@link NeptuneConnectionConfig} instances.
   *
   * @return a new builder instance
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Returns the Neptune cluster endpoint.
   *
   * @return the cluster endpoint URL
   */
  public String getEndpoint() {
    return endpoint;
  }

  /**
   * Returns the port number for the Neptune connection.
   *
   * @return the port number (default is {@value #DEFAULT_PORT})
   */
  public int getPort() {
    return port;
  }

  /**
   * Returns the AWS region where the Neptune cluster is located.
   *
   * @return the AWS region, or {@code null} if not set
   */
  public String getRegion() {
    return region;
  }

  /**
   * Returns whether IAM authentication is enabled for this connection.
   *
   * @return {@code true} if IAM authentication is enabled, {@code false} otherwise
   */
  public boolean isIamAuthEnabled() {
    return iamAuthEnabled;
  }

  /**
   * Returns the connection timeout duration.
   *
   * @return the connection timeout
   */
  public Duration getConnectionTimeout() {
    return connectionTimeout;
  }

  /**
   * Returns the read timeout duration for query execution.
   *
   * @return the read timeout
   */
  public Duration getReadTimeout() {
    return readTimeout;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    NeptuneConnectionConfig that = (NeptuneConnectionConfig) o;
    return port == that.port
        && iamAuthEnabled == that.iamAuthEnabled
        && Objects.equals(endpoint, that.endpoint)
        && Objects.equals(region, that.region)
        && Objects.equals(connectionTimeout, that.connectionTimeout)
        && Objects.equals(readTimeout, that.readTimeout);
  }

  @Override
  public int hashCode() {
    return Objects.hash(endpoint, port, region, iamAuthEnabled, connectionTimeout, readTimeout);
  }

  @Override
  public String toString() {
    return "NeptuneConnectionConfig{"
        + "endpoint='"
        + endpoint
        + '\''
        + ", port="
        + port
        + ", region='"
        + region
        + '\''
        + ", iamAuthEnabled="
        + iamAuthEnabled
        + ", connectionTimeout="
        + connectionTimeout
        + ", readTimeout="
        + readTimeout
        + '}';
  }

  /**
   * Builder for {@link NeptuneConnectionConfig}.
   *
   * <p>Provides a fluent API for constructing configuration objects with validation of required
   * parameters.
   *
   * @lucene.experimental
   */
  public static final class Builder {
    private String endpoint;
    private int port = DEFAULT_PORT;
    private String region;
    private boolean iamAuthEnabled = false;
    private Duration connectionTimeout = DEFAULT_CONNECTION_TIMEOUT;
    private Duration readTimeout = DEFAULT_READ_TIMEOUT;

    private Builder() {}

    /**
     * Sets the Neptune cluster endpoint.
     *
     * <p>This is a required parameter. The endpoint should be the cluster endpoint URL, typically
     * in the format: {@code
     * my-neptune-cluster.cluster-xxx.region.neptune.amazonaws.com}
     *
     * @param endpoint the Neptune cluster endpoint
     * @return this builder
     */
    public Builder endpoint(String endpoint) {
      this.endpoint = endpoint;
      return this;
    }

    /**
     * Sets the port number for the Neptune connection.
     *
     * @param port the port number (default is {@value NeptuneConnectionConfig#DEFAULT_PORT})
     * @return this builder
     * @throws IllegalArgumentException if port is not in valid range (1-65535)
     */
    public Builder port(int port) {
      if (port < 1 || port > 65535) {
        throw new IllegalArgumentException("Port must be between 1 and 65535, got: " + port);
      }
      this.port = port;
      return this;
    }

    /**
     * Sets the AWS region where the Neptune cluster is located.
     *
     * <p>This is required when IAM authentication is enabled.
     *
     * @param region the AWS region (e.g., "us-east-1")
     * @return this builder
     */
    public Builder region(String region) {
      this.region = region;
      return this;
    }

    /**
     * Sets whether IAM authentication should be enabled.
     *
     * <p>When enabled, requests to Neptune will be signed using AWS IAM credentials. This requires
     * the {@link #region(String)} to be set.
     *
     * @param iamAuthEnabled {@code true} to enable IAM authentication
     * @return this builder
     */
    public Builder iamAuthEnabled(boolean iamAuthEnabled) {
      this.iamAuthEnabled = iamAuthEnabled;
      return this;
    }

    /**
     * Sets the connection timeout duration.
     *
     * @param connectionTimeout the connection timeout
     * @return this builder
     * @throws NullPointerException if connectionTimeout is null
     * @throws IllegalArgumentException if connectionTimeout is negative or zero
     */
    public Builder connectionTimeout(Duration connectionTimeout) {
      Objects.requireNonNull(connectionTimeout, "connectionTimeout must not be null");
      if (connectionTimeout.isNegative() || connectionTimeout.isZero()) {
        throw new IllegalArgumentException("connectionTimeout must be positive");
      }
      this.connectionTimeout = connectionTimeout;
      return this;
    }

    /**
     * Sets the read timeout duration for query execution.
     *
     * @param readTimeout the read timeout
     * @return this builder
     * @throws NullPointerException if readTimeout is null
     * @throws IllegalArgumentException if readTimeout is negative or zero
     */
    public Builder readTimeout(Duration readTimeout) {
      Objects.requireNonNull(readTimeout, "readTimeout must not be null");
      if (readTimeout.isNegative() || readTimeout.isZero()) {
        throw new IllegalArgumentException("readTimeout must be positive");
      }
      this.readTimeout = readTimeout;
      return this;
    }

    /**
     * Builds the {@link NeptuneConnectionConfig} instance.
     *
     * @return the configuration instance
     * @throws IllegalStateException if required parameters are not set or configuration is invalid
     */
    public NeptuneConnectionConfig build() {
      if (endpoint == null || endpoint.isBlank()) {
        throw new IllegalStateException("endpoint must be set");
      }
      if (iamAuthEnabled && (region == null || region.isBlank())) {
        throw new IllegalStateException("region must be set when IAM authentication is enabled");
      }
      return new NeptuneConnectionConfig(this);
    }
  }
}

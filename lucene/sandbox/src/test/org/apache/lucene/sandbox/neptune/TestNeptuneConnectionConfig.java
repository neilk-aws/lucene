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
import org.apache.lucene.tests.util.LuceneTestCase;

/** Unit tests for {@link NeptuneConnectionConfig} and its Builder. */
public class TestNeptuneConnectionConfig extends LuceneTestCase {

  public void testBuilderWithRequiredParameters() {
    NeptuneConnectionConfig config =
        NeptuneConnectionConfig.builder()
            .endpoint("my-cluster.region.neptune.amazonaws.com")
            .build();

    assertEquals("my-cluster.region.neptune.amazonaws.com", config.getEndpoint());
    assertEquals(NeptuneConnectionConfig.DEFAULT_PORT, config.getPort());
    assertNull(config.getRegion());
    assertFalse(config.isIamAuthEnabled());
    assertEquals(NeptuneConnectionConfig.DEFAULT_CONNECTION_TIMEOUT, config.getConnectionTimeout());
    assertEquals(NeptuneConnectionConfig.DEFAULT_READ_TIMEOUT, config.getReadTimeout());
  }

  public void testBuilderWithAllParameters() {
    Duration connectionTimeout = Duration.ofSeconds(60);
    Duration readTimeout = Duration.ofMinutes(10);

    NeptuneConnectionConfig config =
        NeptuneConnectionConfig.builder()
            .endpoint("my-cluster.region.neptune.amazonaws.com")
            .port(8183)
            .region("us-west-2")
            .iamAuthEnabled(true)
            .connectionTimeout(connectionTimeout)
            .readTimeout(readTimeout)
            .build();

    assertEquals("my-cluster.region.neptune.amazonaws.com", config.getEndpoint());
    assertEquals(8183, config.getPort());
    assertEquals("us-west-2", config.getRegion());
    assertTrue(config.isIamAuthEnabled());
    assertEquals(connectionTimeout, config.getConnectionTimeout());
    assertEquals(readTimeout, config.getReadTimeout());
  }

  public void testBuilderWithoutEndpointFails() {
    IllegalStateException e =
        expectThrows(
            IllegalStateException.class, () -> NeptuneConnectionConfig.builder().build());
    assertTrue(e.getMessage().contains("endpoint must be set"));
  }

  public void testBuilderWithBlankEndpointFails() {
    IllegalStateException e =
        expectThrows(
            IllegalStateException.class,
            () -> NeptuneConnectionConfig.builder().endpoint("   ").build());
    assertTrue(e.getMessage().contains("endpoint must be set"));
  }

  public void testBuilderWithIamEnabledButNoRegionFails() {
    IllegalStateException e =
        expectThrows(
            IllegalStateException.class,
            () ->
                NeptuneConnectionConfig.builder()
                    .endpoint("my-cluster.neptune.amazonaws.com")
                    .iamAuthEnabled(true)
                    .build());
    assertTrue(e.getMessage().contains("region must be set when IAM authentication is enabled"));
  }

  public void testBuilderWithInvalidPortFails() {
    IllegalArgumentException e1 =
        expectThrows(
            IllegalArgumentException.class,
            () -> NeptuneConnectionConfig.builder().port(0));
    assertTrue(e1.getMessage().contains("Port must be between 1 and 65535"));

    IllegalArgumentException e2 =
        expectThrows(
            IllegalArgumentException.class,
            () -> NeptuneConnectionConfig.builder().port(65536));
    assertTrue(e2.getMessage().contains("Port must be between 1 and 65535"));

    IllegalArgumentException e3 =
        expectThrows(
            IllegalArgumentException.class,
            () -> NeptuneConnectionConfig.builder().port(-1));
    assertTrue(e3.getMessage().contains("Port must be between 1 and 65535"));
  }

  public void testBuilderWithNegativeConnectionTimeoutFails() {
    IllegalArgumentException e =
        expectThrows(
            IllegalArgumentException.class,
            () ->
                NeptuneConnectionConfig.builder()
                    .connectionTimeout(Duration.ofSeconds(-1)));
    assertTrue(e.getMessage().contains("connectionTimeout must be positive"));
  }

  public void testBuilderWithZeroConnectionTimeoutFails() {
    IllegalArgumentException e =
        expectThrows(
            IllegalArgumentException.class,
            () -> NeptuneConnectionConfig.builder().connectionTimeout(Duration.ZERO));
    assertTrue(e.getMessage().contains("connectionTimeout must be positive"));
  }

  public void testBuilderWithNullConnectionTimeoutFails() {
    expectThrows(
        NullPointerException.class,
        () -> NeptuneConnectionConfig.builder().connectionTimeout(null));
  }

  public void testBuilderWithNegativeReadTimeoutFails() {
    IllegalArgumentException e =
        expectThrows(
            IllegalArgumentException.class,
            () ->
                NeptuneConnectionConfig.builder()
                    .readTimeout(Duration.ofSeconds(-1)));
    assertTrue(e.getMessage().contains("readTimeout must be positive"));
  }

  public void testBuilderWithZeroReadTimeoutFails() {
    IllegalArgumentException e =
        expectThrows(
            IllegalArgumentException.class,
            () -> NeptuneConnectionConfig.builder().readTimeout(Duration.ZERO));
    assertTrue(e.getMessage().contains("readTimeout must be positive"));
  }

  public void testBuilderWithNullReadTimeoutFails() {
    expectThrows(
        NullPointerException.class,
        () -> NeptuneConnectionConfig.builder().readTimeout(null));
  }

  public void testEqualsAndHashCode() {
    NeptuneConnectionConfig config1 =
        NeptuneConnectionConfig.builder()
            .endpoint("my-cluster.neptune.amazonaws.com")
            .port(8182)
            .region("us-east-1")
            .iamAuthEnabled(true)
            .build();

    NeptuneConnectionConfig config2 =
        NeptuneConnectionConfig.builder()
            .endpoint("my-cluster.neptune.amazonaws.com")
            .port(8182)
            .region("us-east-1")
            .iamAuthEnabled(true)
            .build();

    NeptuneConnectionConfig config3 =
        NeptuneConnectionConfig.builder()
            .endpoint("other-cluster.neptune.amazonaws.com")
            .port(8182)
            .region("us-east-1")
            .iamAuthEnabled(true)
            .build();

    assertEquals(config1, config2);
    assertEquals(config1.hashCode(), config2.hashCode());
    assertNotEquals(config1, config3);
  }

  public void testEqualsWithDifferentPort() {
    NeptuneConnectionConfig config1 =
        NeptuneConnectionConfig.builder()
            .endpoint("my-cluster.neptune.amazonaws.com")
            .port(8182)
            .build();

    NeptuneConnectionConfig config2 =
        NeptuneConnectionConfig.builder()
            .endpoint("my-cluster.neptune.amazonaws.com")
            .port(8183)
            .build();

    assertNotEquals(config1, config2);
  }

  public void testEqualsWithDifferentIamSetting() {
    NeptuneConnectionConfig config1 =
        NeptuneConnectionConfig.builder()
            .endpoint("my-cluster.neptune.amazonaws.com")
            .region("us-east-1")
            .iamAuthEnabled(true)
            .build();

    NeptuneConnectionConfig config2 =
        NeptuneConnectionConfig.builder()
            .endpoint("my-cluster.neptune.amazonaws.com")
            .region("us-east-1")
            .iamAuthEnabled(false)
            .build();

    assertNotEquals(config1, config2);
  }

  public void testEqualsWithNull() {
    NeptuneConnectionConfig config =
        NeptuneConnectionConfig.builder()
            .endpoint("my-cluster.neptune.amazonaws.com")
            .build();

    assertFalse(config.equals(null));
  }

  public void testEqualsWithDifferentClass() {
    NeptuneConnectionConfig config =
        NeptuneConnectionConfig.builder()
            .endpoint("my-cluster.neptune.amazonaws.com")
            .build();

    assertFalse(config.equals("string"));
  }

  public void testToString() {
    NeptuneConnectionConfig config =
        NeptuneConnectionConfig.builder()
            .endpoint("my-cluster.neptune.amazonaws.com")
            .port(8182)
            .region("us-east-1")
            .iamAuthEnabled(true)
            .build();

    String str = config.toString();
    assertTrue(str.contains("my-cluster.neptune.amazonaws.com"));
    assertTrue(str.contains("8182"));
    assertTrue(str.contains("us-east-1"));
    assertTrue(str.contains("true"));
  }

  public void testDefaultValues() {
    assertEquals(8182, NeptuneConnectionConfig.DEFAULT_PORT);
    assertEquals(Duration.ofSeconds(30), NeptuneConnectionConfig.DEFAULT_CONNECTION_TIMEOUT);
    assertEquals(Duration.ofMinutes(5), NeptuneConnectionConfig.DEFAULT_READ_TIMEOUT);
  }

  public void testValidPortRange() {
    // Test minimum valid port
    NeptuneConnectionConfig minConfig =
        NeptuneConnectionConfig.builder()
            .endpoint("test.neptune.amazonaws.com")
            .port(1)
            .build();
    assertEquals(1, minConfig.getPort());

    // Test maximum valid port
    NeptuneConnectionConfig maxConfig =
        NeptuneConnectionConfig.builder()
            .endpoint("test.neptune.amazonaws.com")
            .port(65535)
            .build();
    assertEquals(65535, maxConfig.getPort());
  }

  public void testRandomizedConfig() {
    // Use LuceneTestCase's random() for randomized testing
    String endpoint = "cluster-" + random().nextInt(1000) + ".neptune.amazonaws.com";
    int port = 1 + random().nextInt(65535);
    String region = "us-" + (random().nextBoolean() ? "east" : "west") + "-" + (1 + random().nextInt(2));
    boolean iamEnabled = random().nextBoolean();

    NeptuneConnectionConfig.Builder builder =
        NeptuneConnectionConfig.builder()
            .endpoint(endpoint)
            .port(port);

    if (iamEnabled) {
      builder.region(region).iamAuthEnabled(true);
    }

    NeptuneConnectionConfig config = builder.build();

    assertEquals(endpoint, config.getEndpoint());
    assertEquals(port, config.getPort());
    assertEquals(iamEnabled, config.isIamAuthEnabled());
    if (iamEnabled) {
      assertEquals(region, config.getRegion());
    }
  }
}

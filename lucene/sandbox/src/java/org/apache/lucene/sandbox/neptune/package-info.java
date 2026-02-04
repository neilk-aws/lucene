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

/**
 * Neptune graph database integration for Lucene.
 *
 * <p>This package provides integration between Apache Lucene and Amazon Neptune graph database,
 * enabling hybrid text + graph search capabilities. It allows combining traditional Lucene text
 * searches with Neptune graph traversals to produce unified search results.
 *
 * <p>Key components:
 *
 * <ul>
 *   <li>{@link org.apache.lucene.sandbox.neptune.NeptuneConnection} - Interface for Neptune database
 *       connectivity
 *   <li>{@link org.apache.lucene.sandbox.neptune.NeptuneConnectionConfig} - Configuration for
 *       Neptune connections
 *   <li>{@link org.apache.lucene.sandbox.neptune.GraphNode} - Represents a node in the Neptune
 *       graph
 *   <li>{@link org.apache.lucene.sandbox.neptune.GraphEdge} - Represents an edge in the Neptune
 *       graph
 *   <li>{@link org.apache.lucene.sandbox.neptune.HybridSearchResult} - Contains combined results
 *       from text and graph searches
 *   <li>{@link org.apache.lucene.sandbox.neptune.NeptuneGraphQuery} - Lucene Query for executing
 *       graph traversals
 *   <li>{@link org.apache.lucene.sandbox.neptune.HybridTextGraphQuery} - Combined text + graph
 *       hybrid query
 *   <li>{@link org.apache.lucene.sandbox.neptune.ScoreCombinationStrategy} - Strategies for
 *       combining text and graph scores
 *   <li>{@link org.apache.lucene.sandbox.neptune.HybridSearchCollector} - Collector for hybrid
 *       search results
 * </ul>
 *
 * @lucene.experimental
 */
package org.apache.lucene.sandbox.neptune;

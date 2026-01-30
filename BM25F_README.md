# BM25F Multi-Field Query Parser for Apache Lucene

## Overview

This implementation adds **BM25F (BM25 for Fields)** multi-field ranking capabilities to Apache Lucene. BM25F is an extension of the BM25 ranking function that properly handles documents with multiple fields by allowing field-specific weights and length normalization parameters.

## What is BM25F?

BM25F extends the standard BM25 ranking function to handle multi-field documents more effectively. Unlike standard BM25, which treats each field independently, BM25F:

- **Aggregates term frequencies** across fields before scoring
- **Applies field-specific weights** to emphasize important fields (e.g., titles over body text)
- **Uses field-specific length normalization** to handle varying field lengths appropriately

### Key Differences from Standard BM25

| Feature | Standard BM25 | BM25F |
|---------|--------------|-------|
| Field Treatment | Each field scored independently | Fields aggregated with weights |
| Term Frequency | Per-field TF | Weighted TF across fields |
| Length Normalization | Single `b` parameter | Per-field `b` parameters |
| Field Weights | Applied as query boosts | Integrated into similarity scoring |

## Components

### 1. BM25FSimilarity

`org.apache.lucene.search.similarities.BM25FSimilarity`

A similarity implementation that extends `PerFieldSimilarityWrapper` to provide field-specific BM25 scoring parameters.

**Features:**
- Per-field boost/weight configuration
- Per-field length normalization (`b`) parameters
- Global `k1` (term frequency saturation) parameter
- Builder pattern for easy configuration

**Example:**
```java
BM25FSimilarity similarity = new BM25FSimilarity.Builder()
    .setK1(1.2f)                              // Global k1 parameter
    .setDefaultB(0.75f)                       // Default b for unconfigured fields
    .addFieldConfig("title", 3.0f, 0.5f)      // title: weight=3.0, b=0.5
    .addFieldConfig("body", 1.0f, 0.75f)      // body: weight=1.0, b=0.75
    .addFieldConfig("anchor", 2.0f, 0.6f)     // anchor: weight=2.0, b=0.6
    .build();
```

### 2. BM25FQueryParser

`org.apache.lucene.queryparser.classic.BM25FQueryParser`

A specialized query parser that extends `MultiFieldQueryParser` for use with BM25F similarity.

**Features:**
- Multi-field query parsing
- Field-specific boost configuration
- Compatible with all standard query syntax
- Validation of field boost configurations

**Example:**
```java
String[] fields = {"title", "body", "anchor"};
Map<String, Float> boosts = new HashMap<>();
boosts.put("title", 3.0f);
boosts.put("body", 1.0f);
boosts.put("anchor", 2.0f);

BM25FQueryParser parser = new BM25FQueryParser(fields, analyzer, boosts);
Query query = parser.parse("information retrieval");
```

## Usage Guide

### Basic Setup

```java
import org.apache.lucene.search.similarities.BM25FSimilarity;
import org.apache.lucene.queryparser.classic.BM25FQueryParser;

// 1. Configure BM25F similarity
BM25FSimilarity similarity = new BM25FSimilarity.Builder()
    .addFieldConfig("title", 3.0f, 0.5f)
    .addFieldConfig("body", 1.0f, 0.75f)
    .build();

// 2. Create query parser
String[] fields = {"title", "body"};
Map<String, Float> boosts = new HashMap<>();
boosts.put("title", 3.0f);
boosts.put("body", 1.0f);
BM25FQueryParser parser = new BM25FQueryParser(fields, analyzer, boosts);

// 3. Set up index and searcher
IndexSearcher searcher = new IndexSearcher(reader);
searcher.setSimilarity(similarity);

// 4. Parse and execute query
Query query = parser.parse("your search query");
TopDocs results = searcher.search(query, 10);
```

### Parameter Tuning Guide

#### Field Weights (Boost)

Field weights determine the relative importance of different fields:

- **Title**: 2.0 - 5.0 (titles are usually highly relevant)
- **Body/Content**: 1.0 (baseline reference)
- **Anchor Text**: 1.5 - 2.5 (external descriptions are valuable)
- **Metadata**: 0.5 - 1.5 (depending on quality and reliability)

#### Length Normalization (b parameter)

The `b` parameter controls how much document length affects scoring:

- **b = 0**: No length normalization (all documents treated equally)
- **b = 0.3 - 0.6**: Light normalization (good for short fields like titles)
- **b = 0.7 - 0.9**: Standard normalization (good for body text)
- **b = 1.0**: Full normalization (maximum length penalty)

**Recommendations:**
- Short fields (title, anchor): b = 0.3 - 0.6
- Long fields (body, content): b = 0.7 - 0.9
- Fixed-length fields: b = 0

#### k1 Parameter

Controls term frequency saturation:

- **k1 = 1.2**: Standard value (recommended starting point)
- **k1 = 0.5 - 1.0**: Lower saturation (for noisy collections)
- **k1 = 1.5 - 2.0**: Higher saturation (for clean collections)

## Complete Example

See `org.apache.lucene.demo.bm25f.BM25FDemo` for a complete working example that:
1. Creates an index with multi-field documents
2. Configures BM25F similarity with field-specific parameters
3. Executes searches and displays ranked results

Run the demo:
```bash
# Compile Lucene first
./gradlew assemble

# Run the demo
java -cp "lucene/demo/build/libs/*:lucene/core/build/libs/*:lucene/queryparser/build/libs/*" \
  org.apache.lucene.demo.bm25f.BM25FDemo /tmp/bm25f-index "search engines"
```

## Use Cases

### 1. Web Search
```java
BM25FSimilarity webSearch = new BM25FSimilarity.Builder()
    .addFieldConfig("title", 4.0f, 0.5f)      // High weight for page titles
    .addFieldConfig("body", 1.0f, 0.75f)      // Standard for body content
    .addFieldConfig("anchor", 2.5f, 0.6f)     // Important for link text
    .addFieldConfig("meta", 1.5f, 0.5f)       // Moderate for metadata
    .build();
```

### 2. E-commerce Product Search
```java
BM25FSimilarity productSearch = new BM25FSimilarity.Builder()
    .addFieldConfig("name", 5.0f, 0.3f)       // Product name most important
    .addFieldConfig("description", 1.0f, 0.8f)
    .addFieldConfig("reviews", 0.8f, 0.85f)   // Reviews less important
    .addFieldConfig("specs", 1.2f, 0.7f)
    .build();
```

### 3. Academic Paper Search
```java
BM25FSimilarity academicSearch = new BM25FSimilarity.Builder()
    .addFieldConfig("title", 3.5f, 0.4f)      // Paper title very important
    .addFieldConfig("abstract", 2.0f, 0.6f)   // Abstract highly relevant
    .addFieldConfig("body", 1.0f, 0.75f)      // Full text baseline
    .addFieldConfig("keywords", 2.5f, 0.0f)   // Keywords, no length norm
    .build();
```

## Testing

The implementation includes comprehensive test suites:

- `TestBM25FSimilarity`: Unit tests for similarity configuration
- `TestBM25FQueryParser`: Tests for query parser functionality
- Integration tests demonstrating end-to-end usage

Run tests:
```bash
./gradlew test --tests TestBM25FSimilarity
./gradlew test --tests TestBM25FQueryParser
```

## Performance Considerations

1. **Memory**: Field-specific similarities use slightly more memory than standard BM25
2. **Query Time**: Performance is comparable to standard multi-field queries
3. **Index Time**: No impact on indexing performance
4. **Caching**: Field similarities are cached per-field for efficiency

## References

- Robertson, S., Zaragoza, H., & Taylor, M. (2004). "Simple BM25 extension to multiple weighted fields." *Proceedings of CIKM 2004*.
- Robertson, S., & Zaragoza, H. (2009). "The Probabilistic Relevance Framework: BM25 and Beyond." *Foundations and Trends in Information Retrieval*.

## API Documentation

### BM25FSimilarity.Builder

| Method | Description |
|--------|-------------|
| `setK1(float)` | Sets global k1 parameter (default: 1.2) |
| `setDefaultB(float)` | Sets default b for unconfigured fields (default: 0.75) |
| `setDiscountOverlaps(boolean)` | Whether to discount overlapping tokens (default: true) |
| `addFieldConfig(String, float, float)` | Adds field configuration (name, boost, b) |
| `build()` | Builds the BM25FSimilarity instance |

### BM25FSimilarity

| Method | Description |
|--------|-------------|
| `getK1()` | Returns the k1 parameter |
| `getDefaultB()` | Returns the default b parameter |
| `getFieldConfig(String)` | Returns configuration for a specific field |
| `get(String)` | Returns BM25Similarity instance for a field |

### BM25FQueryParser

| Method | Description |
|--------|-------------|
| `BM25FQueryParser(String[], Analyzer, Map<String,Float>)` | Constructor with fields, analyzer, and boosts |
| `BM25FQueryParser(String[], Analyzer)` | Constructor with uniform field weights |
| `validateFieldBoosts(Map<String,Float>)` | Static method to validate boost configuration |
| `parse(String)` | Parses query string into Query object |

## License

This implementation is part of Apache Lucene and is licensed under the Apache License 2.0.

## Contributing

Contributions are welcome! Please follow the Lucene contribution guidelines and ensure all tests pass before submitting.

# BM25F Multi-Field Query Parser

This implementation adds BM25F (BM25 Field) multi-field ranking support to Apache Lucene.

## Overview

BM25F extends the BM25 ranking function to handle documents with multiple fields by combining term frequencies across fields with field-specific parameters. This allows more sophisticated ranking strategies where different fields have different importance and length normalization behaviors.

## Components

### 1. BM25FSimilarity

**Location**: `lucene/core/src/java/org/apache/lucene/search/similarities/BM25FSimilarity.java`

A similarity implementation that computes BM25F scores with:
- **Field-specific boosts**: Control the relative importance of each field
- **Field-specific b parameters**: Control length normalization per field
- **Global k1 parameter**: Control term frequency saturation

#### Key Features:
- Extends `Similarity` base class
- Supports configurable field weights and length normalization parameters
- Efficient scoring with precomputed caches
- Comprehensive score explanations

#### Usage Example:
```java
// Create BM25F similarity with field-specific parameters
Map<String, Float> fieldBoosts = new HashMap<>();
fieldBoosts.put("title", 3.0f);  // Title is 3x more important
fieldBoosts.put("body", 1.0f);

Map<String, Float> fieldBParams = new HashMap<>();
fieldBParams.put("title", 0.5f);   // Less length normalization
fieldBParams.put("body", 0.75f);   // Standard length normalization

BM25FSimilarity similarity = new BM25FSimilarity(1.2f, 0.75f, fieldBoosts, fieldBParams);

// Use with IndexSearcher
IndexSearcher searcher = new IndexSearcher(reader);
searcher.setSimilarity(similarity);
```

### 2. BM25FQueryParser

**Location**: `lucene/queryparser/src/java/org/apache/lucene/queryparser/classic/BM25FQueryParser.java`

A query parser that extends `MultiFieldQueryParser` to work with BM25F similarity.

#### Key Features:
- Parses queries across multiple fields
- Configurable field boosts and b parameters
- Returns the BM25FSimilarity instance for use with IndexSearcher
- Supports all standard query parser syntax (AND, OR, NOT, phrases, etc.)

#### Usage Example:
```java
// Create parser for multiple fields
String[] fields = {"title", "body", "abstract"};
Analyzer analyzer = new StandardAnalyzer();

Map<String, Float> boosts = new HashMap<>();
boosts.put("title", 2.0f);
boosts.put("body", 1.0f);

BM25FQueryParser parser = new BM25FQueryParser(fields, analyzer, boosts);

// Configure BM25F parameters
parser.setK1(1.2f);
parser.setDefaultB(0.75f);
parser.setFieldBParam("title", 0.5f);

// Parse and execute query
Query query = parser.parse("machine learning");

IndexSearcher searcher = new IndexSearcher(reader);
searcher.setSimilarity(parser.getSimilarity());
TopDocs results = searcher.search(query, 10);
```

## BM25F Scoring Formula

The BM25F score is computed as:

```
score(q, d) = sum over terms t in q: IDF(t) * (combined_tf / (combined_tf + k1))
```

where:
```
combined_tf = sum over fields f: (boost_f * tf_f) / (1 - b_f + b_f * len_f / avglen_f)

IDF(t) = log(1 + (N - n + 0.5) / (n + 0.5))
```

Parameters:
- `k1`: Controls term frequency saturation (typical: 1.2)
- `b_f`: Length normalization for field f (0 = none, 1 = full, typical: 0.75)
- `boost_f`: Importance weight for field f
- `tf_f`: Term frequency in field f
- `len_f`: Length of field f
- `avglen_f`: Average length of field f across corpus
- `N`: Total number of documents
- `n`: Number of documents containing term t

## Tests

### TestBM25FSimilarity

**Location**: `lucene/core/src/test/org/apache/lucene/search/similarities/TestBM25FSimilarity.java`

Comprehensive tests for BM25FSimilarity including:
- Parameter validation
- Default values
- Field boost configuration
- Field b parameter configuration
- Basic scoring functionality
- Field boost ranking impact
- Field-specific b parameters
- Score explanations

### TestBM25FQueryParser

**Location**: `lucene/queryparser/src/test/org/apache/lucene/queryparser/classic/TestBM25FQueryParser.java`

Tests for BM25FQueryParser including:
- Construction with and without boosts
- Parameter setting and validation
- Query parsing (single term, multi-term, boolean, phrases)
- End-to-end search scenarios
- Field boost impact on ranking
- Special characters and edge cases

## Demo

### BM25FDemo

**Location**: `lucene/demo/src/java/org/apache/lucene/demo/bm25f/BM25FDemo.java`

A complete demonstration showing:
- Configuring BM25F with field-specific parameters
- Indexing documents with multiple fields
- Parsing queries with BM25FQueryParser
- Executing searches and displaying results
- Viewing score explanations

**Running the demo**:
```bash
cd lucene
./gradlew :lucene:demo:run -PmainClass=org.apache.lucene.demo.bm25f.BM25FDemo
```

## When to Use BM25F

BM25F is particularly useful when:

1. **Multiple fields with different importance**: Documents have fields like title, abstract, and body where matches in title should count more than body
2. **Different length normalization needs**: Short fields (like titles) need different length normalization than long fields (like body text)
3. **Heterogeneous document structure**: Documents have varying structure and field presence
4. **Improved relevance over simple field boosting**: BM25F properly combines term frequencies before scoring, unlike simple per-field boosting which scores fields independently

## Advantages over Standard Multi-Field Queries

1. **Unified scoring**: Combines term frequencies across fields before scoring, avoiding issues where a term appearing once in a high-boost field dominates over many occurrences in other fields
2. **Field-specific length normalization**: Different fields can have different sensitivity to document length
3. **Theoretically grounded**: Based on probabilistic retrieval models with proven effectiveness
4. **Configurable**: Fine-grained control over field importance and normalization behavior

## Configuration Guidelines

### k1 Parameter (term frequency saturation)
- **Lower values (0.5 - 1.0)**: Faster saturation, good for collections where term repetition is less meaningful
- **Standard value (1.2)**: Default BM25 value, works well for most collections
- **Higher values (1.5 - 2.0)**: Slower saturation, gives more weight to term frequency

### b Parameter (length normalization)
- **b = 0**: No length normalization, all documents treated equally regardless of length
- **b = 0.3 - 0.5**: Light normalization, good for titles and short fields
- **b = 0.75**: Standard value, good for body text
- **b = 1.0**: Full normalization, heavily penalizes long documents

### Field Boosts
- Start with ratios based on intuition (e.g., title:abstract:body = 3:2:1)
- Tune based on relevance feedback and evaluation metrics
- Higher boosts for fields where matches are more indicative of relevance

## Performance Considerations

- BM25FSimilarity uses precomputed caches for efficient scoring
- Memory usage is similar to standard BM25Similarity
- Query parsing overhead is minimal compared to standard MultiFieldQueryParser
- Scoring performance is comparable to BM25 with multiple fields

## Compatibility

- Requires Apache Lucene 10.x or later
- Compatible with all standard Lucene query types
- Works with any Analyzer
- Can be combined with other Lucene features (facets, highlighting, etc.)

## References

1. Robertson, S., & Zaragoza, H. (2009). The Probabilistic Relevance Framework: BM25 and Beyond. Foundations and Trends in Information Retrieval, 3(4), 333-389.

2. Zaragoza, H., Craswell, N., Taylor, M., Saria, S., & Robertson, S. (2004). Microsoft Cambridge at TREC-13: Web and HARD tracks. In Proceedings of TREC.

## Future Enhancements

Potential improvements for future versions:
- BM25F+ variant with improved term frequency saturation
- Per-field IDF computation options
- Integration with learning-to-rank frameworks
- Query-time field boost adjustment
- Field presence detection and handling

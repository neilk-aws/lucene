# BM25F Multi-Field Query Parser

This implementation provides a BM25F (Best Match 25 with Fields) query parser for Apache Lucene, enabling effective multi-field search with proper term frequency aggregation across fields.

## Overview

BM25F is an extension of the BM25 ranking algorithm that properly handles multiple fields. Unlike standard multi-field queries that create a disjunction (OR) of queries across fields, BM25F aggregates term frequencies across all fields before computing the BM25 score, which provides more accurate relevance scoring for multi-field documents.

## What is BM25F?

BM25F extends the classic BM25 algorithm to handle documents with multiple fields (e.g., title, body, abstract). The key innovation is that it:

1. **Aggregates term frequencies** from all fields into a single weighted frequency
2. **Applies field-specific boosts** to control the importance of different fields
3. **Normalizes by field length** to account for varying field lengths
4. **Computes a single BM25 score** based on the aggregated statistics

This is more principled than simply creating a boolean query across fields, as it properly accounts for how terms appear across different parts of a document.

## Implementation Details

This implementation leverages Lucene's built-in `CombinedFieldQuery` class, which implements the BM25F algorithm. The `BM25FQueryParser` provides a convenient query parser interface that:

- Parses user queries and creates `CombinedFieldQuery` instances for term queries
- Supports field weights (boosts) with validation (weights must be >= 1.0)
- Falls back to standard multi-field behavior for complex query types (phrases, wildcards, etc.)
- Maintains compatibility with Lucene's standard query syntax

### Key Components

1. **BM25FQueryParser**: The main query parser class that extends `QueryParser`
   - Parses query strings and creates appropriate query objects
   - Handles field weights and validation
   - Provides both instance and static parsing methods

2. **CombinedFieldQuery** (built-in Lucene class): Implements BM25F scoring
   - Aggregates term statistics across multiple fields
   - Supports field-specific weights
   - Requires norms to be enabled on all fields

3. **TestBM25FQueryParser**: Comprehensive test suite
   - Tests basic functionality, field weights, and edge cases
   - Includes integration tests with actual indexing and searching
   - Validates scoring behavior

4. **BM25FSearchDemo**: Example application
   - Demonstrates practical usage scenarios
   - Shows impact of different field weights
   - Includes sample documents about machine learning topics

## Usage

### Basic Usage

```java
// Create a parser for title and body fields with equal weights
String[] fields = {"title", "body"};
Analyzer analyzer = new StandardAnalyzer();
BM25FQueryParser parser = new BM25FQueryParser(fields, analyzer);

// Parse a query
Query query = parser.parse("machine learning");

// Use the query with an IndexSearcher
IndexSearcher searcher = new IndexSearcher(reader);
searcher.setSimilarity(new BM25Similarity());
TopDocs results = searcher.search(query, 10);
```

### Using Field Weights

```java
// Create field weights (must be >= 1.0)
Map<String, Float> weights = new HashMap<>();
weights.put("title", 3.0f);  // Title is 3x more important
weights.put("body", 1.0f);

BM25FQueryParser parser = new BM25FQueryParser(fields, analyzer, weights);
Query query = parser.parse("machine learning");
```

### Static Parsing

```java
// Parse without creating a parser instance
Query query = BM25FQueryParser.parse(
    "machine learning",
    new String[]{"title", "body"},
    analyzer,
    weights  // optional, can be null for equal weights
);
```

## Requirements

### Index Requirements

For BM25F to work correctly, your index must satisfy these requirements:

1. **Norms must be enabled**: All fields used in BM25F queries must have norms enabled
   ```java
   FieldType fieldType = new FieldType();
   fieldType.setOmitNorms(false);  // Important!
   fieldType.setIndexOptions(IndexOptions.DOCS_AND_FREQS);
   ```

2. **BM25Similarity must be used**: Set BM25Similarity on both indexing and searching
   ```java
   // Indexing
   IndexWriterConfig config = new IndexWriterConfig(analyzer);
   config.setSimilarity(new BM25Similarity());
   
   // Searching
   searcher.setSimilarity(new BM25Similarity());
   ```

3. **Consistent analyzer**: All fields should use the same analyzer for best results

### Field Weight Requirements

- Field weights must be >= 1.0 (enforced by `CombinedFieldQuery`)
- A weight of 1.0 means normal importance
- Higher weights (e.g., 2.0, 3.0) increase field importance
- Weights < 1.0 are not supported (use different fields or preprocessing instead)

## Query Type Support

The `BM25FQueryParser` handles different query types as follows:

| Query Type | BM25F Support | Behavior |
|------------|---------------|----------|
| Simple terms | ✅ Full | Creates `CombinedFieldQuery` |
| Multi-term queries | ✅ Full | Creates `BooleanQuery` of `CombinedFieldQuery` |
| Boolean operators (+, -, AND, OR) | ✅ Full | Supported with proper operator semantics |
| Field-specific queries (field:term) | ✅ Full | Uses standard single-field behavior |
| Phrase queries ("quoted text") | ⚠️ Fallback | Falls back to standard multi-field |
| Fuzzy queries (term~) | ⚠️ Fallback | Falls back to standard multi-field |
| Wildcard queries (term*) | ⚠️ Fallback | Falls back to standard multi-field |
| Prefix queries (term*) | ⚠️ Fallback | Falls back to standard multi-field |
| Range queries ([a TO z]) | ⚠️ Fallback | Falls back to standard multi-field |
| Regex queries (/regex/) | ⚠️ Fallback | Falls back to standard multi-field |

For query types that fall back to standard multi-field behavior, the parser creates a disjunction (OR) of field-specific queries rather than using BM25F scoring. This maintains correctness while providing BM25F benefits for the most common query types.

## Examples

### Example 1: Basic Search

```java
BM25FQueryParser parser = new BM25FQueryParser(
    new String[]{"title", "body"},
    new StandardAnalyzer()
);

Query q = parser.parse("machine learning");
// Creates: CombinedFieldQuery with "machine" AND CombinedFieldQuery with "learning"
```

### Example 2: Weighted Fields

```java
Map<String, Float> weights = new HashMap<>();
weights.put("title", 5.0f);
weights.put("abstract", 2.0f);
weights.put("body", 1.0f);

BM25FQueryParser parser = new BM25FQueryParser(
    new String[]{"title", "abstract", "body"},
    new StandardAnalyzer(),
    weights
);

Query q = parser.parse("neural networks");
// Title matches count 5x more, abstract 2x more than body
```

### Example 3: Complex Query

```java
Query q = parser.parse("+machine +learning -supervised");
// Must contain "machine" AND "learning", must NOT contain "supervised"
// Each term uses BM25F scoring across all fields
```

## Performance Considerations

1. **Field count**: BM25F performs best with 2-5 fields. Too many fields can dilute term frequency signals.

2. **Weight tuning**: Field weights should be tuned based on:
   - Field importance (title usually more important than body)
   - Field length (shorter fields may need higher weights)
   - Your specific use case and evaluation metrics

3. **Index size**: BM25F queries are comparable in performance to standard term queries. The main overhead is in reading term statistics from multiple fields.

4. **Norm storage**: Since norms must be enabled, ensure you have adequate storage for norm values.

## Testing

Run the test suite:

```bash
./gradlew :lucene:queryparser:test --tests TestBM25FQueryParser
```

Run the demo:

```bash
./gradlew :lucene:demo:run -Ptask=BM25FSearchDemo
```

## References

- Robertson, S., Zaragoza, H., & Taylor, M. (2004). "Simple BM25 extension to multiple weighted fields." CIKM '04.
- Original BM25F paper: http://www.staff.city.ac.uk/~sb317/papers/foundations_bm25_review.pdf
- Lucene CombinedFieldQuery documentation

## Future Enhancements

Potential improvements for future versions:

1. Support for phrase queries with BM25F scoring
2. Integration with query builders for programmatic query construction
3. Support for per-field BM25 parameters (k1, b)
4. Query explanation improvements to show field contribution details
5. Support for dynamic field weights based on document properties

## Contributing

When contributing to this implementation:

1. Ensure all tests pass
2. Add tests for new functionality
3. Follow Lucene coding conventions
4. Update this documentation for significant changes

## License

Licensed under the Apache License, Version 2.0. See the LICENSE file for details.

# BM25F Multi-Field Query Parser for Apache Lucene

## Overview

This implementation provides BM25F (BM25 for multi-field documents) scoring and query parsing for Apache Lucene. BM25F is a principled extension of BM25 that properly handles multi-field documents by combining field statistics before applying the BM25 formula.

## Features

- **BM25FSimilarity**: Full BM25F scoring implementation extending Lucene's Similarity class
- **BM25FQueryParser**: Convenient multi-field query parser with BM25F integration
- **Field-specific boosts**: Configure different weights for different fields (e.g., title vs. body)
- **Field-specific length normalization**: Per-field b parameters for fine-tuned control
- **Comprehensive tests**: Full unit test coverage for both components
- **Detailed documentation**: Extensive user guide with examples and best practices

## Quick Start

### Basic Usage

```java
// Define fields and boosts
String[] fields = {"title", "body", "keywords"};
Map<String, Float> fieldBoosts = new HashMap<>();
fieldBoosts.put("title", 5.0f);
fieldBoosts.put("body", 1.0f);
fieldBoosts.put("keywords", 3.0f);

// Create parser
BM25FQueryParser parser = new BM25FQueryParser(
    fields,
    new StandardAnalyzer(),
    fieldBoosts
);

// Parse query
Query query = parser.parse("information retrieval");

// Configure searcher
BM25FSimilarity similarity = parser.createBM25FSimilarity();
IndexSearcher searcher = new IndexSearcher(reader);
searcher.setSimilarity(similarity);

// Search!
TopDocs results = searcher.search(query, 10);
```

## Why BM25F?

### Problem with Standard Multi-Field Search

Traditional multi-field search in Lucene typically:
1. Scores each field independently using BM25
2. Combines the scores (usually with boosts)

This approach has limitations:
- Field boosts are applied *after* BM25 scoring
- Each field is treated as a separate document
- Document length normalization happens per-field, not holistically

### BM25F Solution

BM25F addresses these issues by:
1. Combining term frequencies across fields *before* scoring
2. Applying field weights at the term frequency level
3. Using a unified length normalization across fields
4. Computing a single BM25 score from combined statistics

This is more theoretically sound and often produces better ranking in practice.

## Components

### BM25FSimilarity

Location: `org.apache.lucene.search.similarities.BM25FSimilarity`

A Similarity implementation that:
- Extends Lucene's Similarity class
- Implements the BM25F scoring formula
- Supports per-field boost weights
- Supports per-field length normalization (b parameters)
- Provides detailed scoring explanations

**Key Parameters:**
- `k1`: Term frequency saturation (default: 1.2)
- `fieldBoosts`: Map of field names to boost values
- `fieldBParams`: Map of field names to b parameters (default: 0.75)

### BM25FQueryParser

Location: `org.apache.lucene.queryparser.classic.BM25FQueryParser`

A QueryParser that:
- Extends MultiFieldQueryParser
- Integrates seamlessly with BM25FSimilarity
- Provides convenience methods for creating matching similarity instances
- Supports all standard QueryParser features (phrases, wildcards, boolean operators, etc.)

## Installation

This implementation is designed to be integrated into Apache Lucene. Files are organized as:

```
lucene/
├── core/
│   └── src/
│       ├── java/org/apache/lucene/search/similarities/
│       │   └── BM25FSimilarity.java
│       └── test/org/apache/lucene/search/similarities/
│           └── TestBM25FSimilarity.java
└── queryparser/
    └── src/
        ├── java/org/apache/lucene/queryparser/classic/
        │   └── BM25FQueryParser.java
        ├── test/org/apache/lucene/queryparser/classic/
        │   └── TestBM25FQueryParser.java
        └── docs/
            └── BM25F_USER_GUIDE.md
```

## Testing

Run the test suite:

```bash
# Test BM25FSimilarity
./gradlew :lucene:core:test --tests TestBM25FSimilarity

# Test BM25FQueryParser
./gradlew :lucene:queryparser:test --tests TestBM25FQueryParser

# Run all tests
./gradlew test
```

## Documentation

See the comprehensive user guide: `lucene/queryparser/docs/BM25F_USER_GUIDE.md`

The user guide includes:
- Detailed explanation of BM25F theory
- Parameter tuning guidelines
- Complete code examples
- Best practices
- Troubleshooting guide

## Examples

### Example 1: Academic Paper Search

```java
String[] fields = {"title", "abstract", "body", "keywords"};

Map<String, Float> boosts = new HashMap<>();
boosts.put("title", 7.0f);      // Titles are very important
boosts.put("abstract", 3.0f);   // Abstracts are moderately important
boosts.put("body", 1.0f);       // Body is baseline
boosts.put("keywords", 5.0f);   // Keywords are highly relevant

Map<String, Float> bParams = new HashMap<>();
bParams.put("title", 0.75f);
bParams.put("abstract", 0.75f);
bParams.put("body", 0.75f);
bParams.put("keywords", 0.3f);  // Less length normalization for keywords

BM25FSimilarity similarity = new BM25FSimilarity(1.2f, boosts, bParams);
```

### Example 2: Product Search

```java
String[] fields = {"name", "description", "category", "brand"};

Map<String, Float> boosts = new HashMap<>();
boosts.put("name", 8.0f);        // Product name is most important
boosts.put("description", 1.0f); // Description is baseline
boosts.put("category", 2.0f);    // Category is moderately important
boosts.put("brand", 3.0f);       // Brand matches are important

Map<String, Float> bParams = new HashMap<>();
bParams.put("name", 0.5f);        // Less length penalty for short names
bParams.put("description", 0.75f);
bParams.put("category", 0.3f);    // Categories are usually short
bParams.put("brand", 0.3f);       // Brands are usually short

BM25FSimilarity similarity = new BM25FSimilarity(1.2f, boosts, bParams);
```

### Example 3: Web Page Search

```java
String[] fields = {"title", "content", "meta_description", "headings"};

Map<String, Float> boosts = new HashMap<>();
boosts.put("title", 10.0f);           // Page title is critical
boosts.put("content", 1.0f);          // Content is baseline
boosts.put("meta_description", 4.0f); // Meta descriptions are important
boosts.put("headings", 6.0f);         // Headings are very important

Map<String, Float> bParams = new HashMap<>();
bParams.put("title", 0.75f);
bParams.put("content", 0.8f);          // More length penalty for verbose content
bParams.put("meta_description", 0.5f); // Less penalty for descriptions
bParams.put("headings", 0.5f);         // Less penalty for headings

BM25FSimilarity similarity = new BM25FSimilarity(1.2f, boosts, bParams);
```

## Performance Considerations

BM25F has similar computational complexity to standard BM25:
- Scoring overhead is minimal compared to standard BM25
- Multi-field queries may be slower than single-field queries
- Precomputed norm caches are used for efficiency
- Memory usage scales with number of fields

For high-performance scenarios:
- Limit the number of fields in queries
- Use appropriate field boosts to focus on key fields
- Consider caching frequently-used queries
- Profile with your specific data and query patterns

## Theoretical Background

BM25F extends BM25 by computing a weighted term frequency (wtf) across fields:

```
wtf = Σ(w_f × tf_f)
```

And a weighted document length (wdl):

```
wdl = Σ(w_f × dl_f)
```

These are then used in the standard BM25 formula:

```
score = idf × (wtf × (k1 + 1)) / (wtf + k1 × (1 - b + b × wdl / avgwdl))
```

This approach is more principled than post-hoc boost multiplication because:
1. Field evidence is combined before non-linear transformations
2. Length normalization considers the document as a whole
3. The probabilistic interpretation of BM25 is preserved

## References

**Primary Reference:**
- Robertson, S., Zaragoza, H., and Taylor, M. (2004). "Simple BM25 Extension to Multiple Weighted Fields." In Proceedings of the thirteenth ACM international conference on Information and knowledge management (CIKM '04).

**Related Work:**
- Robertson, S. and Zaragoza, H. (2009). "The Probabilistic Relevance Framework: BM25 and Beyond." Foundations and Trends in Information Retrieval, Vol. 3, No. 4, pp. 333-389.
- Zaragoza, H., Craswell, N., Taylor, M., Saria, S., and Robertson, S. (2004). "Microsoft Cambridge at TREC-13: Web and HARD tracks." In Proceedings of TREC 2004.

## License

This implementation is licensed under the Apache License, Version 2.0, consistent with Apache Lucene.

## Contributing

This implementation follows Apache Lucene's coding conventions:
- Javadoc comments for all public APIs
- Unit tests for all functionality
- Code formatting using Lucene's style guide
- Proper exception handling and validation

## Support

For issues, questions, or contributions related to this BM25F implementation:
1. Review the user guide documentation
2. Check the unit tests for usage examples
3. Consult the Lucene mailing lists
4. File issues through the standard Lucene process

## Future Enhancements

Potential improvements for future versions:
- Per-field k1 parameters (currently k1 is global)
- Support for field-specific term weighting functions
- Integration with learning-to-rank frameworks
- Additional explanation detail for debugging
- Performance optimizations for very large field sets
- Support for field subsets in queries

## Version History

- **Initial Release**: Full BM25F implementation with similarity class, query parser, tests, and documentation

## Acknowledgments

This implementation is based on the BM25F model developed by Stephen Robertson, Hugo Zaragoza, and Michael Taylor. The implementation follows the design patterns and conventions of Apache Lucene.

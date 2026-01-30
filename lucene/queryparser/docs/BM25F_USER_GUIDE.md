# BM25F Multi-Field Query Parser - User Guide

## Overview

This implementation provides BM25F (BM25 for multi-field documents) support for Apache Lucene. BM25F extends the standard BM25 ranking function to properly handle multi-field documents by combining field statistics before applying the BM25 formula, rather than after.

## What is BM25F?

BM25F is an extension of the BM25 ranking function designed for structured documents with multiple fields. The key insight of BM25F is that it combines evidence from multiple fields in a more principled way than simply boosting individual field queries.

### Key Differences from Standard BM25

1. **Field-specific boosts (weights)**: Different fields can have different importance (e.g., title vs. body)
2. **Field-specific length normalization**: Each field can have its own b parameter
3. **Combined term frequencies**: Term frequencies are combined across fields using weighted sums before applying BM25
4. **Unified scoring**: A single BM25 score is computed from the combined statistics

### The BM25F Formula

The weighted term frequency across fields:
```
wtf = Σ(w_f × tf_f)
```

The weighted document length:
```
wdl = Σ(w_f × dl_f)
```

The final BM25F score:
```
score = idf × (wtf × (k1 + 1)) / (wtf + k1 × (1 - b + b × wdl / avgwdl))
```

Where:
- `w_f` = field boost/weight
- `tf_f` = term frequency in field f
- `dl_f` = document length in field f
- `k1` = term frequency saturation parameter (typically 1.2)
- `b` = length normalization parameter (typically 0.75)

## Components

### BM25FSimilarity

The `BM25FSimilarity` class implements the BM25F scoring function. It extends Lucene's `Similarity` class and provides field-specific boost and length normalization parameters.

### BM25FQueryParser

The `BM25FQueryParser` class provides a convenient way to parse queries across multiple fields with proper BM25F scoring. It extends `MultiFieldQueryParser` and integrates seamlessly with `BM25FSimilarity`.

## Usage Examples

### Basic Usage

```java
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.queryparser.classic.BM25FQueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.BM25FSimilarity;

import java.util.HashMap;
import java.util.Map;

// 1. Define fields and their boosts
String[] fields = {"title", "body", "keywords"};

Map<String, Float> fieldBoosts = new HashMap<>();
fieldBoosts.put("title", 5.0f);     // Title is 5x more important
fieldBoosts.put("body", 1.0f);      // Body has standard weight
fieldBoosts.put("keywords", 3.0f);  // Keywords are 3x more important

// 2. Create the query parser
BM25FQueryParser parser = new BM25FQueryParser(
    fields,
    new StandardAnalyzer(),
    fieldBoosts
);

// 3. Parse a query
Query query = parser.parse("machine learning algorithms");

// 4. Configure the searcher with BM25F similarity
Map<String, Float> fieldBParams = new HashMap<>();
fieldBParams.put("title", 0.75f);
fieldBParams.put("body", 0.75f);
fieldBParams.put("keywords", 0.5f);  // Less length normalization for keywords

BM25FSimilarity similarity = new BM25FSimilarity(1.2f, fieldBoosts, fieldBParams);

IndexSearcher searcher = new IndexSearcher(reader);
searcher.setSimilarity(similarity);

// 5. Search
TopDocs results = searcher.search(query, 10);
```

### Using the Convenience Method

```java
// Create parser with field boosts
BM25FQueryParser parser = new BM25FQueryParser(fields, analyzer, fieldBoosts);

// Create matching similarity using the parser's convenience method
Map<String, Float> bParams = new HashMap<>();
bParams.put("title", 0.75f);
bParams.put("body", 0.75f);
bParams.put("keywords", 0.5f);

BM25FSimilarity similarity = parser.createBM25FSimilarity(1.2f, bParams);

// Or use defaults (k1=1.2, b=0.75 for all fields)
BM25FSimilarity defaultSim = parser.createBM25FSimilarity();
```

### Indexing with BM25F

```java
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.FSDirectory;

import java.nio.file.Paths;

// Setup similarity for indexing
Map<String, Float> fieldBoosts = new HashMap<>();
fieldBoosts.put("title", 5.0f);
fieldBoosts.put("body", 1.0f);

Map<String, Float> fieldBParams = new HashMap<>();
fieldBParams.put("title", 0.75f);
fieldBParams.put("body", 0.75f);

BM25FSimilarity similarity = new BM25FSimilarity(1.2f, fieldBoosts, fieldBParams);

// Configure index writer
IndexWriterConfig config = new IndexWriterConfig(new StandardAnalyzer());
config.setSimilarity(similarity);

IndexWriter writer = new IndexWriter(
    FSDirectory.open(Paths.get("/path/to/index")),
    config
);

// Add documents
Document doc = new Document();
doc.add(new TextField("title", "Introduction to Information Retrieval", Field.Store.YES));
doc.add(new TextField("body", "This book provides a comprehensive overview...", Field.Store.YES));
writer.addDocument(doc);

writer.close();
```

## Parameter Tuning Guide

### Field Boosts (Weights)

Field boosts determine the relative importance of different fields. Higher values mean matches in that field contribute more to the final score.

**Recommended values:**
- **Title fields**: 3.0 - 10.0
  - Titles are usually short and highly relevant
  - Use higher values (7-10) for search engines where titles are critical
  - Use moderate values (3-5) for document collections

- **Body/Content fields**: 1.0 (baseline)
  - This is your reference point
  - All other boosts are relative to this

- **Abstract/Summary fields**: 2.0 - 4.0
  - Summaries are more dense than full text
  - Usually more relevant than body but less than title

- **Metadata fields** (author, keywords, tags): 0.5 - 3.0
  - Exact matches in metadata can be very relevant
  - But these fields may be incomplete or noisy
  - Keywords/tags: 2-3x
  - Author: 1-2x
  - Category: 0.5-1x

### Length Normalization (b parameter)

The b parameter controls how much document length affects scoring. Range: [0, 1]

**Recommended values:**
- **b = 0.75** (Standard BM25 default)
  - Good for most natural language text fields
  - Use for: body, abstract, description

- **b = 0.3 - 0.5** (Lower normalization)
  - For fields where length shouldn't penalize much
  - Use for: keywords, tags, product codes
  - Prevents short lists from being overly favored

- **b = 0.8 - 1.0** (Higher normalization)
  - For verbose fields where length indicates dilution
  - Use for: comments, reviews, user-generated content
  - Penalizes lengthy, rambling text

- **b = 0** (No length normalization)
  - For fields where all instances are similar length
  - Use for: fixed-format fields, codes, IDs

### k1 Parameter

The k1 parameter controls term frequency saturation (how quickly additional term occurrences stop mattering). Range: [1.0, 2.0]

**Recommended values:**
- **k1 = 1.2** (Standard BM25 default)
  - Good for most applications
  - Provides moderate saturation

- **k1 = 1.0 - 1.5**
  - Use when term frequency is reliable
  - Appropriate for well-edited documents

- **k1 = 1.5 - 2.0**
  - Use when higher term frequencies should matter more
  - Appropriate for longer documents or when repetition indicates relevance

## Complete Example: Search Engine

```java
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.*;
import org.apache.lucene.queryparser.classic.BM25FQueryParser;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.BM25FSimilarity;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.RAMDirectory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class BM25FSearchExample {
    
    public static void main(String[] args) throws IOException, ParseException {
        // Configuration
        String[] fields = {"title", "abstract", "body", "keywords"};
        
        Map<String, Float> fieldBoosts = new HashMap<>();
        fieldBoosts.put("title", 7.0f);
        fieldBoosts.put("abstract", 3.0f);
        fieldBoosts.put("body", 1.0f);
        fieldBoosts.put("keywords", 4.0f);
        
        Map<String, Float> fieldBParams = new HashMap<>();
        fieldBParams.put("title", 0.75f);
        fieldBParams.put("abstract", 0.75f);
        fieldBParams.put("body", 0.75f);
        fieldBParams.put("keywords", 0.3f);  // Less length normalization
        
        // Create similarity
        BM25FSimilarity similarity = new BM25FSimilarity(1.2f, fieldBoosts, fieldBParams);
        
        // Index some documents
        Directory index = new RAMDirectory();
        StandardAnalyzer analyzer = new StandardAnalyzer();
        IndexWriterConfig config = new IndexWriterConfig(analyzer);
        config.setSimilarity(similarity);
        
        IndexWriter writer = new IndexWriter(index, config);
        
        // Document 1
        Document doc1 = new Document();
        doc1.add(new TextField("title", 
            "BM25F: A New Ranking Function for Information Retrieval", 
            Field.Store.YES));
        doc1.add(new TextField("abstract", 
            "We present BM25F, an extension of BM25 for multi-field documents.", 
            Field.Store.YES));
        doc1.add(new TextField("body", 
            "BM25F combines field statistics before applying the BM25 formula...", 
            Field.Store.YES));
        doc1.add(new TextField("keywords", 
            "BM25 ranking information-retrieval", 
            Field.Store.YES));
        writer.addDocument(doc1);
        
        // Document 2
        Document doc2 = new Document();
        doc2.add(new TextField("title", 
            "Introduction to Information Retrieval", 
            Field.Store.YES));
        doc2.add(new TextField("abstract", 
            "A comprehensive textbook on information retrieval fundamentals.", 
            Field.Store.YES));
        doc2.add(new TextField("body", 
            "Information retrieval is the science of searching for information...", 
            Field.Store.YES));
        doc2.add(new TextField("keywords", 
            "information-retrieval search textbook", 
            Field.Store.YES));
        writer.addDocument(doc2);
        
        // Document 3
        Document doc3 = new Document();
        doc3.add(new TextField("title", 
            "Machine Learning for Search", 
            Field.Store.YES));
        doc3.add(new TextField("abstract", 
            "Applying machine learning techniques to improve search ranking.", 
            Field.Store.YES));
        doc3.add(new TextField("body", 
            "Modern search engines use machine learning to improve ranking...", 
            Field.Store.YES));
        doc3.add(new TextField("keywords", 
            "machine-learning search ranking", 
            Field.Store.YES));
        writer.addDocument(doc3);
        
        writer.close();
        
        // Search
        IndexReader reader = DirectoryReader.open(index);
        IndexSearcher searcher = new IndexSearcher(reader);
        searcher.setSimilarity(similarity);
        
        // Create query parser
        BM25FQueryParser parser = new BM25FQueryParser(fields, analyzer, fieldBoosts);
        
        // Query 1: Should rank doc1 highest (BM25 in title)
        System.out.println("Query: BM25");
        Query query1 = parser.parse("BM25");
        TopDocs results1 = searcher.search(query1, 10);
        printResults(searcher, results1);
        
        // Query 2: Should find multiple documents
        System.out.println("\nQuery: information retrieval");
        Query query2 = parser.parse("information retrieval");
        TopDocs results2 = searcher.search(query2, 10);
        printResults(searcher, results2);
        
        reader.close();
        index.close();
    }
    
    private static void printResults(IndexSearcher searcher, TopDocs results) 
            throws IOException {
        System.out.println("Found " + results.totalHits + " hits:");
        for (ScoreDoc scoreDoc : results.scoreDocs) {
            Document doc = searcher.doc(scoreDoc.doc);
            System.out.println("  Score: " + scoreDoc.score + 
                             " - Title: " + doc.get("title"));
        }
    }
}
```

## Best Practices

### 1. Consistency Between Indexing and Search

Always use the same similarity configuration for both indexing and searching:

```java
// During indexing
BM25FSimilarity similarity = new BM25FSimilarity(1.2f, boosts, bParams);
config.setSimilarity(similarity);

// During searching - use the SAME configuration
searcher.setSimilarity(similarity);
```

### 2. Field Boost Ratios Matter More Than Absolute Values

What matters is the *ratio* between field boosts, not their absolute values:
- `{title: 5.0, body: 1.0}` is equivalent to `{title: 10.0, body: 2.0}`
- Both give title 5x more weight than body

### 3. Test and Tune

Different document collections require different parameters:
1. Start with defaults (k1=1.2, b=0.75, moderate field boosts)
2. Run evaluation queries
3. Adjust field boosts based on where relevant terms appear
4. Adjust b parameters based on field characteristics
5. Iterate

### 4. Consider Field Characteristics

When setting parameters, consider:
- **Field length distribution**: Uniform length → lower b
- **Field importance**: More important → higher boost
- **Field verbosity**: More verbose → higher b
- **Term frequency patterns**: Repetitive → lower k1

### 5. Monitor Performance

BM25F is computationally similar to standard BM25, but:
- Multi-field queries may be slower than single-field
- More fields = more work
- Consider limiting fields for very fast searches

## Troubleshooting

### Problem: Unexpected Rankings

**Solution**: Check field boosts and verify similarity is set consistently:
```java
// Verify your configuration
System.out.println("Similarity: " + searcher.getSimilarity());
System.out.println("Parser fields: " + Arrays.toString(parser.getFields()));
System.out.println("Field boosts: " + parser.getFieldBoosts());
```

### Problem: No Results

**Solution**: Verify fields exist in your index and are analyzed correctly:
```java
// Check indexed fields
IndexReader reader = DirectoryReader.open(directory);
for (LeafReaderContext context : reader.leaves()) {
    LeafReader leafReader = context.reader();
    System.out.println("Fields: " + leafReader.getFieldInfos());
}
```

### Problem: Scores Are Too Similar

**Solution**: Increase field boost differentials or adjust k1:
```java
// More aggressive field boosts
fieldBoosts.put("title", 10.0f);  // Increased from 5.0
fieldBoosts.put("body", 1.0f);

// Or adjust k1 for more differentiation
BM25FSimilarity similarity = new BM25FSimilarity(1.5f, boosts, bParams);
```

## References

1. Robertson, S., Zaragoza, H., and Taylor, M. (2004). "Simple BM25 Extension to Multiple Weighted Fields." In Proceedings of CIKM '04.

2. Robertson, S. and Zaragoza, H. (2009). "The Probabilistic Relevance Framework: BM25 and Beyond." Foundations and Trends in Information Retrieval.

3. Lucene BM25Similarity documentation: https://lucene.apache.org/core/

## API Documentation

For detailed API documentation, see:
- `org.apache.lucene.search.similarities.BM25FSimilarity`
- `org.apache.lucene.queryparser.classic.BM25FQueryParser`

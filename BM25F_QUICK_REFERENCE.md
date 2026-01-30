# BM25F Quick Reference Card

## 5-Minute Quick Start

```java
// 1. Setup
String[] fields = {"title", "body"};
Map<String, Float> boosts = Map.of("title", 5.0f, "body", 1.0f);

// 2. Parse Query
BM25FQueryParser parser = new BM25FQueryParser(fields, analyzer, boosts);
Query query = parser.parse("your search query");

// 3. Create Similarity
BM25FSimilarity similarity = parser.createBM25FSimilarity();

// 4. Search
searcher.setSimilarity(similarity);
TopDocs results = searcher.search(query, 10);
```

## Class Reference

### BM25FSimilarity

**Constructor:**
```java
BM25FSimilarity(float k1, Map<String,Float> fieldBoosts, Map<String,Float> fieldBParams)
```

**Parameters:**
- `k1`: Term frequency saturation (1.0-2.0, default: 1.2)
- `fieldBoosts`: Field weights (higher = more important)
- `fieldBParams`: Length normalization per field (0.0-1.0, default: 0.75)

**Key Methods:**
- `getK1()`: Get k1 parameter
- `getBoostForField(String)`: Get boost for field
- `getBForField(String)`: Get b parameter for field
- `getFieldBoosts()`: Get all field boosts
- `getFieldBParams()`: Get all b parameters

### BM25FQueryParser

**Constructor:**
```java
BM25FQueryParser(String[] fields, Analyzer analyzer, Map<String,Float> fieldBoosts)
```

**Parameters:**
- `fields`: Array of field names to search
- `analyzer`: Analyzer for query text
- `fieldBoosts`: Field weights (must match similarity config)

**Key Methods:**
- `parse(String)`: Parse query string
- `createBM25FSimilarity()`: Create matching similarity with defaults
- `createBM25FSimilarity(float, Map)`: Create with custom k1 and b params
- `getFields()`: Get configured fields
- `getFieldBoosts()`: Get configured boosts

## Parameter Guidelines

### Field Boosts

| Field Type | Recommended Boost | Example |
|------------|------------------|---------|
| Title/Name | 5-10x | `"title": 7.0f` |
| Abstract/Summary | 2-4x | `"abstract": 3.0f` |
| Body/Content | 1x (baseline) | `"body": 1.0f` |
| Keywords/Tags | 3-5x | `"keywords": 4.0f` |
| Metadata | 0.5-2x | `"author": 1.5f` |

### Length Normalization (b parameter)

| Field Type | Recommended b | Rationale |
|------------|--------------|-----------|
| Standard text | 0.75 | Default BM25 value |
| Short fields (tags, keywords) | 0.3-0.5 | Less length penalty |
| Verbose fields (comments) | 0.8-1.0 | More length penalty |
| Fixed-length fields | 0.0 | No normalization |

### k1 Parameter

| Value | Use Case |
|-------|----------|
| 1.0-1.2 | Standard (default: 1.2) |
| 1.3-1.5 | When TF should matter more |
| 1.6-2.0 | Long documents |

## Common Patterns

### Pattern 1: Academic Search
```java
Map<String, Float> boosts = Map.of(
    "title", 7.0f,
    "abstract", 3.0f,
    "body", 1.0f,
    "keywords", 5.0f
);
Map<String, Float> bParams = Map.of(
    "title", 0.75f,
    "abstract", 0.75f,
    "body", 0.75f,
    "keywords", 0.3f
);
```

### Pattern 2: Product Search
```java
Map<String, Float> boosts = Map.of(
    "name", 8.0f,
    "description", 1.0f,
    "category", 2.0f,
    "brand", 3.0f
);
Map<String, Float> bParams = Map.of(
    "name", 0.5f,
    "description", 0.75f,
    "category", 0.3f,
    "brand", 0.3f
);
```

### Pattern 3: Web Search
```java
Map<String, Float> boosts = Map.of(
    "title", 10.0f,
    "content", 1.0f,
    "meta_description", 4.0f,
    "headings", 6.0f
);
Map<String, Float> bParams = Map.of(
    "title", 0.75f,
    "content", 0.8f,
    "meta_description", 0.5f,
    "headings", 0.5f
);
```

## Complete Example

```java
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.queryparser.classic.BM25FQueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.search.similarities.BM25FSimilarity;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.RAMDirectory;

import java.util.Map;

public class BM25FExample {
    public static void main(String[] args) throws Exception {
        // Configuration
        String[] fields = {"title", "body"};
        Map<String, Float> boosts = Map.of("title", 5.0f, "body", 1.0f);
        Map<String, Float> bParams = Map.of("title", 0.75f, "body", 0.75f);
        
        // Index
        Directory dir = new RAMDirectory();
        StandardAnalyzer analyzer = new StandardAnalyzer();
        IndexWriterConfig config = new IndexWriterConfig(analyzer);
        
        BM25FSimilarity similarity = new BM25FSimilarity(1.2f, boosts, bParams);
        config.setSimilarity(similarity);
        
        IndexWriter writer = new IndexWriter(dir, config);
        
        Document doc = new Document();
        doc.add(new TextField("title", "BM25F Tutorial", Field.Store.YES));
        doc.add(new TextField("body", "Learn about BM25F ranking", Field.Store.YES));
        writer.addDocument(doc);
        writer.close();
        
        // Search
        IndexReader reader = DirectoryReader.open(dir);
        IndexSearcher searcher = new IndexSearcher(reader);
        searcher.setSimilarity(similarity);
        
        BM25FQueryParser parser = new BM25FQueryParser(fields, analyzer, boosts);
        Query query = parser.parse("BM25F");
        
        TopDocs results = searcher.search(query, 10);
        System.out.println("Found " + results.totalHits + " documents");
        
        for (ScoreDoc scoreDoc : results.scoreDocs) {
            Document resultDoc = searcher.doc(scoreDoc.doc);
            System.out.println("Score: " + scoreDoc.score + 
                             " Title: " + resultDoc.get("title"));
        }
        
        reader.close();
        dir.close();
    }
}
```

## Troubleshooting

### Issue: No Results
**Check:**
- Fields exist in index: `reader.getFieldInfos()`
- Query parses correctly: `System.out.println(query)`
- Analyzer matches indexing analyzer

### Issue: Wrong Rankings
**Check:**
- Similarity set on both IndexWriter and IndexSearcher
- Field boosts match between parser and similarity
- Verify boosts: `parser.getFieldBoosts()`

### Issue: Scores Too Similar
**Solutions:**
- Increase field boost differences
- Adjust k1 parameter (try 1.5)
- Check if terms appear in multiple fields

## API Imports

```java
// Similarity
import org.apache.lucene.search.similarities.BM25FSimilarity;

// Query Parser
import org.apache.lucene.queryparser.classic.BM25FQueryParser;
import org.apache.lucene.queryparser.classic.ParseException;

// Standard Lucene
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TopDocs;
```

## Testing Commands

```bash
# Test similarity
./gradlew :lucene:core:test --tests TestBM25FSimilarity

# Test query parser
./gradlew :lucene:queryparser:test --tests TestBM25FQueryParser

# All tests
./gradlew test
```

## Documentation Files

| File | Location | Purpose |
|------|----------|---------|
| User Guide | `lucene/queryparser/docs/BM25F_USER_GUIDE.md` | Comprehensive usage guide |
| README | `lucene/BM25F_README.md` | Overview and quick start |
| Implementation Notes | `lucene/BM25F_IMPLEMENTATION_NOTES.md` | Technical details |
| Summary | `lucene/BM25F_SUMMARY.md` | Implementation summary |

## Key Takeaways

1. **Use field boosts** to prioritize important fields (e.g., title)
2. **Match configuration** between indexing and searching
3. **Start with defaults** (k1=1.2, b=0.75) then tune
4. **Test with your data** - optimal parameters vary by corpus
5. **Boost ratios matter** more than absolute values

## Formula Reference

**BM25F Score:**
```
score = IDF × (wtf × (k1 + 1)) / (wtf + k1 × (1 - b + b × wdl / avgwdl))

where:
  wtf = Σ(w_f × tf_f)    [weighted term frequency]
  wdl = Σ(w_f × dl_f)    [weighted document length]
  IDF = log(1 + (N - n + 0.5) / (n + 0.5))
```

## Support

- **Javadoc**: Full API documentation in source code
- **Tests**: 28 unit tests with usage examples
- **User Guide**: 900 lines of detailed documentation
- **Examples**: Multiple real-world use cases

---

**Quick Links:**
- Source: `lucene/core/src/java/.../BM25FSimilarity.java`
- Tests: `lucene/core/src/test/.../TestBM25FSimilarity.java`
- Parser: `lucene/queryparser/src/java/.../BM25FQueryParser.java`
- Docs: `lucene/queryparser/docs/BM25F_USER_GUIDE.md`

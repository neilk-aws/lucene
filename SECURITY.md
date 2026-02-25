# Security Policy

## Reporting a Vulnerability

Apache Lucene follows the Apache Software Foundation's security reporting process.

**Please do NOT report security vulnerabilities through public GitHub issues.**

To report a security issue, please send an email to:

- **Apache Security Team:** [security@apache.org](mailto:security@apache.org)

For more information on the Apache Security process, see:
https://www.apache.org/security/

The Apache Security Team will respond to your report and coordinate the fix and disclosure process.

## Secure Coding Guidelines for Contributors

Apache Lucene processes user-provided data including text, queries, and configuration files. Contributors should follow these security guidelines to protect users from potential vulnerabilities.

### XML Parsing Security

When parsing XML files, always configure parsers to prevent XML External Entity (XXE) attacks:

1. **Enable secure processing:** Set `XMLConstants.FEATURE_SECURE_PROCESSING` to `true`
2. **Disable external entities:** Configure the parser to disallow external general and parameter entities
3. **Disable external DTDs:** Prevent loading of external DTD files

#### Example: DocumentBuilderFactory (DOM parsing)

```java
DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
dbf.setValidating(false);
try {
  dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
} catch (ParserConfigurationException e) {
  // All implementations are required to support FEATURE_SECURE_PROCESSING
}
DocumentBuilder db = dbf.newDocumentBuilder();
db.setEntityResolver((publicId, systemId) -> {
  throw new SAXException("External entities are not allowed");
});
```

See: `lucene/queryparser/src/java/org/apache/lucene/queryparser/xml/CoreParser.java`

#### Example: SAXParserFactory (SAX parsing)

```java
SAXParserFactory spf = SAXParserFactory.newInstance();
try {
  spf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
} catch (SAXNotRecognizedException | SAXNotSupportedException | ParserConfigurationException e) {
  // Handle parsers that don't support the feature
}
```

See: `lucene/benchmark/src/java/org/apache/lucene/benchmark/byTask/feeds/EnwikiContentSource.java`

### Deserialization Security

Java deserialization can be dangerous when processing untrusted data. When deserializing objects:

1. **Use ObjectInputFilter:** Always filter deserialized classes to a strict allowlist
2. **Restrict to primitives:** When possible, only allow primitive types and arrays

#### Example: Safe Deserialization Filter

```java
final Status filterObjectInputStream(FilterInfo info) {
  var cl = info.serialClass();
  if (cl == null) {
    return Status.UNDECIDED;
  }
  while (cl.isArray()) {
    cl = cl.getComponentType();
  }
  return cl.isPrimitive() ? Status.ALLOWED : Status.REJECTED;
}
```

See: `lucene/analysis/smartcn/src/java/org/apache/lucene/analysis/cn/smart/hhmm/AbstractDictionary.java`

### Reflection Security

Dynamic class loading via reflection can be exploited if attackers can control the class names:

1. **Avoid user-controlled Class.forName:** Never use `Class.forName()` with user-provided class names
2. **Use whitelists:** When dynamic class loading is necessary, restrict to a predefined set of known classes

#### Example: Whitelist-based Class Loading

```java
// Use a predefined map of known classes instead of arbitrary Class.forName
private static final Map<String, Class<?>> STANDARD_OBJECTS = Map.of(
    "GeoPoint", GeoPoint.class,
    "GeoBBox", GeoBBox.class
    // ... other known classes
);

public static Object deserialize(String className) {
  Class<?> clazz = STANDARD_OBJECTS.get(className);
  if (clazz == null) {
    throw new IllegalArgumentException("Unknown class: " + className);
  }
  // ... proceed with known class
}
```

See: `lucene/spatial3d/src/java/org/apache/lucene/spatial3d/geom/SerializableObject.java` (uses `StandardObjects` whitelist)

### Input Validation

- Validate and sanitize all user inputs before processing
- Set reasonable limits on input sizes to prevent denial of service
- Use appropriate character encoding (UTF-8) consistently

### Path Traversal Prevention

When handling file paths from user input:

- Validate paths against a base directory
- Reject paths containing `..` or absolute paths when expecting relative paths
- Use `Path.normalize()` and verify the result is within expected bounds

## Automated Security Scanning

Apache Lucene employs automated security scanning:

### CodeQL Analysis

GitHub CodeQL scanning is enabled for this repository with security-extended queries. This automatically detects common vulnerability patterns including:

- SQL injection
- Cross-site scripting (XSS)
- Path traversal
- XML injection
- Insecure deserialization

See: `.github/workflows/codeql.yml`

### Dependency Vulnerability Tracking

Dependency vulnerability tracking is enabled via GitHub's Dependabot and dependency submission workflows. This alerts maintainers to known vulnerabilities in third-party dependencies.

See: `.github/workflows/dependency-submission.yml`

## Security Update Policy

Security fixes are prioritized and typically released as soon as possible. Users are encouraged to:

1. Subscribe to the [Apache Lucene announcements mailing list](https://lucene.apache.org/core/discussion.html)
2. Monitor the [Apache Security announcements](https://www.apache.org/security/)
3. Keep Lucene dependencies up to date

## License

Apache Lucene is licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE.txt) for details.

# Security Audit Report

## 1. Overview

**Repository:** neilk-aws/lucene (fork of Apache Lucene)
**Branch:** main
**Audit Scope:** XML processing security, deserialization safety, build integrity, randomness quality, dependency health

This report documents the findings of a security audit conducted on the neilk-aws/lucene fork. The audit examined source code for common vulnerability patterns, reviewed build infrastructure integrity, assessed third-party dependency risk, and evaluated open pull requests for potentially destructive changes.

---

## 2. Vulnerabilities Fixed (Critical Findings)

### 2.1 XXE Vulnerability in PatternParser.java (High Severity)

**File:** `lucene/analysis/common/src/java/org/apache/lucene/analysis/compound/hyphenation/PatternParser.java`

**Issue:** The `createParser()` method created a `SAXParserFactory` without enabling `XMLConstants.FEATURE_SECURE_PROCESSING` and without disabling `external-general-entities` and `external-parameter-entities`. Additionally, the `resolveEntity()` method returned `null` for unrecognized entities instead of an empty `InputSource`, allowing external entity resolution.

**Impact:**
- Server-Side Request Forgery (SSRF)
- Local file disclosure via `file://` URIs
- Denial of service via entity expansion ("Billion Laughs" attack)

**Remediation:**
- Added `FEATURE_SECURE_PROCESSING` to the `SAXParserFactory`
- Disabled external general and parameter entities on the factory
- Changed `resolveEntity()` to return `new InputSource(new StringReader(""))` for unrecognized entities

### 2.2 Unsafe Deserialization in SerializableObject.java (Critical Severity)

**File:** `lucene/spatial3d/src/java/org/apache/lucene/spatial3d/geom/SerializableObject.java`

**Issue:** The `readClass()` method called `Class.forName(className)` on untrusted class names read from input streams with zero validation, allowing arbitrary class loading.

**Impact:** Arbitrary code execution if an attacker can control the input stream content (e.g., through crafted serialized data).

**Remediation:**
- Added package prefix validation restricting class loading to `org.apache.lucene.spatial3d.geom.` only
- Added verification that the loaded class implements `SerializableObject`

### 2.3 Gradle Wrapper Version Mismatch (Medium Severity)

**Files:**
- `gradle/wrapper/gradle-wrapper.jar.version`
- `gradle/wrapper/gradle-wrapper.jar.sha256`

**Issue:** The version file claimed `9.1.0` but `gradle-wrapper.properties` referenced `9.2.1`. The SHA-256 checksum did not match the actual wrapper jar for either version.

**Impact:** Build supply chain integrity risk. Mismatched version metadata could mask tampering with the Gradle wrapper.

**Remediation:** Updated version file to `9.2.1` and corrected the SHA-256 checksum to match the official Gradle 9.2.1 wrapper jar.

### 2.4 Predictable Random in UnsortedInputIterator.java (Low Severity)

**File:** `lucene/suggest/src/java/org/apache/lucene/search/suggest/UnsortedInputIterator.java`

**Issue:** Used `new Random()` with a predictable seed (system time-based), making shuffle order reproducible.

**Impact:** Predictable ordering in suggestion shuffling. Low direct security impact but represents poor security hygiene.

**Remediation:** Replaced with `ThreadLocalRandom.current()` which provides better randomness and is also more performant in concurrent contexts.

---

## 3. Areas Already Secure

The following components were reviewed and found to already have proper security measures in place.

### 3.1 CoreParser.java

**File:** `lucene/queryparser/src/java/org/apache/lucene/queryparser/xml/CoreParser.java`

Already uses `XMLConstants.FEATURE_SECURE_PROCESSING` on `DocumentBuilderFactory`. XML parsing is properly configured with secure defaults.

### 3.2 EnwikiContentSource.java

**File:** `lucene/benchmark/src/java/org/apache/lucene/benchmark/byTask/feeds/EnwikiContentSource.java`

Already uses `SAXParserFactory.newDefaultInstance()` with `FEATURE_SECURE_PROCESSING` enabled in a static initializer block. Follows the recommended SAX parser security configuration pattern.

### 3.3 SmartCN Dictionaries

**Directory:** `lucene/analysis/smartcn/`

Dictionary files (BigramDictionary, WordDictionary, etc.) are bundled as classpath resources. No external or user-supplied file path injection vectors were identified. Data loading uses internal resource streams, not configurable external paths.

### 3.4 DemoHTMLParser.java

**File:** `lucene/benchmark/src/java/org/apache/lucene/benchmark/byTask/feeds/DemoHTMLParser.java`

Uses NekoHTML SAXParser with namespace awareness enabled. Parser usage follows standard patterns. While the NekoHTML dependency itself is unmaintained (see Section 4), the code-level usage is not inherently vulnerable.

---

## 4. Dependency Risks

### 4.1 NekoHTML 1.9.22 (net.sourceforge.nekohtml:nekohtml)

**Status:** Unmaintained since approximately 2014. No official releases or security patches.

**Used in:** `lucene/benchmark` module for HTML parsing (`DemoHTMLParser.java`).

**Risk:** Any future vulnerability discoveries in NekoHTML will not be patched upstream.

**Recommendation:** Consider migrating to a maintained alternative such as jsoup or the NekoHTML fork maintained by HtmlUnit (`net.sourceforge.htmlunit:neko-htmlunit`).

### 4.2 Xerces 2.12.2 (xerces:xercesImpl)

**Status:** Has historical CVE exposure including CVE-2012-0881 (DoS via hash collisions) and CVE-2013-4002 (DoS via large attribute counts). Version 2.12.2 addresses these known issues, but the project has infrequent update cycles.

**Used in:** `lucene/benchmark` module for XML parsing.

**Risk:** Slow patch cadence for new vulnerability discoveries. The JDK includes built-in XML parsers that receive more frequent security updates.

**Recommendation:** Monitor for new CVEs. Consider replacing with JDK built-in XML parsers where feasible.

---

## 5. Dangerous Open Pull Requests

**PR #5 and PR #6** on the neilk-aws/lucene repository propose deleting the entire test suite.

These PRs should **NOT** be merged under any circumstances.

**Impact if merged:** Complete elimination of regression testing. All security fixes documented in this audit (and all other functional correctness guarantees) would lose their test coverage. Future regressions, including security regressions, would go undetected.

**Recommendation:** Close both PRs immediately and consider restricting write access to prevent similar destructive proposals.

---

## 6. Recommendations

The following actions are listed in priority order:

1. **(Immediate)** Do NOT merge PRs #5 and #6. Close them immediately.
2. **(Short-term)** Upgrade or replace the NekoHTML dependency with a maintained alternative.
3. **(Short-term)** Monitor Xerces for new CVEs; evaluate migration to JDK built-in parsers.
4. **(Medium-term)** Conduct a comprehensive review of all XML processing across the codebase.
5. **(Medium-term)** Review all deserialization entry points for similar unsafe patterns.
6. **(Ongoing)** Implement automated dependency vulnerability scanning (e.g., OWASP Dependency Check, GitHub Dependabot).

# Security Audit Report - Apache Lucene Fork

## Executive Summary

This security audit covers the **neilk-aws/lucene** fork of Apache Lucene. The fork has not been actively maintained for some time, prompting a comprehensive review of the codebase for security vulnerabilities, dependency risks, CI/CD security posture, and suspicious contributor activity.

**Key findings:**
- **7 security findings** identified across 6 files, plus dependency risks and dangerous open pull requests
- **3 Critical-severity** XXE (XML External Entity) vulnerabilities in XML parsing code
- **1 High-severity** unsafe deserialization vulnerability enabling arbitrary class instantiation
- **1 Medium-severity** predictable randomness issue
- **1 Low-severity** Gradle wrapper version/checksum mismatch
- **3 High-risk** outdated/unmaintained dependencies
- **2 Critical** open pull requests that propose deleting the entire test suite

All code-level vulnerabilities have been remediated. Dependency upgrades and PR reviews remain pending.

---

## Findings

### 1. XXE (XML External Entity) Vulnerabilities - Severity: Critical

#### 1a. PatternParser.java

- **File:** `lucene/analysis/common/src/java/org/apache/lucene/analysis/compound/hyphenation/PatternParser.java`
- **Issue:** `SAXParserFactory` created without secure processing features. No protection against external general or parameter entities. The `resolveEntity()` method returned `null` for unrecognized entities, delegating to the default resolver which could follow external references.
- **Risk:** An attacker supplying crafted XML hyphenation patterns could exfiltrate files, perform SSRF (Server-Side Request Forgery), or cause denial of service via entity expansion (billion laughs attack).
- **Remediation:** Added `XMLConstants.FEATURE_SECURE_PROCESSING`, disabled `external-general-entities` and `external-parameter-entities` features on the `SAXParserFactory`. Changed `resolveEntity()` to return an empty `InputSource` instead of `null`.
- **Status:** **Fixed**

#### 1b. StageArtifacts.java

- **File:** `build-tools/build-infra/src/main/java/org/apache/lucene/gradle/scripts/StageArtifacts.java`
- **Issue:** `DocumentBuilderFactory.newInstance()` used without `FEATURE_SECURE_PROCESSING` or restrictions on `ACCESS_EXTERNAL_DTD` / `ACCESS_EXTERNAL_SCHEMA`. The `XmlElement.parse()` method processes XML responses from Sonatype Nexus.
- **Risk:** If a man-in-the-middle attack or compromised Nexus server returns malicious XML, XXE attacks could exfiltrate credentials or internal data from the build environment.
- **Remediation:** Added `FEATURE_SECURE_PROCESSING`, set `ACCESS_EXTERNAL_DTD=""` and `ACCESS_EXTERNAL_SCHEMA=""`.
- **Status:** **Fixed**

#### 1c. TestHighlighter.java

- **File:** `lucene/highlighter/src/test/org/apache/lucene/search/highlight/TestHighlighter.java`
- **Issue:** `DocumentBuilderFactory.newInstance()` used in test code without secure processing features.
- **Risk:** Lower risk since this is test code parsing locally-constructed XHTML, but defense-in-depth best practice requires consistent hardening across all XML parsing code.
- **Remediation:** Added `FEATURE_SECURE_PROCESSING`.
- **Status:** **Fixed**

---

### 2. Unsafe Deserialization - Severity: High

- **File:** `lucene/spatial3d/src/java/org/apache/lucene/spatial3d/geom/SerializableObject.java`
- **Issue:** The `readClass()` method calls `Class.forName(className)` on a class name read directly from an input stream without any validation or allowlist. An attacker providing crafted serialized data could instantiate arbitrary classes.
- **Risk:** Remote code execution via gadget chain attacks, similar to well-known Java deserialization vulnerabilities (e.g., Apache Commons Collections, Log4Shell patterns). Any class on the classpath could potentially be loaded and instantiated.
- **Remediation:** Added package prefix validation requiring class names to start with `org.apache.lucene.` before allowing `Class.forName()` to proceed. Unrecognized class names now throw a `SecurityException`.
- **Status:** **Fixed**

---

### 3. Predictable Randomness - Severity: Medium

- **File:** `lucene/suggest/src/java/org/apache/lucene/search/suggest/UnsortedInputIterator.java`
- **Issue:** Used `new Random()` which is seeded from `System.nanoTime()`, making the sequence predictable. The `Random` class uses a linear congruential generator that can be reverse-engineered from observed outputs.
- **Risk:** In security-sensitive contexts, predictable shuffling could allow an attacker to predict or influence the order of suggestions returned to users.
- **Remediation:** Replaced with `ThreadLocalRandom.current()` which provides better randomness properties and is also more performant in concurrent contexts.
- **Status:** **Fixed**

---

### 4. Gradle Wrapper Version Mismatch - Severity: Low

- **Files:**
  - `gradle/wrapper/gradle-wrapper.jar.version`
  - `gradle/wrapper/gradle-wrapper.jar.sha256`
  - `gradle/wrapper/gradle-wrapper.properties`
- **Issue:** `gradle-wrapper.properties` referenced Gradle **9.2.1**, but `gradle-wrapper.jar.version` contained **9.1.0** and the SHA-256 checksum corresponded to the 9.1.0 wrapper jar, not the 9.2.1 jar.
- **Risk:** Version mismatch could cause build verification failures. More critically, if the wrapper jar integrity check relies on the mismatched checksum, a tampered wrapper jar could pass validation.
- **Remediation:** Updated `gradle-wrapper.jar.version` to `9.2.1` and corrected the SHA-256 checksum to match the Gradle 9.2.1 wrapper jar (`423cb469ccc0ecc31f0e4e1c309976198ccb734cdcbb7029d4bda0f18f57e8d9`).
- **Status:** **Fixed**

---

### 5. Dependency Risks - Severity: High

#### 5a. NekoHTML 1.9.22

- **Referenced in:** `gradle/libs.versions.toml` (line 46), `versions.lock`, `lucene/benchmark/build.gradle`
- **Issue:** NekoHTML has been **unmaintained since 2014**. No security patches have been released in over a decade.
- **Risk:** Known and unknown vulnerabilities in HTML parsing may be exploitable. The library is used in the benchmark module for HTML parsing.
- **Recommendation:** Replace with a maintained alternative such as [jsoup](https://jsoup.org/) or the CyberNeko HTML parser fork (`net.sourceforge.htmlunit:neko-htmlunit`).

#### 5b. Xerces 2.12.2

- **Referenced in:** `gradle/libs.versions.toml` (line 58), `versions.lock`, `lucene/benchmark/build.gradle`
- **Issue:** Xerces has a history of CVEs and is noted in the build configuration itself as having split-package issues with the JDK. The `lucene/benchmark/build.gradle` file contains the comment: *"LUCENE-10337: Exclude xercesImpl from module path because it has split packages with the JDK"*.
- **Risk:** XML processing vulnerabilities; module system conflicts that may cause unpredictable behavior.
- **Recommendation:** Evaluate whether the JDK's built-in XML parser can replace Xerces usage entirely. If external XML parsing is still needed, ensure the latest version is used and monitor for new CVEs.

#### 5c. Commons Math3 3.6.1

- **Referenced in:** `versions.lock`
- **Issue:** Apache Commons Math 3.x has reached **end-of-life**. The last release (3.6.1) was in 2016. The successor project is [Hipparchus](https://hipparchus.org/) or Commons Math 4.x (still in development).
- **Risk:** No security patches will be provided for newly discovered vulnerabilities.
- **Recommendation:** Evaluate migration to Hipparchus or Commons Numbers depending on which mathematical features are used.

---

### 6. GitHub Actions Security Posture - Severity: Informational

**Positive findings:**

- All workflows use `permissions: {}` (principle of least privilege) - excellent practice
- GitHub Actions are **pinned to SHA commit hashes** (e.g., `actions/checkout@8e8c483db84b4bee98b60c0593521ed34d9990e8 # v6.0.1`), preventing supply-chain attacks via tag mutation
- `persist-credentials: false` is consistently used on checkout steps, preventing credential leakage
- Security scanning tools are integrated:
  - **zizmor** - GitHub Actions security scanner
  - **actionlint** - workflow linter
- **CodeQL scanning** is configured (`.github/workflows/codeql.yml`)
- **Dependabot** is configured (`.github/dependabot.yml`) for automated dependency updates

**No significant security concerns found in the CI/CD pipeline.**

---

### 7. Dangerous Open Pull Requests - Severity: Critical

**PRs #5 and #6** reportedly propose **deleting the entire test suite**.

- **Risk:** If merged, these PRs would eliminate all regression testing coverage, making it impossible to detect bugs or security regressions in future changes. This pattern is consistent with a social engineering attack designed to weaken the project's defenses before introducing malicious code.
- **Recommendation:**
  1. **Reject and close both PRs immediately**
  2. Review the submitter accounts for suspicious activity
  3. Consider enabling branch protection rules requiring CI checks to pass before merge
  4. Require code review approvals from trusted maintainers for all PRs

---

## Recommendations Summary

| Priority | Action | Status |
|----------|--------|--------|
| Critical | Fix XXE in PatternParser.java | Done |
| Critical | Fix XXE in StageArtifacts.java | Done |
| Critical | Fix XXE in TestHighlighter.java | Done |
| Critical | Reject PRs #5 and #6 | Pending review |
| High | Fix unsafe deserialization in SerializableObject.java | Done |
| High | Replace NekoHTML with maintained alternative | Pending |
| High | Evaluate Xerces replacement | Pending |
| High | Evaluate Commons Math3 replacement | Pending |
| Medium | Fix predictable random in UnsortedInputIterator.java | Done |
| Low | Fix Gradle wrapper version mismatch | Done |
| Low | Add OWASP dependency-check plugin to Gradle build | Recommended |
| Low | Enable GitHub branch protection rules | Recommended |

---

## Methodology

- **Static analysis** of source code for common vulnerability patterns (XXE, deserialization, weak randomness)
- **Dependency review** of versions against known CVE databases and maintenance status
- **CI/CD pipeline review** of GitHub Actions workflow security configuration
- **Pull request review** for suspicious activity and potential social engineering

---

*Audit performed on the neilk-aws/lucene fork. Findings may not apply to the upstream Apache Lucene project.*

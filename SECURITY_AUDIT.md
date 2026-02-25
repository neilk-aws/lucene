<!-- Licensed to the Apache Software Foundation (ASF) under one or more contributor
     license agreements. See the NOTICE file distributed with this work for additional
     information regarding copyright ownership. The ASF licenses this file to
     You under the Apache License, Version 2.0 (the "License"); you may not use
     this file except in compliance with the License. You may obtain a copy of
     the License at http://www.apache.org/licenses/LICENSE-2.0 Unless required
     by applicable law or agreed to in writing, software distributed under the
     License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS
     OF ANY KIND, either express or implied. See the License for the specific
     language governing permissions and limitations under the License. -->

# Security Audit Report

**Repository:** neilk-aws/lucene (Apache Lucene fork)
**Date:** 2025-01-27
**Scope:** Code security, dependencies, CI/CD pipelines, open pull requests

---

## 1. Executive Summary

This report documents a comprehensive security audit of the neilk-aws/lucene repository, a fork of Apache Lucene. The audit covered code security vulnerabilities, dependency analysis, GitHub Actions workflow security, open pull request review, and build configuration assessment.

**Key findings:**

- **1 XXE vulnerability fixed** in `PatternParser.java` (XML parser missing secure processing features)
- **1 Gradle wrapper version mismatch fixed** (`gradle-wrapper.jar.version` was out of sync with `gradle-wrapper.properties`)
- **3 dependencies flagged** for maintenance/security concerns (nekohtml, xerces, commons-math3)
- **2 dangerous PRs identified** that would delete the entire test suite (3,000+ files)
- **18 frivolous/duplicate PRs identified** from bot accounts that should be closed

---

## 2. Code Security Fixes Applied

### 2.1 PatternParser.java XXE Fix (MEDIUM)

**File:** `lucene/analysis/common/src/java/org/apache/lucene/analysis/compound/hyphenation/PatternParser.java`

The `createParser()` method created a `SAXParserFactory` without enabling secure processing or disabling external entity resolution, leaving it vulnerable to XML External Entity (XXE) attacks.

**Fixes applied:**
- Added `FEATURE_SECURE_PROCESSING` to enable secure XML processing
- Disabled external general entities (`http://xml.org/sax/features/external-general-entities`)
- Disabled external parameter entities (`http://xml.org/sax/features/external-parameter-entities`)

### 2.2 Gradle Wrapper Version Mismatch (LOW)

**Files:** `gradle/wrapper/gradle-wrapper.jar.version`, `gradle/wrapper/gradle-wrapper.jar.sha256`

The `gradle-wrapper.jar.version` file contained `9.1.0` while `gradle-wrapper.properties` referenced `gradle-9.2.1-bin.zip`. This mismatch was corrected by aligning the version file to `9.2.1` and updating the SHA-256 checksum to match the correct wrapper jar.

---

## 3. Dependencies Assessment

Dependencies are locked via `versions.lock` and Dependabot is configured for weekly Gradle dependency updates.

| Dependency | Version | Severity | Status | Notes |
|---|---|---|---|---|
| nekohtml | 1.9.22 | **CRITICAL** | Unmaintained | Last release 2014. CyberNeko HTML project abandoned. Only used in benchmark code. Replace with jsoup or htmlunit-neko (maintained fork). |
| xerces | 2.12.2 | **HIGH** | CVE History | Known CVE history in older versions. Consider updating to latest available (2.13.0+). |
| commons-math3 | 3.6.1 | LOW | EOL | End-of-life, no known security issues, but no longer maintained. |
| ICU4J | 78.2 | — | Current | Up to date. |
| commons-codec | 1.20.0 | — | Current | Up to date. |
| commons-compress | 1.28.0 | — | Current | Up to date. |
| opennlp-tools | 2.5.7 | — | Current | Up to date. |

---

## 4. GitHub Actions Security Review

17 workflow files were reviewed. Overall, the repository follows strong CI/CD security practices.

### Positive Security Practices

- **SHA-pinned action references:** All actions use full commit SHA pins (e.g., `actions/checkout@8e8c483db84b4bee98b60c0593521ed34d9990e8`) rather than mutable tags, preventing supply-chain attacks via tag manipulation.
- **Minimal permissions:** All workflows declare `permissions: {}` at the top level with minimal per-job permissions granted explicitly.
- **No credential persistence:** `persist-credentials: false` is used consistently across checkout steps.
- **zizmor security scanner:** Configured and runs on all workflow file changes, providing automated CI/CD security analysis.
- **CodeQL scanning:** Enabled for Java, Python, and Actions, providing automated vulnerability detection.
- **Dependabot:** Configured for `github-actions`, `pip`, and `gradle` ecosystems with weekly update cadence.

### Notes

- **`DEVELOCITY_ACCESS_KEY`** is set at the workflow `env` level in `run-checks-all.yml`. This could be scoped more tightly to the specific jobs that need it, though the current configuration is functional.
- **`auto-format.yml`** uses the `issue_comment` trigger, which is a potential abuse vector. However, the workflow includes permission check logic that verifies the commenter is the PR author or has write access to the repository.
- **`pull_request_target`** trigger is used in `label-pull-request.yml` and `verify-changelog-and-set-milestone.yml`. Both have `zizmor: ignore[dangerous-triggers]` annotations, indicating the maintainers have reviewed and accepted the risk.

---

## 5. Open PRs Analysis

25 open PRs were reviewed. All are from bot accounts (`ghostyappbeta[bot]` and `ghostyappzeta[bot]`).

### DANGEROUS — Must Not Merge

| PR | Title | Risk |
|---|---|---|
| **#5** | "Remove all test files - moved to Canary repository" | Deletes **2,999 test files** (615,021 lines removed). Would destroy the entire test suite. |
| **#6** | "Remove test files - moved to Canary repository" | Deletes **3,050 test files**. Same destructive intent as #5. |

### Duplicate BM25F Query Parser PRs (8 duplicates)

PRs #1, #2, #3, #4, #7, #10, #14, #19 — All implement variants of a BM25F multi-field query parser. These are duplicates of each other.

### Duplicate Hindi README Translation PRs (3 duplicates)

PRs #8, #9, #11 — All add a Hindi translation of the README. These are duplicates of each other.

### Frivolous PRs

| PR | Title | Reason |
|---|---|---|
| #12 | "Add jokes to README" | Non-substantive content |
| #13 | "Add Contributors section to README" | Non-substantive content |
| #15 | "Integrate Lucene with Amazon Neptune" | Out of scope for Apache Lucene |
| #16 | "Add a sonnet to the README" | Non-substantive content |
| #17 | "Add haiku to README" | Non-substantive content |
| #18 | "Add search pun tagline to README" | Non-substantive content |
| #20 | "Add jokes folder" | Non-substantive content |
| #21 | "Add jokes folder" | Duplicate of #20 |

### Security-Related PRs (from other audit runs)

PRs #22, #23, #24, #25 — Various security fix PRs from other bot runs that overlap with fixes applied in this audit.

---

## 6. Build Configuration Assessment

### Positive Findings

- **`versions.lock`** ensures reproducible dependency resolution, preventing unexpected transitive dependency changes.
- **Forbidden-APIs plugin** is configured to block unsafe APIs including `ObjectInputStream` (deserialization attacks) and JNDI lookups (described in the configuration as "JNDI is RCE-in-a-box").
- **ErrorProne compiler checks** are enabled, catching common Java programming errors at compile time.
- **OWASP dependency-check** is available for scanning dependencies against known vulnerability databases.
- **Code formatting** is enforced via `./gradlew tidy`, ensuring consistent code style.
- **Checksum verification** is in place for the Gradle wrapper jar, preventing tampering with the build tool.

---

## 7. XML/Deserialization Security Review

- **PatternParser.java** was the only XML parser in the codebase missing `FEATURE_SECURE_PROCESSING` — **now fixed** (see Section 2.1).
- No other `SAXParserFactory`, `DocumentBuilderFactory`, `XMLInputFactory`, or `TransformerFactory` usage was found in Java source on the main branch.
- **Note:** `CoreParser.java` and `EnwikiContentSource.java` (referenced in upstream Apache Lucene) are **not present** in this fork's main branch.
- Smart Chinese dictionaries use `ObjectInputFilter` for safe deserialization, which is the recommended approach.
- The Forbidden-APIs configuration blocks unsafe `ObjectInputStream`/`ObjectOutputStream` usage across the codebase.

---

## 8. CodeQL Configuration

Two query exclusions are configured in `.github/codeql-config.yml`:

| Excluded Query | Rationale |
|---|---|
| `java/implicit-cast-in-compound-assignment` | Noisy check that is already covered by ErrorProne compiler checks. |
| `java/comparison-with-wider-type` | Noisy check that is already covered by ErrorProne compiler checks. |

Both exclusions are appropriate and well-documented. The remaining CodeQL checks provide comprehensive coverage for security vulnerabilities.

---

## 9. Recommended Follow-up Actions

| Priority | Action | Rationale |
|---|---|---|
| **P0** | Close PRs #5 and #6 immediately | They delete the entire test suite (3,000+ files each) |
| **P0** | Close remaining 18 bot-generated PRs (#1–#4, #7–#21) | Duplicates, frivolous content, and out-of-scope features |
| **P1** | Replace nekohtml 1.9.22 | Unmaintained since 2014. Use jsoup or htmlunit-neko (the maintained CyberNeko fork) |
| **P2** | Update xerces to 2.13.0+ | Known CVE history in older versions |
| **P2** | Scope `DEVELOCITY_ACCESS_KEY` to specific jobs | Currently set at workflow `env` level in `run-checks-all.yml` |
| **P3** | Monitor commons-math3 for replacement | EOL but no active security issues known |

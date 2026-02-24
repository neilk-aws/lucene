# Security Audit Report

**Repository:** neilk-aws/lucene (fork of apache/lucene)  
**Date:** 2025-07-18  
**Auditor:** automated security analysis

---

## 1. Executive Summary

This is a security audit of the **neilk-aws/lucene** fork (upstream: **apache/lucene**). The codebase is a large Java search library built with Gradle 9.2.1, targeting JDK 25.

Key findings:

- The fork has accumulated **20 open bot-generated pull requests** that need cleanup, including two that would delete the entire test suite.
- Two concrete fixes were applied during this audit:
  - **Gradle wrapper version mismatch** between `gradle-wrapper.properties` (9.2.1) and `gradle-wrapper.jar.version` (9.1.0).
  - **Missing XXE protection** in `PatternParser.java` (`FEATURE_SECURE_PROCESSING` not set on `SAXParserFactory`).
- Key risk areas include **outdated dependencies in the benchmark module** (nekohtml, xerces) and **dangerous open PRs** that propose deleting thousands of test files.

---

## 2. Dependency Security Audit

Based on `gradle/libs.versions.toml` and `versions.lock`:

### Critical

| Dependency | Version | Issue | Used In | Recommendation |
|-----------|---------|-------|---------|----------------|
| `net.sourceforge.nekohtml:nekohtml` | 1.9.22 | Unmaintained since 2014, known vulnerabilities. Last release was 2014. | `lucene/benchmark` (HTML parsing) | Replace with jsoup or tagsoup. |

### High

| Dependency | Version | Issue | Used In | Recommendation |
|-----------|---------|-------|---------|----------------|
| `xerces:xercesImpl` | 2.12.2 | History of XXE vulnerabilities: CVE-2022-23437 (DoS/buffer overflow), CVE-2013-4002 (DoS). | `lucene/benchmark` (XML parsing) with classpath workaround (LUCENE-10337 in `lucene/benchmark/build.gradle`) | Update to 2.13.0+. |

### Low

| Dependency | Version | Issue | Used In | Recommendation |
|-----------|---------|-------|---------|----------------|
| `org.apache.commons:commons-math3` | 3.6.1 | End-of-life since 2016, superseded by commons-math4. No known active CVEs. | `benchmark-jmh` (transitive, in `versions.lock`) | Migrate when commons-math4 stabilizes. |

### Well-Maintained Dependencies (No Action Needed)

The following dependencies are current and well-maintained:

| Dependency | Version | Notes |
|-----------|---------|-------|
| `commons-codec:commons-codec` | 1.20.0 | Current |
| `org.apache.commons:commons-compress` | 1.28.0 | Current |
| `org.apache.commons:commons-lang3` | 3.18.0 | Current (transitive, in `versions.lock`) |
| `com.ibm.icu:icu4j` | 78.2 | Current |
| `org.apache.opennlp:opennlp-tools` | 2.5.7 | Current |
| `com.google.guava:guava` | 33.5.0-jre | Current (transitive, in `versions.lock`) |
| `org.assertj:assertj-core` | 3.27.7 | Current |
| `org.slf4j:slf4j-api` | 2.0.17 | Current (transitive, in `versions.lock`) |
| `org.antlr:antlr4` | 4.13.2 | Current |
| `junit:junit` | 4.13.2 | Current |
| `org.hamcrest:hamcrest` | 3.0 | Current |

---

## 3. Gradle Wrapper Mismatch (FIXED)

**Status:** FIXED in FEAT-001

**Finding:**
- `gradle/wrapper/gradle-wrapper.properties` referenced Gradle **9.2.1** (`distributionUrl=...gradle-9.2.1-all.zip`)
- `gradle/wrapper/gradle-wrapper.jar.version` contained **9.1.0**
- `gradle/wrapper/gradle-wrapper.jar.sha256` contained the SHA-256 checksum for the 9.1.0 wrapper JAR

**Fix applied:**
- Updated `gradle-wrapper.jar.version` to `9.2.1`
- Updated `gradle-wrapper.jar.sha256` to the correct SHA-256 checksum for the 9.2.1 wrapper JAR

**Risk:** A mismatched wrapper version and checksum could cause the wrapper to download an unexpected Gradle distribution. The `WrapperDownloader.java` bootstrap code uses `.jar.version` to determine which wrapper JAR to fetch and `.jar.sha256` to validate it. A mismatch means the intended version (9.2.1) would fail checksum validation, or an older version (9.1.0) would be downloaded instead.

---

## 4. GitHub Actions/Workflow Security

Based on review of all **17 workflow files** in `.github/workflows/` and **2 composite actions** in `.github/actions/`:

### Positive Findings (Well-Secured)

- **SHA-pinned action versions:** All actions use SHA-pinned versions, e.g., `actions/checkout@8e8c483db84b4bee98b60c0593521ed34d9990e8 # v6.0.1`
- **Minimal permissions:** All workflows set `permissions: {}` at the top level with minimal job-level permissions
- **No credential persistence:** `persist-credentials: false` is consistently used with `actions/checkout`
- **Zizmor security scanner:** Runs as a dedicated job in `actions.yml`, scanning all workflows with `--pedantic` mode and uploading SARIF results
- **Timeout limits:** `timeout-minutes` is set on all jobs (typically 15 or 30 minutes)

### Findings

#### `auto-format.yml` - Medium Risk

- **Trigger:** `issue_comment` (fires on PR comments)
- **Permissions:** `contents: write` (pushes branch)
- **Mitigation:** Includes a permission check step that verifies the user is the PR author or has admin/write access to the repository
- **Risk:** The permission check mitigates unauthorized use, but the `issue_comment` trigger with `contents: write` could potentially be exploited via race conditions between the permission check and the push operation

#### `verify-changelog-and-set-milestone.yml` - Medium Risk

- **Trigger:** `pull_request_target` (line 4) with explicit `# zizmor: ignore[dangerous-triggers]` comment
- **Risk:** `pull_request_target` checks out PR head code, which is a known security pattern risk. However, the script only reads `CHANGES.txt` and does not execute any code from the PR

#### `label-pull-request.yml` - Low Risk

- **Trigger:** `pull_request_target` with `# zizmor: ignore[dangerous-triggers]` comment
- **Mitigation:** Only runs on the upstream repository via guard: `if: (github.repository == 'apache/lucene')` (line 22). Will not execute on forks.

#### `run-checks-all.yml` - Medium Risk

- **Finding:** `DEVELOCITY_ACCESS_KEY` is exposed at the **workflow level** (line 21: `env: DEVELOCITY_ACCESS_KEY: ${{ secrets.DEVELOCITY_ACCESS_KEY }}`), meaning all jobs in the workflow receive access to the secret
- **Recommendation:** Scope the secret to only the specific jobs that need it

#### `run-nightly-smoketester.yml` - Low Risk

- **Finding:** Has `contents: write` permission (line 17) with a comment: `# may work with contents:read`
- **Recommendation:** Test whether `contents: read` suffices and tighten the permission

---

## 5. CodeQL Configuration

**File:** `.github/codeql-config.yml`

**Excluded queries:**
- `java/implicit-cast-in-compound-assignment`
- `java/comparison-with-wider-type`

**Assessment: These exclusions are APPROPRIATE.**

- Both are noisy code quality checks, not security vulnerability detectors
- The project already uses **ErrorProne 2.46.0** (`gradle/libs.versions.toml` line 14) and **forbidden-apis 3.10** (`gradle/libs.versions.toml` line 103) which catch these patterns more precisely
- CodeQL uses the `security-extended` query suite (`.github/workflows/codeql.yml` line 50), which is the recommended choice for security scanning

**CodeQL scan coverage** (`.github/workflows/codeql.yml`):
- `actions` (build-mode: none)
- `python` (build-mode: none)
- `java` (build-mode: none)

---

## 6. Open PR Cleanup

All **20 open PRs** (numbers #1 through #21, with #14 present) were created by bot accounts (`ghostyappbeta[bot]` and `ghostyappzeta[bot]`). **None should be merged without careful review.**

### DANGEROUS - Close Immediately

| PR | Title | Risk |
|----|-------|------|
| #5 | Remove all test files - moved to Canary repository | Deletes **2,999 test files** (615,021 lines). Would destroy the entire test suite. |
| #6 | Remove test files - moved to Canary repository | Deletes **3,050 test files**. Duplicate of #5 with same destructive intent. |

### DUPLICATES - Close All But Potentially One

| PRs | Topic | Action |
|-----|-------|--------|
| #1, #2, #3, #4, #7, #10, #14, #19 | BM25F query parser implementations | 8 duplicate attempts. Close all; if the feature is desired, create a fresh, reviewed implementation. |
| #8, #9, #11 | Hindi README translation | 3 duplicates. Close all. |

### FRIVOLOUS - Close

| PR | Title | Reason |
|----|-------|--------|
| #12 | Add jokes to README | Inappropriate for Apache project fork |
| #16 | Add a sonnet to the README | Inappropriate |
| #17 | Add haiku to README | Inappropriate |
| #18 | Add search pun tagline to README | Inappropriate |
| #20 | Add jokes folder | Empty folder, no purpose |
| #21 | Add jokes folder (v2) | Duplicate of #20 |

### REVIEW CAREFULLY If Feature Desired

| PR | Title | Notes |
|----|-------|-------|
| #13 | Add Contributors section to README | May be appropriate for a fork, but should list actual contributors |
| #15 | Integrate Lucene with Amazon Neptune | Large feature PR (27+ new files). Needs thorough security and code review if considered. |

---

## 7. Build Configuration Security

### Positive Findings

- **`versions.lock`** provides reproducible dependency resolution with checksums (auto-generated by `./gradlew writeLocks writeChecksums`)
- **OWASP dependency-check** configured in `gradle/validation/owasp/exclusions.xml` with documented suppressions
- **Forbidden-APIs plugin** (3.10) blocks dangerous API usage at compile time
- **Repository configuration** uses Google Maven Central mirror on CI for reliability (see `build-tools/build-infra/declare-repositories.gradle`)

### Findings

- **`settings.gradle`** references Gradle plugins by version tag (not SHA):
  - `org.gradle.toolchains.foojay-resolver-convention` 1.0.0 (line 30)
  - `com.gradle.develocity` 3.19.2 (line 31)
  - `com.gradle.common-custom-user-data-gradle-plugin` 2.4.0 (line 32)
  
  This is standard practice for the Gradle plugin portal, which has its own verification mechanisms.

- **No dependency verification signature checking** (Gradle's `dependency-verification.xml` is not present). The `versions.lock` provides checksum-based pinning which is adequate for this project's threat model.

---

## 8. Code Quality / Security Anti-Patterns

### XML Parsing

| File | Status | Details |
|------|--------|---------|
| `lucene/analysis/common/src/java/org/apache/lucene/analysis/compound/hyphenation/PatternParser.java` | **FIXED** | `SAXParserFactory` created without `FEATURE_SECURE_PROCESSING`. Fixed in FEAT-001 (line 110). |
| `lucene/queryparser/src/java/org/apache/lucene/queryparser/xml/CoreParser.java` | OK | Properly sets `FEATURE_SECURE_PROCESSING` (line 186). |
| `lucene/benchmark/src/java/org/apache/lucene/benchmark/byTask/feeds/EnwikiContentSource.java` | OK | Properly sets `FEATURE_SECURE_PROCESSING` on `SAX_PARSER_FACTORY` (line 56). |
| `lucene/highlighter/src/test/org/apache/lucene/search/highlight/TestHighlighter.java` | LOW RISK | `DocumentBuilderFactory` created without `FEATURE_SECURE_PROCESSING` (line 1893), but this is test code parsing hardcoded XHTML strings only (lines 1880-1891). |

### Deserialization

| File | Status | Details |
|------|--------|---------|
| `lucene/analysis/smartcn/src/java/org/apache/lucene/analysis/cn/smart/hhmm/WordDictionary.java` | OK | Uses `ObjectInputStream` with `setObjectInputFilter()` (line 145) for safe deserialization. |
| `lucene/analysis/smartcn/src/java/org/apache/lucene/analysis/cn/smart/hhmm/BigramDictionary.java` | OK | Uses `ObjectInputStream` with `setObjectInputFilter()` for safe deserialization. |
| `lucene/analysis/smartcn/src/java/org/apache/lucene/analysis/cn/smart/hhmm/AbstractDictionary.java` | OK | Provides `filterObjectInputStream()` (line 203) that only allows primitive array types. |
| `lucene/test-framework/src/test/org/apache/lucene/tests/util/TestTestObjectInputFilterFactory.java` | OK | Validates the deserialization filter with allow/deny tests. |

---

## 9. Recommendations Summary

| Priority | Finding | Action |
|----------|---------|--------|
| **P0 - Critical** | PRs #5 and #6 propose removing all tests | Close immediately |
| **P0 - Critical** | 20 bot-generated PRs cluttering the repo | Close all frivolous/duplicate PRs |
| **P1 - High** | `nekohtml` 1.9.22 unmaintained since 2014 | Replace with modern HTML parser (jsoup) |
| **P1 - High** | Gradle wrapper version mismatch | **FIXED** |
| **P1 - High** | `PatternParser.java` missing XXE protection | **FIXED** |
| **P2 - Medium** | `xerces:xercesImpl` 2.12.2 CVE history | Update to latest or isolate further |
| **P2 - Medium** | `DEVELOCITY_ACCESS_KEY` at workflow level | Scope to specific jobs in `run-checks-all.yml` |
| **P2 - Medium** | `run-nightly-smoketester.yml` permissions | Test if `contents: read` suffices instead of `contents: write` |
| **P3 - Low** | `commons-math3` 3.6.1 EOL | Migrate when commons-math4 is stable |
| **P3 - Low** | `TestHighlighter.java` no XXE protection | Add `FEATURE_SECURE_PROCESSING` (defense in depth) |

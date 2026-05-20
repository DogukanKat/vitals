# Engine Facade + JSON Reporter Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extract the analysis pipeline into a reusable `VitalsEngine` facade and add a custom, schema-validated JSON report, so the CLI and future build plugins share one code path.

**Architecture:** New `vitals-engine` module owns the rule registry, the `VitalsEngine` facade, and all reporters (`ConsoleReporter` moved here, `JsonReporter` new). `vitals-core` gains an immutable `AnalysisResult` record. `vitals-cli` slims to argument parsing plus a `--format console|json` option and delegates to the engine.

**Tech Stack:** Java 21, Gradle (Kotlin DSL), JUnit 5 + AssertJ, hand-rolled JSON serialization (no runtime JSON dependency), `com.networknt:json-schema-validator` (test scope) for schema validation.

**Spec:** `docs/superpowers/specs/2026-05-15-engine-facade-json-reporter-design.md`

**Branch:** `feature/engine-facade-json-reporter` (already checked out)

---

## File Structure

**Created:**
- `vitals-core/src/main/java/dev/vitals/core/AnalysisResult.java` — immutable run result.
- `vitals-engine/build.gradle.kts` — module build script.
- `vitals-engine/src/main/java/dev/vitals/engine/package-info.java` — `@NullMarked`.
- `vitals-engine/src/main/java/dev/vitals/engine/VitalsEngine.java` — facade + rule registry.
- `vitals-engine/src/main/java/dev/vitals/engine/Reporter.java` — reporter SPI.
- `vitals-engine/src/main/java/dev/vitals/engine/ConsoleReporter.java` — moved from `vitals-cli`.
- `vitals-engine/src/main/java/dev/vitals/engine/JsonWriter.java` — RFC 8259 string escaping.
- `vitals-engine/src/main/java/dev/vitals/engine/JsonReporter.java` — JSON document + file write.
- `docs/schema/vitals-report-v1.schema.json` — public, versioned JSON Schema.
- `vitals-engine/src/test/java/dev/vitals/engine/AnalysisResultTest.java`
- `vitals-engine/src/test/java/dev/vitals/engine/VitalsEngineTest.java`
- `vitals-engine/src/test/java/dev/vitals/engine/JsonWriterTest.java`
- `vitals-engine/src/test/java/dev/vitals/engine/JsonReporterTest.java`
- `vitals-engine/src/test/resources/fixtures/engine/positive/Order.java` — copied fixture.
- `vitals-engine/src/test/resources/fixtures/engine/clean/Plain.java`

Note: `AnalysisResultTest` lives in `vitals-engine` (not `vitals-core`) so it can sit beside the other engine tests; `AnalysisResult` itself is in `vitals-core` because it is pure domain.

**Modified:**
- `settings.gradle.kts` — include `vitals-engine`.
- `build.gradle.kts` — add `vitals-engine` to the JaCoCo `coveredModules` set.
- `gradle/libs.versions.toml` — add `json-schema-validator`.
- `vitals-cli/build.gradle.kts` — depend on `vitals-engine` instead of rules/static-engine.
- `vitals-cli/src/main/java/dev/vitals/cli/VitalsCli.java` — slimmed, `--format` option.
- `vitals-cli/src/main/java/dev/vitals/cli/package-info.java` — reword.
- `vitals-cli/src/test/java/dev/vitals/cli/VitalsCliIntegrationTest.java` — add JSON case.

**Deleted:**
- `vitals-cli/src/main/java/dev/vitals/cli/ConsoleReporter.java` — moved to engine.

---

## Task 1: `AnalysisResult` record in `vitals-core`

**Files:**
- Create: `vitals-core/src/main/java/dev/vitals/core/AnalysisResult.java`
- Test: `vitals-engine/src/test/java/dev/vitals/engine/AnalysisResultTest.java` (added in Task 3, after the engine module exists)

This task only creates the production record; its test is added in Task 3 because the engine test module does not exist yet. The record is small and validated by construction, so this ordering is safe.

- [ ] **Step 1: Create the record**

Create `vitals-core/src/main/java/dev/vitals/core/AnalysisResult.java`:

```java
package dev.vitals.core;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/**
 * Immutable result of one analysis run: the inputs that were scanned plus the
 * computed {@link Score} and the {@link Diagnostic}s that produced it.
 *
 * @param projectRoot   absolute root of the analyzed project
 * @param filesAnalyzed number of Java sources scanned
 * @param duration      wall-clock time the analysis took
 * @param score         computed 0-100 health score
 * @param diagnostics   findings, in the order rules produced them; copied defensively
 */
public record AnalysisResult(
        Path projectRoot, int filesAnalyzed, Duration duration, Score score, List<Diagnostic> diagnostics) {

    /**
     * @throws IllegalArgumentException if any argument is null/negative
     * @throws NullPointerException if {@code diagnostics} contains a null element
     */
    public AnalysisResult {
        if (projectRoot == null) {
            throw new IllegalArgumentException("projectRoot must not be null");
        }
        if (filesAnalyzed < 0) {
            throw new IllegalArgumentException("filesAnalyzed must be non-negative");
        }
        if (duration == null || duration.isNegative()) {
            throw new IllegalArgumentException("duration must be non-negative");
        }
        if (score == null) {
            throw new IllegalArgumentException("score must not be null");
        }
        diagnostics = List.copyOf(diagnostics);
    }
}
```

- [ ] **Step 2: Compile core**

Run: `./gradlew :vitals-core:compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add vitals-core/src/main/java/dev/vitals/core/AnalysisResult.java
git commit -m "feat(core): add immutable AnalysisResult record"
```

---

## Task 2: Scaffold the `vitals-engine` module

**Files:**
- Modify: `settings.gradle.kts`
- Modify: `gradle/libs.versions.toml`
- Modify: `build.gradle.kts:140-147`
- Create: `vitals-engine/build.gradle.kts`
- Create: `vitals-engine/src/main/java/dev/vitals/engine/package-info.java`

- [ ] **Step 1: Add the module to settings**

In `settings.gradle.kts`, the `include(...)` block currently lists modules ending with `"vitals-static-engine",`. Add `"vitals-engine",` immediately after `"vitals-static-engine",` so the block reads:

```kotlin
include(
    "vitals-core",
    "vitals-static-engine",
    "vitals-engine",
    "vitals-rules-jpa",
    "vitals-rules-spring",
    "vitals-rules-kafka",
    "vitals-rules-redis",
    "vitals-rules-jvm",
    "vitals-cli",
)
```

- [ ] **Step 2: Add the schema-validator dependency to the version catalog**

In `gradle/libs.versions.toml`, under `[versions]` add (after the `assertj = "3.26.3"` line):

```toml
networknt = "1.5.4"
```

Under `[libraries]`, in the `# test` group (after the `assertj-core = ...` line) add:

```toml
json-schema-validator = { module = "com.networknt:json-schema-validator", version.ref = "networknt" }
```

- [ ] **Step 3: Add `vitals-engine` to the coverage gate**

In `build.gradle.kts`, the `coveredModules` set (around line 140) lists the modules under an 80% line-coverage gate. Add `"vitals-engine",` so it reads:

```kotlin
val coveredModules = setOf(
    "vitals-core",
    "vitals-engine",
    "vitals-rules-jpa",
    "vitals-rules-spring",
    "vitals-rules-kafka",
    "vitals-rules-redis",
    "vitals-rules-jvm",
)
```

- [ ] **Step 4: Create the module build script**

Create `vitals-engine/build.gradle.kts`:

```kotlin
plugins {
    `java-library`
}

dependencies {
    api(project(":vitals-core"))
    implementation(project(":vitals-static-engine"))
    implementation(project(":vitals-rules-jpa"))
    implementation(project(":vitals-rules-spring"))
    implementation(project(":vitals-rules-kafka"))
    implementation(project(":vitals-rules-redis"))
    implementation(project(":vitals-rules-jvm"))
    implementation(rootProject.libs.slf4j.api)
    testImplementation(rootProject.libs.json.schema.validator)
}

// The published JSON Schema is the single source of truth; expose it on the
// test classpath so JsonReporterTest validates against the real contract.
sourceSets {
    test {
        resources {
            srcDir(rootProject.file("docs/schema"))
        }
    }
}
```

- [ ] **Step 5: Create the package-info**

Create `vitals-engine/src/main/java/dev/vitals/engine/package-info.java`:

```java
/** Reusable analysis facade and reporters shared by the CLI and build plugins. */
@NullMarked
package dev.vitals.engine;

import org.jspecify.annotations.NullMarked;
```

- [ ] **Step 6: Verify the module resolves**

Run: `./gradlew :vitals-engine:dependencies --configuration compileClasspath -q`
Expected: BUILD SUCCESSFUL, output lists `project :vitals-core`, the rules projects, and `org.slf4j:slf4j-api`.

- [ ] **Step 7: Commit**

```bash
git add settings.gradle.kts gradle/libs.versions.toml build.gradle.kts \
        vitals-engine/build.gradle.kts \
        vitals-engine/src/main/java/dev/vitals/engine/package-info.java
git commit -m "build(engine): scaffold vitals-engine module"
```

---

## Task 3: `VitalsEngine` facade

**Files:**
- Create: `vitals-engine/src/main/java/dev/vitals/engine/VitalsEngine.java`
- Create: `vitals-engine/src/test/resources/fixtures/engine/positive/Order.java`
- Create: `vitals-engine/src/test/resources/fixtures/engine/clean/Plain.java`
- Create: `vitals-engine/src/test/java/dev/vitals/engine/VitalsEngineTest.java`
- Create: `vitals-engine/src/test/java/dev/vitals/engine/AnalysisResultTest.java`

- [ ] **Step 1: Materialize the test fixtures**

Run exactly (copies a rule fixture already proven to trigger JPA-001, so the test does not depend on hand-written trigger code):

```bash
mkdir -p vitals-engine/src/test/resources/fixtures/engine/positive
cp vitals-rules-jpa/src/test/resources/fixtures/jpa-001/positive/Order.java \
   vitals-engine/src/test/resources/fixtures/engine/positive/Order.java
mkdir -p vitals-engine/src/test/resources/fixtures/engine/clean
printf 'package com.example;\n\npublic final class Plain {\n    private Plain() {}\n}\n' \
   > vitals-engine/src/test/resources/fixtures/engine/clean/Plain.java
```

- [ ] **Step 2: Write the failing `VitalsEngineTest`**

Create `vitals-engine/src/test/java/dev/vitals/engine/VitalsEngineTest.java`:

```java
package dev.vitals.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.vitals.core.AnalysisResult;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VitalsEngineTest {

    @Test
    void analyze_givenEagerFetchEntity_reportsJpa001AndLowersScore(@TempDir Path tempDir) {
        Path project = copyFixture("positive", tempDir);

        AnalysisResult result = new VitalsEngine().analyze(project);

        assertThat(result.diagnostics()).anyMatch(d -> d.ruleId().value().equals("JPA-001"));
        assertThat(result.score().value()).isLessThan(100);
        assertThat(result.filesAnalyzed()).isGreaterThanOrEqualTo(1);
        assertThat(result.projectRoot()).isEqualTo(project.toAbsolutePath().normalize());
        assertThat(result.duration().isNegative()).isFalse();
    }

    @Test
    void analyze_givenCleanProject_reportsNoDiagnosticsAndPerfectScore(@TempDir Path tempDir) {
        Path project = copyFixture("clean", tempDir);

        AnalysisResult result = new VitalsEngine().analyze(project);

        assertThat(result.diagnostics()).isEmpty();
        assertThat(result.score().value()).isEqualTo(100);
    }

    @Test
    void analyze_givenNonDirectory_throws(@TempDir Path tempDir) throws Exception {
        Path file = Files.writeString(tempDir.resolve("not-a-dir.txt"), "x");

        assertThatThrownBy(() -> new VitalsEngine().analyze(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Not a directory");
    }

    private static Path copyFixture(String name, Path tempDir) {
        try {
            URL root = VitalsEngineTest.class.getResource("/fixtures/engine/" + name);
            Objects.requireNonNull(root, "fixture not found: " + name);
            Path src = Path.of(root.toURI());
            try (Stream<Path> walk = Files.walk(src)) {
                walk.forEach(p -> {
                    Path target = tempDir.resolve(src.relativize(p).toString());
                    try {
                        if (Files.isDirectory(p)) {
                            Files.createDirectories(target);
                        } else {
                            Files.createDirectories(target.getParent());
                            Files.copy(p, target, StandardCopyOption.REPLACE_EXISTING);
                        }
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
            }
            return tempDir;
        } catch (IOException | URISyntaxException e) {
            throw new IllegalStateException("Failed to materialize fixture " + name, e);
        }
    }
}
```

- [ ] **Step 3: Run it to verify it fails**

Run: `./gradlew :vitals-engine:test --tests 'dev.vitals.engine.VitalsEngineTest'`
Expected: FAIL — compilation error, `VitalsEngine` does not exist.

- [ ] **Step 4: Implement `VitalsEngine`**

Create `vitals-engine/src/main/java/dev/vitals/engine/VitalsEngine.java`:

```java
package dev.vitals.engine;

import dev.vitals.core.AnalysisContext;
import dev.vitals.core.AnalysisResult;
import dev.vitals.core.Diagnostic;
import dev.vitals.core.Score;
import dev.vitals.core.ScoreCalculator;
import dev.vitals.core.StaticRule;
import dev.vitals.rules.jpa.Jpa001EagerFetchRule;
import dev.vitals.rules.jpa.Jpa002NPlusOneRule;
import dev.vitals.rules.jpa.Jpa003OpenInViewRule;
import dev.vitals.rules.jvm.Jvm001ContainerHeapRule;
import dev.vitals.rules.kafka.Kafka001AutoCommitRule;
import dev.vitals.rules.spring.Cfg001HardcodedSecretRule;
import dev.vitals.rules.spring.Di001FieldInjectionRule;
import dev.vitals.rules.spring.Sec001ActuatorExposureRule;
import dev.vitals.rules.spring.Tx001BlockingInTransactionRule;
import dev.vitals.rules.spring.Tx002NonPublicTransactionalRule;
import dev.vitals.staticengine.JavaParserAnalysisContext;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Reusable analysis facade: discovers a project, runs every registered rule,
 * computes the score. Shared by the CLI and the build plugins so the analysis
 * pipeline has exactly one implementation.
 */
public final class VitalsEngine {

    private final List<StaticRule> rules = List.of(
            new Jpa001EagerFetchRule(),
            new Jpa002NPlusOneRule(),
            new Jpa003OpenInViewRule(),
            new Tx001BlockingInTransactionRule(),
            new Tx002NonPublicTransactionalRule(),
            new Di001FieldInjectionRule(),
            new Sec001ActuatorExposureRule(),
            new Cfg001HardcodedSecretRule(),
            new Jvm001ContainerHeapRule(),
            new Kafka001AutoCommitRule());

    /**
     * Analyzes the project rooted at {@code projectRoot}.
     *
     * @param projectRoot directory to scan
     * @return the score and diagnostics for the project
     * @throws IllegalArgumentException if {@code projectRoot} is not a directory
     */
    public AnalysisResult analyze(Path projectRoot) {
        Path root = projectRoot.toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException("Not a directory: " + root);
        }
        long started = System.nanoTime();
        AnalysisContext context = JavaParserAnalysisContext.discover(root);
        List<Diagnostic> diagnostics = new ArrayList<>();
        for (StaticRule rule : rules) {
            diagnostics.addAll(rule.analyze(context));
        }
        Score score = ScoreCalculator.compute(diagnostics);
        Duration duration = Duration.ofNanos(System.nanoTime() - started);
        return new AnalysisResult(root, context.javaSources().size(), duration, score, diagnostics);
    }
}
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `./gradlew :vitals-engine:test --tests 'dev.vitals.engine.VitalsEngineTest'`
Expected: PASS (3 tests).

- [ ] **Step 6: Add `AnalysisResultTest`**

Create `vitals-engine/src/test/java/dev/vitals/engine/AnalysisResultTest.java`:

```java
package dev.vitals.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.vitals.core.AnalysisResult;
import dev.vitals.core.Score;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class AnalysisResultTest {

    @Test
    void constructor_givenValidArgs_copiesDiagnosticsDefensively() {
        List<dev.vitals.core.Diagnostic> source = new ArrayList<>();
        AnalysisResult result =
                new AnalysisResult(Path.of("/p"), 3, Duration.ofMillis(10), new Score(80, 0, 1, 0), source);

        assertThatThrownBy(() -> result.diagnostics().add(null)).isInstanceOf(UnsupportedOperationException.class);
        assertThat(result.diagnostics()).isEmpty();
    }

    @Test
    void constructor_givenNegativeDuration_throws() {
        assertThatThrownBy(() ->
                        new AnalysisResult(Path.of("/p"), 0, Duration.ofMillis(-1), new Score(100, 0, 0, 0), List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duration");
    }

    @Test
    void constructor_givenNegativeFileCount_throws() {
        assertThatThrownBy(
                        () -> new AnalysisResult(Path.of("/p"), -1, Duration.ZERO, new Score(100, 0, 0, 0), List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("filesAnalyzed");
    }
}
```

- [ ] **Step 7: Run the new test**

Run: `./gradlew :vitals-engine:test --tests 'dev.vitals.engine.AnalysisResultTest'`
Expected: PASS (3 tests).

- [ ] **Step 8: Commit**

```bash
git add vitals-engine/src/main/java/dev/vitals/engine/VitalsEngine.java \
        vitals-engine/src/test/java/dev/vitals/engine/VitalsEngineTest.java \
        vitals-engine/src/test/java/dev/vitals/engine/AnalysisResultTest.java \
        vitals-engine/src/test/resources/fixtures/engine
git commit -m "feat(engine): add VitalsEngine analysis facade"
```

---

## Task 4: Move `ConsoleReporter` into the engine, slim the CLI

This task is a behavior-preserving refactor. The existing `VitalsCliIntegrationTest` (console assertions) is the safety net — it must stay green with no edits in this task.

**Files:**
- Create: `vitals-engine/src/main/java/dev/vitals/engine/Reporter.java`
- Create: `vitals-engine/src/main/java/dev/vitals/engine/ConsoleReporter.java`
- Create: `vitals-engine/src/test/java/dev/vitals/engine/ConsoleReporterTest.java`
- Delete: `vitals-cli/src/main/java/dev/vitals/cli/ConsoleReporter.java`
- Modify: `vitals-cli/src/main/java/dev/vitals/cli/VitalsCli.java`
- Modify: `vitals-cli/src/main/java/dev/vitals/cli/package-info.java`
- Modify: `vitals-cli/build.gradle.kts`

`ConsoleReporter` moves into `vitals-engine`, which is under an 80% JaCoCo
gate (Task 9). Its only callers are the CLI integration tests, which do not
count toward the engine module's coverage — so this task adds a dedicated
engine-level `ConsoleReporterTest` that exercises every severity label, every
grade, the empty-diagnostics branch, and the I/O-failure path.

- [ ] **Step 1: Create the `Reporter` SPI**

Create `vitals-engine/src/main/java/dev/vitals/engine/Reporter.java`:

```java
package dev.vitals.engine;

import dev.vitals.core.AnalysisResult;

/** Renders an {@link AnalysisResult} to a destination (console, JSON, ...). */
public interface Reporter {

    /**
     * Writes the report for {@code result} to {@code out}.
     *
     * @param result the analysis to render
     * @param out    sink for the primary (stdout) representation
     */
    void report(AnalysisResult result, Appendable out);
}
```

- [ ] **Step 2: Create `ConsoleReporter` in the engine (output byte-identical to the old one)**

Create `vitals-engine/src/main/java/dev/vitals/engine/ConsoleReporter.java`:

```java
package dev.vitals.engine;

import dev.vitals.core.AnalysisResult;
import dev.vitals.core.Diagnostic;
import dev.vitals.core.Score;
import dev.vitals.core.Severity;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Locale;

/** Plain-text reporter: human-readable score summary and findings. */
public final class ConsoleReporter implements Reporter {

    @Override
    public void report(AnalysisResult result, Appendable out) {
        Score score = result.score();
        double seconds = result.duration().toNanos() / 1_000_000_000.0;
        try {
            out.append("Vitals 0.1.0\n");
            out.append(String.format(Locale.ROOT, "Analyzed %d files in %.1fs", result.filesAnalyzed(), seconds))
                    .append('\n');
            out.append('\n');
            out.append(String.format(Locale.ROOT, "Score: %d / 100 (%s)", score.value(), prettyGrade(score.grade())))
                    .append('\n');
            out.append(String.format(Locale.ROOT, "  Errors:   %d", score.errors())).append('\n');
            out.append(String.format(Locale.ROOT, "  Warnings: %d", score.warnings())).append('\n');
            out.append(String.format(Locale.ROOT, "  Info:     %d", score.infos())).append('\n');
            if (!result.diagnostics().isEmpty()) {
                out.append('\n');
                for (Diagnostic d : result.diagnostics()) {
                    out.append(formatLine(result.projectRoot(), d)).append('\n');
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write console report", e);
        }
    }

    private static String formatLine(Path projectRoot, Diagnostic d) {
        Path relative = projectRoot.relativize(d.location().filePath());
        return String.format(
                Locale.ROOT,
                "  %-6s %s  %s:%d  %s",
                label(d.severity()),
                d.ruleId(),
                relative,
                d.location().line(),
                d.message());
    }

    private static String label(Severity severity) {
        return switch (severity) {
            case Severity.Error e -> "ERROR";
            case Severity.Warn w -> "WARN";
            case Severity.Info i -> "INFO";
        };
    }

    private static String prettyGrade(Score.Grade grade) {
        return switch (grade) {
            case GREAT -> "Great";
            case NEEDS_WORK -> "Needs work";
            case CRITICAL -> "Critical";
        };
    }
}
```

- [ ] **Step 3: Add `ConsoleReporterTest` (engine-module coverage)**

Create `vitals-engine/src/test/java/dev/vitals/engine/ConsoleReporterTest.java`:

```java
package dev.vitals.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.vitals.core.AnalysisResult;
import dev.vitals.core.Diagnostic;
import dev.vitals.core.RuleCategory;
import dev.vitals.core.RuleId;
import dev.vitals.core.Score;
import dev.vitals.core.Severity;
import dev.vitals.core.SourceLocation;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConsoleReporterTest {

    private static Diagnostic diag(RuleId id, Severity severity, RuleCategory category, Path root) {
        return new Diagnostic(
                id,
                severity,
                category,
                new SourceLocation(root.resolve("A.java"), 7, 3),
                "finding from " + id.value(),
                "https://vitals.dev/rules/" + id.value());
    }

    @Test
    void report_givenAllSeveritiesAndCriticalGrade_rendersEveryLabel() {
        Path root = Path.of("/proj");
        AnalysisResult result = new AnalysisResult(
                root,
                3,
                Duration.ofMillis(1200),
                new Score(20, 1, 1, 1),
                List.of(
                        diag(new RuleId("JPA-001"), new Severity.Error(), RuleCategory.JPA, root),
                        diag(new RuleId("DI-001"), new Severity.Warn(), RuleCategory.SPRING, root),
                        diag(new RuleId("JPA-003"), new Severity.Info(), RuleCategory.JPA, root)));

        StringBuilder out = new StringBuilder();
        new ConsoleReporter().report(result, out);
        String text = out.toString();

        assertThat(text).contains("Vitals 0.1.0");
        assertThat(text).contains("Analyzed 3 files in 1.2s");
        assertThat(text).contains("Score: 20 / 100 (Critical)");
        assertThat(text).contains("ERROR  JPA-001");
        assertThat(text).contains("WARN   DI-001");
        assertThat(text).contains("INFO   JPA-003");
        assertThat(text).contains("A.java:7");
    }

    @Test
    void report_givenCleanProject_rendersGreatGradeAndNoFindingsBlock() {
        AnalysisResult result =
                new AnalysisResult(Path.of("/proj"), 0, Duration.ZERO, new Score(100, 0, 0, 0), List.of());

        StringBuilder out = new StringBuilder();
        new ConsoleReporter().report(result, out);

        assertThat(out.toString()).contains("Score: 100 / 100 (Great)");
        assertThat(out.toString()).doesNotContain("ERROR").doesNotContain("WARN").doesNotContain("INFO");
    }

    @Test
    void report_givenNeedsWorkGrade_rendersNeedsWorkLabel() {
        AnalysisResult result =
                new AnalysisResult(Path.of("/proj"), 1, Duration.ofMillis(10), new Score(60, 0, 2, 0), List.of());

        StringBuilder out = new StringBuilder();
        new ConsoleReporter().report(result, out);

        assertThat(out.toString()).contains("Score: 60 / 100 (Needs work)");
    }

    @Test
    void report_givenFailingAppendable_wrapsIoExceptionUnchecked() {
        AnalysisResult result =
                new AnalysisResult(Path.of("/proj"), 0, Duration.ZERO, new Score(100, 0, 0, 0), List.of());
        Appendable failing = new Appendable() {
            @Override
            public Appendable append(CharSequence csq) throws IOException {
                throw new IOException("boom");
            }

            @Override
            public Appendable append(CharSequence csq, int start, int end) throws IOException {
                throw new IOException("boom");
            }

            @Override
            public Appendable append(char c) throws IOException {
                throw new IOException("boom");
            }
        };

        assertThatThrownBy(() -> new ConsoleReporter().report(result, failing))
                .isInstanceOf(UncheckedIOException.class)
                .hasMessageContaining("console report");
    }
}
```

- [ ] **Step 4: Run `ConsoleReporterTest`**

Run: `./gradlew :vitals-engine:test --tests 'dev.vitals.engine.ConsoleReporterTest'`
Expected: PASS (4 tests).

- [ ] **Step 5: Delete the old CLI `ConsoleReporter`**

Run: `git rm vitals-cli/src/main/java/dev/vitals/cli/ConsoleReporter.java`

- [ ] **Step 6: Rewrite `VitalsCli` to delegate to the engine**

Replace the entire contents of `vitals-cli/src/main/java/dev/vitals/cli/VitalsCli.java` with:

```java
package dev.vitals.cli;

import dev.vitals.core.AnalysisResult;
import dev.vitals.engine.ConsoleReporter;
import dev.vitals.engine.JsonReporter;
import dev.vitals.engine.VitalsEngine;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import picocli.CommandLine;

/**
 * {@code vitals .} entry point. Validates the target directory, runs the
 * {@link VitalsEngine}, and renders the result with the selected reporter.
 */
@CommandLine.Command(
        name = "vitals",
        mixinStandardHelpOptions = true,
        version = "0.1.0",
        description = "Check your Spring Boot's vitals before production does.")
public final class VitalsCli implements Callable<Integer> {

    /** Output channel selected via {@code --format}. */
    enum OutputFormat {
        CONSOLE,
        JSON
    }

    @CommandLine.Parameters(
            index = "0",
            paramLabel = "PATH",
            description = "Path to the project to analyze.",
            defaultValue = ".")
    private Path projectPath = Path.of(".");

    @CommandLine.Option(
            names = "--format",
            paramLabel = "FORMAT",
            description = "Output format: console (default) or json.")
    private OutputFormat format = OutputFormat.CONSOLE;

    private final PrintStream out;
    private final PrintStream err;

    public VitalsCli() {
        this(System.out, System.err);
    }

    VitalsCli(PrintStream out, PrintStream err) {
        this.out = out;
        this.err = err;
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new VitalsCli())
                .setCaseInsensitiveEnumValuesAllowed(true)
                .execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() {
        Path root = projectPath.toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            err.println("vitals: not a directory: " + root);
            return 2;
        }
        AnalysisResult result = new VitalsEngine().analyze(root);
        switch (format) {
            case CONSOLE -> new ConsoleReporter().report(result, out);
            case JSON -> new JsonReporter().report(result, out);
        }
        return result.score().errors() > 0 ? 1 : 0;
    }
}
```

Note: this references `JsonReporter`, created in Task 7. The CLI will not compile until then; that is expected and acceptable because Task 4 ends by running the engine module's tests, not the CLI's. The CLI is compiled and tested in Task 8.

- [ ] **Step 7: Reword the CLI package-info**

In `vitals-cli/src/main/java/dev/vitals/cli/package-info.java` change the first line from:

```java
/** CLI entry point and console reporter for Vitals. */
```

to:

```java
/** CLI entry point for Vitals. */
```

- [ ] **Step 8: Repoint the CLI build script**

Replace the entire contents of `vitals-cli/build.gradle.kts` with:

```kotlin
plugins {
    `java-library`
    application
}

dependencies {
    implementation(project(":vitals-engine"))
    implementation(rootProject.libs.picocli)
    runtimeOnly(rootProject.libs.logback.classic)
}

application {
    mainClass.set("dev.vitals.cli.VitalsCli")
    applicationName = "vitals"
}
```

- [ ] **Step 9: Verify the engine module still builds and its tests pass**

Run: `./gradlew :vitals-engine:test`
Expected: PASS — AnalysisResultTest (3), VitalsEngineTest (3), ConsoleReporterTest (4): 10 tests.

- [ ] **Step 10: Commit**

The old CLI `ConsoleReporter` deletion was already staged by `git rm` in Step 5.

```bash
git add vitals-engine/src/main/java/dev/vitals/engine/Reporter.java \
        vitals-engine/src/main/java/dev/vitals/engine/ConsoleReporter.java \
        vitals-engine/src/test/java/dev/vitals/engine/ConsoleReporterTest.java \
        vitals-cli/src/main/java/dev/vitals/cli/VitalsCli.java \
        vitals-cli/src/main/java/dev/vitals/cli/package-info.java \
        vitals-cli/build.gradle.kts
git commit -m "refactor(cli): move ConsoleReporter to vitals-engine, delegate via facade"
```

---

## Task 5: `JsonWriter` (RFC 8259 string escaping)

**Files:**
- Create: `vitals-engine/src/main/java/dev/vitals/engine/JsonWriter.java`
- Test: `vitals-engine/src/test/java/dev/vitals/engine/JsonWriterTest.java`

- [ ] **Step 1: Write the failing test**

Create `vitals-engine/src/test/java/dev/vitals/engine/JsonWriterTest.java`:

```java
package dev.vitals.engine;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class JsonWriterTest {

    @Test
    void quote_givenNull_returnsBareNullLiteral() {
        assertThat(JsonWriter.quote(null)).isEqualTo("null");
    }

    @Test
    void quote_givenPlainString_wrapsInDoubleQuotes() {
        assertThat(JsonWriter.quote("hello")).isEqualTo("\"hello\"");
    }

    @Test
    void quote_givenQuoteAndBackslash_escapesBoth() {
        assertThat(JsonWriter.quote("a\"b\\c")).isEqualTo("\"a\\\"b\\\\c\"");
    }

    @Test
    void quote_givenWindowsPath_escapesEverySeparator() {
        assertThat(JsonWriter.quote("C:\\Users\\x\\Order.java"))
                .isEqualTo("\"C:\\\\Users\\\\x\\\\Order.java\"");
    }

    @Test
    void quote_givenControlCharacters_usesShortAndUnicodeEscapes() {
        assertThat(JsonWriter.quote("a\nb\tc\rd\bfgh"))
                .isEqualTo("\"a\\nb\\tc\\rd\\bf\\fg\\u0001h\"");
    }

    @Test
    void quote_givenNonAsciiUnicode_passesThrough() {
        assertThat(JsonWriter.quote("café ✓")).isEqualTo("\"café ✓\"");
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :vitals-engine:test --tests 'dev.vitals.engine.JsonWriterTest'`
Expected: FAIL — compilation error, `JsonWriter` does not exist.

- [ ] **Step 3: Implement `JsonWriter`**

Create `vitals-engine/src/main/java/dev/vitals/engine/JsonWriter.java`:

```java
package dev.vitals.engine;

import java.util.Locale;
import org.jspecify.annotations.Nullable;

/** Minimal RFC 8259 JSON string escaping. The document shape lives in {@link JsonReporter}. */
final class JsonWriter {

    private JsonWriter() {}

    /**
     * Returns {@code value} as a JSON string literal including the surrounding
     * double quotes, or the bare literal {@code null} when {@code value} is null.
     */
    static String quote(@Nullable String value) {
        if (value == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder(value.length() + 2);
        sb.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
        return sb.toString();
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :vitals-engine:test --tests 'dev.vitals.engine.JsonWriterTest'`
Expected: PASS (6 tests).

- [ ] **Step 5: Commit**

```bash
git add vitals-engine/src/main/java/dev/vitals/engine/JsonWriter.java \
        vitals-engine/src/test/java/dev/vitals/engine/JsonWriterTest.java
git commit -m "feat(engine): add RFC 8259 JsonWriter string escaping"
```

---

## Task 6: Publish the JSON Schema

**Files:**
- Create: `docs/schema/vitals-report-v1.schema.json`

- [ ] **Step 1: Write the schema**

Create `docs/schema/vitals-report-v1.schema.json`:

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "$id": "https://vitals.dev/schema/vitals-report-v1.schema.json",
  "title": "Vitals Report v1",
  "type": "object",
  "additionalProperties": false,
  "required": ["schemaVersion", "tool", "project", "analysis", "score", "diagnostics"],
  "properties": {
    "schemaVersion": { "const": "1.0" },
    "tool": {
      "type": "object",
      "additionalProperties": false,
      "required": ["name", "version"],
      "properties": {
        "name": { "type": "string" },
        "version": { "type": "string" }
      }
    },
    "project": {
      "type": "object",
      "additionalProperties": false,
      "required": ["root", "filesAnalyzed"],
      "properties": {
        "root": { "type": "string" },
        "filesAnalyzed": { "type": "integer", "minimum": 0 }
      }
    },
    "analysis": {
      "type": "object",
      "additionalProperties": false,
      "required": ["durationMillis"],
      "properties": {
        "durationMillis": { "type": "integer", "minimum": 0 }
      }
    },
    "score": {
      "type": "object",
      "additionalProperties": false,
      "required": ["value", "grade", "errors", "warnings", "infos"],
      "properties": {
        "value": { "type": "integer", "minimum": 0, "maximum": 100 },
        "grade": { "enum": ["GREAT", "NEEDS_WORK", "CRITICAL"] },
        "errors": { "type": "integer", "minimum": 0 },
        "warnings": { "type": "integer", "minimum": 0 },
        "infos": { "type": "integer", "minimum": 0 }
      }
    },
    "diagnostics": {
      "type": "array",
      "items": {
        "type": "object",
        "additionalProperties": false,
        "required": ["ruleId", "severity", "category", "location", "message", "helpUrl"],
        "properties": {
          "ruleId": { "type": "string", "pattern": "^[A-Z]+-\\d{3}$" },
          "severity": { "enum": ["ERROR", "WARN", "INFO"] },
          "category": {
            "enum": ["JPA", "SPRING", "KAFKA", "REDIS", "JVM", "SECURITY", "OBSERVABILITY"]
          },
          "location": {
            "type": "object",
            "additionalProperties": false,
            "required": ["file", "line", "column"],
            "properties": {
              "file": { "type": "string" },
              "line": { "type": "integer", "minimum": 0 },
              "column": { "type": "integer", "minimum": 0 }
            }
          },
          "message": { "type": "string", "minLength": 1 },
          "helpUrl": { "type": "string", "minLength": 1 }
        }
      }
    }
  }
}
```

- [ ] **Step 2: Validate the schema is well-formed JSON**

Run: `python3 -c "import json,sys; json.load(open('docs/schema/vitals-report-v1.schema.json')); print('ok')"`
Expected: `ok`

- [ ] **Step 3: Commit**

```bash
git add docs/schema/vitals-report-v1.schema.json
git commit -m "docs(schema): publish vitals-report v1 JSON Schema"
```

---

## Task 7: `JsonReporter`

**Files:**
- Create: `vitals-engine/src/main/java/dev/vitals/engine/JsonReporter.java`
- Test: `vitals-engine/src/test/java/dev/vitals/engine/JsonReporterTest.java`

- [ ] **Step 1: Write the failing test**

Create `vitals-engine/src/test/java/dev/vitals/engine/JsonReporterTest.java`:

```java
package dev.vitals.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import dev.vitals.core.AnalysisResult;
import dev.vitals.core.Diagnostic;
import dev.vitals.core.RuleCategory;
import dev.vitals.core.RuleId;
import dev.vitals.core.Score;
import dev.vitals.core.Severity;
import dev.vitals.core.SourceLocation;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JsonReporterTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void report_givenDiagnostics_emitsSchemaValidJsonWithRelativePaths(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("src/main/java/com/example/Order.java");
        AnalysisResult result = new AnalysisResult(
                tempDir,
                1,
                Duration.ofMillis(1823),
                new Score(67, 1, 0, 0),
                List.of(new Diagnostic(
                        new RuleId("JPA-001"),
                        new Severity.Error(),
                        RuleCategory.JPA,
                        new SourceLocation(file, 23, 12),
                        "FetchType.EAGER on @ManyToOne 'customer'",
                        "https://vitals.dev/rules/JPA-001")));

        StringBuilder out = new StringBuilder();
        new JsonReporter().report(result, out);
        JsonNode root = MAPPER.readTree(out.toString());

        assertThat(validate(out.toString())).isEmpty();
        assertThat(root.get("schemaVersion").asText()).isEqualTo("1.0");
        assertThat(root.get("score").get("grade").asText()).isEqualTo("NEEDS_WORK");
        JsonNode diag = root.get("diagnostics").get(0);
        assertThat(diag.get("ruleId").asText()).isEqualTo("JPA-001");
        assertThat(diag.get("severity").asText()).isEqualTo("ERROR");
        assertThat(diag.get("location").get("file").asText())
                .isEqualTo("src/main/java/com/example/Order.java");
    }

    @Test
    void report_givenCleanProject_emitsEmptyDiagnosticsArray(@TempDir Path tempDir) throws Exception {
        AnalysisResult result =
                new AnalysisResult(tempDir, 0, Duration.ZERO, new Score(100, 0, 0, 0), List.of());

        StringBuilder out = new StringBuilder();
        new JsonReporter().report(result, out);

        assertThat(validate(out.toString())).isEmpty();
        assertThat(MAPPER.readTree(out.toString()).get("diagnostics")).isEmpty();
    }

    @Test
    void report_givenMessageWithQuotesAndBackslash_roundTripsThroughJson(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("A.java");
        AnalysisResult result = new AnalysisResult(
                tempDir,
                1,
                Duration.ofMillis(5),
                new Score(90, 0, 1, 0),
                List.of(new Diagnostic(
                        new RuleId("CFG-001"),
                        new Severity.Warn(),
                        RuleCategory.SPRING,
                        new SourceLocation(file, 1, 1),
                        "bad \"value\" with \\ and\ttab",
                        "https://vitals.dev/rules/CFG-001")));

        StringBuilder out = new StringBuilder();
        new JsonReporter().report(result, out);

        assertThat(validate(out.toString())).isEmpty();
        assertThat(MAPPER.readTree(out.toString()).get("diagnostics").get(0).get("message").asText())
                .isEqualTo("bad \"value\" with \\ and\ttab");
    }

    @Test
    void report_writesReportFileUnderProjectBuildDir(@TempDir Path tempDir) throws Exception {
        AnalysisResult result =
                new AnalysisResult(tempDir, 0, Duration.ZERO, new Score(100, 0, 0, 0), List.of());

        StringBuilder out = new StringBuilder();
        new JsonReporter().report(result, out);

        Path file = tempDir.resolve("build/reports/vitals/report.json");
        assertThat(Files.exists(file)).isTrue();
        assertThat(Files.readString(file, StandardCharsets.UTF_8)).isEqualTo(out.toString().trim());
    }

    private static Set<ValidationMessage> validate(String json) throws Exception {
        JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
        try (InputStream schema = JsonReporterTest.class.getResourceAsStream("/vitals-report-v1.schema.json")) {
            JsonSchema compiled = factory.getSchema(schema);
            return compiled.validate(MAPPER.readTree(json));
        }
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :vitals-engine:test --tests 'dev.vitals.engine.JsonReporterTest'`
Expected: FAIL — compilation error, `JsonReporter` does not exist.

- [ ] **Step 3: Implement `JsonReporter`**

Create `vitals-engine/src/main/java/dev/vitals/engine/JsonReporter.java`:

```java
package dev.vitals.engine;

import dev.vitals.core.AnalysisResult;
import dev.vitals.core.Diagnostic;
import dev.vitals.core.Score;
import dev.vitals.core.Severity;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Emits the Vitals JSON report. Writes the document to the supplied sink and
 * additionally to {@code <projectRoot>/build/reports/vitals/report.json}; a
 * failed file write is logged at WARN and does not abort the run.
 */
public final class JsonReporter implements Reporter {

    private static final Logger log = LoggerFactory.getLogger(JsonReporter.class);
    private static final String SCHEMA_VERSION = "1.0";
    private static final String TOOL_VERSION = "0.1.0";

    @Override
    public void report(AnalysisResult result, Appendable out) {
        String json = render(result);
        try {
            out.append(json).append('\n');
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write JSON report", e);
        }
        writeFile(result.projectRoot(), json);
    }

    private static String render(AnalysisResult result) {
        Score score = result.score();
        StringBuilder sb = new StringBuilder(256);
        sb.append('{');
        sb.append("\"schemaVersion\":").append(JsonWriter.quote(SCHEMA_VERSION));
        sb.append(",\"tool\":{\"name\":")
                .append(JsonWriter.quote("vitals"))
                .append(",\"version\":")
                .append(JsonWriter.quote(TOOL_VERSION))
                .append('}');
        sb.append(",\"project\":{\"root\":")
                .append(JsonWriter.quote(result.projectRoot().toString()))
                .append(",\"filesAnalyzed\":")
                .append(result.filesAnalyzed())
                .append('}');
        sb.append(",\"analysis\":{\"durationMillis\":")
                .append(result.duration().toMillis())
                .append('}');
        sb.append(",\"score\":{\"value\":")
                .append(score.value())
                .append(",\"grade\":")
                .append(JsonWriter.quote(score.grade().name()))
                .append(",\"errors\":")
                .append(score.errors())
                .append(",\"warnings\":")
                .append(score.warnings())
                .append(",\"infos\":")
                .append(score.infos())
                .append('}');
        sb.append(",\"diagnostics\":[");
        List<Diagnostic> diagnostics = result.diagnostics();
        for (int i = 0; i < diagnostics.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            appendDiagnostic(sb, result.projectRoot(), diagnostics.get(i));
        }
        sb.append("]}");
        return sb.toString();
    }

    private static void appendDiagnostic(StringBuilder sb, Path projectRoot, Diagnostic d) {
        String file = projectRoot
                .relativize(d.location().filePath())
                .toString()
                .replace(File.separatorChar, '/');
        sb.append("{\"ruleId\":")
                .append(JsonWriter.quote(d.ruleId().value()))
                .append(",\"severity\":")
                .append(JsonWriter.quote(severityName(d.severity())))
                .append(",\"category\":")
                .append(JsonWriter.quote(d.category().name()))
                .append(",\"location\":{\"file\":")
                .append(JsonWriter.quote(file))
                .append(",\"line\":")
                .append(d.location().line())
                .append(",\"column\":")
                .append(d.location().column())
                .append("},\"message\":")
                .append(JsonWriter.quote(d.message()))
                .append(",\"helpUrl\":")
                .append(JsonWriter.quote(d.helpUrl()))
                .append('}');
    }

    private static String severityName(Severity severity) {
        return switch (severity) {
            case Severity.Error e -> "ERROR";
            case Severity.Warn w -> "WARN";
            case Severity.Info i -> "INFO";
        };
    }

    private static void writeFile(Path projectRoot, String json) {
        Path target = projectRoot.resolve("build/reports/vitals/report.json");
        Path parent = target.getParent();
        try {
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(target, json, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.warn("Could not write {}: {}", target, e.toString());
        }
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :vitals-engine:test --tests 'dev.vitals.engine.JsonReporterTest'`
Expected: PASS (4 tests).

- [ ] **Step 5: Run the whole engine module**

Run: `./gradlew :vitals-engine:test`
Expected: PASS — AnalysisResultTest (3), VitalsEngineTest (3), ConsoleReporterTest (4), JsonWriterTest (6), JsonReporterTest (4): 20 tests.

- [ ] **Step 6: Commit**

```bash
git add vitals-engine/src/main/java/dev/vitals/engine/JsonReporter.java \
        vitals-engine/src/test/java/dev/vitals/engine/JsonReporterTest.java
git commit -m "feat(engine): add schema-validated JsonReporter"
```

---

## Task 8: Wire `--format` into the CLI + integration test

**Files:**
- Modify: `vitals-cli/src/test/java/dev/vitals/cli/VitalsCliIntegrationTest.java`

(`VitalsCli.java` already has the `--format` option and `JsonReporter` wiring from Task 4; it compiles now that Task 7 created `JsonReporter`.)

- [ ] **Step 1: Add the JSON integration test**

In `vitals-cli/src/test/java/dev/vitals/cli/VitalsCliIntegrationTest.java`, add this method directly after `run_givenKafkaAutoCommitWithListener_reportsKafka001` (before the `private static Path copyFixture` helper):

```java
    @Test
    void run_givenJsonFormat_emitsJsonAndWritesReportFile(@TempDir Path tempDir) {
        Path project = copyFixture("jpa-001", "positive", tempDir);
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();

        int exit = new CommandLine(new VitalsCli(new PrintStream(stdout, true, StandardCharsets.UTF_8), System.err))
                .setCaseInsensitiveEnumValuesAllowed(true)
                .execute("--format", "json", project.toString());
        String output = stdout.toString(StandardCharsets.UTF_8).trim();

        assertThat(exit).isEqualTo(1);
        assertThat(output).startsWith("{").endsWith("}");
        assertThat(output).doesNotContain("Vitals 0.1.0");
        assertThat(output).contains("\"schemaVersion\":\"1.0\"");
        assertThat(output).contains("\"ruleId\":\"JPA-001\"");
        assertThat(Files.exists(project.resolve("build/reports/vitals/report.json"))).isTrue();
    }
```

All referenced imports (`ByteArrayOutputStream`, `PrintStream`, `StandardCharsets`, `Files`, `Path`, `CommandLine`, `assertThat`, `@Test`, `@TempDir`) already exist in this file.

- [ ] **Step 2: Run the CLI integration tests**

Run: `./gradlew :vitals-cli:test`
Expected: PASS — all pre-existing console tests still green (behavior preserved) plus the new JSON test.

- [ ] **Step 3: Commit**

```bash
git add vitals-cli/src/test/java/dev/vitals/cli/VitalsCliIntegrationTest.java
git commit -m "test(cli): cover --format json output and report file"
```

---

## Task 9: Full verification

**Files:** none (verification + formatting only)

- [ ] **Step 1: Apply the formatter**

Run: `./gradlew spotlessApply`
Expected: BUILD SUCCESSFUL (may reformat new files).

- [ ] **Step 2: Run the full check**

Run: `./gradlew check`
Expected: BUILD SUCCESSFUL — `spotlessCheck`, `checkstyleMain`/`checkstyleTest`, Error Prone + NullAway (via `compileJava`), all module tests, `jacocoTestCoverageVerification` (including `vitals-engine` ≥ 80%), and `verifyNoConflictCopies` all pass.

- [ ] **Step 3: Manual smoke check against a real fixture**

Run:
```bash
./gradlew :vitals-cli:run --args="--format json vitals-rules-jpa/src/test/resources/fixtures/jpa-001/positive" -q
```
Expected: a single-line JSON object on stdout starting with `{"schemaVersion":"1.0"` and containing `"ruleId":"JPA-001"`, no `Vitals 0.1.0` banner.

- [ ] **Step 4: Commit any formatting changes**

```bash
git add -A
git commit -m "style: apply spotless formatting to vitals-engine" || echo "nothing to format"
```

- [ ] **Step 5: Confirm the branch is clean**

Run: `git status -s`
Expected: empty output.

---

## Notes for the implementer

- The CLI will not compile between Task 4 and Task 7 (it references `JsonReporter` before it exists). This is intentional — Task 4's verification runs only `:vitals-engine:test`, and the CLI is first compiled/tested in Task 8. Do not "fix" this by stubbing `JsonReporter` early; follow the task order.
- Console output must remain byte-for-byte what the pre-existing `VitalsCliIntegrationTest` asserts. If any console test fails in Task 8, the `ConsoleReporter` rewrite in Task 4 Step 2 diverged from the original formatting — diff against the deleted `vitals-cli` `ConsoleReporter`.
- No AI co-author trailer in commits (project convention).
```

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
        assertThat(out.toString())
                .doesNotContain("ERROR")
                .doesNotContain("WARN")
                .doesNotContain("INFO");
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

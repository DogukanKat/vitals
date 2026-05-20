package dev.vitals.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
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
        assertThat(diag.get("location").get("file").asText()).isEqualTo("src/main/java/com/example/Order.java");
    }

    @Test
    void report_givenCleanProject_emitsEmptyDiagnosticsArray(@TempDir Path tempDir) throws Exception {
        AnalysisResult result = new AnalysisResult(tempDir, 0, Duration.ZERO, new Score(100, 0, 0, 0), List.of());

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
        assertThat(MAPPER.readTree(out.toString())
                        .get("diagnostics")
                        .get(0)
                        .get("message")
                        .asText())
                .isEqualTo("bad \"value\" with \\ and\ttab");
    }

    @Test
    void report_writesReportFileUnderProjectBuildDir(@TempDir Path tempDir) throws Exception {
        AnalysisResult result = new AnalysisResult(tempDir, 0, Duration.ZERO, new Score(100, 0, 0, 0), List.of());

        StringBuilder out = new StringBuilder();
        new JsonReporter().report(result, out);

        Path file = tempDir.resolve("build/reports/vitals/report.json");
        assertThat(Files.exists(file)).isTrue();
        assertThat(Files.readString(file, StandardCharsets.UTF_8))
                .as("file contains raw JSON; trailing newline goes to the console sink only")
                .isEqualTo(out.toString().stripTrailing());
    }

    @Test
    void report_givenFailingAppendable_wrapsIoExceptionUnchecked(@TempDir Path tempDir) {
        AnalysisResult result = new AnalysisResult(tempDir, 0, Duration.ZERO, new Score(100, 0, 0, 0), List.of());
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

        assertThatThrownBy(() -> new JsonReporter().report(result, failing))
                .isInstanceOf(UncheckedIOException.class)
                .hasMessageContaining("JSON report");
    }

    private static Set<ValidationMessage> validate(String json) throws Exception {
        JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
        try (InputStream schema = JsonReporterTest.class.getResourceAsStream("/vitals-report-v1.schema.json")) {
            assertThat(schema)
                    .as("vitals-report-v1.schema.json must be on the test classpath")
                    .isNotNull();
            JsonSchema compiled = factory.getSchema(schema);
            return compiled.validate(MAPPER.readTree(json));
        }
    }
}

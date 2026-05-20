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

    private static final Logger LOG = LoggerFactory.getLogger(JsonReporter.class);
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
        String file = projectRoot.relativize(d.location().filePath()).toString().replace(File.separatorChar, '/');
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
            LOG.warn("Could not write {}: {}", target, e.toString());
        }
    }
}

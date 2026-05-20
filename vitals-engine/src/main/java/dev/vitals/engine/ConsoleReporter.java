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
            out.append(String.format(Locale.ROOT, "  Errors:   %d", score.errors()))
                    .append('\n');
            out.append(String.format(Locale.ROOT, "  Warnings: %d", score.warnings()))
                    .append('\n');
            out.append(String.format(Locale.ROOT, "  Info:     %d", score.infos()))
                    .append('\n');
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

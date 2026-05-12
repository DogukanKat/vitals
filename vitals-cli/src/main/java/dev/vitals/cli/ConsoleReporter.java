package dev.vitals.cli;

import dev.vitals.core.Diagnostic;
import dev.vitals.core.Score;
import dev.vitals.core.Severity;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/** Plain-text reporter for the CLI. JSON and HTML reporters will be added alongside later rules. */
final class ConsoleReporter {

    private final PrintStream out;

    ConsoleReporter(PrintStream out) {
        this.out = out;
    }

    void report(Path projectRoot, int filesAnalyzed, double seconds, Score score, List<Diagnostic> diagnostics) {
        out.println("Vitals 0.1.0");
        out.printf(Locale.ROOT, "Analyzed %d files in %.1fs%n", filesAnalyzed, seconds);
        out.println();
        out.printf(Locale.ROOT, "Score: %d / 100 (%s)%n", score.value(), prettyGrade(score.grade()));
        out.printf(Locale.ROOT, "  Errors:   %d%n", score.errors());
        out.printf(Locale.ROOT, "  Warnings: %d%n", score.warnings());
        out.printf(Locale.ROOT, "  Info:     %d%n", score.infos());
        if (!diagnostics.isEmpty()) {
            out.println();
            diagnostics.forEach(d -> out.println(formatLine(projectRoot, d)));
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

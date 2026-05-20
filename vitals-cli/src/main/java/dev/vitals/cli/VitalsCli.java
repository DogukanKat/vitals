package dev.vitals.cli;

import dev.vitals.core.AnalysisResult;
import dev.vitals.engine.ConsoleReporter;
import dev.vitals.engine.JsonReporter;
import dev.vitals.engine.Reporter;
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
            description = "Output format (default: ${DEFAULT-VALUE}): ${COMPLETION-CANDIDATES}.")
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
        Reporter reporter =
                switch (format) {
                    case CONSOLE -> new ConsoleReporter();
                    case JSON -> new JsonReporter();
                };
        reporter.report(result, out);
        return result.score().errors() > 0 ? 1 : 0;
    }
}

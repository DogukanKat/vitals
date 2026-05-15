package dev.vitals.cli;

import dev.vitals.core.AnalysisContext;
import dev.vitals.core.Diagnostic;
import dev.vitals.core.Score;
import dev.vitals.core.ScoreCalculator;
import dev.vitals.core.StaticRule;
import dev.vitals.rules.jpa.Jpa001EagerFetchRule;
import dev.vitals.rules.jpa.Jpa002NPlusOneRule;
import dev.vitals.rules.jpa.Jpa003OpenInViewRule;
import dev.vitals.rules.spring.Cfg001HardcodedSecretRule;
import dev.vitals.rules.spring.Di001FieldInjectionRule;
import dev.vitals.rules.spring.Sec001ActuatorExposureRule;
import dev.vitals.rules.spring.Tx001BlockingInTransactionRule;
import dev.vitals.rules.spring.Tx002NonPublicTransactionalRule;
import dev.vitals.staticengine.JavaParserAnalysisContext;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import picocli.CommandLine;

/**
 * {@code vitals .} entry point. Scans a project, runs registered rules, prints a score + findings.
 *
 * <p>This is intentionally minimal in MVP: no config file, no rule filtering, no HTML report yet —
 * those land alongside subsequent rules.
 */
@CommandLine.Command(
        name = "vitals",
        mixinStandardHelpOptions = true,
        version = "0.1.0",
        description = "Check your Spring Boot's vitals before production does.")
public final class VitalsCli implements Callable<Integer> {

    @CommandLine.Parameters(
            index = "0",
            paramLabel = "PATH",
            description = "Path to the project to analyze.",
            defaultValue = ".")
    private Path projectPath = Path.of(".");

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
        int exitCode = new CommandLine(new VitalsCli()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() {
        Path root = projectPath.toAbsolutePath().normalize();
        if (!java.nio.file.Files.isDirectory(root)) {
            err.println("vitals: not a directory: " + root);
            return 2;
        }
        long started = System.nanoTime();
        AnalysisContext context = JavaParserAnalysisContext.discover(root);
        List<StaticRule> rules = List.of(
                new Jpa001EagerFetchRule(),
                new Jpa002NPlusOneRule(),
                new Jpa003OpenInViewRule(),
                new Tx001BlockingInTransactionRule(),
                new Tx002NonPublicTransactionalRule(),
                new Di001FieldInjectionRule(),
                new Sec001ActuatorExposureRule(),
                new Cfg001HardcodedSecretRule());

        List<Diagnostic> diagnostics = new ArrayList<>();
        for (StaticRule rule : rules) {
            diagnostics.addAll(rule.analyze(context));
        }
        Score score = ScoreCalculator.compute(diagnostics);
        double seconds = (System.nanoTime() - started) / 1_000_000_000.0;

        new ConsoleReporter(out).report(root, context.javaSources().size(), seconds, score, diagnostics);
        return score.errors() > 0 ? 1 : 0;
    }
}

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reusable analysis facade: discovers a project, runs every registered rule,
 * computes the score. Shared by the CLI and the build plugins so the analysis
 * pipeline has exactly one implementation.
 */
public final class VitalsEngine {

    private static final Logger LOG = LoggerFactory.getLogger(VitalsEngine.class);

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
        LOG.debug("Analyzing project at {}", root);
        long started = System.nanoTime();
        AnalysisContext context = JavaParserAnalysisContext.discover(root);
        LOG.debug("Discovered {} Java sources", context.javaSources().size());
        List<Diagnostic> diagnostics = new ArrayList<>();
        for (StaticRule rule : rules) {
            diagnostics.addAll(rule.analyze(context));
        }
        Score score = ScoreCalculator.compute(diagnostics);
        Duration duration = Duration.ofNanos(System.nanoTime() - started);
        LOG.debug(
                "Analysis complete: score {} from {} diagnostics in {} ms",
                score.value(),
                diagnostics.size(),
                duration.toMillis());
        return new AnalysisResult(root, context.javaSources().size(), duration, score, diagnostics);
    }
}

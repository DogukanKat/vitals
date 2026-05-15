package dev.vitals.rules.kafka;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.AnnotationExpr;
import dev.vitals.core.AnalysisContext;
import dev.vitals.core.ConfigSource;
import dev.vitals.core.ConfigValue;
import dev.vitals.core.Diagnostic;
import dev.vitals.core.RuleCategory;
import dev.vitals.core.RuleId;
import dev.vitals.core.Severity;
import dev.vitals.core.SourceLocation;
import dev.vitals.core.StaticRule;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Flags {@code enable.auto.commit=true} in a project that also processes records through a
 * {@code @KafkaListener} (the manual-processing pattern).
 *
 * <p>The rule requires <em>both</em> signals. Auto-commit alone is not necessarily wrong;
 * combined with listener-driven processing it means offsets are committed on a timer based on what
 * {@code poll()} returned, not on whether the listener finished — a crash between poll and
 * processing silently drops those records.
 *
 * <p>It only fires when the property is <em>explicitly</em> {@code true}. Spring Kafka's container
 * factory defaults {@code enable.auto.commit} to {@code false} and manages commits itself, so an
 * unset value is safe — unlike Spring Boot's open-in-view default. Recognised keys: {@code
 * spring.kafka.consumer.enable-auto-commit}, {@code spring.kafka.properties.enable.auto.commit},
 * and the raw {@code enable.auto.commit}.
 *
 * <p>Reference: <a href="https://docs.spring.io/spring-kafka/reference/kafka/committing-offsets.html">Spring
 * Kafka — Committing Offsets</a>.
 */
public final class Kafka001AutoCommitRule implements StaticRule {

    private static final RuleId ID = new RuleId("KAFKA-001");
    private static final String HELP_URL = "https://github.com/vitals-dev/vitals/blob/main/docs/rules/KAFKA-001.md";
    private static final Set<String> AUTO_COMMIT_KEYS = Set.of(
            "spring.kafka.consumer.enable-auto-commit",
            "spring.kafka.properties.enable.auto.commit",
            "enable.auto.commit");

    @Override
    public RuleId id() {
        return ID;
    }

    @Override
    public Severity defaultSeverity() {
        return new Severity.Error();
    }

    @Override
    public RuleCategory category() {
        return RuleCategory.KAFKA;
    }

    @Override
    public String shortDescription() {
        return "enable.auto.commit=true with a @KafkaListener — at-least-once processing is broken";
    }

    @Override
    public String helpUrl() {
        return HELP_URL;
    }

    @Override
    public List<Diagnostic> analyze(AnalysisContext context) {
        if (!hasKafkaListener(context)) {
            return List.of();
        }
        List<Diagnostic> findings = new ArrayList<>();
        for (ConfigSource config : context.configSources()) {
            for (String key : AUTO_COMMIT_KEYS) {
                Optional<ConfigValue> entry = config.get(key);
                if (entry.isEmpty()) {
                    continue;
                }
                if ("true".equals(entry.get().value().trim().toLowerCase(Locale.ROOT))) {
                    findings.add(new Diagnostic(
                            ID,
                            defaultSeverity(),
                            category(),
                            new SourceLocation(config.path(), entry.get().line(), 0),
                            "'" + key + "=true' with a @KafkaListener — records can be lost on crash."
                                    + " Set it to false and acknowledge after processing.",
                            HELP_URL));
                }
            }
        }
        return List.copyOf(findings);
    }

    private static boolean hasKafkaListener(AnalysisContext context) {
        for (AnalysisContext.JavaSource source : context.javaSources()) {
            if (source.compilationUnit() instanceof CompilationUnit unit
                    && unit.findAll(AnnotationExpr.class).stream()
                            .map(AnnotationExpr::getNameAsString)
                            .anyMatch(name -> name.equals("KafkaListener") || name.endsWith(".KafkaListener"))) {
                return true;
            }
        }
        return false;
    }
}

package dev.vitals.rules.jpa;

import dev.vitals.core.AnalysisContext;
import dev.vitals.core.ConfigSource;
import dev.vitals.core.ConfigValue;
import dev.vitals.core.Diagnostic;
import dev.vitals.core.RuleCategory;
import dev.vitals.core.RuleId;
import dev.vitals.core.Severity;
import dev.vitals.core.SourceLocation;
import dev.vitals.core.StaticRule;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Flags Spring Boot's {@code spring.jpa.open-in-view} when it is either explicitly enabled or left
 * unset (the framework default is {@code true}).
 *
 * <p>OSIV keeps a Hibernate {@code Session} open for the duration of the HTTP request so that lazy
 * associations resolve in the view layer. The cost is that every request — even those that no
 * longer need the database — holds a connection until the response is fully rendered. Under load
 * the HikariCP pool starves and the application starts returning 503s.
 *
 * <p>The rule fires once per project, attached to the primary config file. If any discovered
 * config file sets the property to {@code false} the rule remains silent: the developer has made
 * the decision explicitly.
 *
 * <p>Reference: Vlad Mihalcea — <a
 * href="https://vladmihalcea.com/the-open-session-in-view-anti-pattern/">"The
 * Open Session In View anti-pattern"</a>.
 */
public final class Jpa003OpenInViewRule implements StaticRule {

    private static final RuleId ID = new RuleId("JPA-003");
    private static final String HELP_URL = "https://github.com/vitals-dev/vitals/blob/main/docs/rules/JPA-003.md";
    private static final String KEY = "spring.jpa.open-in-view";

    @Override
    public RuleId id() {
        return ID;
    }

    @Override
    public Severity defaultSeverity() {
        return new Severity.Warn();
    }

    @Override
    public RuleCategory category() {
        return RuleCategory.JPA;
    }

    @Override
    public String shortDescription() {
        return "spring.jpa.open-in-view is enabled or unset (Spring Boot defaults to true)";
    }

    @Override
    public String helpUrl() {
        return HELP_URL;
    }

    @Override
    public List<Diagnostic> analyze(AnalysisContext context) {
        List<ConfigSource> configs = context.configSources();
        if (configs.isEmpty()) {
            return List.of();
        }

        Optional<Match> explicitTrue = Optional.empty();
        for (ConfigSource config : configs) {
            Optional<ConfigValue> entry = config.get(KEY);
            if (entry.isEmpty()) {
                continue;
            }
            String normalized = entry.get().value().trim().toLowerCase(Locale.ROOT);
            if ("false".equals(normalized)) {
                return List.of();
            }
            if ("true".equals(normalized) && explicitTrue.isEmpty()) {
                explicitTrue = Optional.of(new Match(config, entry.get()));
            }
        }

        if (explicitTrue.isPresent()) {
            Match m = explicitTrue.get();
            return List.of(new Diagnostic(
                    ID,
                    defaultSeverity(),
                    category(),
                    new SourceLocation(m.source().path(), m.value().line(), 0),
                    "spring.jpa.open-in-view is enabled — disable it to avoid connection pool starvation.",
                    HELP_URL));
        }

        ConfigSource primary = pickPrimary(configs);
        return List.of(new Diagnostic(
                ID,
                defaultSeverity(),
                category(),
                new SourceLocation(primary.path(), 1, 0),
                "spring.jpa.open-in-view is unset; Spring Boot defaults to true. Set it to false explicitly.",
                HELP_URL));
    }

    private record Match(ConfigSource source, ConfigValue value) {}

    private static ConfigSource pickPrimary(List<ConfigSource> configs) {
        return configs.stream()
                .filter(c -> rank(c) >= 0)
                .min((a, b) -> Integer.compare(rank(a), rank(b)))
                .orElse(configs.get(0));
    }

    private static int rank(ConfigSource config) {
        String name = config.path().getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.equals("application.yml") || name.equals("application.yaml")) {
            return 0;
        }
        if (name.equals("application.properties")) {
            return 1;
        }
        if (name.startsWith("application")) {
            return 2;
        }
        if (name.startsWith("bootstrap")) {
            return 3;
        }
        return Integer.MAX_VALUE;
    }
}

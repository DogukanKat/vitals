package dev.vitals.rules.spring;

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
import java.util.Set;

/**
 * Flags Spring Boot Actuator endpoints that are exposed over HTTP and read sensitive information
 * (or expose write/control actions). Configured by {@code
 * management.endpoints.web.exposure.include}.
 *
 * <p>Two patterns fire the rule:
 *
 * <ul>
 *   <li>the wildcard {@code *} — every Actuator endpoint, including {@code heapdump} and {@code
 *       env}, becomes reachable over HTTP;
 *   <li>any explicit reference to {@code heapdump}, {@code shutdown}, {@code env}, {@code
 *       configprops}, {@code threaddump}, {@code httptrace}, or {@code httpexchanges}.
 * </ul>
 *
 * <p>The rule cannot tell from configuration alone whether a Spring Security filter chain protects
 * these endpoints — it always fires, and the diagnostic message asks the operator to verify the
 * auth path. The {@code exclude} property is not consulted in this revision; if you exclude the
 * dangerous endpoints, suppress the rule and document it in your config.
 *
 * <p>Reference: Spring Boot reference — <a
 * href="https://docs.spring.io/spring-boot/reference/actuator/endpoints.html">Actuator endpoints</a>.
 */
public final class Sec001ActuatorExposureRule implements StaticRule {

    private static final RuleId ID = new RuleId("SEC-001");
    private static final String HELP_URL =
            "https://github.com/vitals-dev/vitals/blob/main/docs/rules/SEC-001.md";
    private static final String KEY = "management.endpoints.web.exposure.include";
    private static final Set<String> SENSITIVE_ENDPOINTS = Set.of(
            "heapdump", "shutdown", "env", "configprops", "threaddump", "httptrace", "httpexchanges");

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
        return RuleCategory.SECURITY;
    }

    @Override
    public String shortDescription() {
        return "Sensitive Actuator endpoint exposed over HTTP (management.endpoints.web.exposure.include)";
    }

    @Override
    public String helpUrl() {
        return HELP_URL;
    }

    @Override
    public List<Diagnostic> analyze(AnalysisContext context) {
        List<Diagnostic> findings = new ArrayList<>();
        for (ConfigSource config : context.configSources()) {
            for (ConfigValue entry : config.getList(KEY)) {
                String normalized = entry.value().trim().toLowerCase(Locale.ROOT);
                if (normalized.isEmpty()) {
                    continue;
                }
                if ("*".equals(normalized)) {
                    findings.add(diagnostic(config, entry, "wildcard '*' exposes every Actuator endpoint"
                            + " — restrict to a named allowlist (health, info, …)."));
                } else if (SENSITIVE_ENDPOINTS.contains(normalized)) {
                    findings.add(diagnostic(config, entry, "Actuator endpoint '" + normalized
                            + "' is exposed over HTTP — confirm it sits behind authenticated routes."));
                }
            }
        }
        return List.copyOf(findings);
    }

    private Diagnostic diagnostic(ConfigSource config, ConfigValue entry, String message) {
        return new Diagnostic(
                ID,
                defaultSeverity(),
                category(),
                new SourceLocation(config.path(), entry.line(), 0),
                message,
                HELP_URL);
    }
}

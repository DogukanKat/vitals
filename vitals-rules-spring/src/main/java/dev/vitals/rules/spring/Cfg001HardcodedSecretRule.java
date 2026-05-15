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
import java.util.Map;
import java.util.Set;

/**
 * Flags configuration keys that look like credentials whose value is a hardcoded literal rather
 * than a Spring property placeholder of the dollar-brace form.
 *
 * <p>The key's final dot-segment is matched against a curated set of secret names (and the
 * suffixes {@code password} / {@code secret}, which catch {@code key-store-password} and
 * {@code client-secret}-style keys). A value is considered a hardcoded secret when it is
 * non-blank, is not a property placeholder, and is not one of the obvious non-secret literals
 * ({@code true}, {@code false}, {@code none}, …).
 *
 * <p>Reference: Spring Boot reference — <a
 * href="https://docs.spring.io/spring-boot/reference/features/external-config.html">Externalized
 * Configuration</a>: secrets belong in environment variables or a vault, never in a checked-in
 * config file.
 */
public final class Cfg001HardcodedSecretRule implements StaticRule {

    private static final RuleId ID = new RuleId("CFG-001");
    private static final String HELP_URL = "https://github.com/vitals-dev/vitals/blob/main/docs/rules/CFG-001.md";
    private static final Set<String> SECRET_SEGMENTS = Set.of(
            "password",
            "passwd",
            "secret",
            "apikey",
            "api-key",
            "token",
            "access-token",
            "refresh-token",
            "private-key",
            "privatekey",
            "secret-key",
            "secretkey",
            "access-key",
            "accesskey",
            "client-secret",
            "credential",
            "credentials");
    private static final Set<String> NON_SECRET_LITERALS =
            Set.of("true", "false", "none", "null", "disabled", "enabled", "default");

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
        return "Hardcoded credential in a config file — use a ${PLACEHOLDER} instead";
    }

    @Override
    public String helpUrl() {
        return HELP_URL;
    }

    @Override
    public List<Diagnostic> analyze(AnalysisContext context) {
        List<Diagnostic> findings = new ArrayList<>();
        for (ConfigSource config : context.configSources()) {
            for (Map.Entry<String, ConfigValue> entry : config.entries().entrySet()) {
                if (isSecretKey(entry.getKey()) && isHardcoded(entry.getValue().value())) {
                    findings.add(new Diagnostic(
                            ID,
                            defaultSeverity(),
                            category(),
                            new SourceLocation(config.path(), entry.getValue().line(), 0),
                            "Hardcoded secret at '" + entry.getKey()
                                    + "' — move it to an environment variable and reference it as ${...}.",
                            HELP_URL));
                }
            }
        }
        return List.copyOf(findings);
    }

    private static boolean isSecretKey(String dotKey) {
        int lastDot = dotKey.lastIndexOf('.');
        String segment = (lastDot < 0 ? dotKey : dotKey.substring(lastDot + 1)).toLowerCase(Locale.ROOT);
        // Strip an indexed suffix like "[0]" so list elements are still matched.
        int bracket = segment.indexOf('[');
        if (bracket >= 0) {
            segment = segment.substring(0, bracket);
        }
        return SECRET_SEGMENTS.contains(segment) || segment.endsWith("password") || segment.endsWith("secret");
    }

    private static boolean isHardcoded(String value) {
        String trimmed = value.trim();
        if (trimmed.isEmpty() || trimmed.startsWith("${")) {
            return false;
        }
        return !NON_SECRET_LITERALS.contains(trimmed.toLowerCase(Locale.ROOT));
    }
}

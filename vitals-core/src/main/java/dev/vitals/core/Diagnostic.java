package dev.vitals.core;

/**
 * A single rule violation produced by a {@link Rule}.
 *
 * <p>Diagnostics are immutable value objects. Reporters render them; they never mutate after
 * construction.
 *
 * @param ruleId   identifier of the rule that produced this diagnostic
 * @param severity finding severity (defaulted from the rule, optionally overridden by config)
 * @param category top-level grouping
 * @param location where in source the finding was detected
 * @param message  human-readable, single-sentence summary suitable for CLI output
 * @param helpUrl  link to documentation (typically {@code docs/rules/<RULE-ID>.md})
 */
public record Diagnostic(
        RuleId ruleId,
        Severity severity,
        RuleCategory category,
        SourceLocation location,
        String message,
        String helpUrl) {

    public Diagnostic {
        if (ruleId == null) {
            throw new IllegalArgumentException("ruleId must not be null");
        }
        if (severity == null) {
            throw new IllegalArgumentException("severity must not be null");
        }
        if (category == null) {
            throw new IllegalArgumentException("category must not be null");
        }
        if (location == null) {
            throw new IllegalArgumentException("location must not be null");
        }
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("message must not be blank");
        }
        if (helpUrl == null || helpUrl.isBlank()) {
            throw new IllegalArgumentException("helpUrl must not be blank");
        }
    }
}

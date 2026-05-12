package dev.vitals.core;

/**
 * Metadata contract every Vitals rule implements.
 *
 * <p>Splitting metadata ({@code Rule}) from execution ({@link StaticRule}) lets the future runtime
 * probe expose the same identity without dragging static-analysis machinery onto a Spring app's
 * classpath.
 */
public interface Rule {

    /** Stable identifier, e.g. {@code JPA-001}. */
    RuleId id();

    /** Severity assigned when no user override is configured. */
    Severity defaultSeverity();

    /** Top-level grouping for reporting and filtering. */
    RuleCategory category();

    /** One-line summary shown in CLI output and the rule catalog. */
    String shortDescription();

    /** URL to the rule's documentation page. */
    String helpUrl();
}

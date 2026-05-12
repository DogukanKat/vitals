package dev.vitals.core;

/** Top-level grouping for a {@link Rule}, used for reporting and CLI filtering. */
public enum RuleCategory {
    JPA,
    SPRING,
    KAFKA,
    REDIS,
    JVM,
    SECURITY,
    OBSERVABILITY
}

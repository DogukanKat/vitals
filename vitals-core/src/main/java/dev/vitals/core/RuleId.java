package dev.vitals.core;

import java.util.regex.Pattern;

/**
 * Identifier of a {@link Rule}, e.g. {@code JPA-001}.
 *
 * <p>The format is enforced at construction time so an invalid identifier can never escape into
 * reports. Use a stable two-or-more letter prefix followed by a three-digit sequence number.
 */
public record RuleId(String value) {

    private static final Pattern PATTERN = Pattern.compile("[A-Z]+-\\d{3}");

    /** @throws IllegalArgumentException if {@code value} does not match {@code [A-Z]+-NNN}. */
    public RuleId {
        if (value == null || !PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("RuleId must match PATTERN-NNN: " + value);
        }
    }

    @Override
    public String toString() {
        return value;
    }
}

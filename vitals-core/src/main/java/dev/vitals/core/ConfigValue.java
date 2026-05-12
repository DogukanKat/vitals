package dev.vitals.core;

/**
 * A single configuration entry resolved from a {@link ConfigSource}, paired with its source line.
 *
 * @param value raw string value as written in the file (never {@code null}, may be empty)
 * @param line  1-based line number of the entry, or {@code 0} if not available
 */
public record ConfigValue(String value, int line) {

    public ConfigValue {
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
    }
}

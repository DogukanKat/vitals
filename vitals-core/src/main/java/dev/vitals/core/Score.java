package dev.vitals.core;

/**
 * Final 0–100 health score plus diagnostic counts.
 *
 * <p>Buckets map to {@link Grade} for human-friendly output: GREAT (≥75), NEEDS_WORK (≥50),
 * CRITICAL (&lt;50). See {@link ScoreCalculator} for the weighting formula.
 *
 * @param value    score in the closed range {@code [0, 100]}
 * @param errors   count of {@link Severity.Error} diagnostics
 * @param warnings count of {@link Severity.Warn} diagnostics
 * @param infos    count of {@link Severity.Info} diagnostics
 */
public record Score(int value, int errors, int warnings, int infos) {

    public Score {
        if (value < 0 || value > 100) {
            throw new IllegalArgumentException("Score out of range: " + value);
        }
        if (errors < 0 || warnings < 0 || infos < 0) {
            throw new IllegalArgumentException("Counts must be non-negative");
        }
    }

    /** Returns the qualitative bucket this score falls into. */
    public Grade grade() {
        if (value >= 75) {
            return Grade.GREAT;
        }
        if (value >= 50) {
            return Grade.NEEDS_WORK;
        }
        return Grade.CRITICAL;
    }

    /** Coarse classification surfaced in CLI output and badges. */
    public enum Grade {
        GREAT,
        NEEDS_WORK,
        CRITICAL
    }
}

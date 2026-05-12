package dev.vitals.core;

import java.util.List;

/**
 * Aggregates {@link Diagnostic} severities into a 0–100 {@link Score}.
 *
 * <p>Formula: start at 100, subtract {@link Severity#weight()} per diagnostic, clamp to {@code 0}.
 * It is intentionally simple — a generous Error costs 5 points, a Warn 2, an Info 1. This is the
 * <em>v1</em> formula; future versions may introduce category multipliers but must keep the public
 * range stable.
 */
public final class ScoreCalculator {

    private ScoreCalculator() {}

    /**
     * Compute a {@link Score} from a list of diagnostics.
     *
     * @param diagnostics findings to aggregate (must not be {@code null}; empty yields a perfect
     *     score of {@code 100})
     * @return immutable score, never {@code null}
     */
    public static Score compute(List<Diagnostic> diagnostics) {
        if (diagnostics == null) {
            throw new IllegalArgumentException("diagnostics must not be null");
        }

        int errors = 0;
        int warnings = 0;
        int infos = 0;
        int penalty = 0;

        for (Diagnostic d : diagnostics) {
            penalty += d.severity().weight();
            switch (d.severity()) {
                case Severity.Error e -> errors++;
                case Severity.Warn w -> warnings++;
                case Severity.Info i -> infos++;
            }
        }

        int value = Math.max(0, 100 - penalty);
        return new Score(value, errors, warnings, infos);
    }
}

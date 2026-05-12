package dev.vitals.core;

/**
 * Severity of a {@link Diagnostic}.
 *
 * <p>Modelled as a sealed interface so the three variants behave as records and can be exhaustively
 * pattern-matched. The {@link #weight()} value feeds {@link ScoreCalculator}: a higher weight
 * subtracts more from the final 0–100 score.
 */
public sealed interface Severity permits Severity.Error, Severity.Warn, Severity.Info {

    /** Penalty applied per diagnostic of this severity when computing a {@link Score}. */
    int weight();

    /** Action required — rule violation that should block a release. */
    record Error() implements Severity {
        @Override
        public int weight() {
            return 5;
        }
    }

    /** Degraded but functional — should be fixed but not necessarily blocking. */
    record Warn() implements Severity {
        @Override
        public int weight() {
            return 2;
        }
    }

    /** Informational signal — visibility only. */
    record Info() implements Severity {
        @Override
        public int weight() {
            return 1;
        }
    }
}

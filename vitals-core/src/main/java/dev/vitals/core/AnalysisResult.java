package dev.vitals.core;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/**
 * Immutable result of one analysis run: the inputs that were scanned plus the
 * computed {@link Score} and the {@link Diagnostic}s that produced it.
 *
 * @param projectRoot   absolute root of the analyzed project
 * @param filesAnalyzed number of Java sources scanned
 * @param duration      wall-clock time the analysis took
 * @param score         computed 0-100 health score
 * @param diagnostics   findings, in the order rules produced them; copied defensively, must not contain null elements
 */
public record AnalysisResult(
        Path projectRoot, int filesAnalyzed, Duration duration, Score score, List<Diagnostic> diagnostics) {

    /**
     * @throws IllegalArgumentException if {@code projectRoot}, {@code duration}, or {@code score}
     *     is null; or if {@code filesAnalyzed} is negative; or if {@code duration} is negative
     * @throws NullPointerException if {@code diagnostics} is null or contains a null element
     */
    public AnalysisResult {
        if (projectRoot == null) {
            throw new IllegalArgumentException("projectRoot must not be null");
        }
        if (filesAnalyzed < 0) {
            throw new IllegalArgumentException("filesAnalyzed must be non-negative");
        }
        if (duration == null) {
            throw new IllegalArgumentException("duration must not be null");
        }
        if (duration.isNegative()) {
            throw new IllegalArgumentException("duration must be non-negative");
        }
        if (score == null) {
            throw new IllegalArgumentException("score must not be null");
        }
        diagnostics = List.copyOf(diagnostics);
    }
}

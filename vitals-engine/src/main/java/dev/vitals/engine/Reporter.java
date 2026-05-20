package dev.vitals.engine;

import dev.vitals.core.AnalysisResult;

/** Renders an {@link AnalysisResult} to a destination (console, JSON, ...). */
public interface Reporter {

    /**
     * Writes the report for {@code result} to {@code out}.
     *
     * @param result the analysis to render
     * @param out    sink for the primary (stdout) representation
     */
    void report(AnalysisResult result, Appendable out);
}

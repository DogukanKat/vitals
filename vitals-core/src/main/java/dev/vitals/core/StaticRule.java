package dev.vitals.core;

import java.util.List;

/**
 * A {@link Rule} that operates against an {@link AnalysisContext} produced by the static engine.
 *
 * <p>Implementations must be pure functions of their inputs: no I/O, no global state, no mutation
 * of the context. The same context fed to {@link #analyze(AnalysisContext)} twice must produce
 * equal diagnostic lists.
 */
public interface StaticRule extends Rule {

    /**
     * Run the rule and return any diagnostics found.
     *
     * @param context analysis context for the project being scanned, never {@code null}
     * @return immutable list of diagnostics (possibly empty); never {@code null}
     */
    List<Diagnostic> analyze(AnalysisContext context);
}

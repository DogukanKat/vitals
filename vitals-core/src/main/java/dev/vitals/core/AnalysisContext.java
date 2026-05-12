package dev.vitals.core;

import java.nio.file.Path;
import java.util.List;

/**
 * Input handed to a {@link StaticRule#analyze(AnalysisContext)} invocation.
 *
 * <p>This is a minimal abstraction in MVP: the JavaParser/ByteBuddy-backed implementation lives in
 * {@code vitals-static-engine}. Rules consume opaque {@link JavaSource} handles and read primitive
 * source content; the engine is responsible for parsing.
 */
public interface AnalysisContext {

    /** Root directory of the project being analyzed. */
    Path projectRoot();

    /** All Java sources discovered under {@link #projectRoot()}, in stable order. */
    List<JavaSource> javaSources();

    /** A discovered Java source file, paired with its parsed compilation unit handle. */
    interface JavaSource {

        /** Path to the file, typically under {@code src/main/java}. */
        Path path();

        /**
         * Opaque parsed handle. The static engine module knows the concrete type; rule modules
         * down-cast through a visitor helper. Kept as {@code Object} here so {@code vitals-core}
         * stays free of JavaParser types.
         */
        Object compilationUnit();
    }
}

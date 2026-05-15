package dev.vitals.core;

import java.nio.file.Path;
import java.util.List;

/**
 * A discovered Dockerfile, exposed as its raw lines.
 *
 * <p>Dockerfiles are line-oriented and do not warrant a structured parser for the checks Vitals
 * performs; rules scan {@link #lines()} directly. Line numbers in diagnostics are 1-based indexes
 * into this list.
 *
 * @param path  path to the file
 * @param lines file content, one entry per physical line, in order
 */
public record Dockerfile(Path path, List<String> lines) {

    public Dockerfile {
        if (path == null) {
            throw new IllegalArgumentException("path must not be null");
        }
        lines = List.copyOf(lines);
    }
}

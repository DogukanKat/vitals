package dev.vitals.core;

import java.nio.file.Path;

/**
 * Pointer to a source position attached to a {@link Diagnostic}.
 *
 * <p>Lines and columns are 1-based to match common editor and compiler conventions; a zero or
 * negative value indicates "unknown" and should be tolerated by reporters.
 *
 * @param filePath absolute or project-relative path to the offending file
 * @param line     1-based line number, or 0 if not applicable
 * @param column   1-based column number, or 0 if not applicable
 */
public record SourceLocation(Path filePath, int line, int column) {

    public SourceLocation {
        if (filePath == null) {
            throw new IllegalArgumentException("filePath must not be null");
        }
    }
}

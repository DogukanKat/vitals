/**
 * Core domain types for Vitals: rules, diagnostics, severity, scoring.
 *
 * <p>This package has no Spring, JavaParser, or ByteBuddy dependency by design. Adapters live in
 * {@code vitals-static-engine}; rule implementations live in {@code vitals-rules-*} modules.
 */
@NullMarked
package dev.vitals.core;

import org.jspecify.annotations.NullMarked;

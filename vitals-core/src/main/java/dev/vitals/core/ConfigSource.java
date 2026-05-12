package dev.vitals.core;

import java.nio.file.Path;
import java.util.Optional;

/**
 * A Spring-style configuration file discovered in the analyzed project.
 *
 * <p>Implementations live in {@code vitals-static-engine}: one for {@code application*.properties}
 * and one for {@code application*.yml} / {@code .yaml} / {@code bootstrap*.{yml,yaml}}. Rules use
 * the dotted-key lookup; the underlying parsing model stays an implementation detail.
 */
public interface ConfigSource {

    /** Path to the configuration file. */
    Path path();

    /**
     * Lookup by dot-separated key (e.g. {@code spring.jpa.open-in-view}).
     *
     * @param dotKey fully qualified property name
     * @return the value plus source line, or empty when the key is not set
     */
    Optional<ConfigValue> get(String dotKey);
}

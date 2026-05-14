package dev.vitals.core;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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

    /**
     * Multi-valued lookup that handles both Spring representations of a list:
     * <ul>
     *   <li>a comma-separated scalar, e.g. {@code management.endpoints.web.exposure.include=*};
     *   <li>a YAML list flattened to indexed keys ({@code key[0]}, {@code key[1]}, …).
     * </ul>
     * Returns an empty list when neither form is present.
     */
    default List<ConfigValue> getList(String dotKey) {
        Optional<ConfigValue> scalar = get(dotKey);
        if (scalar.isPresent()) {
            int line = scalar.get().line();
            return Arrays.stream(scalar.get().value().split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(s -> new ConfigValue(s, line))
                    .toList();
        }
        List<ConfigValue> indexed = new ArrayList<>();
        for (int i = 0; ; i++) {
            Optional<ConfigValue> entry = get(dotKey + "[" + i + "]");
            if (entry.isEmpty()) {
                break;
            }
            indexed.add(entry.get());
        }
        return List.copyOf(indexed);
    }
}

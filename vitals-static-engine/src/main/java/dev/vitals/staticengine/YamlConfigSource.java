package dev.vitals.staticengine;

import dev.vitals.core.ConfigSource;
import dev.vitals.core.ConfigValue;
import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.SequenceNode;

/**
 * {@link ConfigSource} backed by a SnakeYAML parse of a {@code .yml} / {@code .yaml} file.
 *
 * <p>Hierarchical keys are flattened into dotted form ({@code spring.jpa.open-in-view}). Sequence
 * indices are encoded as {@code key[0]}, matching Spring Boot's binder. Multi-document YAML files
 * are merged into a single flat view (later documents win on key collision) — sufficient for the
 * "is this property set anywhere?" question rules ask.
 */
public final class YamlConfigSource implements ConfigSource {

    private static final Logger LOG = LoggerFactory.getLogger(YamlConfigSource.class);

    private final Path path;
    private final Map<String, ConfigValue> values;

    private YamlConfigSource(Path path, Map<String, ConfigValue> values) {
        this.path = path;
        this.values = Map.copyOf(values);
    }

    /** Read and parse the given YAML file. */
    public static YamlConfigSource load(Path path) {
        Map<String, ConfigValue> values = new HashMap<>();
        try (Reader reader = Files.newBufferedReader(path)) {
            Yaml yaml = new Yaml();
            for (Node doc : yaml.composeAll(reader)) {
                flatten(doc, "", values);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read " + path, e);
        } catch (RuntimeException e) {
            LOG.warn("Skipping malformed YAML at {}: {}", path, e.getMessage());
        }
        return new YamlConfigSource(path, values);
    }

    private static void flatten(Node node, String prefix, Map<String, ConfigValue> out) {
        switch (node) {
            case MappingNode mapping -> {
                for (NodeTuple tuple : mapping.getValue()) {
                    String key = scalarText(tuple.getKeyNode());
                    String fullKey = prefix.isEmpty() ? key : prefix + "." + key;
                    flatten(tuple.getValueNode(), fullKey, out);
                }
            }
            case SequenceNode sequence -> {
                int i = 0;
                for (Node child : sequence.getValue()) {
                    flatten(child, prefix + "[" + i + "]", out);
                    i++;
                }
            }
            case ScalarNode scalar -> {
                int line = scalar.getStartMark() != null ? scalar.getStartMark().getLine() + 1 : 0;
                out.put(prefix, new ConfigValue(scalar.getValue(), line));
            }
            default -> {
                // Anchor/alias nodes are dereferenced by SnakeYAML before reaching here.
            }
        }
    }

    private static String scalarText(Node keyNode) {
        return keyNode instanceof ScalarNode scalar ? scalar.getValue() : keyNode.toString();
    }

    @Override
    public Path path() {
        return path;
    }

    @Override
    public Optional<ConfigValue> get(String dotKey) {
        return Optional.ofNullable(values.get(dotKey));
    }

    @Override
    public java.util.Map<String, ConfigValue> entries() {
        return values;
    }
}

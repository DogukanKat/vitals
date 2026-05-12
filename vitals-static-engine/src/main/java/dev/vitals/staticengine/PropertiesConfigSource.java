package dev.vitals.staticengine;

import dev.vitals.core.ConfigSource;
import dev.vitals.core.ConfigValue;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** {@link ConfigSource} backed by a Java {@code .properties} file. */
public final class PropertiesConfigSource implements ConfigSource {

    private final Path path;
    private final Map<String, ConfigValue> values;

    private PropertiesConfigSource(Path path, Map<String, ConfigValue> values) {
        this.path = path;
        this.values = Map.copyOf(values);
    }

    /** Read and parse the given properties file. */
    public static PropertiesConfigSource load(Path path) {
        Map<String, ConfigValue> values = new HashMap<>();
        try {
            List<String> lines = Files.readAllLines(path);
            for (int i = 0; i < lines.size(); i++) {
                String raw = lines.get(i).strip();
                if (raw.isEmpty() || raw.startsWith("#") || raw.startsWith("!")) {
                    continue;
                }
                int sep = firstSeparator(raw);
                if (sep < 0) {
                    continue;
                }
                String key = raw.substring(0, sep).strip();
                String value = raw.substring(sep + 1).strip();
                values.put(key, new ConfigValue(value, i + 1));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read " + path, e);
        }
        return new PropertiesConfigSource(path, values);
    }

    private static int firstSeparator(String line) {
        int eq = line.indexOf('=');
        int colon = line.indexOf(':');
        if (eq < 0) {
            return colon;
        }
        if (colon < 0) {
            return eq;
        }
        return Math.min(eq, colon);
    }

    @Override
    public Path path() {
        return path;
    }

    @Override
    public Optional<ConfigValue> get(String dotKey) {
        return Optional.ofNullable(values.get(dotKey));
    }
}

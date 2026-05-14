package dev.vitals.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ConfigSourceTest {

    @Test
    void getList_givenCommaSeparatedScalar_splitsAndTrims() {
        ConfigSource source = inMemory(Map.of("a.b", new ConfigValue("x , y,z ", 4)));
        assertThat(source.getList("a.b")).extracting(ConfigValue::value).containsExactly("x", "y", "z");
        assertThat(source.getList("a.b")).allSatisfy(v -> assertThat(v.line()).isEqualTo(4));
    }

    @Test
    void getList_givenIndexedEntries_collectsUntilGap() {
        ConfigSource source = inMemory(Map.of(
                "a.b[0]", new ConfigValue("first", 1),
                "a.b[1]", new ConfigValue("second", 2)));
        assertThat(source.getList("a.b")).extracting(ConfigValue::value).containsExactly("first", "second");
    }

    @Test
    void getList_givenUnset_returnsEmpty() {
        assertThat(inMemory(Map.of()).getList("a.b")).isEmpty();
    }

    private static ConfigSource inMemory(Map<String, ConfigValue> values) {
        return new ConfigSource() {
            @Override
            public Path path() {
                return Path.of("test.yml");
            }

            @Override
            public Optional<ConfigValue> get(String dotKey) {
                return Optional.ofNullable(values.get(dotKey));
            }
        };
    }
}

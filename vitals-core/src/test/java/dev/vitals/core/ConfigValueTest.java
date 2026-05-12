package dev.vitals.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ConfigValueTest {

    @Test
    void construct_givenValueAndLine_storesBoth() {
        ConfigValue v = new ConfigValue("true", 14);
        assertThat(v.value()).isEqualTo("true");
        assertThat(v.line()).isEqualTo(14);
    }

    @Test
    void construct_givenEmptyValue_succeeds() {
        assertThat(new ConfigValue("", 0).value()).isEmpty();
    }

    @Test
    void construct_givenNullValue_throws() {
        assertThatThrownBy(() -> new ConfigValue(null, 1)).isInstanceOf(IllegalArgumentException.class);
    }
}

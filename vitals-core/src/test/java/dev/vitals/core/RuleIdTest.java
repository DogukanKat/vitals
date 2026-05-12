package dev.vitals.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class RuleIdTest {

    @Test
    void construct_givenValidFormat_succeeds() {
        assertThat(new RuleId("JPA-001").value()).isEqualTo("JPA-001");
        assertThat(new RuleId("KAFKA-042").value()).isEqualTo("KAFKA-042");
    }

    @Test
    void construct_givenLowerCasePrefix_throws() {
        assertThatThrownBy(() -> new RuleId("jpa-001")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void construct_givenTwoDigitNumber_throws() {
        assertThatThrownBy(() -> new RuleId("JPA-01")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void construct_givenNull_throws() {
        assertThatThrownBy(() -> new RuleId(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void toString_returnsValue() {
        assertThat(new RuleId("JPA-001")).hasToString("JPA-001");
    }
}

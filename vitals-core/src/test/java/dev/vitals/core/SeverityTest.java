package dev.vitals.core;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SeverityTest {

    @Test
    void weight_givenError_isFive() {
        assertThat(new Severity.Error().weight()).isEqualTo(5);
    }

    @Test
    void weight_givenWarn_isTwo() {
        assertThat(new Severity.Warn().weight()).isEqualTo(2);
    }

    @Test
    void weight_givenInfo_isOne() {
        assertThat(new Severity.Info().weight()).isEqualTo(1);
    }

    @Test
    void severities_areEqualByValue() {
        assertThat(new Severity.Error()).isEqualTo(new Severity.Error());
        assertThat(new Severity.Warn()).isEqualTo(new Severity.Warn());
        assertThat(new Severity.Info()).isEqualTo(new Severity.Info());
    }
}

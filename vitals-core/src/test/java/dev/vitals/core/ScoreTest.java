package dev.vitals.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ScoreTest {

    @Test
    void grade_atHundred_isGreat() {
        assertThat(new Score(100, 0, 0, 0).grade()).isEqualTo(Score.Grade.GREAT);
    }

    @Test
    void grade_at75_isGreat() {
        assertThat(new Score(75, 0, 0, 0).grade()).isEqualTo(Score.Grade.GREAT);
    }

    @Test
    void grade_at74_isNeedsWork() {
        assertThat(new Score(74, 0, 0, 0).grade()).isEqualTo(Score.Grade.NEEDS_WORK);
    }

    @Test
    void grade_at49_isCritical() {
        assertThat(new Score(49, 0, 0, 0).grade()).isEqualTo(Score.Grade.CRITICAL);
    }

    @Test
    void construct_outOfRange_throws() {
        assertThatThrownBy(() -> new Score(-1, 0, 0, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Score(101, 0, 0, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void construct_negativeCount_throws() {
        assertThatThrownBy(() -> new Score(50, -1, 0, 0)).isInstanceOf(IllegalArgumentException.class);
    }
}

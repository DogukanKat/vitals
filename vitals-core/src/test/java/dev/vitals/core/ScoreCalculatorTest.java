package dev.vitals.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScoreCalculatorTest {

    private static final RuleId ID = new RuleId("JPA-001");
    private static final SourceLocation LOC = new SourceLocation(Path.of("Foo.java"), 1, 1);

    @Test
    void compute_givenEmpty_isPerfect() {
        Score score = ScoreCalculator.compute(List.of());
        assertThat(score.value()).isEqualTo(100);
        assertThat(score.errors()).isZero();
        assertThat(score.warnings()).isZero();
        assertThat(score.infos()).isZero();
    }

    @Test
    void compute_givenMixedSeverities_subtractsWeights() {
        List<Diagnostic> diagnostics =
                List.of(diag(new Severity.Error()), diag(new Severity.Warn()), diag(new Severity.Info()));
        Score score = ScoreCalculator.compute(diagnostics);
        assertThat(score.value()).isEqualTo(100 - 5 - 2 - 1);
        assertThat(score.errors()).isEqualTo(1);
        assertThat(score.warnings()).isEqualTo(1);
        assertThat(score.infos()).isEqualTo(1);
    }

    @Test
    void compute_givenManyErrors_clampsAtZero() {
        List<Diagnostic> many = java.util.stream.IntStream.range(0, 50)
                .mapToObj(i -> diag(new Severity.Error()))
                .toList();
        Score score = ScoreCalculator.compute(many);
        assertThat(score.value()).isZero();
        assertThat(score.errors()).isEqualTo(50);
    }

    @Test
    void compute_givenNull_throws() {
        assertThatThrownBy(() -> ScoreCalculator.compute(null)).isInstanceOf(IllegalArgumentException.class);
    }

    private static Diagnostic diag(Severity s) {
        return new Diagnostic(ID, s, RuleCategory.JPA, LOC, "msg", "https://example/JPA-001");
    }
}

package dev.vitals.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.vitals.core.AnalysisResult;
import dev.vitals.core.Diagnostic;
import dev.vitals.core.Score;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class AnalysisResultTest {

    @Test
    void constructor_givenValidArgs_copiesDiagnosticsDefensively() {
        List<Diagnostic> source = new ArrayList<>();
        AnalysisResult result =
                new AnalysisResult(Path.of("/p"), 3, Duration.ofMillis(10), new Score(80, 0, 1, 0), source);

        assertThatThrownBy(() -> result.diagnostics().add(null)).isInstanceOf(UnsupportedOperationException.class);
        assertThat(result.diagnostics()).isEmpty();
    }

    @Test
    void constructor_givenNegativeDuration_throws() {
        assertThatThrownBy(() ->
                        new AnalysisResult(Path.of("/p"), 0, Duration.ofMillis(-1), new Score(100, 0, 0, 0), List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duration");
    }

    @Test
    void constructor_givenNegativeFileCount_throws() {
        assertThatThrownBy(
                        () -> new AnalysisResult(Path.of("/p"), -1, Duration.ZERO, new Score(100, 0, 0, 0), List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("filesAnalyzed");
    }
}

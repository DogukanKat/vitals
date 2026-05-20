package dev.vitals.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.vitals.core.AnalysisResult;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VitalsEngineTest {

    @Test
    void analyze_givenEagerFetchEntity_reportsJpa001AndLowersScore(@TempDir Path tempDir) {
        Path project = copyFixture("positive", tempDir);

        AnalysisResult result = new VitalsEngine().analyze(project);

        assertThat(result.diagnostics()).anyMatch(d -> d.ruleId().value().equals("JPA-001"));
        assertThat(result.score().value()).isLessThan(100);
        assertThat(result.filesAnalyzed()).isGreaterThanOrEqualTo(1);
        assertThat(result.projectRoot()).isEqualTo(project.toAbsolutePath().normalize());
        assertThat(result.duration().isNegative()).isFalse();
    }

    @Test
    void analyze_givenCleanProject_reportsNoDiagnosticsAndPerfectScore(@TempDir Path tempDir) {
        Path project = copyFixture("clean", tempDir);

        AnalysisResult result = new VitalsEngine().analyze(project);

        assertThat(result.diagnostics()).isEmpty();
        assertThat(result.score().value()).isEqualTo(100);
    }

    @Test
    void analyze_givenNonDirectory_throws(@TempDir Path tempDir) throws Exception {
        Path file = Files.writeString(tempDir.resolve("not-a-dir.txt"), "x");

        assertThatThrownBy(() -> new VitalsEngine().analyze(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Not a directory");
    }

    private static Path copyFixture(String name, Path tempDir) {
        try {
            URL root = VitalsEngineTest.class.getResource("/fixtures/engine/" + name);
            Objects.requireNonNull(root, "fixture not found: " + name);
            Path src = Path.of(root.toURI());
            try (Stream<Path> walk = Files.walk(src)) {
                walk.forEach(p -> {
                    Path target = tempDir.resolve(src.relativize(p).toString());
                    try {
                        if (Files.isDirectory(p)) {
                            Files.createDirectories(target);
                        } else {
                            Files.createDirectories(target.getParent());
                            Files.copy(p, target, StandardCopyOption.REPLACE_EXISTING);
                        }
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
            }
            return tempDir;
        } catch (IOException | URISyntaxException e) {
            throw new IllegalStateException("Failed to materialize fixture " + name, e);
        }
    }
}

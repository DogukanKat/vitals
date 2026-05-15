package dev.vitals.rules.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import dev.vitals.core.Diagnostic;
import dev.vitals.core.RuleCategory;
import dev.vitals.core.Severity;
import dev.vitals.staticengine.JavaParserAnalysisContext;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class Kafka001AutoCommitRuleTest {

    private final Kafka001AutoCommitRule rule = new Kafka001AutoCommitRule();

    @Test
    void analyze_givenAutoCommitTrueWithListener_reportsError(@TempDir Path tempDir) {
        Path project = copyFixture("positive", tempDir);
        List<Diagnostic> diagnostics = rule.analyze(JavaParserAnalysisContext.discover(project));

        assertThat(diagnostics).hasSize(1);
        Diagnostic d = diagnostics.get(0);
        assertThat(d.ruleId().value()).isEqualTo("KAFKA-001");
        assertThat(d.severity()).isInstanceOf(Severity.Error.class);
        assertThat(d.category()).isEqualTo(RuleCategory.KAFKA);
        assertThat(d.message()).contains("spring.kafka.consumer.enable-auto-commit=true");
        assertThat(d.location().line()).isEqualTo(8);
    }

    @Test
    void analyze_givenAutoCommitFalseWithListener_reportsNothing(@TempDir Path tempDir) {
        Path project = copyFixture("negative", tempDir);
        assertThat(rule.analyze(JavaParserAnalysisContext.discover(project))).isEmpty();
    }

    @Test
    void analyze_givenAutoCommitTrueButNoListener_reportsNothing(@TempDir Path tempDir) {
        Path project = copyFixture("edge", tempDir);
        assertThat(rule.analyze(JavaParserAnalysisContext.discover(project))).isEmpty();
    }

    private static Path copyFixture(String name, Path tempDir) {
        try {
            URL root = Kafka001AutoCommitRuleTest.class.getResource("/fixtures/kafka-001/" + name);
            Objects.requireNonNull(root, "fixture not found: " + name);
            Path src = Path.of(root.toURI());
            try (Stream<Path> walk = Files.walk(src)) {
                walk.forEach(p -> {
                    Path relative = src.relativize(p);
                    Path target = tempDir.resolve(relative.toString());
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

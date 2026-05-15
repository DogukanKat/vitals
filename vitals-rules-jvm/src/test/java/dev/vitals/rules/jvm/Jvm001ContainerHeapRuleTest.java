package dev.vitals.rules.jvm;

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

class Jvm001ContainerHeapRuleTest {

    private final Jvm001ContainerHeapRule rule = new Jvm001ContainerHeapRule();

    @Test
    void analyze_givenJavaImageWithoutHeapBound_reportsWarning(@TempDir Path tempDir) {
        Path project = copyFixture("positive", tempDir);
        List<Diagnostic> diagnostics = rule.analyze(JavaParserAnalysisContext.discover(project));

        assertThat(diagnostics).hasSize(1);
        Diagnostic d = diagnostics.get(0);
        assertThat(d.ruleId().value()).isEqualTo("JVM-001");
        assertThat(d.severity()).isInstanceOf(Severity.Warn.class);
        assertThat(d.category()).isEqualTo(RuleCategory.JVM);
        assertThat(d.message()).contains("does not bound the heap");
        assertThat(d.location().line()).isEqualTo(1);
    }

    @Test
    void analyze_givenJavaToolOptionsMaxRamPercentage_reportsNothing(@TempDir Path tempDir) {
        Path project = copyFixture("negative", tempDir);
        assertThat(rule.analyze(JavaParserAnalysisContext.discover(project))).isEmpty();
    }

    @Test
    void analyze_givenMultiStageXmxAndNonJavaImage_reportsNothing(@TempDir Path tempDir) {
        Path project = copyFixture("edge", tempDir);
        assertThat(rule.analyze(JavaParserAnalysisContext.discover(project))).isEmpty();
    }

    @Test
    void analyze_givenDevcontainerDockerfile_reportsNothing(@TempDir Path tempDir) {
        Path project = copyFixture("devcontainer", tempDir);
        assertThat(rule.analyze(JavaParserAnalysisContext.discover(project))).isEmpty();
    }

    private static Path copyFixture(String name, Path tempDir) {
        try {
            URL root = Jvm001ContainerHeapRuleTest.class.getResource("/fixtures/jvm-001/" + name);
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

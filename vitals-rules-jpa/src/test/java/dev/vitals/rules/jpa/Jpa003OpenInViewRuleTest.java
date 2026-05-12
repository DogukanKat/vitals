package dev.vitals.rules.jpa;

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

class Jpa003OpenInViewRuleTest {

    private final Jpa003OpenInViewRule rule = new Jpa003OpenInViewRule();

    @Test
    void analyze_givenOpenInViewTrue_reportsWarningOnYamlLine(@TempDir Path tempDir) {
        Path project = copyFixture("positive", tempDir);
        List<Diagnostic> diagnostics = rule.analyze(JavaParserAnalysisContext.discover(project));

        assertThat(diagnostics).hasSize(1);
        Diagnostic d = diagnostics.get(0);
        assertThat(d.ruleId().value()).isEqualTo("JPA-003");
        assertThat(d.severity()).isInstanceOf(Severity.Warn.class);
        assertThat(d.category()).isEqualTo(RuleCategory.JPA);
        assertThat(d.message()).contains("open-in-view").contains("enabled");
        assertThat(d.location().filePath().getFileName()).hasToString("application.yml");
        assertThat(d.location().line()).isEqualTo(8);
    }

    @Test
    void analyze_givenOpenInViewFalse_reportsNothing(@TempDir Path tempDir) {
        Path project = copyFixture("negative", tempDir);
        assertThat(rule.analyze(JavaParserAnalysisContext.discover(project))).isEmpty();
    }

    @Test
    void analyze_givenUnsetInProperties_flagsDefault(@TempDir Path tempDir) {
        Path project = copyFixture("edge", tempDir);
        List<Diagnostic> diagnostics = rule.analyze(JavaParserAnalysisContext.discover(project));

        assertThat(diagnostics).hasSize(1);
        Diagnostic d = diagnostics.get(0);
        assertThat(d.message()).contains("unset").contains("defaults to true");
        assertThat(d.location().filePath().getFileName()).hasToString("application.properties");
    }

    private static Path copyFixture(String name, Path tempDir) {
        try {
            URL root = Jpa003OpenInViewRuleTest.class.getResource("/fixtures/jpa-003/" + name);
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

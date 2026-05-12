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
import org.junit.jupiter.api.io.TempDir;

class Jpa001EagerFetchRuleTest {

    private final Jpa001EagerFetchRule rule = new Jpa001EagerFetchRule();

    @org.junit.jupiter.api.Test
    void analyze_givenEagerManyToOneAndOneToOne_reportsTwoDiagnostics(@TempDir Path tempDir) {
        Path project = copyFixture("positive", tempDir);
        List<Diagnostic> diagnostics = rule.analyze(JavaParserAnalysisContext.discover(project));

        assertThat(diagnostics).hasSize(2);
        assertThat(diagnostics).allSatisfy(d -> {
            assertThat(d.ruleId().value()).isEqualTo("JPA-001");
            assertThat(d.severity()).isInstanceOf(Severity.Error.class);
            assertThat(d.category()).isEqualTo(RuleCategory.JPA);
        });
        assertThat(diagnostics)
                .extracting(Diagnostic::message)
                .anyMatch(m -> m.contains("@ManyToOne 'customer'"))
                .anyMatch(m -> m.contains("@OneToOne 'invoice'"));
    }

    @org.junit.jupiter.api.Test
    void analyze_givenAllLazy_reportsNothing(@TempDir Path tempDir) {
        Path project = copyFixture("negative", tempDir);
        assertThat(rule.analyze(JavaParserAnalysisContext.discover(project))).isEmpty();
    }

    @org.junit.jupiter.api.Test
    void analyze_givenMixedAssociations_reportsOnlyExplicitEager(@TempDir Path tempDir) {
        Path project = copyFixture("edge", tempDir);
        List<Diagnostic> diagnostics = rule.analyze(JavaParserAnalysisContext.discover(project));

        assertThat(diagnostics).hasSize(1);
        assertThat(diagnostics.get(0).message()).contains("@ManyToMany 'tags'");
    }

    private static Path copyFixture(String name, Path tempDir) {
        try {
            URL root = Jpa001EagerFetchRuleTest.class.getResource("/fixtures/jpa-001/" + name);
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

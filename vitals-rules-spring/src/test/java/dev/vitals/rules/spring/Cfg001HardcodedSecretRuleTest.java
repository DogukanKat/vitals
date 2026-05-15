package dev.vitals.rules.spring;

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

class Cfg001HardcodedSecretRuleTest {

    private final Cfg001HardcodedSecretRule rule = new Cfg001HardcodedSecretRule();

    @Test
    void analyze_givenHardcodedSecretsInProperties_reportsEach(@TempDir Path tempDir) {
        Path project = copyFixture("positive", tempDir);
        List<Diagnostic> diagnostics = rule.analyze(JavaParserAnalysisContext.discover(project));

        assertThat(diagnostics).hasSize(3);
        assertThat(diagnostics).allSatisfy(d -> {
            assertThat(d.ruleId().value()).isEqualTo("CFG-001");
            assertThat(d.severity()).isInstanceOf(Severity.Error.class);
            assertThat(d.category()).isEqualTo(RuleCategory.SECURITY);
        });
        assertThat(diagnostics)
                .extracting(Diagnostic::message)
                .anyMatch(m -> m.contains("spring.datasource.password"))
                .anyMatch(m -> m.contains("app.payment.api-key"))
                .anyMatch(m -> m.contains("server.ssl.key-store-password"));
    }

    @Test
    void analyze_givenAllPlaceholders_reportsNothing(@TempDir Path tempDir) {
        Path project = copyFixture("negative", tempDir);
        assertThat(rule.analyze(JavaParserAnalysisContext.discover(project))).isEmpty();
    }

    @Test
    void analyze_givenMixedKeys_reportsOnlyHardcodedSecret(@TempDir Path tempDir) {
        Path project = copyFixture("edge", tempDir);
        List<Diagnostic> diagnostics = rule.analyze(JavaParserAnalysisContext.discover(project));

        assertThat(diagnostics).hasSize(1);
        assertThat(diagnostics.get(0).message()).contains("app.jwt.secret");
    }

    private static Path copyFixture(String name, Path tempDir) {
        try {
            URL root = Cfg001HardcodedSecretRuleTest.class.getResource("/fixtures/cfg-001/" + name);
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

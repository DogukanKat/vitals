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

class Tx001BlockingInTransactionRuleTest {

    private final Tx001BlockingInTransactionRule rule = new Tx001BlockingInTransactionRule();

    @Test
    void analyze_givenRestTemplateAndSleepInTransactional_reportsBoth(@TempDir Path tempDir) {
        Path project = copyFixture("positive", tempDir);
        List<Diagnostic> diagnostics = rule.analyze(JavaParserAnalysisContext.discover(project));

        assertThat(diagnostics).hasSize(2);
        assertThat(diagnostics).allSatisfy(d -> {
            assertThat(d.ruleId().value()).isEqualTo("TX-001");
            assertThat(d.severity()).isInstanceOf(Severity.Error.class);
            assertThat(d.category()).isEqualTo(RuleCategory.SPRING);
        });
        assertThat(diagnostics)
                .extracting(Diagnostic::message)
                .anyMatch(m -> m.contains("Blocking HTTP call 'restTemplate.getForObject(...)'"))
                .anyMatch(m -> m.contains("Thread.sleep(...)"));
    }

    @Test
    void analyze_givenTransactionalMethodWithoutBlockingCalls_reportsNothing(@TempDir Path tempDir) {
        Path project = copyFixture("negative", tempDir);
        assertThat(rule.analyze(JavaParserAnalysisContext.discover(project))).isEmpty();
    }

    @Test
    void analyze_givenClassLevelTransactionalAndReactorBlock_reportsBlockOnly(@TempDir Path tempDir) {
        Path project = copyFixture("edge", tempDir);
        List<Diagnostic> diagnostics = rule.analyze(JavaParserAnalysisContext.discover(project));

        assertThat(diagnostics).hasSize(1);
        Diagnostic d = diagnostics.get(0);
        assertThat(d.message()).contains("Reactor '.block()'");
        assertThat(d.location().filePath().getFileName()).hasToString("CatalogService.java");
    }

    private static Path copyFixture(String name, Path tempDir) {
        try {
            URL root = Tx001BlockingInTransactionRuleTest.class.getResource("/fixtures/tx-001/" + name);
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

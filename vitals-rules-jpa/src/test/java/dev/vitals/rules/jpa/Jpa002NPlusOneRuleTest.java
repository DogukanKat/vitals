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

class Jpa002NPlusOneRuleTest {

    private final Jpa002NPlusOneRule rule = new Jpa002NPlusOneRule();

    @Test
    void analyze_givenAssociationGetterInForEachLoop_reportsDiagnostic(@TempDir Path tempDir) {
        Path project = copyFixture("positive", tempDir);
        List<Diagnostic> diagnostics = rule.analyze(JavaParserAnalysisContext.discover(project));

        assertThat(diagnostics).hasSize(1);
        Diagnostic d = diagnostics.get(0);
        assertThat(d.ruleId().value()).isEqualTo("JPA-002");
        assertThat(d.severity()).isInstanceOf(Severity.Error.class);
        assertThat(d.category()).isEqualTo(RuleCategory.JPA);
        assertThat(d.message()).contains("getCustomer()").contains("N+1");
        assertThat(d.location().filePath().getFileName()).hasToString("OrderSummaryService.java");
    }

    @Test
    void analyze_givenNoLoopAroundAssociationGetter_reportsNothing(@TempDir Path tempDir) {
        Path project = copyFixture("negative", tempDir);
        assertThat(rule.analyze(JavaParserAnalysisContext.discover(project))).isEmpty();
    }

    @Test
    void analyze_givenAssociationGetterInStreamLambdas_reportsBoth(@TempDir Path tempDir) {
        Path project = copyFixture("edge", tempDir);
        List<Diagnostic> diagnostics = rule.analyze(JavaParserAnalysisContext.discover(project));

        assertThat(diagnostics).hasSize(2);
        assertThat(diagnostics)
                .extracting(Diagnostic::message)
                .anyMatch(m -> m.contains("getCustomer()"))
                .anyMatch(m -> m.contains("getLines()"));
    }

    private static Path copyFixture(String name, Path tempDir) {
        try {
            URL root = Jpa002NPlusOneRuleTest.class.getResource("/fixtures/jpa-002/" + name);
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

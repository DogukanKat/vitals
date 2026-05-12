package dev.vitals.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

class VitalsCliIntegrationTest {

    @Test
    void run_givenProjectWithEagerFetch_reportsError(@TempDir Path tempDir) {
        Path project = copyFixture("positive", tempDir);
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();

        int exit = new CommandLine(new VitalsCli(new PrintStream(stdout, true, StandardCharsets.UTF_8), System.err))
                .execute(project.toString());
        String output = stdout.toString(StandardCharsets.UTF_8);

        assertThat(exit).isEqualTo(1);
        assertThat(output).contains("Vitals 0.1.0");
        assertThat(output).contains("JPA-001");
        assertThat(output).contains("FetchType.EAGER");
        assertThat(output).contains("Score:");
        assertThat(output).doesNotContain("Score: 100");
    }

    @Test
    void run_givenCleanProject_reportsPerfectScore(@TempDir Path tempDir) {
        Path project = copyFixture("negative", tempDir);
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();

        int exit = new CommandLine(new VitalsCli(new PrintStream(stdout, true, StandardCharsets.UTF_8), System.err))
                .execute(project.toString());
        String output = stdout.toString(StandardCharsets.UTF_8);

        assertThat(exit).isZero();
        assertThat(output).contains("Score: 100 / 100");
        assertThat(output).contains("Great");
    }

    private static Path copyFixture(String name, Path tempDir) {
        try {
            URL root = VitalsCliIntegrationTest.class.getResource("/fixtures/jpa-001/" + name);
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

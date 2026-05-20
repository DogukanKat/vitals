package dev.vitals.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Spawns a real {@code java -cp ... dev.vitals.cli.VitalsCli} child process to assert that
 * {@code --format json} writes a JSON-only payload to stdout, with all log output routed to stderr.
 * The in-process integration tests cannot catch a logback misconfiguration because they capture
 * the CLI's injected {@code PrintStream}, not the JVM's real {@code System.out}.
 */
class VitalsCliProcessStdoutTest {

    @Test
    void realProcess_givenJsonFormat_emitsPureJsonOnStdout(@TempDir Path tempDir) throws Exception {
        Path project = copyFixture("jpa-001", "positive", tempDir);

        String javaBin = ProcessHandle.current().info().command().orElseGet(() -> Path.of(
                        System.getProperty("java.home"), "bin", "java")
                .toString());
        String classpath = System.getProperty("java.class.path");

        ProcessBuilder pb = new ProcessBuilder(
                        javaBin, "-cp", classpath, "dev.vitals.cli.VitalsCli", "--format", "json", project.toString())
                .redirectErrorStream(false);
        Process proc = pb.start();
        String stdout;
        String stderr;
        int exit;
        try (ExecutorService exec = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<String> stdoutFuture =
                    exec.submit(() -> new String(proc.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
            Future<String> stderrFuture =
                    exec.submit(() -> new String(proc.getErrorStream().readAllBytes(), StandardCharsets.UTF_8));
            exit = proc.waitFor();
            stdout = stdoutFuture.get();
            stderr = stderrFuture.get();
        }

        String trimmed = stdout.trim();
        assertThat(exit)
                .as("JPA-001 fixture must produce errors -> exit 1 (stderr=%s)", stderr)
                .isEqualTo(1);
        assertThat(trimmed)
                .as("stdout must be a single JSON object — no log lines allowed before or after (stderr=%s)", stderr)
                .startsWith("{")
                .endsWith("}");
        assertThat(trimmed).contains("\"schemaVersion\":\"1.0\"");
        assertThat(trimmed).contains("\"ruleId\":\"JPA-001\"");
        assertThat(trimmed)
                .as("logback output (level tokens) must never appear on stdout")
                .doesNotContain("DEBUG")
                .doesNotContain("INFO")
                .doesNotContain("WARN ")
                .doesNotContain("ERROR ");
    }

    private static Path copyFixture(String rule, String name, Path tempDir) {
        try {
            URL root = VitalsCliProcessStdoutTest.class.getResource("/fixtures/" + rule + "/" + name);
            Objects.requireNonNull(root, "fixture not found: " + rule + "/" + name);
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

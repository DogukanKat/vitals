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
        Path project = copyFixture("jpa-001", "positive", tempDir);
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
        Path project = copyFixture("jpa-001", "negative", tempDir);
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();

        int exit = new CommandLine(new VitalsCli(new PrintStream(stdout, true, StandardCharsets.UTF_8), System.err))
                .execute(project.toString());
        String output = stdout.toString(StandardCharsets.UTF_8);

        assertThat(exit).isZero();
        assertThat(output).contains("Score: 100 / 100");
        assertThat(output).contains("Great");
    }

    @Test
    void run_givenProjectWithNPlusOneLoop_reportsJpa002(@TempDir Path tempDir) {
        Path project = copyFixture("jpa-002", "positive", tempDir);
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();

        int exit = new CommandLine(new VitalsCli(new PrintStream(stdout, true, StandardCharsets.UTF_8), System.err))
                .execute(project.toString());
        String output = stdout.toString(StandardCharsets.UTF_8);

        assertThat(exit).isEqualTo(1);
        assertThat(output).contains("JPA-002");
        assertThat(output).contains("getCustomer()");
        assertThat(output).contains("N+1");
    }

    @Test
    void run_givenOpenInViewEnabled_reportsJpa003(@TempDir Path tempDir) {
        Path project = copyFixture("jpa-003", "positive", tempDir);
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();

        int exit = new CommandLine(new VitalsCli(new PrintStream(stdout, true, StandardCharsets.UTF_8), System.err))
                .execute(project.toString());
        String output = stdout.toString(StandardCharsets.UTF_8);

        assertThat(exit).isZero();
        assertThat(output).contains("WARN").contains("JPA-003").contains("open-in-view");
    }

    @Test
    void run_givenBlockingCallInTransactional_reportsTx001(@TempDir Path tempDir) {
        Path project = copyFixture("tx-001", "positive", tempDir);
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();

        int exit = new CommandLine(new VitalsCli(new PrintStream(stdout, true, StandardCharsets.UTF_8), System.err))
                .execute(project.toString());
        String output = stdout.toString(StandardCharsets.UTF_8);

        assertThat(exit).isEqualTo(1);
        assertThat(output).contains("TX-001");
        assertThat(output).contains("Thread.sleep");
        assertThat(output).contains("restTemplate.getForObject");
    }

    @Test
    void run_givenTransactionalOnPrivateMethod_reportsTx002(@TempDir Path tempDir) {
        Path project = copyFixture("tx-002", "positive", tempDir);
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();

        int exit = new CommandLine(new VitalsCli(new PrintStream(stdout, true, StandardCharsets.UTF_8), System.err))
                .execute(project.toString());
        String output = stdout.toString(StandardCharsets.UTF_8);

        assertThat(exit).isEqualTo(1);
        assertThat(output).contains("TX-002");
        assertThat(output).contains("private method 'persist'");
    }

    @Test
    void run_givenAutowiredOnFields_reportsDi001(@TempDir Path tempDir) {
        Path project = copyFixture("di-001", "positive", tempDir);
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();

        int exit = new CommandLine(new VitalsCli(new PrintStream(stdout, true, StandardCharsets.UTF_8), System.err))
                .execute(project.toString());
        String output = stdout.toString(StandardCharsets.UTF_8);

        assertThat(exit).isZero();
        assertThat(output).contains("WARN").contains("DI-001").contains("@Autowired on field");
    }

    @Test
    void run_givenActuatorWildcardExposure_reportsSec001(@TempDir Path tempDir) {
        Path project = copyFixture("sec-001", "positive", tempDir);
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();

        int exit = new CommandLine(new VitalsCli(new PrintStream(stdout, true, StandardCharsets.UTF_8), System.err))
                .execute(project.toString());
        String output = stdout.toString(StandardCharsets.UTF_8);

        assertThat(exit).isEqualTo(1);
        assertThat(output).contains("SEC-001").contains("wildcard '*'");
    }

    @Test
    void run_givenHardcodedSecrets_reportsCfg001(@TempDir Path tempDir) {
        Path project = copyFixture("cfg-001", "positive", tempDir);
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();

        int exit = new CommandLine(new VitalsCli(new PrintStream(stdout, true, StandardCharsets.UTF_8), System.err))
                .execute(project.toString());
        String output = stdout.toString(StandardCharsets.UTF_8);

        assertThat(exit).isEqualTo(1);
        assertThat(output).contains("CFG-001").contains("spring.datasource.password");
    }

    @Test
    void run_givenJavaImageWithoutHeapBound_reportsJvm001(@TempDir Path tempDir) {
        Path project = copyFixture("jvm-001", "positive", tempDir);
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();

        int exit = new CommandLine(new VitalsCli(new PrintStream(stdout, true, StandardCharsets.UTF_8), System.err))
                .execute(project.toString());
        String output = stdout.toString(StandardCharsets.UTF_8);

        assertThat(exit).isZero();
        assertThat(output).contains("WARN").contains("JVM-001").contains("bound the heap");
    }

    @Test
    void run_givenKafkaAutoCommitWithListener_reportsKafka001(@TempDir Path tempDir) {
        Path project = copyFixture("kafka-001", "positive", tempDir);
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();

        int exit = new CommandLine(new VitalsCli(new PrintStream(stdout, true, StandardCharsets.UTF_8), System.err))
                .execute(project.toString());
        String output = stdout.toString(StandardCharsets.UTF_8);

        assertThat(exit).isEqualTo(1);
        assertThat(output).contains("KAFKA-001").contains("enable-auto-commit=true");
    }

    @Test
    void run_givenJsonFormat_emitsJsonAndWritesReportFile(@TempDir Path tempDir) throws IOException {
        Path project = copyFixture("jpa-001", "positive", tempDir);
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();

        int exit = new CommandLine(new VitalsCli(new PrintStream(stdout, true, StandardCharsets.UTF_8), System.err))
                .setCaseInsensitiveEnumValuesAllowed(true)
                .execute("--format", "json", project.toString());
        String output = stdout.toString(StandardCharsets.UTF_8).trim();

        assertThat(exit).isEqualTo(1);
        assertThat(output).startsWith("{").endsWith("}");
        assertThat(output).doesNotContain("Vitals 0.1.0");
        assertThat(output).contains("\"schemaVersion\":\"1.0\"");
        assertThat(output).contains("\"ruleId\":\"JPA-001\"");
        assertThat(Files.readString(project.resolve("build/reports/vitals/report.json"), StandardCharsets.UTF_8))
                .contains("\"schemaVersion\"");
    }

    private static Path copyFixture(String rule, String name, Path tempDir) {
        try {
            URL root = VitalsCliIntegrationTest.class.getResource("/fixtures/" + rule + "/" + name);
            Objects.requireNonNull(root, "fixture not found: " + rule + "/" + name);
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

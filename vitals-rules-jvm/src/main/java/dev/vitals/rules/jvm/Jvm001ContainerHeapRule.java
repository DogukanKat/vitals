package dev.vitals.rules.jvm;

import dev.vitals.core.AnalysisContext;
import dev.vitals.core.Diagnostic;
import dev.vitals.core.Dockerfile;
import dev.vitals.core.RuleCategory;
import dev.vitals.core.RuleId;
import dev.vitals.core.Severity;
import dev.vitals.core.SourceLocation;
import dev.vitals.core.StaticRule;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Flags Dockerfiles that build a Java container image without bounding the JVM heap to the
 * container's memory.
 *
 * <p>A container always runs under a memory limit at orchestration time (Kubernetes {@code
 * resources.limits.memory}, {@code docker run --memory}, …). Those limits live outside the
 * Dockerfile, so this rule treats "this image runs a JVM" as the constrained context and checks
 * whether the heap is explicitly sized. On Java 21 the JVM honours the cgroup limit but defaults
 * to {@code MaxRAMPercentage=25}, which is wrong for most service containers — too small to start
 * under load, or wastefully under-using a large pod. Setting {@code -Xmx} or {@code
 * -XX:MaxRAMPercentage} makes the sizing intentional.
 *
 * <p>The rule fires once per Dockerfile that (a) is a Java image — a JDK/JRE base image or a
 * {@code java} invocation in {@code ENTRYPOINT}/{@code CMD}/{@code RUN} — and (b) contains no
 * {@code -Xmx} or {@code MaxRAMPercentage} on any line. It is a deliberate scoping decision to key
 * off the Dockerfile rather than the orchestration manifest; a manifest-aware companion rule may
 * follow.
 *
 * <p>Reference: <a href="https://docs.oracle.com/en/java/javase/21/docs/specs/man/java.html">{@code
 * java} tool — container awareness</a>.
 */
public final class Jvm001ContainerHeapRule implements StaticRule {

    private static final RuleId ID = new RuleId("JVM-001");
    private static final String HELP_URL = "https://github.com/vitals-dev/vitals/blob/main/docs/rules/JVM-001.md";
    private static final Set<String> JDK_IMAGE_MARKERS = Set.of(
            "temurin",
            "corretto",
            "openjdk",
            "semeru",
            "sapmachine",
            "zulu",
            "liberica",
            "graalvm",
            "bellsoft",
            "-jdk",
            "-jre",
            "/jdk",
            "/jre");

    @Override
    public RuleId id() {
        return ID;
    }

    @Override
    public Severity defaultSeverity() {
        return new Severity.Warn();
    }

    @Override
    public RuleCategory category() {
        return RuleCategory.JVM;
    }

    @Override
    public String shortDescription() {
        return "Java container image without an explicit heap bound (-Xmx / MaxRAMPercentage)";
    }

    @Override
    public String helpUrl() {
        return HELP_URL;
    }

    @Override
    public List<Diagnostic> analyze(AnalysisContext context) {
        List<Diagnostic> findings = new ArrayList<>();
        for (Dockerfile dockerfile : context.dockerfiles()) {
            if (isNonDeployment(dockerfile)) {
                continue;
            }
            int javaImageLine = javaImageLine(dockerfile.lines());
            if (javaImageLine < 0 || hasHeapBound(dockerfile.lines())) {
                continue;
            }
            findings.add(new Diagnostic(
                    ID,
                    defaultSeverity(),
                    category(),
                    new SourceLocation(dockerfile.path(), javaImageLine, 0),
                    "Java container image does not bound the heap — set -Xmx or "
                            + "-XX:MaxRAMPercentage so the JVM respects the container memory limit.",
                    HELP_URL));
        }
        return List.copyOf(findings);
    }

    // Dev-tooling images (.devcontainer, Gitpod) are not deployment artifacts — their heap is
    // never the production heap, so flagging them is noise.
    private static boolean isNonDeployment(Dockerfile dockerfile) {
        String path = dockerfile.path().toString().replace('\\', '/').toLowerCase(Locale.ROOT);
        return path.contains("/.devcontainer/") || path.contains("/.gitpod") || path.contains("gitpod.dockerfile");
    }

    private static int javaImageLine(List<String> lines) {
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).strip().toLowerCase(Locale.ROOT);
            if (line.startsWith("from ") && JDK_IMAGE_MARKERS.stream().anyMatch(line::contains)) {
                return i + 1;
            }
            if ((line.startsWith("entrypoint") || line.startsWith("cmd") || line.startsWith("run "))
                    && line.contains("java")) {
                return i + 1;
            }
        }
        return -1;
    }

    private static boolean hasHeapBound(List<String> lines) {
        return lines.stream()
                .map(l -> l.toLowerCase(Locale.ROOT))
                .anyMatch(l -> l.contains("-xmx") || l.contains("maxrampercentage"));
    }
}

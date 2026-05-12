package dev.vitals.staticengine;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import dev.vitals.core.AnalysisContext;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link AnalysisContext} implementation backed by JavaParser.
 *
 * <p>Discovers {@code *.java} files under a project root, parses each, and exposes the resulting
 * {@link CompilationUnit} handles to rules through the opaque {@link JavaSource#compilationUnit()}
 * accessor.
 */
public final class JavaParserAnalysisContext implements AnalysisContext {

    private static final Logger LOG = LoggerFactory.getLogger(JavaParserAnalysisContext.class);

    private final Path projectRoot;
    private final List<JavaSource> sources;

    private JavaParserAnalysisContext(Path projectRoot, List<JavaSource> sources) {
        this.projectRoot = projectRoot;
        this.sources = List.copyOf(sources);
    }

    /**
     * Discover and parse all Java sources under {@code projectRoot}.
     *
     * @param projectRoot directory to scan; must exist
     * @return a populated context
     */
    public static JavaParserAnalysisContext discover(Path projectRoot) {
        if (!Files.isDirectory(projectRoot)) {
            throw new IllegalArgumentException("projectRoot is not a directory: " + projectRoot);
        }
        JavaParser parser =
                new JavaParser(new ParserConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21));
        List<JavaSource> collected = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(projectRoot)) {
            walk.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".java"))
                    .filter(p -> !p.toString().contains("/build/"))
                    .sorted(Comparator.naturalOrder())
                    .forEach(path -> parseOne(parser, path).ifPresent(collected::add));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to walk project root " + projectRoot, e);
        }
        LOG.debug("Discovered {} Java sources under {}", collected.size(), projectRoot);
        return new JavaParserAnalysisContext(projectRoot, collected);
    }

    private static java.util.Optional<JavaSource> parseOne(JavaParser parser, Path path) {
        try {
            ParseResult<CompilationUnit> result = parser.parse(path);
            if (!result.isSuccessful() || result.getResult().isEmpty()) {
                LOG.warn("Skipping unparsable source {}: {}", path, result.getProblems());
                return java.util.Optional.empty();
            }
            return java.util.Optional.of(
                    new ParsedSource(path, result.getResult().get()));
        } catch (IOException e) {
            LOG.warn("Failed to read {}: {}", path, e.getMessage());
            return java.util.Optional.empty();
        }
    }

    @Override
    public Path projectRoot() {
        return projectRoot;
    }

    @Override
    public List<JavaSource> javaSources() {
        return sources;
    }

    private record ParsedSource(Path path, CompilationUnit unit) implements JavaSource {
        @Override
        public Object compilationUnit() {
            return unit;
        }
    }
}

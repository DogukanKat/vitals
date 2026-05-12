package dev.vitals.staticengine;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import dev.vitals.core.AnalysisContext;
import dev.vitals.core.ConfigSource;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link AnalysisContext} implementation backed by JavaParser and SnakeYAML.
 *
 * <p>Discovers {@code *.java} files plus Spring-style config files ({@code application*.yml},
 * {@code .yaml}, {@code .properties} and {@code bootstrap*.*}) under a project root.
 */
public final class JavaParserAnalysisContext implements AnalysisContext {

    private static final Logger LOG = LoggerFactory.getLogger(JavaParserAnalysisContext.class);

    private static final Pattern CONFIG_FILE =
            Pattern.compile("(application|bootstrap)(-[^/]+)?\\.(yml|yaml|properties)", Pattern.CASE_INSENSITIVE);

    private final Path projectRoot;
    private final List<JavaSource> sources;
    private final List<ConfigSource> configs;

    private JavaParserAnalysisContext(Path projectRoot, List<JavaSource> sources, List<ConfigSource> configs) {
        this.projectRoot = projectRoot;
        this.sources = List.copyOf(sources);
        this.configs = List.copyOf(configs);
    }

    /**
     * Discover and parse all Java sources and Spring config files under {@code projectRoot}.
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
        List<JavaSource> javaSources = new ArrayList<>();
        List<ConfigSource> configSources = new ArrayList<>();

        try (Stream<Path> walk = Files.walk(projectRoot)) {
            walk.filter(Files::isRegularFile)
                    .filter(p -> !p.toString().contains("/build/"))
                    .sorted(Comparator.naturalOrder())
                    .forEach(path -> {
                        String name = path.getFileName().toString();
                        if (name.endsWith(".java")) {
                            parseJava(parser, path).ifPresent(javaSources::add);
                        } else if (CONFIG_FILE.matcher(name).matches()) {
                            configSources.add(loadConfig(path));
                        }
                    });
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to walk project root " + projectRoot, e);
        }
        LOG.debug(
                "Discovered {} Java sources and {} config files under {}",
                javaSources.size(),
                configSources.size(),
                projectRoot);
        return new JavaParserAnalysisContext(projectRoot, javaSources, configSources);
    }

    private static Optional<JavaSource> parseJava(JavaParser parser, Path path) {
        try {
            ParseResult<CompilationUnit> result = parser.parse(path);
            if (!result.isSuccessful() || result.getResult().isEmpty()) {
                LOG.warn("Skipping unparsable source {}: {}", path, result.getProblems());
                return Optional.empty();
            }
            return Optional.of(new ParsedSource(path, result.getResult().get()));
        } catch (IOException e) {
            LOG.warn("Failed to read {}: {}", path, e.getMessage());
            return Optional.empty();
        }
    }

    private static ConfigSource loadConfig(Path path) {
        String name = path.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
        return name.endsWith(".properties") ? PropertiesConfigSource.load(path) : YamlConfigSource.load(path);
    }

    @Override
    public Path projectRoot() {
        return projectRoot;
    }

    @Override
    public List<JavaSource> javaSources() {
        return sources;
    }

    @Override
    public List<ConfigSource> configSources() {
        return configs;
    }

    private record ParsedSource(Path path, CompilationUnit unit) implements JavaSource {
        @Override
        public Object compilationUnit() {
            return unit;
        }
    }
}

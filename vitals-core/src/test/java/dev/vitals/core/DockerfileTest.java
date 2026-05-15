package dev.vitals.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class DockerfileTest {

    @Test
    void construct_storesPathAndLines() {
        Dockerfile d = new Dockerfile(Path.of("Dockerfile"), List.of("FROM eclipse-temurin:21-jre"));
        assertThat(d.path()).isEqualTo(Path.of("Dockerfile"));
        assertThat(d.lines()).containsExactly("FROM eclipse-temurin:21-jre");
    }

    @Test
    void construct_copiesLinesDefensively() {
        List<String> mutable = new ArrayList<>(List.of("FROM x"));
        Dockerfile d = new Dockerfile(Path.of("Dockerfile"), mutable);
        mutable.add("RUN evil");
        assertThat(d.lines()).containsExactly("FROM x");
    }

    @Test
    void construct_givenNullPath_throws() {
        assertThatThrownBy(() -> new Dockerfile(null, List.of())).isInstanceOf(IllegalArgumentException.class);
    }
}

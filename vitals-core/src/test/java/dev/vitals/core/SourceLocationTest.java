package dev.vitals.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class SourceLocationTest {

    @Test
    void construct_givenValidInputs_storesFields() {
        SourceLocation location = new SourceLocation(Path.of("/tmp/Foo.java"), 12, 4);
        assertThat(location.filePath()).isEqualTo(Path.of("/tmp/Foo.java"));
        assertThat(location.line()).isEqualTo(12);
        assertThat(location.column()).isEqualTo(4);
    }

    @Test
    void construct_givenNullPath_throws() {
        assertThatThrownBy(() -> new SourceLocation(null, 1, 1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void construct_givenUnknownLineAndColumn_isPermitted() {
        assertThat(new SourceLocation(Path.of("/tmp/Foo.java"), 0, 0).line()).isZero();
    }
}

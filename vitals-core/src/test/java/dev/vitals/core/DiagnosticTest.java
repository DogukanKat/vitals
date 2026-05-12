package dev.vitals.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class DiagnosticTest {

    private static final RuleId ID = new RuleId("JPA-001");
    private static final SourceLocation LOC = new SourceLocation(Path.of("Foo.java"), 1, 1);

    @Test
    void construct_givenValidInputs_succeeds() {
        Diagnostic d = new Diagnostic(
                ID,
                new Severity.Error(),
                RuleCategory.JPA,
                LOC,
                "EAGER fetch on @ManyToOne",
                "https://example/JPA-001");
        assertThat(d.ruleId()).isEqualTo(ID);
        assertThat(d.severity()).isInstanceOf(Severity.Error.class);
        assertThat(d.category()).isEqualTo(RuleCategory.JPA);
    }

    @Test
    void construct_givenBlankMessage_throws() {
        assertThatThrownBy(() -> new Diagnostic(
                        ID, new Severity.Error(), RuleCategory.JPA, LOC, "  ", "https://example/JPA-001"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void construct_givenBlankHelpUrl_throws() {
        assertThatThrownBy(() -> new Diagnostic(ID, new Severity.Error(), RuleCategory.JPA, LOC, "boom", ""))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

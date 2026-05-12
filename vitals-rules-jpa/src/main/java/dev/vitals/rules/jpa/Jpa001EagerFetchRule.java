package dev.vitals.rules.jpa;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import dev.vitals.core.AnalysisContext;
import dev.vitals.core.Diagnostic;
import dev.vitals.core.RuleCategory;
import dev.vitals.core.RuleId;
import dev.vitals.core.Severity;
import dev.vitals.core.SourceLocation;
import dev.vitals.core.StaticRule;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Flags {@code @ManyToOne}, {@code @OneToOne}, and {@code @ManyToMany} associations that declare
 * {@code fetch = FetchType.EAGER}.
 *
 * <p>{@code @ManyToOne} and {@code @OneToOne} default to {@code EAGER}, so triggering this rule
 * requires the developer to have <em>explicitly</em> written {@code EAGER} — that's the case worth
 * flagging at this severity. Implicit-EAGER defaults are addressed by a separate, future rule.
 *
 * <p>Reference: Vlad Mihalcea, "The best way to map a JPA association" series
 * (https://vladmihalcea.com/category/jpa/).
 */
public final class Jpa001EagerFetchRule implements StaticRule {

    private static final RuleId ID = new RuleId("JPA-001");
    private static final String HELP_URL = "https://github.com/vitals-dev/vitals/blob/main/docs/rules/JPA-001.md";
    private static final Set<String> TARGET_ANNOTATIONS = Set.of("ManyToOne", "OneToOne", "ManyToMany");

    @Override
    public RuleId id() {
        return ID;
    }

    @Override
    public Severity defaultSeverity() {
        return new Severity.Error();
    }

    @Override
    public RuleCategory category() {
        return RuleCategory.JPA;
    }

    @Override
    public String shortDescription() {
        return "FetchType.EAGER on @ManyToOne/@OneToOne/@ManyToMany associations";
    }

    @Override
    public String helpUrl() {
        return HELP_URL;
    }

    @Override
    public List<Diagnostic> analyze(AnalysisContext context) {
        List<Diagnostic> findings = new ArrayList<>();
        for (AnalysisContext.JavaSource source : context.javaSources()) {
            if (!(source.compilationUnit() instanceof CompilationUnit unit)) {
                continue;
            }
            unit.findAll(FieldDeclaration.class).forEach(field -> field.getAnnotations()
                    .forEach(annotation -> inspect(source, field, annotation, findings)));
        }
        return List.copyOf(findings);
    }

    private void inspect(
            AnalysisContext.JavaSource source,
            FieldDeclaration field,
            AnnotationExpr annotation,
            List<Diagnostic> findings) {
        if (!TARGET_ANNOTATIONS.contains(annotation.getNameAsString())) {
            return;
        }
        if (!(annotation instanceof NormalAnnotationExpr normal)) {
            return;
        }
        for (MemberValuePair pair : normal.getPairs()) {
            if (!"fetch".equals(pair.getNameAsString())) {
                continue;
            }
            String value = pair.getValue().toString();
            if (!value.endsWith("EAGER")) {
                continue;
            }
            int line = annotation.getBegin().map(p -> p.line).orElse(0);
            int column = annotation.getBegin().map(p -> p.column).orElse(0);
            String fieldName = field.getVariables()
                    .getFirst()
                    .map(v -> v.getNameAsString())
                    .orElse("<field>");
            findings.add(new Diagnostic(
                    ID,
                    defaultSeverity(),
                    category(),
                    new SourceLocation(source.path(), line, column),
                    "FetchType.EAGER on @" + annotation.getNameAsString() + " '" + fieldName
                            + "' — prefer LAZY and fetch with a query.",
                    HELP_URL));
        }
    }
}

package dev.vitals.rules.spring;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import dev.vitals.core.AnalysisContext;
import dev.vitals.core.Diagnostic;
import dev.vitals.core.RuleCategory;
import dev.vitals.core.RuleId;
import dev.vitals.core.Severity;
import dev.vitals.core.SourceLocation;
import dev.vitals.core.StaticRule;
import java.util.ArrayList;
import java.util.List;

/**
 * Flags fields annotated with Spring's {@code @Autowired} — the field-injection anti-pattern.
 *
 * <p>The rule matches the unqualified annotation name {@code Autowired} as well as the
 * fully-qualified {@code org.springframework.beans.factory.annotation.Autowired}.
 *
 * <p>It does <em>not</em> flag {@code @Autowired} on constructors (the recommended placement) or
 * setters (setter injection — a separate, much less common pattern). Only field-targeted
 * annotations fire the rule.
 *
 * <p>Reference: Spring Framework reference — <a
 * href="https://docs.spring.io/spring-framework/reference/core/beans/dependencies/factory-collaborators.html#beans-constructor-injection">Constructor-based
 * vs. setter-based DI</a>: "the Spring team generally advocates constructor injection".
 */
public final class Di001FieldInjectionRule implements StaticRule {

    private static final RuleId ID = new RuleId("DI-001");
    private static final String HELP_URL = "https://github.com/vitals-dev/vitals/blob/main/docs/rules/DI-001.md";

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
        return RuleCategory.SPRING;
    }

    @Override
    public String shortDescription() {
        return "@Autowired on a field — prefer constructor injection";
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
            unit.findAll(FieldDeclaration.class).forEach(field -> inspect(source, field, findings));
        }
        return List.copyOf(findings);
    }

    private void inspect(AnalysisContext.JavaSource source, FieldDeclaration field, List<Diagnostic> findings) {
        if (!hasAutowired(field)) {
            return;
        }
        String fieldName =
                field.getVariables().getFirst().map(v -> v.getNameAsString()).orElse("<field>");
        int line = field.getBegin().map(p -> p.line).orElse(0);
        int column = field.getBegin().map(p -> p.column).orElse(0);
        findings.add(new Diagnostic(
                ID,
                defaultSeverity(),
                category(),
                new SourceLocation(source.path(), line, column),
                "@Autowired on field '" + fieldName
                        + "' — declare it final and inject through the constructor instead.",
                HELP_URL));
    }

    private static boolean hasAutowired(FieldDeclaration field) {
        return field.getAnnotations().stream()
                .map(AnnotationExpr::getNameAsString)
                .anyMatch(name -> name.equals("Autowired") || name.endsWith(".Autowired"));
    }
}

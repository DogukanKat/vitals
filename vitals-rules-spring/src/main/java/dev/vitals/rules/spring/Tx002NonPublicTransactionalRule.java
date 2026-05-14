package dev.vitals.rules.spring;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
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
 * Flags methods annotated with {@code @Transactional} whose visibility is {@code private} or
 * {@code protected}.
 *
 * <p>Spring's transactional support is implemented via proxy-based AOP. The proxy intercepts public
 * method calls and wraps them in a transaction; non-public methods are invoked directly on the
 * target object and the annotation has no effect. The same mechanism explains the related
 * "self-invocation" bug — a {@code this.x()} call from inside the same class also bypasses the
 * proxy — but TX-002 limits itself to the modifier-level check because it is unambiguous.
 *
 * <p>Recognises Spring's {@code @org.springframework.transaction.annotation.Transactional} and
 * Jakarta EE's {@code @jakarta.transaction.Transactional}; both unqualified and fully-qualified
 * forms match.
 *
 * <p>Reference: Spring Framework reference — <a
 * href="https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/annotations.html">Using
 * Transactional</a>: "the @Transactional annotation is typically used on methods with public
 * visibility".
 */
public final class Tx002NonPublicTransactionalRule implements StaticRule {

    private static final RuleId ID = new RuleId("TX-002");
    private static final String HELP_URL = "https://github.com/vitals-dev/vitals/blob/main/docs/rules/TX-002.md";

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
        return RuleCategory.SPRING;
    }

    @Override
    public String shortDescription() {
        return "@Transactional on a private or protected method — the proxy will not intercept it";
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
            unit.findAll(MethodDeclaration.class).forEach(method -> inspect(source, method, findings));
        }
        return List.copyOf(findings);
    }

    private void inspect(AnalysisContext.JavaSource source, MethodDeclaration method, List<Diagnostic> findings) {
        if (!hasTransactional(method)) {
            return;
        }
        String modifier;
        if (method.isPrivate()) {
            modifier = "private";
        } else if (method.isProtected()) {
            modifier = "protected";
        } else {
            return;
        }
        int line = method.getBegin().map(p -> p.line).orElse(0);
        int column = method.getBegin().map(p -> p.column).orElse(0);
        findings.add(new Diagnostic(
                ID,
                defaultSeverity(),
                category(),
                new SourceLocation(source.path(), line, column),
                "@Transactional on " + modifier + " method '" + method.getNameAsString()
                        + "' — Spring's proxy only intercepts public calls. Make it public or extract it to a bean.",
                HELP_URL));
    }

    private static boolean hasTransactional(MethodDeclaration method) {
        return method.getAnnotations().stream()
                .map(AnnotationExpr::getNameAsString)
                .anyMatch(name -> name.equals("Transactional") || name.endsWith(".Transactional"));
    }
}

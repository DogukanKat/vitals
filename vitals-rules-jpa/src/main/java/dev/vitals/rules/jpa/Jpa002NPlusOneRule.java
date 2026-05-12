package dev.vitals.rules.jpa;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ThisExpr;
import com.github.javaparser.ast.stmt.DoStmt;
import com.github.javaparser.ast.stmt.ForEachStmt;
import com.github.javaparser.ast.stmt.ForStmt;
import com.github.javaparser.ast.stmt.WhileStmt;
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
import java.util.TreeSet;

/**
 * Flags JPA association getters invoked inside a loop body, which is the canonical N+1 query
 * pattern.
 *
 * <p>The rule runs in two passes. First it scans every {@code @Entity} in the project and records
 * the getter names derived from fields annotated with {@code @ManyToOne}, {@code @OneToOne},
 * {@code @OneToMany}, or {@code @ManyToMany} (e.g. {@code customer} → {@code getCustomer}). Then it
 * walks every source file and reports any {@link MethodCallExpr} whose name matches one of those
 * getters, where the call is qualified on an instance receiver (not {@code this}) and lives inside
 * a {@code for}, {@code for-each}, {@code while}, {@code do/while}, or a lambda passed to a stream
 * per-element operation ({@code forEach}, {@code map}, {@code filter}, …).
 *
 * <p>This is a heuristic and will miss two cases: the relation has already been {@code JOIN FETCH}
 * -ed (we cannot detect that statically without query analysis), or the loop runs a single
 * iteration. False positives are acceptable here — the user can switch to {@code findWithCustomer}
 * or an entity graph, which is the correct fix either way.
 *
 * <p>Reference: Vlad Mihalcea — <a href="https://vladmihalcea.com/n-plus-1-query-problem/">"The
 * best way to detect the N+1 query problem"</a>.
 */
public final class Jpa002NPlusOneRule implements StaticRule {

    private static final RuleId ID = new RuleId("JPA-002");
    private static final String HELP_URL = "https://github.com/vitals-dev/vitals/blob/main/docs/rules/JPA-002.md";
    private static final Set<String> ASSOCIATION_ANNOTATIONS =
            Set.of("ManyToOne", "OneToOne", "OneToMany", "ManyToMany");
    private static final Set<String> STREAM_PER_ELEMENT_METHODS = Set.of(
            "forEach",
            "map",
            "filter",
            "peek",
            "flatMap",
            "anyMatch",
            "allMatch",
            "noneMatch",
            "mapToInt",
            "mapToLong",
            "mapToDouble",
            "mapToObj",
            "takeWhile",
            "dropWhile");

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
        return "Association getter invoked inside a loop (N+1 query risk)";
    }

    @Override
    public String helpUrl() {
        return HELP_URL;
    }

    @Override
    public List<Diagnostic> analyze(AnalysisContext context) {
        Set<String> getters = collectAssociationGetters(context);
        if (getters.isEmpty()) {
            return List.of();
        }
        List<Diagnostic> findings = new ArrayList<>();
        for (AnalysisContext.JavaSource source : context.javaSources()) {
            if (!(source.compilationUnit() instanceof CompilationUnit unit)) {
                continue;
            }
            unit.findAll(MethodCallExpr.class).forEach(call -> inspect(source, call, getters, findings));
        }
        return List.copyOf(findings);
    }

    private void inspect(
            AnalysisContext.JavaSource source, MethodCallExpr call, Set<String> getters, List<Diagnostic> findings) {
        if (!getters.contains(call.getNameAsString())) {
            return;
        }
        if (!hasInstanceScope(call)) {
            return;
        }
        if (!insideLoop(call)) {
            return;
        }
        int line = call.getBegin().map(p -> p.line).orElse(0);
        int column = call.getBegin().map(p -> p.column).orElse(0);
        findings.add(new Diagnostic(
                ID,
                defaultSeverity(),
                category(),
                new SourceLocation(source.path(), line, column),
                "Association getter '" + call.getNameAsString() + "()' called inside a loop — likely N+1 query.",
                HELP_URL));
    }

    private static Set<String> collectAssociationGetters(AnalysisContext context) {
        Set<String> getters = new TreeSet<>();
        for (AnalysisContext.JavaSource source : context.javaSources()) {
            if (!(source.compilationUnit() instanceof CompilationUnit unit)) {
                continue;
            }
            unit.findAll(FieldDeclaration.class).forEach(field -> {
                boolean isAssociation = field.getAnnotations().stream()
                        .map(AnnotationExpr::getNameAsString)
                        .anyMatch(ASSOCIATION_ANNOTATIONS::contains);
                if (!isAssociation) {
                    return;
                }
                field.getVariables().forEach(v -> getters.add(toGetterName(v.getNameAsString())));
            });
        }
        return getters;
    }

    private static String toGetterName(String fieldName) {
        if (fieldName.isEmpty()) {
            return fieldName;
        }
        return "get" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
    }

    private static boolean hasInstanceScope(MethodCallExpr call) {
        return call.getScope().filter(scope -> !(scope instanceof ThisExpr)).isPresent();
    }

    private static boolean insideLoop(MethodCallExpr call) {
        Node node = call.getParentNode().orElse(null);
        while (node != null) {
            if (node instanceof ForStmt
                    || node instanceof ForEachStmt
                    || node instanceof WhileStmt
                    || node instanceof DoStmt) {
                return true;
            }
            if (node instanceof LambdaExpr lambda && isStreamPerElementArg(lambda)) {
                return true;
            }
            node = node.getParentNode().orElse(null);
        }
        return false;
    }

    private static boolean isStreamPerElementArg(LambdaExpr lambda) {
        return lambda.getParentNode()
                .filter(parent -> parent instanceof MethodCallExpr call
                        && STREAM_PER_ELEMENT_METHODS.contains(call.getNameAsString()))
                .isPresent();
    }
}

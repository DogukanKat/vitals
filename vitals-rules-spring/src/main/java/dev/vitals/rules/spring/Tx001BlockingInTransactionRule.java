package dev.vitals.rules.spring;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import dev.vitals.core.AnalysisContext;
import dev.vitals.core.Diagnostic;
import dev.vitals.core.RuleCategory;
import dev.vitals.core.RuleId;
import dev.vitals.core.Severity;
import dev.vitals.core.SourceLocation;
import dev.vitals.core.StaticRule;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Flags blocking I/O — {@code Thread.sleep(...)}, synchronous HTTP clients ({@code RestTemplate},
 * {@code RestClient}), and Reactor {@code .block*()} calls — invoked inside a method that is
 * itself marked {@code @Transactional} or sits in a class annotated {@code @Transactional}.
 *
 * <p>The rule does no symbol resolution. It identifies {@code RestTemplate} / {@code RestClient}
 * receivers by walking the enclosing class for fields, local variables, and parameters typed with
 * one of those simple type names; calls on those receiver names are then flagged. Reactor blocks
 * ({@code block}, {@code blockFirst}, {@code blockLast}, {@code blockOptional}) are matched by
 * method name only.
 *
 * <p>Reference: Spring Framework reference — <a
 * href="https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative.html">Using
 * Transactional</a>; long-running transactions starve the connection pool.
 */
public final class Tx001BlockingInTransactionRule implements StaticRule {

    private static final RuleId ID = new RuleId("TX-001");
    private static final String HELP_URL = "https://github.com/vitals-dev/vitals/blob/main/docs/rules/TX-001.md";
    private static final Set<String> BLOCKING_CLIENT_TYPES = Set.of("RestTemplate", "RestClient");
    private static final Set<String> REACTOR_BLOCKING_METHODS =
            Set.of("block", "blockFirst", "blockLast", "blockOptional");

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
        return "Blocking I/O (Thread.sleep / RestTemplate / .block) inside @Transactional";
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
            unit.findAll(ClassOrInterfaceDeclaration.class).forEach(clazz -> inspectClass(source, clazz, findings));
        }
        return List.copyOf(findings);
    }

    private void inspectClass(
            AnalysisContext.JavaSource source, ClassOrInterfaceDeclaration clazz, List<Diagnostic> findings) {
        boolean classTransactional = hasTransactional(clazz.getAnnotations());
        Set<String> classBlockingReceivers = collectFieldReceivers(clazz);

        for (MethodDeclaration method : clazz.getMethods()) {
            if (!classTransactional && !hasTransactional(method.getAnnotations())) {
                continue;
            }
            Set<String> receivers = new HashSet<>(classBlockingReceivers);
            receivers.addAll(collectLocalReceivers(method));
            method.findAll(MethodCallExpr.class).forEach(call -> inspectCall(source, call, receivers, findings));
        }
    }

    private static boolean hasTransactional(NodeList<AnnotationExpr> annotations) {
        return annotations.stream()
                .map(AnnotationExpr::getNameAsString)
                .anyMatch(name -> name.equals("Transactional") || name.endsWith(".Transactional"));
    }

    private static Set<String> collectFieldReceivers(ClassOrInterfaceDeclaration clazz) {
        Set<String> names = new HashSet<>();
        for (FieldDeclaration field : clazz.getFields()) {
            if (BLOCKING_CLIENT_TYPES.contains(field.getElementType().asString())) {
                field.getVariables().forEach(v -> names.add(v.getNameAsString()));
            }
        }
        return names;
    }

    private static Set<String> collectLocalReceivers(MethodDeclaration method) {
        Set<String> names = new HashSet<>();
        for (Parameter parameter : method.getParameters()) {
            if (BLOCKING_CLIENT_TYPES.contains(parameter.getType().asString())) {
                names.add(parameter.getNameAsString());
            }
        }
        method.findAll(VariableDeclarator.class).forEach(v -> {
            if (BLOCKING_CLIENT_TYPES.contains(v.getType().asString())) {
                names.add(v.getNameAsString());
            }
        });
        return names;
    }

    private void inspectCall(
            AnalysisContext.JavaSource source,
            MethodCallExpr call,
            Set<String> blockingReceivers,
            List<Diagnostic> findings) {
        Optional<String> message = classify(call, blockingReceivers);
        if (message.isEmpty()) {
            return;
        }
        int line = call.getBegin().map(p -> p.line).orElse(0);
        int column = call.getBegin().map(p -> p.column).orElse(0);
        findings.add(new Diagnostic(
                ID,
                defaultSeverity(),
                category(),
                new SourceLocation(source.path(), line, column),
                message.get(),
                HELP_URL));
    }

    private static Optional<String> classify(MethodCallExpr call, Set<String> blockingReceivers) {
        if (isThreadSleep(call)) {
            return Optional.of(
                    "Thread.sleep(...) inside @Transactional — holds the database connection for the sleep duration.");
        }
        Optional<String> receiver = receiverName(call);
        if (receiver.isPresent() && blockingReceivers.contains(receiver.get())) {
            return Optional.of("Blocking HTTP call '" + receiver.get() + "." + call.getNameAsString()
                    + "(...)' inside @Transactional — fetch outside the transaction.");
        }
        if (REACTOR_BLOCKING_METHODS.contains(call.getNameAsString())) {
            return Optional.of("Reactor '." + call.getNameAsString()
                    + "()' inside @Transactional — synchronous wait on async work.");
        }
        return Optional.empty();
    }

    private static boolean isThreadSleep(MethodCallExpr call) {
        if (!"sleep".equals(call.getNameAsString())) {
            return false;
        }
        return call.getScope()
                .filter(scope -> scope instanceof NameExpr name && "Thread".equals(name.getNameAsString()))
                .isPresent();
    }

    private static Optional<String> receiverName(MethodCallExpr call) {
        return call.getScope().filter(scope -> scope instanceof NameExpr).map(scope -> ((NameExpr) scope)
                .getNameAsString());
    }
}

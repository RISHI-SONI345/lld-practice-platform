package com.lldpractice.lld_practice_platform.parser.javacode;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.Problem;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.SwitchStmt;
import com.github.javaparser.ast.stmt.UnparsableStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.printer.DefaultPrettyPrinter;
import com.github.javaparser.printer.configuration.DefaultConfigurationOption;
import com.github.javaparser.printer.configuration.DefaultPrinterConfiguration;
import com.lldpractice.lld_practice_platform.model.*;
import com.lldpractice.lld_practice_platform.parser.ParseResult;
import com.lldpractice.lld_practice_platform.parser.SubmissionParser;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/**
 * Reads Java source with JavaParser and builds a DesignModel from names and structure only.
 * It never compiles, resolves symbols, or runs code, and it never throws on bad input.
 */
@Component
public class JavaCodeParser implements SubmissionParser {
    private static final Set<String> SINGLE_ARG = Set.of("List", "Set", "Collection", "Queue", "Deque", "Optional");
    private static final Set<String> MAP_LIKE = Set.of("Map");

    // JavaParser and its pretty printer keep mutable state, so one instance must never be used by two threads at once.
    // Submissions are parsed from request threads and from the background evaluation thread, so each thread gets its own.
    private final ThreadLocal<JavaParser> parser = ThreadLocal.withInitial(() -> new JavaParser(
            new ParserConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17)));
    private final ThreadLocal<DefaultPrettyPrinter> bodyPrinter = ThreadLocal.withInitial(() ->
            new DefaultPrettyPrinter(new DefaultPrinterConfiguration()
                    .removeOption(new DefaultConfigurationOption(DefaultPrinterConfiguration.ConfigOption.PRINT_COMMENTS))));

    @Override
    public String format() {
        return Submission.JAVA;
    }

    @Override
    public ParseResult parse(Submission submission) {
        List<ParseIssue> issues = new ArrayList<>();
        try {
            return doParse(submission, issues);
        } catch (RuntimeException e) {
            issues.add(new ParseIssue("", 0, Severity.HIGH, "Parser failed unexpectedly: " + e));
            return result(DesignModel.empty(), issues);
        }
    }

    private record Decl(String file, TypeDeclaration<?> type) {
    }

    private ParseResult doParse(Submission submission, List<ParseIssue> issues) {
        if (submission == null || !submission.hasContent()) {
            issues.add(new ParseIssue("", 0, Severity.HIGH, "The submission is empty: no Java code was provided."));
            return result(DesignModel.empty(), issues);
        }

        // Pass 1: parse every file and collect top-level type declarations.
        List<Decl> decls = new ArrayList<>();
        Set<String> known = new LinkedHashSet<>();
        for (SourceFile f : submission.files()) {
            if (f.content() == null || f.content().isBlank()) continue;
            try {
                CompilationUnit cu = parseFile(f, issues);
                if (cu == null) continue;
                for (TypeDeclaration<?> td : cu.getTypes()) {
                    String name = td.getNameAsString();
                    if (!known.add(name)) {
                        issues.add(new ParseIssue(f.name(), line(td), Severity.MEDIUM,
                                "Duplicate class name '" + name + "' ignored; the first one wins."));
                        continue;
                    }
                    decls.add(new Decl(f.name(), td));
                }
            } catch (RuntimeException e) {
                issues.add(new ParseIssue(f.name(), 0, Severity.HIGH, "Could not read this file: " + e));
            }
        }

        // Pass 2: build nodes and relationships now that all class names are known.
        Map<String, ClassNode> classes = new LinkedHashMap<>();
        Set<String> edgeKeys = new LinkedHashSet<>();
        List<Relationship> rels = new ArrayList<>();
        for (Decl d : decls) {
            try {
                classes.put(d.type().getNameAsString(), buildClass(d.file(), d.type(), known, rels, edgeKeys, issues));
            } catch (RuntimeException e) {
                issues.add(new ParseIssue(d.file(), line(d.type()), Severity.MEDIUM,
                        "Could not read class '" + d.type().getNameAsString() + "': " + e));
            }
        }
        return result(new DesignModel(classes, rels, issues), issues);
    }

    private ParseResult result(DesignModel model, List<ParseIssue> issues) {
        DesignModel m = new DesignModel(model.classes(), model.relationships(), issues);
        return new ParseResult(m, List.copyOf(issues));
    }

    /** Returns the compilation unit, or null if the file must be skipped (and says why in issues). */
    private CompilationUnit parseFile(SourceFile f, List<ParseIssue> issues) {
        com.github.javaparser.ParseResult<CompilationUnit> r = parser.get().parse(f.content());
        Optional<CompilationUnit> cu = r.getResult();
        if (r.isSuccessful() && cu.isPresent()) return cu.get();

        List<Problem> problems = r.getProblems();
        boolean statementLevelOnly = cu.isPresent() && !cu.get().findAll(UnparsableStmt.class).isEmpty();
        if (cu.isPresent() && !cu.get().getTypes().isEmpty() && statementLevelOnly) {
            for (Problem p : problems) {
                issues.add(new ParseIssue(f.name(), problemLine(p), Severity.MEDIUM,
                        "Syntax problem inside a method body (structure still analysed): "
                                + p.getMessage().lines().findFirst().orElse("")));
            }
            return cu.get();
        }
        String first = problems.isEmpty() ? "unknown syntax error"
                : problems.get(0).getMessage().lines().findFirst().orElse("syntax error");
        issues.add(new ParseIssue(f.name(), problems.isEmpty() ? 0 : problemLine(problems.get(0)), Severity.HIGH,
                "File skipped because of a syntax error outside a method body: " + first));
        return null;
    }

    private static int problemLine(Problem p) {
        return p.getLocation().flatMap(l -> l.getBegin().getRange()).map(rg -> rg.begin.line).orElse(0);
    }

    private ClassNode buildClass(String file, TypeDeclaration<?> td, Set<String> known, List<Relationship> rels,
                                 Set<String> edgeKeys, List<ParseIssue> issues) {
        String name = td.getNameAsString();
        SourceRef ref = new SourceRef(file, line(td));
        ClassKind kind;
        List<String> ext = new ArrayList<>();
        List<String> impl = new ArrayList<>();
        List<FieldNode> fields = new ArrayList<>();
        List<MethodNode> methods = new ArrayList<>();
        List<String> bodies = new ArrayList<>();
        List<String> constants = new ArrayList<>();

        if (td instanceof ClassOrInterfaceDeclaration cd) {
            kind = cd.isInterface() ? ClassKind.INTERFACE
                    : cd.isAbstract() ? ClassKind.ABSTRACT_CLASS : ClassKind.CLASS;
            cd.getExtendedTypes().forEach(t -> ext.add(t.getNameAsString()));
            cd.getImplementedTypes().forEach(t -> impl.add(t.getNameAsString()));
        } else if (td instanceof EnumDeclaration ed) {
            kind = ClassKind.ENUM;
            ed.getImplementedTypes().forEach(t -> impl.add(t.getNameAsString()));
            ed.getEntries().forEach(e -> constants.add(e.getNameAsString()));
        } else if (td instanceof RecordDeclaration rd) {
            kind = ClassKind.RECORD;
            rd.getImplementedTypes().forEach(t -> impl.add(t.getNameAsString()));
            for (Parameter p : rd.getParameters()) {
                List<String> names = typeNames(p.getType());
                fields.add(new FieldNode(p.getNameAsString(), String.join(",", names), "private", true,
                        p.getType().asString()));
                names.forEach(n -> edge(rels, edgeKeys, known, name, n, RelationshipKind.HAS_A, ref));
            }
        } else {
            issues.add(new ParseIssue(file, line(td), Severity.LOW,
                    "Declaration '" + name + "' is not a class, interface, enum or record and was ignored."));
            kind = ClassKind.CLASS;
        }

        ext.forEach(t -> edge(rels, edgeKeys, known, name, t, RelationshipKind.EXTENDS, ref));
        impl.forEach(t -> edge(rels, edgeKeys, known, name, t, RelationshipKind.IMPLEMENTS, ref));

        boolean inInterface = kind == ClassKind.INTERFACE;
        for (BodyDeclaration<?> member : td.getMembers()) {
            if (member instanceof TypeDeclaration<?> nested) {
                issues.add(new ParseIssue(file, line(nested), Severity.LOW,
                        "Nested type '" + nested.getNameAsString() + "' inside '" + name
                                + "' is not modelled and was ignored."));
            } else if (member instanceof FieldDeclaration fd) {
                for (VariableDeclarator v : fd.getVariables()) {
                    List<String> names = typeNames(v.getType());
                    fields.add(new FieldNode(v.getNameAsString(), String.join(",", names),
                            visibility(fd.getModifiers(), inInterface), fd.isFinal(), v.getType().asString()));
                    SourceRef fref = new SourceRef(file, line(fd));
                    names.forEach(n -> edge(rels, edgeKeys, known, name, n, RelationshipKind.HAS_A, fref));
                }
            } else if (member instanceof CallableDeclaration<?> cdl) {
                methods.add(buildMethod(file, name, cdl, known, rels, edgeKeys, bodies, inInterface));
            }
        }

        String summary = td.getJavadoc().map(j -> firstSentence(j.getDescription().toText())).orElse("");
        List<String> sortedExt = sorted(ext);
        List<String> sortedImpl = sorted(impl);
        String sig = hash(kind + "|" + sortedExt + "|" + sortedImpl + "|"
                + sorted(fields.stream().map(f -> f.visibility() + " " + f.type() + " " + f.name() + " " + f.isFinal()).toList())
                + "|" + sorted(methods.stream().map(JavaCodeParser::methodSignature).toList()));
        String body = hash(String.join("\n--\n", sorted(bodies)));
        return new ClassNode(name, kind, summary, ext, impl, fields, methods, ref, sig, body, constants);
    }

    private MethodNode buildMethod(String file, String owner, CallableDeclaration<?> cdl, Set<String> known,
                                   List<Relationship> rels, Set<String> edgeKeys, List<String> bodies,
                                   boolean inInterface) {
        SourceRef mref = new SourceRef(file, line(cdl));
        List<ParameterNode> params = new ArrayList<>();
        for (Parameter p : cdl.getParameters()) {
            params.add(new ParameterNode(p.getType().asString(), p.getNameAsString()));
            typeNames(p.getType()).forEach(n -> edge(rels, edgeKeys, known, owner, n, RelationshipKind.USES, mref));
        }
        String ret = "";
        Optional<BlockStmt> body = Optional.empty();
        if (cdl instanceof MethodDeclaration md) {
            ret = md.getType().asString();
            typeNames(md.getType()).forEach(n -> edge(rels, edgeKeys, known, owner, n, RelationshipKind.USES, mref));
            body = md.getBody();
        } else if (cdl instanceof ConstructorDeclaration cd) {
            body = Optional.of(cd.getBody());
        }

        MethodSignals signals = MethodSignals.none();
        if (body.isPresent()) {
            BlockStmt b = body.get();
            signals = signalsOf(b);
            for (String t : signals.instantiatedTypes()) {
                edge(rels, edgeKeys, known, owner, t, RelationshipKind.USES, mref);
            }
            for (MethodCallExpr call : b.findAll(MethodCallExpr.class)) {
                call.getScope().filter(Expression::isNameExpr).map(s -> s.asNameExpr().getNameAsString())
                        .ifPresent(n -> edge(rels, edgeKeys, known, owner, n, RelationshipKind.USES, mref));
            }
            bodies.add(methodSignature(cdl.getNameAsString(), params, ret) + " " + bodyPrinter.get().print(b));
        }
        return new MethodNode(cdl.getNameAsString(), ret, params, visibility(cdl.getModifiers(), inInterface),
                signals, mref);
    }

    private MethodSignals signalsOf(BlockStmt b) {
        List<String> selectors = new ArrayList<>();
        b.findAll(SwitchStmt.class).forEach(s -> selectors.add(s.getSelector().toString()));
        b.findAll(SwitchExpr.class).forEach(s -> selectors.add(s.getSelector().toString()));
        int instanceOfs = b.findAll(InstanceOfExpr.class).size();
        List<NumericLiteral> lits = new ArrayList<>();
        b.findAll(IntegerLiteralExpr.class).forEach(l -> lits.add(new NumericLiteral(l.getValue(), line(l))));
        b.findAll(LongLiteralExpr.class).forEach(l -> lits.add(new NumericLiteral(l.getValue(), line(l))));
        b.findAll(DoubleLiteralExpr.class).forEach(l -> lits.add(new NumericLiteral(l.getValue(), line(l))));
        lits.sort(Comparator.comparingInt(NumericLiteral::line));
        List<String> created = new ArrayList<>();
        b.findAll(ObjectCreationExpr.class).forEach(o -> created.add(o.getType().getNameAsString()));
        int lines = b.getRange().map(r -> r.end.line - r.begin.line + 1).orElse(0);
        return new MethodSignals(selectors, instanceOfs, lits, created, lines);
    }

    /** Unwraps collections, Optional and arrays; any other generic keeps only its outer name. */
    static List<String> typeNames(Type t) {
        if (t == null) return List.of();
        if (t.isArrayType()) return typeNames(t.asArrayType().getComponentType());
        if (t.isWildcardType()) {
            var w = t.asWildcardType();
            return w.getExtendedType().or(w::getSuperType).map(JavaCodeParser::typeNames).orElse(List.of());
        }
        if (t.isClassOrInterfaceType()) {
            ClassOrInterfaceType c = t.asClassOrInterfaceType();
            String n = c.getNameAsString();
            Optional<NodeList<Type>> args = c.getTypeArguments();
            if (args.isPresent() && !args.get().isEmpty()) {
                if (SINGLE_ARG.contains(n)) return typeNames(args.get().get(0));
                if (MAP_LIKE.contains(n)) {
                    List<String> out = new ArrayList<>();
                    args.get().forEach(a -> out.addAll(typeNames(a)));
                    return out;
                }
            }
            return List.of(n);
        }
        return List.of(t.asString());
    }

    private static void edge(List<Relationship> rels, Set<String> keys, Set<String> known, String from, String to,
                             RelationshipKind kind, SourceRef ref) {
        if (!known.contains(to)) return;
        if (from.equals(to) && (kind == RelationshipKind.HAS_A || kind == RelationshipKind.USES)) return;
        if (keys.add(from + ">" + to + ">" + kind)) {
            rels.add(new Relationship(from, to, kind, ref));
        }
    }

    private static String visibility(NodeList<Modifier> mods, boolean inInterface) {
        for (Modifier m : mods) {
            switch (m.getKeyword()) {
                case PUBLIC -> {
                    return "public";
                }
                case PROTECTED -> {
                    return "protected";
                }
                case PRIVATE -> {
                    return "private";
                }
                default -> {
                }
            }
        }
        return inInterface ? "public" : "package";
    }

    private static String methodSignature(MethodNode m) {
        return methodSignature(m.name(), m.parameters(), m.returnType()) + " " + m.visibility();
    }

    private static String methodSignature(String name, List<ParameterNode> params, String ret) {
        return name + "(" + String.join(",", params.stream().map(ParameterNode::type).toList()) + "):" + ret;
    }

    private static List<String> sorted(List<String> in) {
        List<String> out = new ArrayList<>(in);
        Collections.sort(out);
        return out;
    }

    private static String firstSentence(String text) {
        String t = text == null ? "" : text.strip().replaceAll("\\s+", " ");
        int i = t.indexOf(". ");
        if (i >= 0) return t.substring(0, i + 1);
        return t;
    }

    private static int line(com.github.javaparser.ast.Node n) {
        return n.getBegin().map(p -> p.line).orElse(0);
    }

    private static String hash(String s) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(d);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}

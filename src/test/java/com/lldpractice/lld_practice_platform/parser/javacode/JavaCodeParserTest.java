package com.lldpractice.lld_practice_platform.parser.javacode;

import com.lldpractice.lld_practice_platform.model.*;
import com.lldpractice.lld_practice_platform.parser.ParseResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JavaCodeParserTest {
    private final JavaCodeParser parser = new JavaCodeParser();

    static Submission sub(String... nameAndContent) {
        java.util.ArrayList<SourceFile> files = new java.util.ArrayList<>();
        for (int i = 0; i < nameAndContent.length; i += 2) {
            files.add(new SourceFile(nameAndContent[i], nameAndContent[i + 1]));
        }
        return new Submission(Submission.JAVA, files, "", Rationale.empty());
    }

    private ParseResult parse(String... nc) {
        return parser.parse(sub(nc));
    }

    @Test
    void mapsClassInterfaceEnumAndRecordKinds() {
        ParseResult r = parse("A.java", """
                /** A vehicle. It moves. */
                public abstract class Vehicle {}
                interface Pricing { double fee(int hours); }
                enum Size { SMALL, LARGE }
                record Ticket(String id) {}
                """);
        assertThat(r.model().find("Vehicle").get().kind()).isEqualTo(ClassKind.ABSTRACT_CLASS);
        assertThat(r.model().find("Vehicle").get().summary()).isEqualTo("A vehicle.");
        assertThat(r.model().find("Pricing").get().kind()).isEqualTo(ClassKind.INTERFACE);
        assertThat(r.model().find("Size").get().kind()).isEqualTo(ClassKind.ENUM);
        assertThat(r.model().find("Ticket").get().kind()).isEqualTo(ClassKind.RECORD);
        assertThat(r.usable()).isTrue();
    }

    @Test
    void collectionFieldGivesHasAndParameterAndNewGiveUses() {
        ParseResult r = parse("A.java", """
                import java.util.*;
                class Spot {}
                class Ticket {}
                class Floor { private List<Spot> spots = new ArrayList<>(); }
                class Lot {
                    Ticket park(Floor f) { return new Ticket(); }
                }
                """);
        DesignModel m = r.model();
        assertThat(m.relationships()).anyMatch(x -> x.from().equals("Floor") && x.to().equals("Spot")
                && x.kind() == RelationshipKind.HAS_A);
        assertThat(m.relationships()).anyMatch(x -> x.from().equals("Lot") && x.to().equals("Floor")
                && x.kind() == RelationshipKind.USES);
        assertThat(m.relationships()).anyMatch(x -> x.from().equals("Lot") && x.to().equals("Ticket")
                && x.kind() == RelationshipKind.USES);
        assertThat(m.dependenciesOf("Floor")).containsExactly("Spot");
    }

    @Test
    void mapUnwrapsBothArgumentsButOtherGenericsKeepOuterName() {
        ParseResult r = parse("A.java", """
                import java.util.*;
                class Car {}
                class Spot {}
                class Box<T> {}
                class Lot {
                    Map<Car, Spot> assigned;
                    Box<Car> box;
                }
                """);
        assertThat(r.model().dependenciesOf("Lot")).containsExactlyInAnyOrder("Car", "Spot", "Box");
        assertThat(r.model().relationships()).noneMatch(x -> x.from().equals("Box"));
    }

    @Test
    void jdkAndLibraryTypesNeverBecomeRelationships() {
        ParseResult r = parse("A.java", "class Lot { String name; java.time.Instant at; }");
        assertThat(r.model().relationships()).isEmpty();
    }

    @Test
    void extendsAndImplementsAreRecordedWhenTargetInSubmission() {
        ParseResult r = parse("A.java", """
                interface Pricing {}
                class Base {}
                class Hourly extends Base implements Pricing, Comparable<Hourly> {}
                """);
        assertThat(r.model().relationships()).extracting(Relationship::kind, Relationship::to)
                .contains(org.assertj.core.groups.Tuple.tuple(RelationshipKind.EXTENDS, "Base"),
                        org.assertj.core.groups.Tuple.tuple(RelationshipKind.IMPLEMENTS, "Pricing"));
        assertThat(r.model().implementersOf("Pricing")).containsExactly("Hourly");
    }

    @Test
    void emptySubmissionGivesEmptyModelAndOneIssue() {
        ParseResult r = parse("A.java", "   \n  ");
        assertThat(r.model().classes()).isEmpty();
        assertThat(r.issues()).hasSize(1);
        assertThat(r.usable()).isFalse();
    }

    @Test
    void noFilesAtAllIsAlsoEmpty() {
        ParseResult r = parser.parse(new Submission(Submission.JAVA, List.of(), "", null));
        assertThat(r.usable()).isFalse();
        assertThat(r.issues()).hasSize(1);
    }

    @Test
    void syntaxErrorInsideMethodBodyKeepsStructureAndWarns() {
        ParseResult r = parse("A.java", """
                class Lot {
                    int cap;
                    void park() {
                        int x = ;
                    }
                    void unpark() {}
                }
                """);
        assertThat(r.model().find("Lot")).isPresent();
        assertThat(r.model().hasMethod("Lot", "park")).isTrue();
        assertThat(r.model().hasMethod("Lot", "unpark")).isTrue();
        assertThat(r.issues()).isNotEmpty();
        assertThat(r.issues().get(0).message()).contains("method body");
    }

    @Test
    void classLevelSyntaxErrorSkipsThatFileButParsesTheOther() {
        ParseResult r = parse(
                "Bad.java", "class Bad { void ( { }",
                "Good.java", "class Good { void ok() {} }");
        assertThat(r.model().find("Good")).isPresent();
        assertThat(r.model().find("Bad")).isEmpty();
        assertThat(r.issues()).anyMatch(i -> i.file().equals("Bad.java") && i.severity() == Severity.HIGH);
    }

    @Test
    void onlyBrokenFilesGivesUnusableModel() {
        ParseResult r = parse("Bad.java", "class { }}} ???");
        assertThat(r.usable()).isFalse();
        assertThat(r.issues()).isNotEmpty();
    }

    @Test
    void nestedTypeIsIgnoredWithAnIssue() {
        ParseResult r = parse("A.java", "class Outer { static class Inner {} void f() {} }");
        assertThat(r.model().find("Inner")).isEmpty();
        assertThat(r.issues()).anyMatch(i -> i.message().contains("Inner") && i.message().contains("ignored"));
        assertThat(r.model().hasMethod("Outer", "f")).isTrue();
    }

    @Test
    void duplicateClassNamesAreReportedAndFirstWins() {
        ParseResult r = parse("A.java", "class Dup { void a() {} }", "B.java", "class Dup { void b() {} }");
        assertThat(r.model().hasMethod("Dup", "a")).isTrue();
        assertThat(r.model().hasMethod("Dup", "b")).isFalse();
        assertThat(r.issues()).anyMatch(i -> i.message().contains("Duplicate"));
    }

    @Test
    void unknownTypesAndMissingImportsAreIgnoredNotErrors() {
        ParseResult r = parse("A.java", "class Lot { Mystery m; void f(Unknown u) { u.go(); } }");
        assertThat(r.issues()).isEmpty();
        assertThat(r.model().relationships()).isEmpty();
    }

    @Test
    void methodSignalsAreExtracted() {
        ParseResult r = parse("A.java", """
                class Vehicle {}
                class Fee {
                    double calc(Object o, int type) {
                        switch (type) { case 1: return 5; default: return 20.5; }
                    }
                    boolean is(Object o) {
                        if (o instanceof String && o instanceof Integer) return true;
                        Vehicle v = new Vehicle();
                        return false;
                    }
                }
                """);
        MethodNode calc = r.model().find("Fee").get().methods().get(0);
        assertThat(calc.signals().switchSelectors()).containsExactly("type");
        assertThat(calc.signals().numericLiterals()).extracting(NumericLiteral::value).contains("5", "20.5");
        MethodNode is = r.model().find("Fee").get().methods().get(1);
        assertThat(is.signals().instanceofCount()).isEqualTo(2);
        assertThat(is.signals().instantiatedTypes()).containsExactly("Vehicle");
        assertThat(r.model().dependenciesOf("Fee")).contains("Vehicle");
    }

    @Test
    void staticCallOnKnownClassIsAUsesEdge() {
        ParseResult r = parse("A.java", "class Util { static int f() { return 2; } } class Lot { int g() { return Util.f(); } }");
        assertThat(r.model().dependenciesOf("Lot")).containsExactly("Util");
    }

    @Test
    void bodyOnlyEditChangesBodyHashButNotSignatureHash() {
        ClassNode a = parse("A.java", "class C { int f() { return 1; } }").model().find("C").get();
        ClassNode b = parse("A.java", "class C { int f() { return 2; } }").model().find("C").get();
        assertThat(b.signatureHash()).isEqualTo(a.signatureHash());
        assertThat(b.bodyHash()).isNotEqualTo(a.bodyHash());
    }

    @Test
    void commentAndWhitespaceOnlyEditChangesNeitherHash() {
        ClassNode a = parse("A.java", "class C { int f() { return 1; } }").model().find("C").get();
        ClassNode b = parse("A.java", """
                class C {
                    // explains f
                    int f() {
                        /* inline */
                        return   1;   // done
                    }
                }
                """).model().find("C").get();
        assertThat(b.signatureHash()).isEqualTo(a.signatureHash());
        assertThat(b.bodyHash()).isEqualTo(a.bodyHash());
    }

    @Test
    void addingAMethodChangesSignatureHash() {
        ClassNode a = parse("A.java", "class C { int f() { return 1; } }").model().find("C").get();
        ClassNode b = parse("A.java", "class C { int f() { return 1; } int g() { return 1; } }").model().find("C").get();
        assertThat(b.signatureHash()).isNotEqualTo(a.signatureHash());
    }

    @Test
    void javaSeventeenFeaturesParse() {
        ParseResult r = parse("A.java", """
                class C {
                    String f(int x) { return switch (x) { case 1 -> "a"; default -> "b"; }; }
                }
                """);
        assertThat(r.issues()).isEmpty();
        assertThat(r.model().find("C").get().methods().get(0).signals().switchSelectors()).containsExactly("x");
    }

    @Test
    void modelIsImmutable() {
        DesignModel m = parse("A.java", "class C {}").model();
        org.junit.jupiter.api.Assertions.assertThrows(UnsupportedOperationException.class,
                () -> m.classes().put("X", null));
    }

    @Test
    void fieldsKeepTheTypeAsWrittenSoAListIsNotMistakenForASingleObject() {
        ClassNode c = parse("A.java", "import java.util.*; class Car {} class Lot { private final List<Car> cars; private TreeSet<Integer> stops; private Car one; }")
                .model().find("Lot").get();
        assertThat(c.fields()).extracting(FieldNode::name, FieldNode::declaredType)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("cars", "List<Car>"),
                        org.assertj.core.groups.Tuple.tuple("stops", "TreeSet<Integer>"),
                        org.assertj.core.groups.Tuple.tuple("one", "Car"));
        assertThat(c.fields().get(0).type()).isEqualTo("Car");
    }

    @Test
    void enumConstantsAreRecordedAndShownToTheReviewer() {
        ClassNode e = parse("A.java", "enum CarState { IDLE, MOVING_UP, DOORS_OPEN }").model().find("CarState").get();
        assertThat(e.enumConstants()).containsExactly("IDLE", "MOVING_UP", "DOORS_OPEN");
        org.assertj.core.api.Assertions.assertThat(com.lldpractice.lld_practice_platform.llm.ModelTextRenderer.render(
                parse("A.java", "enum CarState { IDLE, DOORS_OPEN }").model())).contains("constants IDLE, DOORS_OPEN");
    }

    @Test
    void parsingFromManyThreadsAtOnceGivesTheSameModelEveryTime() throws Exception {
        String code = """
                import java.util.*;
                class Spot {}
                class Lot {
                    private final List<Spot> spots = new ArrayList<>();
                    int count(int x) { switch (x) { case 1: return 5; default: return 20; } }
                    boolean has(Object o) { return o instanceof Spot && spots.size() > 3; }
                }
                """;
        Submission s = sub("A.java", code);
        String expected = parser.parse(s).model().find("Lot").get().bodyHash();
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(8);
        java.util.List<java.util.concurrent.Future<String>> results = new java.util.ArrayList<>();
        for (int i = 0; i < 400; i++) {
            results.add(pool.submit(() -> {
                ParseResult r = parser.parse(s);
                return r.issues().isEmpty() && r.model().find("Lot").isPresent()
                        ? r.model().find("Lot").get().bodyHash() : "BROKEN " + r.issues();
            }));
        }
        for (var f : results) assertThat(f.get()).isEqualTo(expected);
        pool.shutdown();
    }
}

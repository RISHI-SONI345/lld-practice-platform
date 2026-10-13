package com.lldpractice.lld_practice_platform.evaluation.rules;

import com.lldpractice.lld_practice_platform.evaluation.RuleThresholds;
import com.lldpractice.lld_practice_platform.model.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.lldpractice.lld_practice_platform.evaluation.TestSupport.model;
import static com.lldpractice.lld_practice_platform.evaluation.TestSupport.problem;
import static org.assertj.core.api.Assertions.assertThat;

class RulesTest {
    private final RuleThresholds thresholds = new RuleThresholds();

    // ---- GOD_CLASS
    @Test
    void godClassFiresOnTooManyMethods() {
        String methods = IntStream.range(0, 13).mapToObj(i -> "void m" + i + "() {}").collect(Collectors.joining("\n"));
        List<Finding> f = new GodClassRule(thresholds).check(model("class Big {" + methods + "}"), problem());
        assertThat(f).hasSize(1);
        assertThat(f.get(0).subject()).isEqualTo("Big");
        assertThat(f.get(0).severity()).isEqualTo(Severity.MEDIUM);
        assertThat(f.get(0).kind()).isEqualTo(FindingKind.FACT);
    }

    @Test
    void godClassFiresOnTooManyFields() {
        String fields = IntStream.range(0, 9).mapToObj(i -> "int f" + i + ";").collect(Collectors.joining());
        assertThat(new GodClassRule(thresholds).check(model("class Big {" + fields + "}"), problem())).hasSize(1);
    }

    @Test
    void godClassDoesNotFireAtTheLimitAndIgnoresConstructors() {
        String methods = IntStream.range(0, 12).mapToObj(i -> "void m" + i + "() {}").collect(Collectors.joining("\n"));
        assertThat(new GodClassRule(thresholds).check(model("class Ok { Ok() {} " + methods + "}"), problem())).isEmpty();
    }

    // ---- TYPE_SWITCH
    @Test
    void typeSwitchFiresOnSwitchOverKind() {
        List<Finding> f = new TypeSwitchRule(thresholds).check(model(
                "class Fee { int f(int vehicleKind) { switch (vehicleKind) { case 1: return 2; default: return 3; } } }"),
                problem());
        assertThat(f).hasSize(1);
        assertThat(f.get(0).subject()).isEqualTo("Fee.f");
    }

    @Test
    void typeSwitchFiresOnTwoInstanceOfChecks() {
        assertThat(new TypeSwitchRule(thresholds).check(model(
                "class Fee { int f(Object o) { if (o instanceof String) return 1; if (o instanceof Integer) return 2; return 3; } }"),
                problem())).hasSize(1);
    }

    @Test
    void typeSwitchIgnoresOtherSwitchesAndSingleInstanceOf() {
        assertThat(new TypeSwitchRule(thresholds).check(model(
                "class Fee { int f(int day, Object o) { boolean b = o instanceof String; switch (day) { case 1: return 2; default: return 3; } } }"),
                problem())).isEmpty();
    }

    // ---- HARDCODED_VALUE
    @Test
    void hardcodedValueFiresInsideAWatchedCapabilityMethod() {
        List<Finding> f = new HardcodedValueRule().check(model(
                "class Lot { double computeFee(int hours) { return hours * 50; } }"), problem());
        assertThat(f).hasSize(1);
        assertThat(f.get(0).subject()).isEqualTo("Lot.computeFee");
        assertThat(f.get(0).message()).contains("50");
    }

    @Test
    void hardcodedValueAllowsZeroOneAndMinusOne() {
        assertThat(new HardcodedValueRule().check(model(
                "class Lot { int computeFee(int h) { if (h == 0) return -1; return h + 1; } }"), problem())).isEmpty();
    }

    @Test
    void hardcodedValueIgnoresMethodsOutsideWatchedCapabilities() {
        assertThat(new HardcodedValueRule().check(model(
                "class Lot { int parkSpot(int h) { return h * 50; } }"), problem())).isEmpty();
    }

    // ---- CONCRETE_DEPENDENCY
    @Test
    void concreteDependencyFiresWhenInterfaceExistsButIsNotUsed() {
        List<Finding> f = new ConcreteDependencyRule().check(model("""
                interface Pricing { double fee(); }
                class HourlyPricing implements Pricing { public double fee() { return 1; } }
                class Lot { private HourlyPricing pricing; }
                """), problem());
        assertThat(f).hasSize(1);
        assertThat(f.get(0).subject()).isEqualTo("Lot");
        assertThat(f.get(0).severity()).isEqualTo(Severity.LOW);
    }

    @Test
    void concreteDependencyQuietWhenTheInterfaceIsAlsoReferenced() {
        assertThat(new ConcreteDependencyRule().check(model("""
                interface Pricing { double fee(); }
                class HourlyPricing implements Pricing { public double fee() { return 1; } }
                class Lot { private HourlyPricing a; private Pricing b; }
                """), problem())).isEmpty();
    }

    @Test
    void concreteDependencyQuietWhenConcreteClassHasNoInterface() {
        assertThat(new ConcreteDependencyRule().check(model("class Spot {} class Lot { Spot s; }"), problem())).isEmpty();
    }

    // ---- DEPENDENCY_CYCLE
    @Test
    void cycleFoundForThreeClasses() {
        List<Finding> f = new DependencyCycleRule().check(model("class A { B b; } class B { C c; } class C { A a; }"), problem());
        assertThat(f).hasSize(1);
        assertThat(f.get(0).subject()).isEqualTo("A");
        assertThat(f.get(0).message()).contains("A, B, C");
    }

    @Test
    void noCycleForChainOrSelfReference() {
        assertThat(new DependencyCycleRule().check(model("class A { B b; } class B { C c; } class C { C next; }"), problem())).isEmpty();
    }

    @Test
    void cycleRuleOnEmptyModelGivesNothing() {
        assertThat(new DependencyCycleRule().check(DesignModel.empty(), problem())).isEmpty();
    }

    // ---- ORPHAN_OR_UNUSED
    @Test
    void orphanClassAndUnimplementedInterfaceFire() {
        List<Finding> f = new OrphanOrUnusedRule().check(model("class Lonely {} interface Ghost {} class A { B b; } class B {}"), problem());
        assertThat(f).extracting(Finding::subject).containsExactlyInAnyOrder("Lonely", "Ghost");
    }

    @Test
    void entryTypeIsExemptFromOrphanRule() {
        assertThat(new OrphanOrUnusedRule().check(model("class ParkingLotService {}"), problem())).isEmpty();
    }

    // ---- CAPABILITY_OWNERSHIP
    @Test
    void missingCapabilityIsHighAndPresentOnesAreInfoNamingTheOwner() {
        List<Finding> f = new CapabilityOwnershipRule().check(model("""
                class ParkingLotService { public void park(Object v) {} }
                class Allocator { int assignSpot(Object v) { return 1; } }
                class Billing { double calculateFee(int h) { return h; } }
                """), problem());
        assertThat(f).filteredOn(x -> x.severity() == Severity.HIGH).hasSize(1)
                .first().satisfies(x -> {
                    assertThat(x.message()).contains("track capacity");
                    assertThat(x.subject()).isEqualTo("ParkingLotService");
                });
        assertThat(f).filteredOn(x -> x.severity() == Severity.INFO).extracting(Finding::subject)
                .containsExactlyInAnyOrder("Allocator.assignSpot", "Billing.calculateFee");
    }

    @Test
    void theEntryTypesOwnPublicMethodIsNotAnOwner() {
        List<Finding> f = new CapabilityOwnershipRule().check(model("""
                class ParkingLotService { public void park(Object v) {} }
                """), problem());
        assertThat(f).filteredOn(x -> x.discriminator().equals("assign-spot"))
                .singleElement().satisfies(x -> assertThat(x.severity()).isEqualTo(Severity.HIGH));
    }

    @Test
    void aSameNamedMethodInAnotherClassCountsAsTheDelegationItIs() {
        List<Finding> f = new CapabilityOwnershipRule().check(model("""
                class ParkingLotService { public void park(Object v) {} }
                class Allocator { void park(Object v) {} }
                """), problem());
        assertThat(f).filteredOn(x -> x.discriminator().equals("assign-spot"))
                .singleElement().satisfies(x -> {
                    assertThat(x.severity()).isEqualTo(Severity.INFO);
                    assertThat(x.subject()).isEqualTo("Allocator.park");
                });
    }

    @Test
    void aPrivateHelperInTheEntryTypeCanOwnACapability() {
        List<Finding> f = new CapabilityOwnershipRule().check(model("""
                class ParkingLotService { public void park(Object v) {} private double computeFee(int h) { return h; } }
                """), problem());
        assertThat(f).filteredOn(x -> x.discriminator().equals("compute-fee"))
                .singleElement().satisfies(x -> assertThat(x.subject()).isEqualTo("ParkingLotService.computeFee"));
    }

    @Test
    void unparkDoesNotMatchParkHintButAssignSpotMatchesAssign() {
        assertThat(com.lldpractice.lld_practice_platform.evaluation.CapabilityMatcher.matches("unpark", List.of("park"))).isFalse();
        assertThat(com.lldpractice.lld_practice_platform.evaluation.CapabilityMatcher.matches("assignSpot", List.of("assign"))).isTrue();
        assertThat(com.lldpractice.lld_practice_platform.evaluation.CapabilityMatcher.matches("getAvailableSpots", List.of("available"))).isTrue();
    }

    @Test
    void twoMissingCapabilitiesHaveDifferentFingerprints() {
        List<Finding> f = new CapabilityOwnershipRule().check(model("class ParkingLotService {}"), problem());
        assertThat(f).extracting(Finding::fingerprint).doesNotHaveDuplicates();
    }

    @Test
    void capabilityRuleOnEmptyModelGivesNothing() {
        assertThat(new CapabilityOwnershipRule().check(DesignModel.empty(), problem())).isEmpty();
    }

    // ---- CAPABILITY_OWNERSHIP: handled implicitly, not by a method named after it
    @Test
    void anEnumConstantThatNamesTheCapabilityGivesMediumNotHigh() {
        List<Finding> f = new CapabilityOwnershipRule().check(model("""
                class ParkingLotService { public void park(Object v) {} }
                enum SpotState { FREE, ASSIGNED }
                """), problem());
        assertThat(f).filteredOn(x -> x.discriminator().equals("assign-spot")).singleElement().satisfies(x -> {
            assertThat(x.severity()).isEqualTo(Severity.MEDIUM);
            assertThat(x.subject()).isEqualTo("SpotState");
            assertThat(x.message()).contains("SpotState.ASSIGNED").contains("handled inside another method");
        });
    }

    @Test
    void aFieldOrAClassNamedAfterTheCapabilityAlsoCounts() {
        List<Finding> f = new CapabilityOwnershipRule().check(model("""
                class ParkingLotService { public void park(Object v) {} private int availableCount; }
                """), problem());
        assertThat(f).filteredOn(x -> x.discriminator().equals("track-capacity")).singleElement().satisfies(x -> {
            assertThat(x.severity()).isEqualTo(Severity.MEDIUM);
            assertThat(x.message()).contains("ParkingLotService.availableCount");
        });
        List<Finding> g = new CapabilityOwnershipRule().check(model("""
                class ParkingLotService { public void park(Object v) {} }
                class PriceTable { }
                """), problem());
        assertThat(g).filteredOn(x -> x.discriminator().equals("compute-fee")).singleElement()
                .satisfies(x -> assertThat(x.message()).contains("the class PriceTable"));
    }

    @Test
    void withNoEvidenceAnywhereItIsStillHigh() {
        List<Finding> f = new CapabilityOwnershipRule().check(model("class ParkingLotService { public void park(Object v) {} }"), problem());
        assertThat(f).filteredOn(x -> x.discriminator().equals("track-capacity")).singleElement()
                .satisfies(x -> assertThat(x.severity()).isEqualTo(Severity.HIGH));
    }

    @Test
    void aRealMethodStillBeatsImplicitEvidence() {
        List<Finding> f = new CapabilityOwnershipRule().check(model("""
                class ParkingLotService { public void park(Object v) {} }
                class Lot { private int available; int availableSpots() { return 1; } }
                """), problem());
        assertThat(f).filteredOn(x -> x.discriminator().equals("track-capacity")).singleElement().satisfies(x -> {
            assertThat(x.severity()).isEqualTo(Severity.INFO);
            assertThat(x.subject()).isEqualTo("Lot.availableSpots");
        });
    }
}

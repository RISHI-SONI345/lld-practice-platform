package com.lldpractice.lld_practice_platform.orchestration;

import com.lldpractice.lld_practice_platform.model.*;
import org.junit.jupiter.api.Test;

import static com.lldpractice.lld_practice_platform.evaluation.TestSupport.model;
import static org.assertj.core.api.Assertions.assertThat;

class ChangeImpactCalculatorTest {
    private final ChangeImpactCalculator calc = new ChangeImpactCalculator(new EvaluationProperties());

    @Test
    void addedModifiedRemovedAndInterfacesTouched() {
        DesignModel base = model("interface Pricing { double fee(); } class A { int f() { return 1; } } class Gone { }");
        DesignModel rev = model("interface Pricing { double fee(int h); } class A { int f() { return 2; } } class New1 {} class New2 {} class New3 {}");
        ChangeImpact i = calc.calculate(base, rev);
        assertThat(i.added()).containsExactlyInAnyOrder("New1", "New2", "New3");
        assertThat(i.modified()).containsExactlyInAnyOrder("Pricing", "A");
        assertThat(i.removed()).containsExactly("Gone");
        assertThat(i.interfacesTouched()).containsExactly("Pricing");
        assertThat(i.verdict()).isEqualTo(ContainmentVerdict.SPREAD);
    }

    @Test
    void nothingModifiedOrRemovedIsAdditive() {
        ChangeImpact i = calc.calculate(model("class A {}"), model("class A {} class B {}"));
        assertThat(i.verdict()).isEqualTo(ContainmentVerdict.ADDITIVE);
    }

    @Test
    void identicalModelsAreAdditiveWithNothingChanged() {
        ChangeImpact i = calc.calculate(model("class A {}"), model("class A {}"));
        assertThat(i.added()).isEmpty();
        assertThat(i.verdict()).isEqualTo(ContainmentVerdict.ADDITIVE);
    }

    @Test
    void twoTouchedWithAtLeastAsManyAddedIsContained() {
        ChangeImpact i = calc.calculate(
                model("class A { int f() { return 1; } } class B { int g() { return 1; } }"),
                model("class A { int f() { return 2; } } class B { int g() { return 2; } } class C {} class D {}"));
        assertThat(i.verdict()).isEqualTo(ContainmentVerdict.CONTAINED);
    }

    @Test
    void oneTouchedButNothingAddedIsSpread() {
        ChangeImpact i = calc.calculate(model("class A { int f() { return 1; } }"), model("class A { int f() { return 2; } }"));
        assertThat(i.verdict()).isEqualTo(ContainmentVerdict.SPREAD);
    }

    @Test
    void threeTouchedIsSpreadEvenWithManyAdded() {
        ChangeImpact i = calc.calculate(
                model("class A { int f() { return 1; } } class B { int g() { return 1; } } class C { int h() { return 1; } }"),
                model("class A { int f() { return 2; } } class B { int g() { return 2; } } class C { int h() { return 2; } } class D {} class E {} class F {} class G {}"));
        assertThat(i.verdict()).isEqualTo(ContainmentVerdict.SPREAD);
    }

    @Test
    void aRenamedClassCountsAsOneRemovedAndOneAdded() {
        ChangeImpact i = calc.calculate(model("class Old { }"), model("class Fresh { }"));
        assertThat(i.removed()).containsExactly("Old");
        assertThat(i.added()).containsExactly("Fresh");
        assertThat(i.verdict()).isEqualTo(ContainmentVerdict.CONTAINED);
    }

    @Test
    void commentOnlyChangeIsNotAModification() {
        ChangeImpact i = calc.calculate(model("class A { int f() { return 1; } }"),
                model("class A { /* note */ int f() { return 1; } // hi\n }"));
        assertThat(i.modified()).isEmpty();
    }
}

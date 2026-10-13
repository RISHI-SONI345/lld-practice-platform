package com.lldpractice.lld_practice_platform.llm;

import com.lldpractice.lld_practice_platform.llm.LlmReviewParser.RawFinding;
import com.lldpractice.lld_practice_platform.model.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static com.lldpractice.lld_practice_platform.evaluation.TestSupport.model;
import static org.assertj.core.api.Assertions.assertThat;

class FindingValidatorTest {
    private final FindingValidator v = new FindingValidator();
    private final DesignModel m = model("class Lot { void park() {} } class Spot {}");

    private static RawFinding raw(String category, String severity, String subject) {
        return new RawFinding(category, severity, subject, "claim", "why", "fix", "tradeoff");
    }

    @Test
    void keepsAValidFindingAndPointsEvidenceAtTheSubject() {
        var out = v.validate(List.of(raw("COHESION", "MEDIUM", "Lot.park")), m, List.of(), "llm-review");
        assertThat(out.kept()).hasSize(1);
        Finding f = out.kept().get(0);
        assertThat(f.kind()).isEqualTo(FindingKind.JUDGMENT);
        assertThat(f.ruleId()).isEqualTo("COHESION");
        assertThat(f.evidence()).hasSize(1);
        assertThat(out.dropped()).isEmpty();
    }

    @Test
    void dropsUnknownClassOrMethodMissingFieldsAndUnknownCategory() {
        var out = v.validate(List.of(
                raw("COHESION", "LOW", "Ghost"),
                raw("COHESION", "LOW", "Lot.fly"),
                new RawFinding("COHESION", "LOW", "Lot", null, "why", "fix", "t"),
                new RawFinding("COHESION", "LOW", "Lot", "c", "why", "fix", null),
                raw("VIBES", "LOW", "Lot"),
                raw("COHESION", "LOW", "Lot")), m, List.of(), "x");
        assertThat(out.kept()).hasSize(1);
        assertThat(out.dropped()).containsEntry("subject not in code", 2).containsEntry("missing field", 2)
                .containsEntry("unknown category", 1);
        assertThat(out.droppedTotal()).isEqualTo(5);
        assertThat(out.describe()).contains("kept 1").contains("dropped 5");
    }

    @Test
    void unknownSeverityBecomesLow() {
        var out = v.validate(List.of(raw("COHESION", "CATASTROPHIC", "Lot")), m, List.of(), "x");
        assertThat(out.kept().get(0).severity()).isEqualTo(Severity.LOW);
    }

    @Test
    void dropsAFindingThatRestatesAFactOnTheSameSubject() {
        Finding fact = new Finding("GOD_CLASS", FindingKind.FACT, Severity.MEDIUM, "Lot", "m", "w", "f", "", List.of(), "rules");
        var out = v.validate(List.of(raw("COHESION", "LOW", "Lot"), raw("TRADEOFF", "LOW", "Lot")), m, List.of(fact), "x");
        assertThat(out.kept()).extracting(Finding::ruleId).containsExactly("TRADEOFF");
        assertThat(out.dropped()).containsEntry("restates a FACT", 1);
    }

    @Test
    void keepsAtMostEightHighestSeverityFirst() {
        List<RawFinding> many = IntStream.range(0, 12)
                .mapToObj(i -> raw(i % 2 == 0 ? "COHESION" : "TRADEOFF", i == 11 ? "HIGH" : "LOW", i < 6 ? "Lot" : "Spot"))
                .toList();
        // 4 unique category+subject pairs exist, so most are duplicates; widen the unique set.
        List<RawFinding> unique = new java.util.ArrayList<>();
        for (String c : List.of("COHESION", "PATTERN_FIT", "EXTENSIBILITY", "ENCAPSULATION", "TRADEOFF"))
            for (String s : List.of("Lot", "Spot")) unique.add(raw(c, c.equals("TRADEOFF") && s.equals("Spot") ? "HIGH" : "LOW", s));
        var out = v.validate(unique, m, List.of(), "x");
        assertThat(out.kept()).hasSize(8);
        assertThat(out.kept().get(0).severity()).isEqualTo(Severity.HIGH);
        assertThat(out.dropped()).containsKey("over the limit of 8");
        assertThat(many).hasSize(12);
    }

    @Test
    void duplicateCategoryAndSubjectIsDropped() {
        var out = v.validate(List.of(raw("COHESION", "LOW", "Lot"), raw("COHESION", "HIGH", "Lot")), m, List.of(), "x");
        assertThat(out.kept()).hasSize(1);
        assertThat(out.dropped()).containsEntry("duplicate", 1);
    }

    @Test
    void emptyInputGivesNothing() {
        var out = v.validate(List.of(), m, List.of(), "x");
        assertThat(out.kept()).isEmpty();
        assertThat(out.describe()).isEqualTo("kept 0, dropped 0");
    }

    @Test
    void changeRequestCategoryIsOnlyAllowedInAChangeRound() {
        var raws = List.of(raw("CHANGE_REQUEST", "MEDIUM", "Lot"));
        var normal = v.validate(raws, m, List.of(), "x", false);
        assertThat(normal.kept()).isEmpty();
        assertThat(normal.dropped()).containsEntry("unknown category", 1);
        assertThat(v.validate(raws, m, List.of(), "x", true).kept()).hasSize(1);
        assertThat(LlmPrompts.categoriesFor(false)).doesNotContain("CHANGE_REQUEST");
        assertThat(LlmPrompts.categoriesFor(true)).contains("CHANGE_REQUEST");
    }
}

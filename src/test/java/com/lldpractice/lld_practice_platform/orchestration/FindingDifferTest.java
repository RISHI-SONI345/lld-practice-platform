package com.lldpractice.lld_practice_platform.orchestration;

import com.lldpractice.lld_practice_platform.model.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FindingDifferTest {
    private final FindingDiffer differ = new FindingDiffer();

    private static Finding f(String rule, String subject, FindingKind kind, String message) {
        return new Finding(rule, kind, Severity.LOW, subject, message, "w", "f", "", List.of(), "e");
    }

    @Test
    void resolvedNewAndPersistingByFingerprint() {
        var prev = List.of(f("A", "X", FindingKind.FACT, "old"), f("B", "Y", FindingKind.FACT, "gone"));
        var cur = List.of(f("A", "X", FindingKind.FACT, "reworded"), f("C", "Z", FindingKind.FACT, "fresh"));
        var d = differ.diff(prev, cur);
        assertThat(d.resolved()).extracting(Finding::ruleId).containsExactly("B");
        assertThat(d.added()).extracting(Finding::ruleId).containsExactly("C");
        assertThat(d.persisting()).extracting(Finding::message).containsExactly("reworded");
        assertThat(d.summary()).isEqualTo("1 resolved, 1 new, 1 persisting");
    }

    @Test
    void rewordedJudgmentStillCountsAsPersisting() {
        var prev = List.of(f("COHESION", "Lot", FindingKind.JUDGMENT, "the lot does too much"));
        var cur = List.of(f("COHESION", "Lot", FindingKind.JUDGMENT, "Lot has several responsibilities"));
        assertThat(differ.diff(prev, cur).persisting()).hasSize(1);
    }

    @Test
    void factAndJudgmentAreDiffedSeparately() {
        var prev = List.of(f("A", "X", FindingKind.FACT, "m"), f("COHESION", "X", FindingKind.JUDGMENT, "m"));
        var cur = List.of(f("A", "X", FindingKind.FACT, "m"));
        assertThat(differ.diff(prev, cur, FindingKind.FACT).persisting()).hasSize(1);
        assertThat(differ.diff(prev, cur, FindingKind.JUDGMENT).resolved()).hasSize(1);
    }

    @Test
    void emptyListsAndDuplicateFingerprints() {
        assertThat(differ.diff(List.of(), List.of()).summary()).isEqualTo("0 resolved, 0 new, 0 persisting");
        var dup = List.of(f("A", "X", FindingKind.FACT, "1"), f("A", "X", FindingKind.FACT, "2"));
        assertThat(differ.diff(dup, dup).persisting()).hasSize(1);
    }
}

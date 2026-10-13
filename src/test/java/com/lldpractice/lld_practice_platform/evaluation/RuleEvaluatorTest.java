package com.lldpractice.lld_practice_platform.evaluation;

import com.lldpractice.lld_practice_platform.model.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.lldpractice.lld_practice_platform.evaluation.TestSupport.model;
import static com.lldpractice.lld_practice_platform.evaluation.TestSupport.problem;
import static org.assertj.core.api.Assertions.assertThat;

class RuleEvaluatorTest {
    private static Rule rule(String id, java.util.function.Supplier<List<Finding>> body) {
        return new Rule() {
            public String id() { return id; }
            public List<Finding> check(DesignModel m, com.lldpractice.lld_practice_platform.problem.Problem p) { return body.get(); }
        };
    }

    @Test
    void aRuleThatThrowsIsSkippedAndNotedWhileOthersStillReport() {
        Finding ok = new Finding("OK_RULE", FindingKind.FACT, Severity.LOW, "A", "m", "w", "f", "", List.of(), "rules");
        RuleEvaluator ev = new RuleEvaluator(List.of(
                rule("BOOM", () -> { throw new IllegalStateException("kaboom"); }),
                rule("OK_RULE", () -> List.of(ok))));
        EvaluationResult r = ev.evaluate(new EvaluationContext(problem(), null, model("class A {}"), List.of(), null));
        assertThat(r.findings()).containsExactly(ok);
        assertThat(r.note()).contains("BOOM skipped").contains("kaboom");
        assertThat(ev.stage()).isEqualTo(Stage.IMMEDIATE);
    }

    @Test
    void emptyModelProducesNoFindingsFromRealRules() {
        RuleEvaluator ev = new RuleEvaluator(List.of(
                new com.lldpractice.lld_practice_platform.evaluation.rules.GodClassRule(new RuleThresholds()),
                new com.lldpractice.lld_practice_platform.evaluation.rules.DependencyCycleRule(),
                new com.lldpractice.lld_practice_platform.evaluation.rules.OrphanOrUnusedRule(),
                new com.lldpractice.lld_practice_platform.evaluation.rules.CapabilityOwnershipRule()));
        assertThat(ev.evaluate(new EvaluationContext(problem(), null, DesignModel.empty(), List.of(), null)).findings()).isEmpty();
    }
}

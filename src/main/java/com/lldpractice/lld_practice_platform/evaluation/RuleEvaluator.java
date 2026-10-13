package com.lldpractice.lld_practice_platform.evaluation;

import com.lldpractice.lld_practice_platform.model.Finding;
import com.lldpractice.lld_practice_platform.model.Stage;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/** The IMMEDIATE evaluator: runs every Rule. A rule that throws is skipped and noted; the others still report. */
@Component
public class RuleEvaluator implements Evaluator {
    public static final String ID = "rules";
    private final List<Rule> rules;

    public RuleEvaluator(List<Rule> rules) {
        this.rules = List.copyOf(rules);
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public Stage stage() {
        return Stage.IMMEDIATE;
    }

    @Override
    public EvaluationResult evaluate(EvaluationContext ctx) {
        List<Finding> findings = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        for (Rule rule : rules) {
            try {
                findings.addAll(rule.check(ctx.model(), ctx.problem()));
            } catch (RuntimeException e) {
                skipped.add(rule.id() + " skipped: " + e);
            }
        }
        String note = skipped.isEmpty() ? rules.size() + " rules ran" : String.join("; ", skipped);
        return new EvaluationResult(findings, List.of(), List.of(), note);
    }
}

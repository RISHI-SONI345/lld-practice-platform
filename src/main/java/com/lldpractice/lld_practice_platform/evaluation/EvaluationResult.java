package com.lldpractice.lld_practice_platform.evaluation;

import com.lldpractice.lld_practice_platform.model.DecisionAssessment;
import com.lldpractice.lld_practice_platform.model.Finding;

import java.util.List;

/** What one evaluator returns: findings plus optional LLM extras and a note for its EvaluatorRun. */
public record EvaluationResult(List<Finding> findings, List<DecisionAssessment> assessments, List<String> strengths,
                               String note) {
    public EvaluationResult {
        findings = findings == null ? List.of() : List.copyOf(findings);
        assessments = assessments == null ? List.of() : List.copyOf(assessments);
        strengths = strengths == null ? List.of() : List.copyOf(strengths);
        note = note == null ? "" : note;
    }

    public static EvaluationResult of(List<Finding> findings) {
        return new EvaluationResult(findings, List.of(), List.of(), "");
    }
}

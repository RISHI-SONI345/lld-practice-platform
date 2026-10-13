package com.lldpractice.lld_practice_platform.model;

import java.util.List;

public record EvaluationReport(Completeness completeness, List<Finding> findings, List<EvaluatorRun> runs,
                               String mermaid, ChangeImpact changeImpact, List<DecisionAssessment> assessments,
                               List<String> strengths) {
    public EvaluationReport {
        findings = findings == null ? List.of() : List.copyOf(findings);
        runs = runs == null ? List.of() : List.copyOf(runs);
        assessments = assessments == null ? List.of() : List.copyOf(assessments);
        strengths = strengths == null ? List.of() : List.copyOf(strengths);
    }

    public static EvaluationReport empty() {
        return new EvaluationReport(Completeness.PARTIAL, List.of(), List.of(), "", null, List.of(), List.of());
    }

    public List<Finding> facts() {
        return findings.stream().filter(f -> f.kind() == FindingKind.FACT).toList();
    }

    public List<Finding> judgments() {
        return findings.stream().filter(f -> f.kind() == FindingKind.JUDGMENT).toList();
    }
}

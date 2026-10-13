package com.lldpractice.lld_practice_platform.evaluation;

import com.lldpractice.lld_practice_platform.model.*;
import com.lldpractice.lld_practice_platform.problem.Problem;

import java.util.List;

/** Everything an evaluator may read. {@code impact} is set only in a change round. */
public record EvaluationContext(Problem problem, Submission submission, DesignModel model, List<Finding> findingsSoFar,
                                ChangeImpact impact) {
    public EvaluationContext {
        findingsSoFar = findingsSoFar == null ? List.of() : List.copyOf(findingsSoFar);
    }

    public EvaluationContext withFindings(List<Finding> findings) {
        return new EvaluationContext(problem, submission, model, findings, impact);
    }
}

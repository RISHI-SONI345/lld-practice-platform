package com.lldpractice.lld_practice_platform.evaluation;

import com.lldpractice.lld_practice_platform.model.Stage;

/** Extension point: a new evaluation approach is a new Evaluator that declares its stage. */
public interface Evaluator {
    String id();

    Stage stage();

    EvaluationResult evaluate(EvaluationContext ctx) throws EvaluationException;
}

package com.lldpractice.lld_practice_platform.model;

import java.time.Duration;

/** A record of what each evaluator did, so a failure is visible and not silent. */
public record EvaluatorRun(String evaluatorId, Stage stage, RunOutcome outcome, int attemptsUsed, Duration duration,
                           String message) {
}

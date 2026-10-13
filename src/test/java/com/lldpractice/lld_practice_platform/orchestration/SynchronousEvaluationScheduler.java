package com.lldpractice.lld_practice_platform.orchestration;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Runs the background step on the calling thread so tests see a known order. */
public class SynchronousEvaluationScheduler implements EvaluationScheduler {
    private final EvaluationOrchestrator orchestrator;
    public final List<UUID> scheduled = new ArrayList<>();

    public SynchronousEvaluationScheduler(EvaluationOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @Override
    public void schedule(UUID attemptId) {
        scheduled.add(attemptId);
        orchestrator.evaluateDeferred(attemptId);
    }
}

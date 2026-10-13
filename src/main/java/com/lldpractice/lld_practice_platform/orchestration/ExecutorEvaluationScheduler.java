package com.lldpractice.lld_practice_platform.orchestration;

import com.lldpractice.lld_practice_platform.orchestration.EvaluationScheduler;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Runs the DEFERRED stage on a small background pool. */
@Component
public class ExecutorEvaluationScheduler implements EvaluationScheduler {
    private final EvaluationOrchestrator orchestrator;
    private final ExecutorService pool = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "deferred-evaluation");
        t.setDaemon(true);
        return t;
    });

    public ExecutorEvaluationScheduler(EvaluationOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @Override
    public void schedule(UUID attemptId) {
        pool.submit(() -> orchestrator.evaluateDeferred(attemptId));
    }

    @PreDestroy
    void shutdown() {
        pool.shutdownNow();
    }
}

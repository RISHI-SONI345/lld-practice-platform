package com.lldpractice.lld_practice_platform.orchestration;

import java.util.UUID;

public interface EvaluationScheduler {
    void schedule(UUID attemptId);
}

package com.lldpractice.lld_practice_platform.orchestration;

import org.springframework.stereotype.Component;

import java.time.Duration;

/** Every tunable default for evaluation in one place. */
@Component
public class EvaluationProperties {
    public Duration llmCallTimeout = Duration.ofSeconds(60);
    public Duration retryBackoff = Duration.ofSeconds(2);
    public int maxCallRetries = 1;
    public Duration deferredDeadline = Duration.ofSeconds(150);
    public int maxFiles = 20;
    public int maxTotalBytes = 200_000;
    public int containedMaxTouched = 2;
}

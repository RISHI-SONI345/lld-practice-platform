package com.lldpractice.lld_practice_platform.orchestration;

import com.lldpractice.lld_practice_platform.attempt.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.List;

/**
 * At startup nothing can be running in this process, so any attempt still SUBMITTED or EVALUATING was interrupted.
 * It moves to PARTIAL with the reason "interrupted", so the retry button works.
 */
@Component
public class StaleEvaluationRecovery {
    private static final Logger log = LoggerFactory.getLogger(StaleEvaluationRecovery.class);
    private final AttemptRepository attempts;
    private final Clock clock;

    public StaleEvaluationRecovery(AttemptRepository attempts, Clock clock) {
        this.attempts = attempts;
        this.clock = clock;
    }

    @EventListener(ApplicationReadyEvent.class)
    public int recover() {
        int n = 0;
        for (Attempt a : attempts.findByStatus(AttemptStatus.SUBMITTED)) {
            a.apply(AttemptEvent.START_EVALUATION, clock.instant());
            interrupt(a);
            n++;
        }
        for (Attempt a : attempts.findByStatus(AttemptStatus.EVALUATING)) {
            interrupt(a);
            n++;
        }
        if (n > 0) log.warn("Recovered {} interrupted attempt(s)", n);
        return n;
    }

    private void interrupt(Attempt a) {
        a.setFailureReason("interrupted");
        a.apply(AttemptEvent.FINISH_PARTIAL, clock.instant());
        attempts.save(a);
    }
}

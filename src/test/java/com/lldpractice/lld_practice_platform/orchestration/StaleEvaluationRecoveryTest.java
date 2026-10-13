package com.lldpractice.lld_practice_platform.orchestration;

import com.lldpractice.lld_practice_platform.attempt.*;
import com.lldpractice.lld_practice_platform.llm.FakeLlmClient;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class StaleEvaluationRecoveryTest {
    @Test
    void attemptsInterruptedByARestartMoveToPartialSoRetryWorks() {
        Harness h = new Harness(new FakeLlmClient().thenReturn(Samples.GOOD_REVIEW_JSON));
        Attempt a = h.service.startAttempt(h.learner, "parking-lot");
        a.replaceSubmission(Samples.goodParkingLot(), Instant.EPOCH);
        a.apply(AttemptEvent.SUBMIT, Instant.EPOCH);
        a.apply(AttemptEvent.START_EVALUATION, Instant.EPOCH);
        h.repo.save(a); // simulates a crash while EVALUATING

        Attempt b = h.service.startAttempt(h.learner, "parking-lot");
        b.replaceSubmission(Samples.goodParkingLot(), Instant.EPOCH);
        b.apply(AttemptEvent.SUBMIT, Instant.EPOCH);
        h.repo.save(b); // simulates a crash between SUBMITTED and EVALUATING

        int n = new StaleEvaluationRecovery(h.repo, Clock.systemUTC()).recover();
        assertThat(n).isEqualTo(2);
        for (Attempt x : new Attempt[]{a, b}) {
            Attempt r = h.repo.find(x.id()).orElseThrow();
            assertThat(r.status()).isEqualTo(AttemptStatus.PARTIAL);
            assertThat(r.failureReason()).isEqualTo("interrupted");
        }
        // Retry now works, and a second retry press is rejected.
        h.service.retry(h.learner, a.id());
        assertThat(h.repo.find(a.id()).orElseThrow().status()).isIn(AttemptStatus.PARTIAL, AttemptStatus.COMPLETED);
    }

    @Test
    void nothingToRecoverIsFine() {
        Harness h = new Harness(new FakeLlmClient().thenReturn("{}"));
        assertThat(new StaleEvaluationRecovery(h.repo, Clock.systemUTC()).recover()).isZero();
    }
}

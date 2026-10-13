package com.lldpractice.lld_practice_platform.attempt;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static com.lldpractice.lld_practice_platform.attempt.AttemptEvent.*;
import static com.lldpractice.lld_practice_platform.attempt.AttemptStatus.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AttemptLifecycleTest {

    private static final Map<AttemptStatus, Map<AttemptEvent, AttemptStatus>> LEGAL = Map.of(
            DRAFT, Map.of(SUBMIT, SUBMITTED),
            SUBMITTED, Map.of(START_EVALUATION, EVALUATING),
            EVALUATING, Map.of(COMPLETE, COMPLETED, FINISH_PARTIAL, PARTIAL, FAIL, FAILED),
            PARTIAL, Map.of(RETRY, EVALUATING),
            COMPLETED, Map.of(),
            FAILED, Map.of());

    @Test
    void everyStatusEventPairBehavesAsTheTableSays() {
        for (AttemptStatus from : AttemptStatus.values()) {
            for (AttemptEvent event : AttemptEvent.values()) {
                AttemptStatus expected = LEGAL.get(from).get(event);
                if (expected != null) {
                    assertThat(AttemptLifecycle.next(from, event)).as("%s + %s", from, event).isEqualTo(expected);
                    assertThat(AttemptLifecycle.canApply(from, event)).isTrue();
                } else {
                    assertThatThrownBy(() -> AttemptLifecycle.next(from, event))
                            .as("%s + %s", from, event)
                            .isInstanceOf(IllegalTransitionException.class);
                    assertThat(AttemptLifecycle.canApply(from, event)).isFalse();
                }
            }
        }
    }

    @Test
    void happyPathDraftToCompleted() {
        Attempt a = newAttempt();
        Instant t = Instant.parse("2026-01-01T00:00:00Z");
        a.apply(SUBMIT, t);
        a.apply(START_EVALUATION, t);
        a.apply(COMPLETE, t.plusSeconds(5));
        assertThat(a.status()).isEqualTo(COMPLETED);
        assertThat(a.updatedAt()).isEqualTo(t.plusSeconds(5));
    }

    @Test
    void partialCanBeRetriedOnceButNotTwice() {
        Attempt a = newAttempt();
        Instant t = Instant.parse("2026-01-01T00:00:00Z");
        a.apply(SUBMIT, t);
        a.apply(START_EVALUATION, t);
        a.apply(FINISH_PARTIAL, t);
        a.apply(RETRY, t);
        assertThat(a.status()).isEqualTo(EVALUATING);
        assertThatThrownBy(() -> a.apply(RETRY, t))
                .isInstanceOf(IllegalTransitionException.class)
                .hasMessageContaining("RETRY")
                .hasMessageContaining("EVALUATING");
    }

    @Test
    void failedIsFinalAndRejectsRetry() {
        Attempt a = newAttempt();
        Instant t = Instant.parse("2026-01-01T00:00:00Z");
        a.apply(SUBMIT, t);
        a.apply(START_EVALUATION, t);
        a.apply(FAIL, t);
        assertThatThrownBy(() -> a.apply(RETRY, t)).isInstanceOf(IllegalTransitionException.class);
    }

    @Test
    void rejectedTransitionLeavesStatusUnchanged() {
        Attempt a = newAttempt();
        assertThatThrownBy(() -> a.apply(COMPLETE, Instant.EPOCH)).isInstanceOf(IllegalTransitionException.class);
        assertThat(a.status()).isEqualTo(DRAFT);
    }

    @Test
    void onlyADraftCanBeEdited() {
        Attempt a = newAttempt();
        a.apply(SUBMIT, Instant.EPOCH);
        assertThatThrownBy(() -> a.replaceSubmission(null, Instant.EPOCH)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void pollingStopsOnlyOnTerminalStatuses() {
        assertThat(COMPLETED.isTerminalForPolling()).isTrue();
        assertThat(PARTIAL.isTerminalForPolling()).isTrue();
        assertThat(FAILED.isTerminalForPolling()).isTrue();
        assertThat(EVALUATING.isTerminalForPolling()).isFalse();
        assertThat(SUBMITTED.isTerminalForPolling()).isFalse();
    }

    private static Attempt newAttempt() {
        return Attempt.newDraft(UUID.randomUUID(), "parking-lot", 1, AttemptKind.INITIAL, null, null, Instant.EPOCH);
    }
}

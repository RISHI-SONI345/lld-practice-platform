package com.lldpractice.lld_practice_platform.attempt;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/** Pure transition table. The six states differ only in which transitions are legal. */
public final class AttemptLifecycle {
    private static final Map<AttemptStatus, Map<AttemptEvent, AttemptStatus>> TABLE = new EnumMap<>(AttemptStatus.class);

    static {
        for (AttemptStatus s : AttemptStatus.values()) {
            TABLE.put(s, new EnumMap<>(AttemptEvent.class));
        }
        TABLE.get(AttemptStatus.DRAFT).put(AttemptEvent.SUBMIT, AttemptStatus.SUBMITTED);
        TABLE.get(AttemptStatus.SUBMITTED).put(AttemptEvent.START_EVALUATION, AttemptStatus.EVALUATING);
        TABLE.get(AttemptStatus.EVALUATING).put(AttemptEvent.COMPLETE, AttemptStatus.COMPLETED);
        TABLE.get(AttemptStatus.EVALUATING).put(AttemptEvent.FINISH_PARTIAL, AttemptStatus.PARTIAL);
        TABLE.get(AttemptStatus.EVALUATING).put(AttemptEvent.FAIL, AttemptStatus.FAILED);
        TABLE.get(AttemptStatus.PARTIAL).put(AttemptEvent.RETRY, AttemptStatus.EVALUATING);
    }

    private AttemptLifecycle() {
    }

    public static AttemptStatus next(AttemptStatus from, AttemptEvent event) {
        return Optional.ofNullable(TABLE.get(from).get(event))
                .orElseThrow(() -> new IllegalTransitionException(from, event));
    }

    public static boolean canApply(AttemptStatus from, AttemptEvent event) {
        return TABLE.get(from).containsKey(event);
    }
}

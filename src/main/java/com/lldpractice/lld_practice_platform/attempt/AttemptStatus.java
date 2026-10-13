package com.lldpractice.lld_practice_platform.attempt;

public enum AttemptStatus {
    DRAFT, SUBMITTED, EVALUATING, COMPLETED, PARTIAL, FAILED;

    public boolean isTerminalForPolling() {
        return this == COMPLETED || this == PARTIAL || this == FAILED;
    }
}

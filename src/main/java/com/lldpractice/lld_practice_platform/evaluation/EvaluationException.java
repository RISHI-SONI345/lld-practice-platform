package com.lldpractice.lld_practice_platform.evaluation;

public class EvaluationException extends Exception {
    private final boolean retryable;

    public EvaluationException(String message) {
        this(message, null, false);
    }

    public EvaluationException(String message, Throwable cause) {
        this(message, cause, false);
    }

    /** {@code retryable} is true for transient trouble such as an LLM timeout or a transport error. */
    public EvaluationException(String message, Throwable cause, boolean retryable) {
        super(message, cause);
        this.retryable = retryable;
    }

    public boolean isRetryable() {
        return retryable;
    }
}

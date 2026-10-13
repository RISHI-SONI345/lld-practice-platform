package com.lldpractice.lld_practice_platform.orchestration;

/** The submission broke a limit or was empty. The message is safe to show to the learner. */
public class SubmissionRejectedException extends RuntimeException {
    public SubmissionRejectedException(String message) {
        super(message);
    }
}

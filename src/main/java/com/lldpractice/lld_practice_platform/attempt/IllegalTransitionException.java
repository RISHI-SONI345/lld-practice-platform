package com.lldpractice.lld_practice_platform.attempt;

public class IllegalTransitionException extends RuntimeException {
    public IllegalTransitionException(AttemptStatus from, AttemptEvent event) {
        super("Cannot apply " + event + " to an attempt that is " + from);
    }
}

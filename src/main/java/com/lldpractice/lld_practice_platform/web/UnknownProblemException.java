package com.lldpractice.lld_practice_platform.web;

public class UnknownProblemException extends RuntimeException {
    public UnknownProblemException(String id) {
        super("Unknown problem " + id);
    }
}

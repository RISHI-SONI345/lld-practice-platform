package com.lldpractice.lld_practice_platform.llm;

public class LlmException extends Exception {
    public LlmException(String message) {
        super(message);
    }

    public LlmException(String message, Throwable cause) {
        super(message, cause);
    }

    public static class Timeout extends LlmException {
        public Timeout(String message) {
            super(message);
        }
    }

    public static class Transport extends LlmException {
        public Transport(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

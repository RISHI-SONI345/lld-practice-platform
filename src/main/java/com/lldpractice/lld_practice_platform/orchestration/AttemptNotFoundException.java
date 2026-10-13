package com.lldpractice.lld_practice_platform.orchestration;

import java.util.UUID;

public class AttemptNotFoundException extends RuntimeException {
    public AttemptNotFoundException(UUID id) {
        super("No attempt with id " + id);
    }
}

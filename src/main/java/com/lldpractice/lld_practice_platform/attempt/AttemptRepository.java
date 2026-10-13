package com.lldpractice.lld_practice_platform.attempt;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AttemptRepository {
    Attempt save(Attempt attempt);

    Optional<Attempt> find(UUID id);

    /** Oldest first. */
    List<Attempt> history(UUID learnerId, String problemId);

    int nextSequence(UUID learnerId, String problemId);

    List<Attempt> findByStatus(AttemptStatus status);
}

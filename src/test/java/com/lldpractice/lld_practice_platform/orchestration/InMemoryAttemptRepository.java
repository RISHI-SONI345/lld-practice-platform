package com.lldpractice.lld_practice_platform.orchestration;

import com.lldpractice.lld_practice_platform.attempt.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/** Copies on save and on read, like a database would, so tests do not share mutable objects by accident. */
public class InMemoryAttemptRepository implements AttemptRepository {
    private final Map<UUID, Attempt> store = new ConcurrentHashMap<>();

    private static Attempt copy(Attempt a) {
        return new Attempt(a.id(), a.learnerId(), a.problemId(), a.sequence(), a.kind(), a.baseAttemptId(),
                a.submission(), a.status(), a.report(), a.failureReason(), a.createdAt(), a.updatedAt());
    }

    @Override
    public Attempt save(Attempt attempt) {
        store.put(attempt.id(), copy(attempt));
        return attempt;
    }

    @Override
    public Optional<Attempt> find(UUID id) {
        return Optional.ofNullable(store.get(id)).map(InMemoryAttemptRepository::copy);
    }

    @Override
    public List<Attempt> history(UUID learnerId, String problemId) {
        return store.values().stream()
                .filter(a -> a.learnerId().equals(learnerId) && a.problemId().equals(problemId))
                .sorted(Comparator.comparingInt(Attempt::sequence)).map(InMemoryAttemptRepository::copy)
                .collect(Collectors.toList());
    }

    @Override
    public int nextSequence(UUID learnerId, String problemId) {
        return history(learnerId, problemId).stream().mapToInt(Attempt::sequence).max().orElse(0) + 1;
    }

    @Override
    public List<Attempt> findByStatus(AttemptStatus status) {
        return store.values().stream().filter(a -> a.status() == status).map(InMemoryAttemptRepository::copy)
                .collect(Collectors.toList());
    }
}

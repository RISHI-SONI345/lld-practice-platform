package com.lldpractice.lld_practice_platform.orchestration;

import com.lldpractice.lld_practice_platform.attempt.*;
import com.lldpractice.lld_practice_platform.model.*;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

/** Builds the history view: one summary per attempt and the diff against the previous one. */
@Service
public class AttemptHistoryService {
    private final AttemptRepository attempts;
    private final FindingDiffer differ;

    public AttemptHistoryService(AttemptRepository attempts, FindingDiffer differ) {
        this.attempts = attempts;
        this.differ = differ;
    }

    public record Row(UUID attemptId, int sequence, AttemptKind kind, AttemptStatus status, Instant at,
                      Map<Severity, Long> factCounts, int judgmentCount, ContainmentVerdict verdict, String summary) {
        private static final java.time.format.DateTimeFormatter WHEN =
                java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'").withZone(java.time.ZoneOffset.UTC);

        /** The time as the learner reads it, for example 2026-10-13 16:55 UTC. */
        public String whenText() {
            return at == null ? "" : WHEN.format(at);
        }
    }

    /** {@code judgmentsComparable} is false when either attempt is PARTIAL, so only FACT findings are compared. */
    public record AttemptDiff(int previousSequence, FindingDiffer.Diff facts, FindingDiffer.Diff judgments,
                              boolean judgmentsComparable) {
    }

    public List<Row> history(UUID learnerId, String problemId) {
        List<Attempt> all = attempts.history(learnerId, problemId);
        List<Row> rows = new ArrayList<>();
        for (Attempt a : all) {
            Map<Severity, Long> counts = new EnumMap<>(Severity.class);
            for (Severity s : Severity.values()) counts.put(s, 0L);
            a.report().facts().forEach(f -> counts.merge(f.severity(), 1L, Long::sum));
            String summary = diffAgainstPrevious(a, all).map(d -> d.facts().summary()).orElse("");
            ContainmentVerdict verdict = a.report().changeImpact() == null ? null : a.report().changeImpact().verdict();
            rows.add(new Row(a.id(), a.sequence(), a.kind(), a.status(), a.updatedAt(), counts,
                    a.report().judgments().size(), verdict, summary));
        }
        return rows;
    }

    public Optional<AttemptDiff> diffAgainstPrevious(Attempt current) {
        return diffAgainstPrevious(current, attempts.history(current.learnerId(), current.problemId()));
    }

    private Optional<AttemptDiff> diffAgainstPrevious(Attempt current, List<Attempt> all) {
        if (current.status() != AttemptStatus.COMPLETED && current.status() != AttemptStatus.PARTIAL) return Optional.empty();
        Attempt previous = all.stream()
                .filter(a -> a.sequence() < current.sequence())
                .filter(a -> a.status() == AttemptStatus.COMPLETED || a.status() == AttemptStatus.PARTIAL)
                .max(Comparator.comparingInt(Attempt::sequence)).orElse(null);
        if (previous == null) return Optional.empty();
        boolean comparable = previous.status() == AttemptStatus.COMPLETED && current.status() == AttemptStatus.COMPLETED;
        FindingDiffer.Diff facts = differ.diff(previous.report().findings(), current.report().findings(), FindingKind.FACT);
        FindingDiffer.Diff judgments = comparable
                ? differ.diff(previous.report().findings(), current.report().findings(), FindingKind.JUDGMENT)
                : new FindingDiffer.Diff(List.of(), List.of(), List.of());
        return Optional.of(new AttemptDiff(previous.sequence(), facts, judgments, comparable));
    }
}

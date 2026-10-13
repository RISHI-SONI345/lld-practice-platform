package com.lldpractice.lld_practice_platform.attempt;

import com.lldpractice.lld_practice_platform.model.EvaluationReport;
import com.lldpractice.lld_practice_platform.model.Submission;

import java.time.Instant;
import java.util.UUID;

/** One learner's try at one problem. Status changes only through {@link #apply(AttemptEvent, Instant)}. */
public class Attempt {
    private final UUID id;
    private final UUID learnerId;
    private final String problemId;
    private final int sequence;
    private final AttemptKind kind;
    private final UUID baseAttemptId;
    private final Instant createdAt;
    private Submission submission;
    private AttemptStatus status;
    private EvaluationReport report;
    private String failureReason;
    private Instant updatedAt;

    public Attempt(UUID id, UUID learnerId, String problemId, int sequence, AttemptKind kind, UUID baseAttemptId,
                   Submission submission, AttemptStatus status, EvaluationReport report, String failureReason,
                   Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.learnerId = learnerId;
        this.problemId = problemId;
        this.sequence = sequence;
        this.kind = kind;
        this.baseAttemptId = baseAttemptId;
        this.submission = submission;
        this.status = status;
        this.report = report == null ? EvaluationReport.empty() : report;
        this.failureReason = failureReason;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static Attempt newDraft(UUID learnerId, String problemId, int sequence, AttemptKind kind, UUID baseAttemptId,
                                   Submission submission, Instant now) {
        return new Attempt(UUID.randomUUID(), learnerId, problemId, sequence, kind, baseAttemptId, submission,
                AttemptStatus.DRAFT, EvaluationReport.empty(), null, now, now);
    }

    public void apply(AttemptEvent event, Instant now) {
        this.status = AttemptLifecycle.next(status, event);
        this.updatedAt = now;
    }

    public void replaceSubmission(Submission s, Instant now) {
        if (status != AttemptStatus.DRAFT) {
            throw new IllegalStateException("Only a draft can be edited");
        }
        this.submission = s;
        this.updatedAt = now;
    }

    public void attachReport(EvaluationReport r, Instant now) {
        this.report = r;
        this.updatedAt = now;
    }

    public void setFailureReason(String reason) {
        this.failureReason = reason;
    }

    public UUID id() { return id; }
    public UUID learnerId() { return learnerId; }
    public String problemId() { return problemId; }
    public int sequence() { return sequence; }
    public AttemptKind kind() { return kind; }
    public UUID baseAttemptId() { return baseAttemptId; }
    public Submission submission() { return submission; }
    public AttemptStatus status() { return status; }
    public EvaluationReport report() { return report; }
    public String failureReason() { return failureReason; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
}

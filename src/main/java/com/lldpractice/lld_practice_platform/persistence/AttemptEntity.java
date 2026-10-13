package com.lldpractice.lld_practice_platform.persistence;

import com.lldpractice.lld_practice_platform.attempt.AttemptKind;
import com.lldpractice.lld_practice_platform.attempt.AttemptStatus;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "attempts", uniqueConstraints = @UniqueConstraint(columnNames = {"learner_id", "problem_id", "sequence_no"}))
public class AttemptEntity {
    @Id
    private UUID id;
    @Column(name = "learner_id", nullable = false)
    private UUID learnerId;
    @Column(name = "problem_id", nullable = false)
    private String problemId;
    @Column(name = "sequence_no", nullable = false)
    private int sequence;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AttemptKind kind;
    private UUID baseAttemptId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AttemptStatus status;
    @Lob
    @Column(nullable = false)
    private String submissionJson;
    @Lob
    private String reportJson;
    @Column(length = 4000)
    private String failureReason;
    private Instant createdAt;
    private Instant updatedAt;

    protected AttemptEntity() {
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getLearnerId() { return learnerId; }
    public void setLearnerId(UUID learnerId) { this.learnerId = learnerId; }
    public String getProblemId() { return problemId; }
    public void setProblemId(String problemId) { this.problemId = problemId; }
    public int getSequence() { return sequence; }
    public void setSequence(int sequence) { this.sequence = sequence; }
    public AttemptKind getKind() { return kind; }
    public void setKind(AttemptKind kind) { this.kind = kind; }
    public UUID getBaseAttemptId() { return baseAttemptId; }
    public void setBaseAttemptId(UUID baseAttemptId) { this.baseAttemptId = baseAttemptId; }
    public AttemptStatus getStatus() { return status; }
    public void setStatus(AttemptStatus status) { this.status = status; }
    public String getSubmissionJson() { return submissionJson; }
    public void setSubmissionJson(String submissionJson) { this.submissionJson = submissionJson; }
    public String getReportJson() { return reportJson; }
    public void setReportJson(String reportJson) { this.reportJson = reportJson; }
    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public static AttemptEntity blank() {
        return new AttemptEntity();
    }
}

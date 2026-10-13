package com.lldpractice.lld_practice_platform.persistence;

import com.lldpractice.lld_practice_platform.attempt.Attempt;
import com.lldpractice.lld_practice_platform.attempt.AttemptRepository;
import com.lldpractice.lld_practice_platform.attempt.AttemptStatus;
import com.lldpractice.lld_practice_platform.model.EvaluationReport;
import com.lldpractice.lld_practice_platform.model.Submission;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Stores the submission and report as JSON columns, so the domain never meets JPA. */
@Repository
public class JpaAttemptRepository implements AttemptRepository {
    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private final AttemptJpaRepository jpa;

    public JpaAttemptRepository(AttemptJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Attempt save(Attempt a) {
        AttemptEntity e = jpa.findById(a.id()).orElseGet(AttemptEntity::blank);
        e.setId(a.id());
        e.setLearnerId(a.learnerId());
        e.setProblemId(a.problemId());
        e.setSequence(a.sequence());
        e.setKind(a.kind());
        e.setBaseAttemptId(a.baseAttemptId());
        e.setStatus(a.status());
        e.setSubmissionJson(MAPPER.writeValueAsString(a.submission()));
        e.setReportJson(MAPPER.writeValueAsString(a.report()));
        e.setFailureReason(truncate(a.failureReason()));
        e.setCreatedAt(a.createdAt());
        e.setUpdatedAt(a.updatedAt());
        return toAttempt(jpa.save(e));
    }

    @Override
    public Optional<Attempt> find(UUID id) {
        return jpa.findById(id).map(JpaAttemptRepository::toAttempt);
    }

    @Override
    public List<Attempt> history(UUID learnerId, String problemId) {
        return jpa.findByLearnerIdAndProblemIdOrderBySequenceAsc(learnerId, problemId).stream()
                .map(JpaAttemptRepository::toAttempt).toList();
    }

    @Override
    public int nextSequence(UUID learnerId, String problemId) {
        return jpa.findFirstByLearnerIdAndProblemIdOrderBySequenceDesc(learnerId, problemId)
                .map(e -> e.getSequence() + 1).orElse(1);
    }

    @Override
    public List<Attempt> findByStatus(AttemptStatus status) {
        return jpa.findByStatus(status).stream().map(JpaAttemptRepository::toAttempt).toList();
    }

    private static Attempt toAttempt(AttemptEntity e) {
        Submission s = MAPPER.readValue(e.getSubmissionJson(), Submission.class);
        EvaluationReport r = e.getReportJson() == null ? EvaluationReport.empty()
                : MAPPER.readValue(e.getReportJson(), EvaluationReport.class);
        return new Attempt(e.getId(), e.getLearnerId(), e.getProblemId(), e.getSequence(), e.getKind(),
                e.getBaseAttemptId(), s, e.getStatus(), r, e.getFailureReason(), e.getCreatedAt(), e.getUpdatedAt());
    }

    private static String truncate(String s) {
        return s == null || s.length() <= 4000 ? s : s.substring(0, 4000);
    }
}

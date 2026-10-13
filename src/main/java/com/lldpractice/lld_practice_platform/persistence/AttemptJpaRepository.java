package com.lldpractice.lld_practice_platform.persistence;

import com.lldpractice.lld_practice_platform.attempt.AttemptStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AttemptJpaRepository extends JpaRepository<AttemptEntity, UUID> {
    List<AttemptEntity> findByLearnerIdAndProblemIdOrderBySequenceAsc(UUID learnerId, String problemId);

    Optional<AttemptEntity> findFirstByLearnerIdAndProblemIdOrderBySequenceDesc(UUID learnerId, String problemId);

    List<AttemptEntity> findByStatus(AttemptStatus status);
}

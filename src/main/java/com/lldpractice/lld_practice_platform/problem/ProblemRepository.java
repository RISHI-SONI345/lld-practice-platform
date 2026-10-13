package com.lldpractice.lld_practice_platform.problem;

import java.util.List;
import java.util.Optional;

public interface ProblemRepository {
    List<Problem> all();

    Optional<Problem> find(String id);
}

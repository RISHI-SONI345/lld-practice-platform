package com.lldpractice.lld_practice_platform.orchestration;

import com.lldpractice.lld_practice_platform.problem.Problem;
import com.lldpractice.lld_practice_platform.problem.ProblemRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/** What the web layer may ask about problems. The repository stays behind this service. */
@Service
public class ProblemCatalog {
    private final ProblemRepository problems;

    public ProblemCatalog(ProblemRepository problems) {
        this.problems = problems;
    }

    public List<Problem> all() {
        return problems.all();
    }

    public Optional<Problem> find(String id) {
        return problems.find(id);
    }
}

package com.lldpractice.lld_practice_platform.web;

import com.lldpractice.lld_practice_platform.attempt.Attempt;
import com.lldpractice.lld_practice_platform.orchestration.AttemptHistoryService;
import com.lldpractice.lld_practice_platform.orchestration.AttemptService;
import com.lldpractice.lld_practice_platform.problem.Problem;
import com.lldpractice.lld_practice_platform.orchestration.ProblemCatalog;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.UUID;

@Controller
public class ProblemController {
    private final ProblemCatalog problems;
    private final AttemptService attempts;
    private final AttemptHistoryService history;
    private final LearnerIdResolver learners;

    public ProblemController(ProblemCatalog problems, AttemptService attempts, AttemptHistoryService history,
                             LearnerIdResolver learners) {
        this.problems = problems;
        this.attempts = attempts;
        this.history = history;
        this.learners = learners;
    }

    @GetMapping("/problems/{id}")
    String problem(@PathVariable String id, Model model) {
        model.addAttribute("problem", find(id));
        return "problem";
    }

    @PostMapping("/problems/{id}/attempts")
    String start(@PathVariable String id, HttpServletRequest req, HttpServletResponse resp) {
        find(id);
        UUID learner = learners.resolve(req, resp);
        Attempt draft = attempts.startAttempt(learner, id);
        return "redirect:/attempts/" + draft.id() + "/edit";
    }

    @GetMapping("/problems/{id}/history")
    String history(@PathVariable String id, HttpServletRequest req, HttpServletResponse resp, Model model) {
        UUID learner = learners.resolve(req, resp);
        model.addAttribute("problem", find(id));
        model.addAttribute("rows", history.history(learner, id));
        return "history";
    }

    private Problem find(String id) {
        return problems.find(id).orElseThrow(() -> new UnknownProblemException(id));
    }
}

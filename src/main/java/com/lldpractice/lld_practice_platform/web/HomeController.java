package com.lldpractice.lld_practice_platform.web;

import com.lldpractice.lld_practice_platform.attempt.Attempt;
import com.lldpractice.lld_practice_platform.orchestration.AttemptService;
import com.lldpractice.lld_practice_platform.orchestration.ProblemCatalog;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Controller
public class HomeController {
    private final ProblemCatalog problems;
    private final AttemptService attempts;
    private final LearnerIdResolver learners;

    public HomeController(ProblemCatalog problems, AttemptService attempts, LearnerIdResolver learners) {
        this.problems = problems;
        this.attempts = attempts;
        this.learners = learners;
    }

    @GetMapping("/")
    String index(HttpServletRequest req, HttpServletResponse resp, Model model) {
        UUID learner = learners.resolve(req, resp);
        Map<String, Attempt> latest = new LinkedHashMap<>();
        attempts.latestPerProblem(learner).forEach(a -> latest.put(a.problemId(), a));
        model.addAttribute("problems", problems.all());
        model.addAttribute("latest", latest);
        return "index";
    }
}

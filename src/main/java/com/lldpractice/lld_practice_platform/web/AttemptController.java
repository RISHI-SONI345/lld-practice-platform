package com.lldpractice.lld_practice_platform.web;

import com.lldpractice.lld_practice_platform.attempt.Attempt;
import com.lldpractice.lld_practice_platform.attempt.AttemptKind;
import com.lldpractice.lld_practice_platform.attempt.AttemptStatus;
import com.lldpractice.lld_practice_platform.model.*;
import com.lldpractice.lld_practice_platform.orchestration.AttemptHistoryService;
import com.lldpractice.lld_practice_platform.orchestration.AttemptService;
import com.lldpractice.lld_practice_platform.orchestration.SubmissionRejectedException;
import com.lldpractice.lld_practice_platform.problem.Problem;
import com.lldpractice.lld_practice_platform.orchestration.ProblemCatalog;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@Controller
public class AttemptController {
    private final AttemptService attempts;
    private final ProblemCatalog problems;
    private final AttemptHistoryService history;
    private final LearnerIdResolver learners;

    public AttemptController(AttemptService attempts, ProblemCatalog problems, AttemptHistoryService history,
                             LearnerIdResolver learners) {
        this.attempts = attempts;
        this.problems = problems;
        this.history = history;
        this.learners = learners;
    }

    // ---- editor
    @GetMapping("/attempts/{id}/edit")
    String edit(@PathVariable UUID id, HttpServletRequest req, HttpServletResponse resp, Model model) {
        Attempt a = attempts.load(learners.resolve(req, resp), id);
        if (a.status() != AttemptStatus.DRAFT) return "redirect:/attempts/" + id;
        fillEditor(model, a, a.submission(), null);
        return "editor";
    }

    @PostMapping("/attempts/{id}/draft")
    String saveDraft(@PathVariable UUID id, @RequestParam MultiValueMap<String, String> form,
                     HttpServletRequest req, HttpServletResponse resp) {
        UUID learner = learners.resolve(req, resp);
        try {
            attempts.saveDraft(learner, id, SubmissionForm.toSubmission(form));
        } catch (SubmissionRejectedException e) {
            return "redirect:/attempts/" + id;
        }
        return "redirect:/attempts/" + id + "/edit?saved";
    }

    @PostMapping("/attempts/{id}/submit")
    String submit(@PathVariable UUID id, @RequestParam MultiValueMap<String, String> form,
                  HttpServletRequest req, HttpServletResponse resp, Model model) {
        UUID learner = learners.resolve(req, resp);
        Submission submission = SubmissionForm.toSubmission(form);
        try {
            attempts.submit(learner, id, submission);
            return "redirect:/attempts/" + id;
        } catch (SubmissionRejectedException e) {
            Attempt a = attempts.load(learner, id);
            if (a.status() != AttemptStatus.DRAFT) return "redirect:/attempts/" + id;
            fillEditor(model, a, submission, e.getMessage());
            resp.setStatus(400);
            return "editor";
        }
    }

    // ---- report
    @GetMapping("/attempts/{id}")
    String attempt(@PathVariable UUID id, HttpServletRequest req, HttpServletResponse resp, Model model) {
        Attempt a = attempts.load(learners.resolve(req, resp), id);
        if (a.status() == AttemptStatus.DRAFT) return "redirect:/attempts/" + id + "/edit";
        Problem problem = problems.find(a.problemId()).orElseThrow(() -> new UnknownProblemException(a.problemId()));
        EvaluationReport report = a.report();

        Map<Severity, List<Finding>> factGroups = new LinkedHashMap<>();
        for (Severity s : List.of(Severity.HIGH, Severity.MEDIUM, Severity.LOW, Severity.INFO)) {
            List<Finding> inGroup = report.facts().stream().filter(f -> f.severity() == s).toList();
            if (!inGroup.isEmpty()) factGroups.put(s, inGroup);
        }
        Map<Severity, Long> factCounts = new EnumMap<>(Severity.class);
        for (Severity s : Severity.values()) factCounts.put(s, report.facts().stream().filter(f -> f.severity() == s).count());

        model.addAttribute("attempt", a);
        model.addAttribute("problem", problem);
        model.addAttribute("report", report);
        model.addAttribute("factGroups", factGroups);
        model.addAttribute("factCounts", factCounts);
        model.addAttribute("running", !a.status().isTerminalForPolling());
        model.addAttribute("canRetry", a.status() == AttemptStatus.PARTIAL);
        model.addAttribute("canChange", a.status() == AttemptStatus.COMPLETED || a.status() == AttemptStatus.PARTIAL);
        model.addAttribute("isChange", a.kind() == AttemptKind.CHANGE_REVISION);
        model.addAttribute("diff", history.diffAgainstPrevious(a).orElse(null));
        return "attempt";
    }

    /** Polled every two seconds until the attempt is COMPLETED, PARTIAL or FAILED. */
    @GetMapping("/attempts/{id}/status")
    @ResponseBody
    Map<String, Object> status(@PathVariable UUID id, HttpServletRequest req, HttpServletResponse resp) {
        Attempt a = attempts.load(learners.resolve(req, resp), id);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", a.status().name());
        out.put("completeness", a.report().completeness().name());
        out.put("done", a.status().isTerminalForPolling());
        out.put("factCount", a.report().facts().size());
        out.put("judgmentCount", a.report().judgments().size());
        out.put("failureReason", a.failureReason());
        List<Map<String, Object>> runs = new ArrayList<>();
        for (EvaluatorRun r : a.report().runs()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("evaluatorId", r.evaluatorId());
            m.put("stage", r.stage().name());
            m.put("outcome", r.outcome().name());
            m.put("attemptsUsed", r.attemptsUsed());
            m.put("durationMs", r.duration().toMillis());
            m.put("message", r.message());
            runs.add(m);
        }
        out.put("runs", runs);
        return out;
    }

    // ---- actions
    @PostMapping("/attempts/{id}/retry")
    String retry(@PathVariable UUID id, HttpServletRequest req, HttpServletResponse resp) {
        attempts.retry(learners.resolve(req, resp), id);
        return "redirect:/attempts/" + id;
    }

    @PostMapping("/attempts/{id}/change-request")
    String changeRequest(@PathVariable UUID id, HttpServletRequest req, HttpServletResponse resp) {
        Attempt draft = attempts.startChangeRevision(learners.resolve(req, resp), id);
        return "redirect:/attempts/" + draft.id() + "/edit";
    }

    /** After a FAILED attempt: a new draft that keeps the code, so the learner can fix it and submit again. */
    @PostMapping("/attempts/{id}/edit-again")
    String editAgain(@PathVariable UUID id, HttpServletRequest req, HttpServletResponse resp) {
        Attempt draft = attempts.startFromExisting(learners.resolve(req, resp), id);
        return "redirect:/attempts/" + draft.id() + "/edit";
    }

    private void fillEditor(Model model, Attempt a, Submission shown, String error) {
        Problem problem = problems.find(a.problemId()).orElseThrow(() -> new UnknownProblemException(a.problemId()));
        model.addAttribute("attempt", a);
        model.addAttribute("problem", problem);
        model.addAttribute("submission", shown);
        model.addAttribute("error", error);
        model.addAttribute("isChange", a.kind() == AttemptKind.CHANGE_REVISION);
    }
}

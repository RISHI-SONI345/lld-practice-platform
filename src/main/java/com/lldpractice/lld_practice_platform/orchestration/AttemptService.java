package com.lldpractice.lld_practice_platform.orchestration;

import com.lldpractice.lld_practice_platform.attempt.*;
import com.lldpractice.lld_practice_platform.model.*;
import com.lldpractice.lld_practice_platform.parser.ParserRegistry;
import com.lldpractice.lld_practice_platform.problem.Problem;
import com.lldpractice.lld_practice_platform.problem.ProblemRepository;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

/** The only write path for the web layer. */
@Service
public class AttemptService {
    private final AttemptRepository attempts;
    private final ProblemRepository problems;
    private final EvaluationOrchestrator orchestrator;
    private final EvaluationScheduler scheduler;
    private final ParserRegistry parsers;
    private final EvaluationProperties props;
    private final Clock clock;

    public AttemptService(AttemptRepository attempts, ProblemRepository problems, EvaluationOrchestrator orchestrator,
                          EvaluationScheduler scheduler, ParserRegistry parsers, EvaluationProperties props, Clock clock) {
        this.attempts = attempts;
        this.problems = problems;
        this.orchestrator = orchestrator;
        this.scheduler = scheduler;
        this.parsers = parsers;
        this.props = props;
        this.clock = clock;
    }

    /** A new DRAFT pre-filled with the problem's skeleton. */
    public Attempt startAttempt(UUID learnerId, String problemId) {
        Problem p = problems.find(problemId).orElseThrow(() -> new IllegalArgumentException("Unknown problem " + problemId));
        Submission skeleton = new Submission(Submission.JAVA, p.skeleton(), "", Rationale.empty());
        Attempt a = Attempt.newDraft(learnerId, problemId, attempts.nextSequence(learnerId, problemId),
                AttemptKind.INITIAL, null, skeleton, clock.instant());
        return attempts.save(a);
    }

    /** A CHANGE_REVISION draft pre-filled with the base attempt's code. A FAILED or DRAFT attempt cannot be a base. */
    public Attempt startChangeRevision(UUID learnerId, UUID baseAttemptId) {
        Attempt base = load(learnerId, baseAttemptId);
        if (base.status() != AttemptStatus.COMPLETED && base.status() != AttemptStatus.PARTIAL) {
            throw new SubmissionRejectedException("Only a finished attempt can be revised for the change request (this one is "
                    + base.status() + ").");
        }
        Attempt a = Attempt.newDraft(learnerId, base.problemId(), attempts.nextSequence(learnerId, base.problemId()),
                AttemptKind.CHANGE_REVISION, base.id(), base.submission(), clock.instant());
        return attempts.save(a);
    }

    /** After a FAILED attempt: a new INITIAL draft that keeps the learner's code so they can fix it. */
    public Attempt startFromExisting(UUID learnerId, UUID attemptId) {
        Attempt old = load(learnerId, attemptId);
        if (old.status() != AttemptStatus.FAILED) {
            throw new SubmissionRejectedException("Only a failed attempt can be reopened for editing (this one is "
                    + old.status() + ").");
        }
        Attempt a = Attempt.newDraft(learnerId, old.problemId(), attempts.nextSequence(learnerId, old.problemId()),
                AttemptKind.INITIAL, null, old.submission(), clock.instant());
        return attempts.save(a);
    }

    public Attempt saveDraft(UUID learnerId, UUID attemptId, Submission submission) {
        Attempt a = load(learnerId, attemptId);
        if (a.status() != AttemptStatus.DRAFT) {
            throw new SubmissionRejectedException("This attempt was already submitted and can no longer be edited.");
        }
        a.replaceSubmission(submission, clock.instant());
        return attempts.save(a);
    }

    /** Validates, submits, runs the immediate stage and hands the deferred stage to the scheduler. */
    public Attempt submit(UUID learnerId, UUID attemptId, Submission submission) {
        Attempt a = load(learnerId, attemptId);
        if (a.status() != AttemptStatus.DRAFT) {
            throw new SubmissionRejectedException("This attempt was already submitted.");
        }
        validate(submission);
        a.replaceSubmission(submission, clock.instant());
        a.apply(AttemptEvent.SUBMIT, clock.instant());
        attempts.save(a);
        if (orchestrator.evaluateImmediate(a.id())) {
            scheduler.schedule(a.id());
        }
        return attempts.find(a.id()).orElseThrow();
    }

    /** Retries the LLM stage. Allowed only from PARTIAL; a second press is an illegal transition. */
    public Attempt retry(UUID learnerId, UUID attemptId) {
        Attempt a = load(learnerId, attemptId);
        a.apply(AttemptEvent.RETRY, clock.instant());
        a.setFailureReason(null);
        attempts.save(a);
        scheduler.schedule(a.id());
        return a;
    }

    /** Loads an attempt that belongs to this learner. Someone else's attempt looks like it does not exist. */
    public Attempt load(UUID learnerId, UUID attemptId) {
        return attempts.find(attemptId).filter(a -> a.learnerId().equals(learnerId))
                .orElseThrow(() -> new AttemptNotFoundException(attemptId));
    }

    public List<Attempt> latestPerProblem(UUID learnerId) {
        return problems.all().stream()
                .map(p -> attempts.history(learnerId, p.id()))
                .filter(h -> !h.isEmpty())
                .map(h -> h.get(h.size() - 1))
                .toList();
    }

    private void validate(Submission s) {
        if (s == null || !s.hasContent()) {
            throw new SubmissionRejectedException("Add some Java code before submitting.");
        }
        long nonBlank = s.files().stream().filter(f -> f.content() != null && !f.content().isBlank()).count();
        if (s.files().size() > props.maxFiles) {
            throw new SubmissionRejectedException("Too many files: " + s.files().size() + " (the limit is " + props.maxFiles + ").");
        }
        if (s.totalSize() > props.maxTotalBytes) {
            throw new SubmissionRejectedException("The code is too large: " + s.totalSize() + " characters (the limit is "
                    + props.maxTotalBytes + ").");
        }
        if (nonBlank == 0) throw new SubmissionRejectedException("Add some Java code before submitting.");
        parsers.forFormat(s.format());
    }
}

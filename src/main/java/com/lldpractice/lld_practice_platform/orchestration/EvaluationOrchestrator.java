package com.lldpractice.lld_practice_platform.orchestration;

import com.lldpractice.lld_practice_platform.attempt.*;
import com.lldpractice.lld_practice_platform.evaluation.*;
import com.lldpractice.lld_practice_platform.model.*;
import com.lldpractice.lld_practice_platform.parser.ParseResult;
import com.lldpractice.lld_practice_platform.parser.ParserRegistry;
import com.lldpractice.lld_practice_platform.problem.Problem;
import com.lldpractice.lld_practice_platform.problem.ProblemRepository;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/**
 * Parses, runs the IMMEDIATE evaluators, saves, then (separately, in the background) runs the DEFERRED evaluators
 * with a timeout, bounded retries and an overall deadline. It applies lifecycle events as it goes.
 */
@Service
public class EvaluationOrchestrator {
    private static final Logger log = LoggerFactory.getLogger(EvaluationOrchestrator.class);

    private final ParserRegistry parsers;
    private final List<Evaluator> evaluators;
    private final AttemptRepository attempts;
    private final ProblemRepository problems;
    private final DiagramRenderer diagrams;
    private final ChangeImpactCalculator impactCalculator;
    private final EvaluationProperties props;
    private final Clock clock;
    private final ExecutorService callPool = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "evaluator-call");
        t.setDaemon(true);
        return t;
    });

    public EvaluationOrchestrator(ParserRegistry parsers, List<Evaluator> evaluators, AttemptRepository attempts,
                                  ProblemRepository problems, DiagramRenderer diagrams,
                                  ChangeImpactCalculator impactCalculator, EvaluationProperties props, Clock clock) {
        this.parsers = parsers;
        this.evaluators = List.copyOf(evaluators);
        this.attempts = attempts;
        this.problems = problems;
        this.diagrams = diagrams;
        this.impactCalculator = impactCalculator;
        this.props = props;
        this.clock = clock;
    }

    @PreDestroy
    void shutdown() {
        callPool.shutdownNow();
    }

    /**
     * Stage one. Applies START_EVALUATION, parses, runs IMMEDIATE evaluators and saves.
     *
     * @return true if the DEFERRED stage should be scheduled, false if the attempt already ended (FAILED)
     */
    public boolean evaluateImmediate(UUID attemptId) {
        Attempt attempt = attempts.find(attemptId).orElseThrow(() -> new AttemptNotFoundException(attemptId));
        Problem problem = problems.find(attempt.problemId())
                .orElseThrow(() -> new IllegalStateException("Unknown problem " + attempt.problemId()));
        attempt.apply(AttemptEvent.START_EVALUATION, clock.instant());
        attempts.save(attempt);

        ParseResult parsed;
        try {
            parsed = parsers.parse(attempt.submission());
        } catch (RuntimeException e) {
            return fail(attempt, "The submission could not be parsed: " + e.getMessage(), List.of());
        }
        List<Finding> findings = new ArrayList<>(ParseIssueFindings.from(parsed.issues()));
        if (!parsed.usable()) {
            String reasons = parsed.issues().isEmpty() ? "No class could be read from your files."
                    : String.join(" ", parsed.issues().stream().map(ParseIssue::message).toList());
            return fail(attempt, reasons, findings);
        }
        for (String entry : problem.entryTypes()) {
            if (parsed.model().find(entry).isEmpty()) findings.add(ParseIssueFindings.missingEntryType(entry));
        }

        ChangeImpact impact = null;
        if (attempt.kind() == AttemptKind.CHANGE_REVISION && attempt.baseAttemptId() != null) {
            impact = attempts.find(attempt.baseAttemptId())
                    .map(base -> impactCalculator.calculate(parsers.parse(base.submission()).model(), parsed.model()))
                    .orElse(null);
        }

        EvaluationContext ctx = new EvaluationContext(problem, attempt.submission(), parsed.model(), findings, impact);
        List<EvaluatorRun> runs = new ArrayList<>();
        for (Evaluator ev : evaluators) {
            if (ev.stage() != Stage.IMMEDIATE) continue;
            long t0 = System.nanoTime();
            try {
                EvaluationResult r = ev.evaluate(ctx);
                findings.addAll(r.findings());
                runs.add(new EvaluatorRun(ev.id(), Stage.IMMEDIATE, RunOutcome.OK, 1, since(t0), r.note()));
            } catch (EvaluationException | RuntimeException e) {
                log.warn("Immediate evaluator {} failed", ev.id(), e);
                runs.add(new EvaluatorRun(ev.id(), Stage.IMMEDIATE, RunOutcome.FAILED, 1, since(t0), String.valueOf(e.getMessage())));
            }
        }
        String mermaid = "";
        try {
            mermaid = diagrams.render(parsed.model());
        } catch (RuntimeException e) {
            log.warn("Diagram rendering failed", e);
        }
        attempt.attachReport(new EvaluationReport(Completeness.PARTIAL, findings, runs, mermaid, impact, List.of(), List.of()),
                clock.instant());
        attempts.save(attempt);
        log.info("attempt {} #{} ({}): immediate stage done, {} FACT findings, deferred stage next", attemptId, attempt.sequence(),
                attempt.problemId(), findings.size());
        return true;
    }

    private boolean fail(Attempt attempt, String reason, List<Finding> findings) {
        attempt.attachReport(new EvaluationReport(Completeness.PARTIAL, findings, List.of(), "", null, List.of(), List.of()),
                clock.instant());
        attempt.setFailureReason(reason);
        attempt.apply(AttemptEvent.FAIL, clock.instant());
        attempts.save(attempt);
        log.warn("attempt {} #{} ({}): FAILED, nothing could be parsed: {}", attempt.id(), attempt.sequence(), attempt.problemId(), reason);
        return false;
    }

    /** Stage two. Never leaves an attempt in EVALUATING: any unexpected error ends it as PARTIAL. */
    public void evaluateDeferred(UUID attemptId) {
        Attempt attempt = attempts.find(attemptId).orElse(null);
        if (attempt == null || attempt.status() != AttemptStatus.EVALUATING) {
            log.info("Skipping deferred stage for {}: not evaluating", attemptId);
            return;
        }
        try {
            runDeferred(attempt);
        } catch (RuntimeException e) {
            log.error("Deferred stage crashed for {}", attemptId, e);
            attempt.setFailureReason("The AI review stopped unexpectedly: " + e);
            attempt.apply(AttemptEvent.FINISH_PARTIAL, clock.instant());
            attempts.save(attempt);
        }
    }

    private void runDeferred(Attempt attempt) {
        Problem problem = problems.find(attempt.problemId()).orElseThrow();
        EvaluationReport before = attempt.report();
        ParseResult parsed = parsers.parse(attempt.submission());
        List<Finding> facts = before.facts();
        List<EvaluatorRun> runs = new ArrayList<>(before.runs().stream().filter(r -> r.stage() == Stage.IMMEDIATE).toList());
        EvaluationContext ctx = new EvaluationContext(problem, attempt.submission(), parsed.model(), facts, before.changeImpact());

        List<Finding> all = new ArrayList<>(facts);
        List<DecisionAssessment> assessments = new ArrayList<>();
        List<String> strengths = new ArrayList<>();
        boolean allOk = true;
        String failure = null;
        long start = System.nanoTime();
        for (Evaluator ev : evaluators) {
            if (ev.stage() != Stage.DEFERRED) continue;
            Attemptable result = runWithRetries(ev, ctx, start);
            runs.add(result.run());
            if (result.run().outcome() == RunOutcome.OK) {
                all.addAll(result.result().findings());
                assessments.addAll(result.result().assessments());
                strengths.addAll(result.result().strengths());
            } else {
                allOk = false;
                failure = result.run().message();
            }
        }
        attempt.attachReport(new EvaluationReport(allOk ? Completeness.COMPLETE : Completeness.PARTIAL, all, runs,
                before.mermaid(), before.changeImpact(), assessments, strengths), clock.instant());
        attempt.setFailureReason(allOk ? null : failure);
        attempt.apply(allOk ? AttemptEvent.COMPLETE : AttemptEvent.FINISH_PARTIAL, clock.instant());
        attempts.save(attempt);
        if (allOk) {
            log.info("attempt {} #{}: COMPLETED in {} ms, {} FACT + {} JUDGMENT findings ({})", attempt.id(), attempt.sequence(),
                    Duration.ofNanos(System.nanoTime() - start).toMillis(), facts.size(), all.size() - facts.size(),
                    runs.get(runs.size() - 1).message());
        } else {
            log.warn("attempt {} #{}: PARTIAL after {} ms: {}", attempt.id(), attempt.sequence(),
                    Duration.ofNanos(System.nanoTime() - start).toMillis(), failure);
        }
    }

    private record Attemptable(EvaluatorRun run, EvaluationResult result) {
    }

    private Attemptable runWithRetries(Evaluator ev, EvaluationContext ctx, long overallStartNanos) {
        long t0 = System.nanoTime();
        int used = 0;
        RunOutcome lastOutcome = RunOutcome.FAILED;
        String lastMessage = "not run";
        int maxAttempts = 1 + Math.max(0, props.maxCallRetries);
        while (used < maxAttempts) {
            if (Duration.ofNanos(System.nanoTime() - overallStartNanos).compareTo(props.deferredDeadline) > 0) {
                lastOutcome = RunOutcome.TIMED_OUT;
                lastMessage = "The overall deadline of " + props.deferredDeadline.toSeconds() + " seconds for the AI review was reached";
                break;
            }
            used++;
            Future<EvaluationResult> future = callPool.submit(() -> ev.evaluate(ctx));
            try {
                EvaluationResult r = future.get(props.llmCallTimeout.toMillis(), TimeUnit.MILLISECONDS);
                return new Attemptable(new EvaluatorRun(ev.id(), ev.stage(), RunOutcome.OK, used, since(t0), r.note()), r);
            } catch (TimeoutException e) {
                future.cancel(true);
                lastOutcome = RunOutcome.TIMED_OUT;
                lastMessage = "The AI review took longer than " + props.llmCallTimeout.toMillis() + " ms";
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                lastOutcome = RunOutcome.FAILED;
                lastMessage = "Interrupted";
                break;
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                lastOutcome = RunOutcome.FAILED;
                lastMessage = String.valueOf(cause.getMessage());
                boolean retryable = cause instanceof EvaluationException ee && ee.isRetryable();
                if (!retryable) break;
            }
            if (used < maxAttempts) sleep(props.retryBackoff);
        }
        return new Attemptable(new EvaluatorRun(ev.id(), ev.stage(), lastOutcome, used, since(t0), lastMessage), null);
    }

    private static void sleep(Duration d) {
        if (d.isZero() || d.isNegative()) return;
        try {
            Thread.sleep(d.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static Duration since(long t0) {
        return Duration.ofNanos(System.nanoTime() - t0);
    }
}

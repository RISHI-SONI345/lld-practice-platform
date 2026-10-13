package com.lldpractice.lld_practice_platform.orchestration;

import com.lldpractice.lld_practice_platform.attempt.*;
import com.lldpractice.lld_practice_platform.evaluation.*;
import com.lldpractice.lld_practice_platform.evaluation.rules.*;
import com.lldpractice.lld_practice_platform.llm.*;
import com.lldpractice.lld_practice_platform.parser.ParserRegistry;
import com.lldpractice.lld_practice_platform.parser.javacode.JavaCodeParser;
import com.lldpractice.lld_practice_platform.problem.YamlProblemRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Wires the real components together without Spring, with a scripted LLM and fast time limits. */
public class Harness {
    public final UUID learner = UUID.randomUUID();
    public final InMemoryAttemptRepository repo = new InMemoryAttemptRepository();
    public final YamlProblemRepository problems = new YamlProblemRepository("classpath:problems/*.yml");
    public final EvaluationProperties props = new EvaluationProperties();
    public final FakeLlmClient llm;
    public final EvaluationOrchestrator orchestrator;
    public final SynchronousEvaluationScheduler scheduler;
    public final AttemptService service;
    public final AttemptHistoryService history;
    public final ChangeImpactCalculator impact;

    public Harness(FakeLlmClient llm) {
        this(llm, List.of());
    }

    public Harness(FakeLlmClient llm, List<Rule> extraRules) {
        this.llm = llm;
        props.llmCallTimeout = Duration.ofMillis(400);
        props.retryBackoff = Duration.ZERO;
        props.deferredDeadline = Duration.ofSeconds(5);
        Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
        RuleThresholds t = new RuleThresholds();
        List<Rule> rules = new ArrayList<>(List.of(new GodClassRule(t), new TypeSwitchRule(t), new HardcodedValueRule(),
                new ConcreteDependencyRule(), new DependencyCycleRule(), new OrphanOrUnusedRule(),
                new CapabilityOwnershipRule()));
        rules.addAll(extraRules);
        ParserRegistry parsers = new ParserRegistry(List.of(new JavaCodeParser()));
        impact = new ChangeImpactCalculator(props);
        orchestrator = new EvaluationOrchestrator(parsers,
                List.of(new RuleEvaluator(rules), new LlmDesignReviewEvaluator(llm, new FindingValidator())),
                repo, problems, new MermaidClassDiagramRenderer(), impact, props, clock);
        scheduler = new SynchronousEvaluationScheduler(orchestrator);
        service = new AttemptService(repo, problems, orchestrator, scheduler, parsers, props, clock);
        history = new AttemptHistoryService(repo, new FindingDiffer());
    }

    public Attempt submitParking(com.lldpractice.lld_practice_platform.model.Submission s) {
        Attempt draft = service.startAttempt(learner, "parking-lot");
        return service.submit(learner, draft.id(), s);
    }
}

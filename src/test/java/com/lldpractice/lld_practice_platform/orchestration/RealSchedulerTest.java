package com.lldpractice.lld_practice_platform.orchestration;

import com.lldpractice.lld_practice_platform.attempt.Attempt;
import com.lldpractice.lld_practice_platform.attempt.AttemptStatus;
import com.lldpractice.lld_practice_platform.llm.FakeLlmClient;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/** The real background pool, not the synchronous double: the submit returns before the AI stage ends. */
class RealSchedulerTest {
    @Test
    void submitReturnsWhileEvaluatingAndTheBackgroundStageFinishesLater() throws Exception {
        Harness base = new Harness(new FakeLlmClient().thenSleep(600, Samples.GOOD_REVIEW_JSON));
        base.props.llmCallTimeout = Duration.ofSeconds(5);
        ExecutorEvaluationScheduler real = new ExecutorEvaluationScheduler(base.orchestrator);
        AttemptService service = new AttemptService(base.repo, base.problems, base.orchestrator, real,
                new com.lldpractice.lld_practice_platform.parser.ParserRegistry(
                        java.util.List.of(new com.lldpractice.lld_practice_platform.parser.javacode.JavaCodeParser())),
                base.props, java.time.Clock.systemUTC());

        Attempt draft = service.startAttempt(base.learner, "parking-lot");
        Attempt returned = service.submit(base.learner, draft.id(), Samples.goodParkingLot());

        assertThat(returned.status()).isEqualTo(AttemptStatus.EVALUATING);
        assertThat(returned.report().facts()).isNotEmpty();
        assertThat(returned.report().judgments()).isEmpty();

        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline
                && base.repo.find(draft.id()).orElseThrow().status() == AttemptStatus.EVALUATING) {
            Thread.sleep(50);
        }
        Attempt done = base.repo.find(draft.id()).orElseThrow();
        assertThat(done.status()).isEqualTo(AttemptStatus.COMPLETED);
        assertThat(done.report().judgments()).hasSize(1);
    }
}

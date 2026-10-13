package com.lldpractice.lld_practice_platform.orchestration;

import com.lldpractice.lld_practice_platform.attempt.*;
import com.lldpractice.lld_practice_platform.evaluation.Rule;
import com.lldpractice.lld_practice_platform.llm.FakeLlmClient;
import com.lldpractice.lld_practice_platform.model.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EvaluationOrchestratorTest {

    private static EvaluatorRun llmRun(Attempt a) {
        return a.report().runs().stream().filter(r -> r.evaluatorId().equals("llm-review")).findFirst().orElseThrow();
    }

    @Test
    void happyPathEndsCompletedWithFactsJudgmentsAssessmentsAndDiagram() {
        Harness h = new Harness(new FakeLlmClient().thenReturn(Samples.GOOD_REVIEW_JSON));
        Attempt a = h.submitParking(Samples.goodParkingLot());

        assertThat(a.status()).isEqualTo(AttemptStatus.COMPLETED);
        assertThat(a.report().completeness()).isEqualTo(Completeness.COMPLETE);
        assertThat(a.report().facts()).isNotEmpty();
        assertThat(a.report().judgments()).hasSize(1);
        assertThat(a.report().judgments().get(0).kind()).isEqualTo(FindingKind.JUDGMENT);
        assertThat(a.report().assessments()).extracting(DecisionAssessment::decisionPointId).containsExactly("fee-computation");
        assertThat(a.report().strengths()).hasSize(1);
        assertThat(a.report().mermaid()).startsWith("classDiagram").contains("PricingPolicy");
        assertThat(a.report().runs()).extracting(EvaluatorRun::outcome).containsOnly(RunOutcome.OK);
        // The good design should trip none of the structural complaints.
        assertThat(a.report().facts()).extracting(Finding::ruleId)
                .doesNotContain("GOD_CLASS", "TYPE_SWITCH", "HARDCODED_VALUE", "DEPENDENCY_CYCLE", "CONCRETE_DEPENDENCY");
        assertThat(h.scheduler.scheduled).containsExactly(a.id());
    }

    @Test
    void weakDesignTripsTheExpectedRules() {
        Harness h = new Harness(new FakeLlmClient().thenReturn("{\"findings\":[]}"));
        Attempt a = h.submitParking(Samples.weakParkingLot());
        assertThat(a.status()).isEqualTo(AttemptStatus.COMPLETED);
        assertThat(a.report().facts()).extracting(Finding::ruleId)
                .contains("TYPE_SWITCH", "HARDCODED_VALUE", "CAPABILITY_OWNERSHIP");
        assertThat(a.report().facts()).filteredOn(f -> f.ruleId().equals("CAPABILITY_OWNERSHIP"))
                .filteredOn(f -> f.severity() == Severity.HIGH).isNotEmpty();
    }

    @Test
    void factsAreSavedBeforeTheLlmRuns() {
        // Check the state the learner would see between the two stages.
        Harness h = new Harness(new FakeLlmClient().thenReturn(Samples.GOOD_REVIEW_JSON));
        Attempt draft = h.service.startAttempt(h.learner, "parking-lot");
        draft.replaceSubmission(Samples.goodParkingLot(), java.time.Instant.EPOCH);
        draft.apply(AttemptEvent.SUBMIT, java.time.Instant.EPOCH);
        h.repo.save(draft);

        assertThat(h.orchestrator.evaluateImmediate(draft.id())).isTrue();
        Attempt mid = h.repo.find(draft.id()).orElseThrow();
        assertThat(mid.status()).isEqualTo(AttemptStatus.EVALUATING);
        assertThat(mid.report().facts()).isNotEmpty();
        assertThat(mid.report().judgments()).isEmpty();
        assertThat(h.llm.callCount()).isZero();

        h.orchestrator.evaluateDeferred(draft.id());
        assertThat(h.repo.find(draft.id()).orElseThrow().status()).isEqualTo(AttemptStatus.COMPLETED);
    }

    @Test
    void llmTimeoutRetriesOnceThenEndsPartialKeepingFacts() {
        FakeLlmClient llm = new FakeLlmClient().thenTimeOut();
        Harness h = new Harness(llm);
        Attempt a = h.submitParking(Samples.goodParkingLot());

        assertThat(a.status()).isEqualTo(AttemptStatus.PARTIAL);
        assertThat(a.report().completeness()).isEqualTo(Completeness.PARTIAL);
        assertThat(a.report().facts()).isNotEmpty();
        assertThat(a.report().judgments()).isEmpty();
        assertThat(llmRun(a).attemptsUsed()).isEqualTo(2);
        assertThat(llmRun(a).outcome()).isEqualTo(RunOutcome.FAILED);
        assertThat(llm.callCount()).isEqualTo(2);
        assertThat(a.failureReason()).contains("unavailable");
    }

    @Test
    void transportErrorThenSuccessCompletes() {
        Harness h = new Harness(new FakeLlmClient().thenFailTransport().thenReturn(Samples.GOOD_REVIEW_JSON));
        Attempt a = h.submitParking(Samples.goodParkingLot());
        assertThat(a.status()).isEqualTo(AttemptStatus.COMPLETED);
        assertThat(llmRun(a).attemptsUsed()).isEqualTo(2);
    }

    @Test
    void aCallSlowerThanTheTimeoutIsMarkedTimedOut() {
        Harness h = new Harness(new FakeLlmClient().thenSleep(3000, Samples.GOOD_REVIEW_JSON));
        h.props.maxCallRetries = 0;
        Attempt a = h.submitParking(Samples.goodParkingLot());
        assertThat(a.status()).isEqualTo(AttemptStatus.PARTIAL);
        assertThat(llmRun(a).outcome()).isEqualTo(RunOutcome.TIMED_OUT);
    }

    @Test
    void malformedJsonGetsOneRepairRetryAndCanRecover() {
        FakeLlmClient llm = new FakeLlmClient().thenReturn("this is not json").thenReturn(Samples.GOOD_REVIEW_JSON);
        Harness h = new Harness(llm);
        Attempt a = h.submitParking(Samples.goodParkingLot());
        assertThat(a.status()).isEqualTo(AttemptStatus.COMPLETED);
        assertThat(llm.callCount()).isEqualTo(2);
        assertThat(llm.requests().get(1).prompt()).contains("could not be used");
        assertThat(llmRun(a).message()).contains("repaired");
    }

    @Test
    void malformedJsonTwiceEndsPartialWithoutFurtherRetries() {
        FakeLlmClient llm = new FakeLlmClient().thenReturn("nope");
        Harness h = new Harness(llm);
        Attempt a = h.submitParking(Samples.goodParkingLot());
        assertThat(a.status()).isEqualTo(AttemptStatus.PARTIAL);
        assertThat(llm.callCount()).isEqualTo(2);
        assertThat(llmRun(a).attemptsUsed()).isEqualTo(1);
        assertThat(a.failureReason()).contains("malformed");
    }

    @Test
    void citedClassesThatDoNotExistAreDroppedAndCountedButAttemptStillCompletes() {
        String json = "{\"findings\":[{\"category\":\"COHESION\",\"severity\":\"HIGH\",\"subject\":\"GhostManager\","
                + "\"claim\":\"c\",\"why\":\"w\",\"fix\":\"f\",\"tradeoff\":\"t\"}]}";
        Harness h = new Harness(new FakeLlmClient().thenReturn(json));
        Attempt a = h.submitParking(Samples.goodParkingLot());
        assertThat(a.status()).isEqualTo(AttemptStatus.COMPLETED);
        assertThat(a.report().judgments()).isEmpty();
        assertThat(llmRun(a).message()).contains("dropped 1").contains("subject not in code").contains("no usable findings");
    }

    @Test
    void nothingParsesEndsFailedWithTheParseErrors() {
        Harness h = new Harness(new FakeLlmClient().thenReturn("{}"));
        Attempt a = h.submitParking(Samples.sub("x", new SourceFile("Bad.java", "class { ??? }}}")));
        assertThat(a.status()).isEqualTo(AttemptStatus.FAILED);
        assertThat(a.failureReason()).contains("syntax error");
        assertThat(a.report().facts()).extracting(Finding::ruleId).contains("PARSE_ISSUE");
        assertThat(h.llm.callCount()).isZero();
        assertThat(h.scheduler.scheduled).isEmpty();
    }

    @Test
    void aBrokenFileIsSkippedWithAWarningWhileTheRestIsEvaluated() {
        Harness h = new Harness(new FakeLlmClient().thenReturn("{\"findings\":[]}"));
        java.util.ArrayList<SourceFile> files = new java.util.ArrayList<>(Samples.goodParkingLot().files());
        files.add(new SourceFile("Bad.java", "class Bad { void ( { }"));
        Attempt a = h.submitParking(new Submission(Submission.JAVA, files, "", null));
        assertThat(a.status()).isEqualTo(AttemptStatus.COMPLETED);
        assertThat(a.report().facts()).anyMatch(f -> f.ruleId().equals("PARSE_ISSUE") && f.subject().equals("Bad.java"));
    }

    @Test
    void missingEntryTypeAddsAHighParseIssue() {
        Harness h = new Harness(new FakeLlmClient().thenReturn("{\"findings\":[]}"));
        Attempt a = h.submitParking(Samples.sub("x", new SourceFile("Other.java", "class Other { }")));
        assertThat(a.report().facts()).anyMatch(f -> f.ruleId().equals("PARSE_ISSUE")
                && f.severity() == Severity.HIGH && f.subject().equals("ParkingLotService"));
    }

    @Test
    void aRuleThatThrowsIsSkippedAndNotedWhileOthersStillReport() {
        Rule boom = new Rule() {
            public String id() { return "BOOM"; }
            public List<Finding> check(DesignModel m, com.lldpractice.lld_practice_platform.problem.Problem p) {
                throw new IllegalStateException("kaboom");
            }
        };
        Harness h = new Harness(new FakeLlmClient().thenReturn("{\"findings\":[]}"), List.of(boom));
        Attempt a = h.submitParking(Samples.weakParkingLot());
        assertThat(a.status()).isEqualTo(AttemptStatus.COMPLETED);
        assertThat(a.report().facts()).extracting(Finding::ruleId).contains("TYPE_SWITCH");
        assertThat(a.report().runs().get(0).message()).contains("BOOM skipped");
    }

    @Test
    void retryFromPartialRerunsOnlyTheLlmStageAndCanComplete() {
        FakeLlmClient llm = new FakeLlmClient().thenTimeOut().thenTimeOut().thenReturn(Samples.GOOD_REVIEW_JSON);
        Harness h = new Harness(llm);
        Attempt partial = h.submitParking(Samples.goodParkingLot());
        assertThat(partial.status()).isEqualTo(AttemptStatus.PARTIAL);
        int factsBefore = partial.report().facts().size();

        h.service.retry(h.learner, partial.id());
        Attempt done = h.repo.find(partial.id()).orElseThrow();
        assertThat(done.status()).isEqualTo(AttemptStatus.COMPLETED);
        assertThat(done.report().facts()).hasSize(factsBefore);
        assertThat(done.report().judgments()).hasSize(1);
        assertThat(done.report().runs().stream().filter(r -> r.evaluatorId().equals("llm-review"))).hasSize(1);
        assertThat(done.failureReason()).isNull();
    }

    @Test
    void retryIsRejectedUnlessPartial() {
        Harness h = new Harness(new FakeLlmClient().thenReturn(Samples.GOOD_REVIEW_JSON));
        Attempt done = h.submitParking(Samples.goodParkingLot());
        assertThatThrownBy(() -> h.service.retry(h.learner, done.id())).isInstanceOf(IllegalTransitionException.class);
    }

    @Test
    void anotherLearnerCannotSeeOrRetryMyAttempt() {
        Harness h = new Harness(new FakeLlmClient().thenReturn(Samples.GOOD_REVIEW_JSON));
        Attempt mine = h.submitParking(Samples.goodParkingLot());
        UUID other = UUID.randomUUID();
        assertThatThrownBy(() -> h.service.load(other, mine.id())).isInstanceOf(AttemptNotFoundException.class);
        assertThatThrownBy(() -> h.service.retry(other, mine.id())).isInstanceOf(AttemptNotFoundException.class);
    }

    @Test
    void deferredStageOnAnAttemptThatIsNotEvaluatingIsIgnored() {
        Harness h = new Harness(new FakeLlmClient().thenReturn(Samples.GOOD_REVIEW_JSON));
        Attempt done = h.submitParking(Samples.goodParkingLot());
        int calls = h.llm.callCount();
        h.orchestrator.evaluateDeferred(done.id());
        assertThat(h.llm.callCount()).isEqualTo(calls);
        assertThat(h.repo.find(done.id()).orElseThrow().status()).isEqualTo(AttemptStatus.COMPLETED);
    }

    @Test
    void resubmittingCreatesANewAttemptWithTheNextSequence() {
        Harness h = new Harness(new FakeLlmClient().thenReturn(Samples.GOOD_REVIEW_JSON));
        Attempt first = h.submitParking(Samples.goodParkingLot());
        Attempt second = h.submitParking(Samples.goodParkingLot());
        assertThat(second.id()).isNotEqualTo(first.id());
        assertThat(second.sequence()).isEqualTo(first.sequence() + 1);
        assertThat(h.repo.history(h.learner, "parking-lot")).extracting(Attempt::sequence).containsExactly(1, 2);
    }

    // ---- submission validation
    @Test
    void emptySubmissionIsRejectedBeforeAnythingRuns() {
        Harness h = new Harness(new FakeLlmClient().thenReturn("{}"));
        Attempt draft = h.service.startAttempt(h.learner, "parking-lot");
        assertThatThrownBy(() -> h.service.submit(h.learner, draft.id(),
                Samples.sub("x", new SourceFile("A.java", "   "))))
                .isInstanceOf(SubmissionRejectedException.class).hasMessageContaining("Add some Java code");
        assertThat(h.repo.find(draft.id()).orElseThrow().status()).isEqualTo(AttemptStatus.DRAFT);
    }

    @Test
    void tooManyFilesAndTooMuchCodeAreRejected() {
        Harness h = new Harness(new FakeLlmClient().thenReturn("{}"));
        Attempt draft = h.service.startAttempt(h.learner, "parking-lot");
        SourceFile[] many = new SourceFile[21];
        for (int i = 0; i < many.length; i++) many[i] = new SourceFile("F" + i + ".java", "class F" + i + " {}");
        assertThatThrownBy(() -> h.service.submit(h.learner, draft.id(), Samples.sub("x", many)))
                .isInstanceOf(SubmissionRejectedException.class).hasMessageContaining("Too many files");
        SourceFile big = new SourceFile("Big.java", "class Big {}" + " ".repeat(200_001));
        assertThatThrownBy(() -> h.service.submit(h.learner, draft.id(), Samples.sub("x", big)))
                .isInstanceOf(SubmissionRejectedException.class).hasMessageContaining("too large");
    }

    @Test
    void unknownFormatIsRejected() {
        Harness h = new Harness(new FakeLlmClient().thenReturn("{}"));
        Attempt draft = h.service.startAttempt(h.learner, "parking-lot");
        Submission s = new Submission("cobol", List.of(new SourceFile("A", "x")), "", null);
        assertThatThrownBy(() -> h.service.submit(h.learner, draft.id(), s))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("cobol");
    }

    @Test
    void aSubmittedAttemptCannotBeSubmittedOrEditedAgain() {
        Harness h = new Harness(new FakeLlmClient().thenReturn(Samples.GOOD_REVIEW_JSON));
        Attempt a = h.submitParking(Samples.goodParkingLot());
        assertThatThrownBy(() -> h.service.submit(h.learner, a.id(), Samples.goodParkingLot()))
                .isInstanceOf(SubmissionRejectedException.class);
        assertThatThrownBy(() -> h.service.saveDraft(h.learner, a.id(), Samples.goodParkingLot()))
                .isInstanceOf(SubmissionRejectedException.class);
    }

    @Test
    void draftIsPrefilledWithTheSkeleton() {
        Harness h = new Harness(new FakeLlmClient().thenReturn("{}"));
        Attempt draft = h.service.startAttempt(h.learner, "elevator");
        assertThat(draft.status()).isEqualTo(AttemptStatus.DRAFT);
        assertThat(draft.submission().files()).extracting(SourceFile::name).contains("ElevatorSystem.java", "Direction.java");
    }

    // ---- change round
    @Test
    void changeRevisionComputesBlastRadiusAndPassesItToTheLlm() {
        FakeLlmClient llm = new FakeLlmClient().thenReturn(Samples.GOOD_REVIEW_JSON);
        Harness h = new Harness(llm);
        Attempt base = h.submitParking(Samples.goodParkingLot());
        Attempt rev = h.service.startChangeRevision(h.learner, base.id());
        assertThat(rev.kind()).isEqualTo(AttemptKind.CHANGE_REVISION);
        assertThat(rev.baseAttemptId()).isEqualTo(base.id());
        assertThat(rev.submission()).isEqualTo(base.submission());

        Attempt done = h.service.submit(h.learner, rev.id(), Samples.goodParkingLotWithEvSpots());
        ChangeImpact impact = done.report().changeImpact();
        assertThat(impact).isNotNull();
        assertThat(impact.added()).containsExactlyInAnyOrder("EvSpot", "ChargingPolicy");
        assertThat(impact.modified()).isEmpty();
        assertThat(impact.removed()).isEmpty();
        assertThat(impact.verdict()).isEqualTo(ContainmentVerdict.ADDITIVE);
        String lastPrompt = llm.requests().get(llm.requests().size() - 1).prompt();
        assertThat(lastPrompt).contains("CHANGE REQUEST").contains("verdict ADDITIVE");
    }

    @Test
    void aFailedOrDraftAttemptCannotBeTheBaseOfAChangeRound() {
        Harness h = new Harness(new FakeLlmClient().thenReturn("{}"));
        Attempt draft = h.service.startAttempt(h.learner, "parking-lot");
        assertThatThrownBy(() -> h.service.startChangeRevision(h.learner, draft.id()))
                .isInstanceOf(SubmissionRejectedException.class);
        Attempt failed = h.submitParking(Samples.sub("x", new SourceFile("Bad.java", "class { ??? }}}")));
        assertThat(failed.status()).isEqualTo(AttemptStatus.FAILED);
        assertThatThrownBy(() -> h.service.startChangeRevision(h.learner, failed.id()))
                .isInstanceOf(SubmissionRejectedException.class);
    }

    @Test
    void aPartialAttemptCanBeTheBaseOfAChangeRound() {
        Harness h = new Harness(new FakeLlmClient().thenTimeOut());
        Attempt partial = h.submitParking(Samples.goodParkingLot());
        assertThat(partial.status()).isEqualTo(AttemptStatus.PARTIAL);
        assertThat(h.service.startChangeRevision(h.learner, partial.id()).kind()).isEqualTo(AttemptKind.CHANGE_REVISION);
    }

    // ---- history
    @Test
    void historyShowsResolvedNewAndPersistingAgainstThePreviousAttempt() {
        Harness h = new Harness(new FakeLlmClient().thenReturn("{\"findings\":[]}"));
        Attempt weak = h.submitParking(Samples.weakParkingLot());
        Attempt good = h.submitParking(Samples.goodParkingLot());

        var diff = h.history.diffAgainstPrevious(good).orElseThrow();
        assertThat(diff.previousSequence()).isEqualTo(weak.sequence());
        assertThat(diff.facts().resolved()).extracting(Finding::ruleId).contains("TYPE_SWITCH", "HARDCODED_VALUE");
        assertThat(diff.judgmentsComparable()).isTrue();

        var rows = h.history.history(h.learner, "parking-lot");
        assertThat(rows).extracting(AttemptHistoryService.Row::sequence).containsExactly(1, 2);
        assertThat(rows.get(0).summary()).isEmpty();
        assertThat(rows.get(1).summary()).contains("resolved").contains("new").contains("persisting");
    }

    @Test
    void partialAttemptsOnlyCompareFacts() {
        FakeLlmClient llm = new FakeLlmClient().thenReturn(Samples.GOOD_REVIEW_JSON);
        Harness h = new Harness(llm);
        h.submitParking(Samples.goodParkingLot());
        FakeLlmClient llm2Script = llm; // keep the first script, then fail from the next call on
        llm2Script.thenTimeOut();
        Attempt second = h.submitParking(Samples.goodParkingLot());
        assertThat(second.status()).isEqualTo(AttemptStatus.PARTIAL);
        var diff = h.history.diffAgainstPrevious(second).orElseThrow();
        assertThat(diff.judgmentsComparable()).isFalse();
        assertThat(diff.judgments().resolved()).isEmpty();
    }

    @Test
    void failedAttemptsAreSkippedWhenFindingThePreviousAttempt() {
        Harness h = new Harness(new FakeLlmClient().thenReturn("{\"findings\":[]}"));
        Attempt first = h.submitParking(Samples.goodParkingLot());
        h.submitParking(Samples.sub("x", new SourceFile("Bad.java", "class { ??? }}}")));
        Attempt third = h.submitParking(Samples.goodParkingLot());
        var diff = h.history.diffAgainstPrevious(third).orElseThrow();
        assertThat(diff.previousSequence()).isEqualTo(first.sequence());
        assertThat(diff.facts().resolved()).isEmpty();
        assertThat(diff.facts().added()).isEmpty();
    }

    @Test
    void firstAttemptHasNothingToCompareAgainst() {
        Harness h = new Harness(new FakeLlmClient().thenReturn("{\"findings\":[]}"));
        Attempt first = h.submitParking(Samples.goodParkingLot());
        assertThat(h.history.diffAgainstPrevious(first)).isEmpty();
    }

    @Test
    void historyRowShowsAReadableUtcTime() {
        Harness h = new Harness(new FakeLlmClient().thenReturn("{\"findings\":[]}"));
        h.submitParking(Samples.goodParkingLot());
        assertThat(h.history.history(h.learner, "parking-lot").get(0).whenText()).isEqualTo("2026-01-01 00:00 UTC");
    }
}

package com.lldpractice.lld_practice_platform.web;

import com.lldpractice.lld_practice_platform.attempt.Attempt;
import com.lldpractice.lld_practice_platform.attempt.AttemptRepository;
import com.lldpractice.lld_practice_platform.attempt.AttemptStatus;
import com.lldpractice.lld_practice_platform.llm.FakeLlmClient;
import com.lldpractice.lld_practice_platform.llm.LlmClient;
import com.lldpractice.lld_practice_platform.model.SourceFile;
import com.lldpractice.lld_practice_platform.model.Submission;
import com.lldpractice.lld_practice_platform.orchestration.EvaluationOrchestrator;
import com.lldpractice.lld_practice_platform.orchestration.EvaluationScheduler;
import com.lldpractice.lld_practice_platform.orchestration.Samples;
import com.lldpractice.lld_practice_platform.orchestration.SynchronousEvaluationScheduler;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Import(WebFlowIntegrationTest.Config.class)
class WebFlowIntegrationTest {

    @TestConfiguration
    static class Config {
        @Bean
        @Primary
        FakeLlmClient fakeLlm() {
            return new FakeLlmClient();
        }

        @Bean
        @Primary
        EvaluationScheduler synchronousScheduler(EvaluationOrchestrator orchestrator) {
            return new SynchronousEvaluationScheduler(orchestrator);
        }
    }

    @Autowired MockMvc mvc;
    @Autowired FakeLlmClient llm;
    @Autowired AttemptRepository repo;
    @Autowired LlmClient llmClient;

    private Cookie learner;

    @BeforeEach
    void freshLearner() {
        llm.reset();
        llm.thenReturn(Samples.GOOD_REVIEW_JSON);
        learner = new Cookie("lld_learner", UUID.randomUUID().toString());
    }

    private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder b) {
        return b.cookie(learner);
    }

    private UUID startAttempt(String problem) throws Exception {
        MvcResult r = mvc.perform(as(post("/problems/" + problem + "/attempts"))).andExpect(status().is3xxRedirection()).andReturn();
        String loc = r.getResponse().getRedirectedUrl();
        assertThat(loc).matches("/attempts/[0-9a-f-]{36}/edit");
        return UUID.fromString(loc.split("/")[2]);
    }

    private MockHttpServletRequestBuilder submitForm(UUID id, Submission s) {
        MockHttpServletRequestBuilder b = as(post("/attempts/" + id + "/submit"));
        for (SourceFile f : s.files()) {
            b.param("fileName", f.name()).param("fileContent", f.content());
        }
        return b.param("assumptions", s.assumptions())
                .param("patternUsed", s.rationale().patternUsed())
                .param("patternRejected", s.rationale().patternRejected())
                .param("note", s.rationale().note());
    }

    @Test
    void homePageListsTheThreeProblemsAndIssuesALearnerCookie() throws Exception {
        MvcResult r = mvc.perform(get("/")).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Parking Lot")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Elevator System")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Vending Machine")))
                .andReturn();
        assertThat(r.getResponse().getHeader("Set-Cookie")).contains("lld_learner=").contains("HttpOnly").contains("SameSite=Lax");
        mvc.perform(as(get("/"))).andExpect(status().isOk());
    }

    @Test
    void problemPageShowsRequirementsAndUnknownProblemIs404() throws Exception {
        mvc.perform(get("/problems/parking-lot")).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Part hours round up")));
        mvc.perform(get("/problems/nope")).andExpect(status().isNotFound());
        mvc.perform(as(post("/problems/nope/attempts"))).andExpect(status().isNotFound());
    }

    @Test
    void fullLoopStartEditSubmitReadReport() throws Exception {
        UUID id = startAttempt("parking-lot");
        mvc.perform(as(get("/attempts/" + id + "/edit"))).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("ParkingLotService.java")));

        mvc.perform(submitForm(id, Samples.goodParkingLot())).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/attempts/" + id));

        String page = mvc.perform(as(get("/attempts/" + id))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(page).contains("COMPLETED").contains("AI opinion, advisory").contains("Rule-based findings")
                .contains("classDiagram").contains("Start the change request").contains("Decision points").contains("Strengths")
                .contains("Pricing and allocation sit behind interfaces.");
        // The editor is no longer available once submitted.
        mvc.perform(as(get("/attempts/" + id + "/edit"))).andExpect(redirectedUrl("/attempts/" + id));

        mvc.perform(as(get("/attempts/" + id + "/status"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.done").value(true))
                .andExpect(jsonPath("$.judgmentCount").value(1))
                .andExpect(jsonPath("$.runs.length()").value(2))
                .andExpect(jsonPath("$.runs[1].evaluatorId").value("llm-review"));
    }

    @Test
    void savedReportSurvivesThePersistenceRoundTrip() throws Exception {
        UUID id = startAttempt("parking-lot");
        mvc.perform(submitForm(id, Samples.goodParkingLot()));
        Attempt a = repo.find(id).orElseThrow();
        assertThat(a.status()).isEqualTo(AttemptStatus.COMPLETED);
        assertThat(a.submission().files()).hasSize(Samples.goodParkingLot().files().size());
        assertThat(a.report().facts()).isNotEmpty();
        assertThat(a.report().judgments()).hasSize(1);
        assertThat(a.report().runs()).hasSize(2);
        assertThat(a.report().runs().get(0).duration()).isNotNull();
        assertThat(a.report().mermaid()).startsWith("classDiagram");
        assertThat(a.report().facts().get(0).fingerprint()).isNotBlank();
    }

    @Test
    void emptySubmissionReRendersTheEditorWithAMessage() throws Exception {
        UUID id = startAttempt("parking-lot");
        mvc.perform(as(post("/attempts/" + id + "/submit")).param("fileName", "A.java").param("fileContent", "  "))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Add some Java code")));
        assertThat(repo.find(id).orElseThrow().status()).isEqualTo(AttemptStatus.DRAFT);
    }

    @Test
    void saveDraftKeepsTheTypedCode() throws Exception {
        UUID id = startAttempt("parking-lot");
        mvc.perform(as(post("/attempts/" + id + "/draft")).param("fileName", "ParkingLotService.java")
                        .param("fileContent", "class ParkingLotService { /* my draft */ }").param("assumptions", "one gate"))
                .andExpect(redirectedUrl("/attempts/" + id + "/edit?saved"));
        mvc.perform(as(get("/attempts/" + id + "/edit"))).andExpect(content().string(org.hamcrest.Matchers.containsString("my draft")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("one gate")));
    }

    @Test
    void learnerTextIsEscapedEverywhereItIsShown() throws Exception {
        UUID id = startAttempt("parking-lot");
        String evil = "<script>alert(1)</script>";
        Submission s = new Submission("java", List.of(
                new SourceFile("ParkingLotService.java", "class ParkingLotService { void park() { int x = " + evil + "; } }"),
                new SourceFile("<img src=x onerror=alert(1)>.java", "class B { }")), evil, null);
        mvc.perform(submitForm(id, s)).andExpect(status().is3xxRedirection());
        String page = mvc.perform(as(get("/attempts/" + id))).andReturn().getResponse().getContentAsString();
        assertThat(page).doesNotContain("<script>alert(1)").doesNotContain("<img src=x");
        String editor = mvc.perform(as(get("/attempts/" + id + "/edit"))).andReturn().getResponse().getContentAsString();
        assertThat(editor).doesNotContain("<script>alert(1)");
    }

    @Test
    void anotherBrowserCannotSeeMyAttempt() throws Exception {
        UUID id = startAttempt("parking-lot");
        mvc.perform(submitForm(id, Samples.goodParkingLot()));
        Cookie other = new Cookie("lld_learner", UUID.randomUUID().toString());
        mvc.perform(get("/attempts/" + id).cookie(other)).andExpect(status().isNotFound());
        mvc.perform(get("/attempts/" + id + "/status").cookie(other)).andExpect(status().isNotFound());
        mvc.perform(post("/attempts/" + id + "/retry").cookie(other)).andExpect(status().isNotFound());
        mvc.perform(get("/attempts/" + UUID.randomUUID()).cookie(other)).andExpect(status().isNotFound());
    }

    @Test
    void llmOutageShowsFactsABannerAndARetryButtonThatWorksOnce() throws Exception {
        llm.reset();
        llm.thenTimeOut().thenTimeOut().thenReturn(Samples.GOOD_REVIEW_JSON);
        UUID id = startAttempt("parking-lot");
        mvc.perform(submitForm(id, Samples.goodParkingLot()));
        String page = mvc.perform(as(get("/attempts/" + id))).andReturn().getResponse().getContentAsString();
        assertThat(page).contains("PARTIAL").contains("The AI review was unavailable").contains("Retry the AI review")
                .contains("Rule-based findings");

        mvc.perform(as(post("/attempts/" + id + "/retry"))).andExpect(redirectedUrl("/attempts/" + id));
        assertThat(repo.find(id).orElseThrow().status()).isEqualTo(AttemptStatus.COMPLETED);
        // A second press is an illegal transition with a clear message.
        mvc.perform(as(post("/attempts/" + id + "/retry"))).andExpect(status().isConflict())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Cannot apply RETRY")));
    }

    @Test
    void unreadableCodeEndsFailedAndOffersEditAndResubmit() throws Exception {
        UUID id = startAttempt("parking-lot");
        mvc.perform(submitForm(id, new Submission("java", List.of(new SourceFile("Bad.java", "class { ??? }}}")), "", null)));
        String page = mvc.perform(as(get("/attempts/" + id))).andReturn().getResponse().getContentAsString();
        assertThat(page).contains("FAILED").contains("We could not read your code").contains("Edit and resubmit");

        MvcResult again = mvc.perform(as(post("/attempts/" + id + "/edit-again"))).andExpect(status().is3xxRedirection()).andReturn();
        String loc = again.getResponse().getRedirectedUrl();
        assertThat(loc).endsWith("/edit").isNotEqualTo("/attempts/" + id + "/edit");
        mvc.perform(as(get(loc))).andExpect(content().string(org.hamcrest.Matchers.containsString("Bad.java")));
        // A failed attempt cannot start a change round.
        mvc.perform(as(post("/attempts/" + id + "/change-request"))).andExpect(status().isBadRequest());
    }

    @Test
    void changeRoundShowsBlastRadiusAndTheHistoryDiff() throws Exception {
        UUID base = startAttempt("parking-lot");
        mvc.perform(submitForm(base, Samples.goodParkingLot()));

        MvcResult r = mvc.perform(as(post("/attempts/" + base + "/change-request"))).andExpect(status().is3xxRedirection()).andReturn();
        UUID rev = UUID.fromString(r.getResponse().getRedirectedUrl().split("/")[2]);
        mvc.perform(as(get("/attempts/" + rev + "/edit"))).andExpect(content().string(org.hamcrest.Matchers.containsString("Change request")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("electric-vehicle")));
        mvc.perform(submitForm(rev, Samples.goodParkingLotWithEvSpots())).andExpect(status().is3xxRedirection());

        String page = mvc.perform(as(get("/attempts/" + rev))).andReturn().getResponse().getContentAsString();
        assertThat(page).contains("Blast radius of the change").contains("ADDITIVE").contains("EvSpot").contains("Compared with attempt");

        String history = mvc.perform(as(get("/problems/parking-lot/history"))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(history).contains("Attempt history").contains("Change round").contains("ADDITIVE").contains("resolved");
    }

    @Test
    void historyPageForANewLearnerIsEmpty() throws Exception {
        mvc.perform(as(get("/problems/elevator/history"))).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("No attempts yet")));
    }

    @Test
    void staticAssetsAndMermaidWebjarAreServedLocally() throws Exception {
        mvc.perform(get("/css/app.css")).andExpect(status().isOk());
        mvc.perform(get("/js/attempt.js")).andExpect(status().isOk());
        mvc.perform(get("/webjars/mermaid/11.12.3/dist/mermaid.min.js")).andExpect(status().isOk());
    }

    @Test
    void theWiredLlmIsOurFake() {
        assertThat(llmClient).isSameAs(llm);
    }

    @Test
    void anAttemptThatIsStillEvaluatingShowsALoaderAndTheFactsAlreadySaved() throws Exception {
        UUID id = startAttempt("parking-lot");
        Attempt a = repo.find(id).orElseThrow();
        a.replaceSubmission(Samples.goodParkingLot(), java.time.Instant.EPOCH);
        a.apply(com.lldpractice.lld_practice_platform.attempt.AttemptEvent.SUBMIT, java.time.Instant.EPOCH);
        a.apply(com.lldpractice.lld_practice_platform.attempt.AttemptEvent.START_EVALUATION, java.time.Instant.EPOCH);
        repo.save(a);

        String page = mvc.perform(as(get("/attempts/" + id))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(page).contains("EVALUATING").contains("Waiting for the AI review").contains("class=\"spinner\"")
                .contains("role=\"status\"").contains("id=\"elapsed\"");
        mvc.perform(as(get("/attempts/" + id + "/status"))).andExpect(jsonPath("$.done").value(false));
    }

    @Test
    void theEditorFormShowsABusyStateWhenSubmitted() throws Exception {
        UUID id = startAttempt("parking-lot");
        String page = mvc.perform(as(get("/attempts/" + id + "/edit"))).andReturn().getResponse().getContentAsString();
        assertThat(page).contains("data-busy=\"Submitting...\"").contains("/js/attempt.js");
    }

    @Test
    void everyPageHasTheThemeToggleAndTheThemeScriptIsServed() throws Exception {
        for (String url : new String[]{"/", "/problems/parking-lot", "/problems/elevator/history"}) {
            String page = mvc.perform(as(get(url))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertThat(page).as(url).contains("id=\"theme-toggle\"").contains("/js/theme.js");
        }
        String js = mvc.perform(get("/js/theme.js")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(js).contains("data-theme").contains("localStorage").contains("prefers-color-scheme");
        String css = mvc.perform(get("/css/app.css")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(css).contains(":root[data-theme=\"dark\"]").contains("--on-accent");
    }
}

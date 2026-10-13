package com.lldpractice.lld_practice_platform.llm;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GeminiLlmClientTest {
    @Test
    void extractsTheTextFromACandidate() throws Exception {
        String body = "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"{\\\"findings\\\":[]}\"}]}}]}";
        assertThat(GeminiLlmClient.extractText(body)).isEqualTo("{\"findings\":[]}");
    }

    @Test
    void anUnexpectedShapeIsAnLlmException() {
        assertThatThrownBy(() -> GeminiLlmClient.extractText("{\"error\":\"x\"}")).isInstanceOf(LlmException.class);
        assertThatThrownBy(() -> GeminiLlmClient.extractText("not json")).isInstanceOf(LlmException.class);
    }

    @Test
    void demoClientReturnsClearlyLabelledPlaceholderForAPromptWithClasses() throws Exception {
        var r = new DemoLlmClient().complete(new LlmRequest("s", "DESIGN MODEL\nclass Lot [CLASS]\n  method public void park()\n", 0));
        assertThat(r.text()).contains("Demo mode").contains("\"subject\":\"Lot\"");
        assertThat(LlmReviewParser.parse(r.text()).findings()).hasSize(1);
    }

    @Test
    void demoClientWithNoClassesReturnsNoFindings() throws Exception {
        assertThat(LlmReviewParser.parse(new DemoLlmClient().complete(new LlmRequest("s", "nothing", 0)).text()).findings()).isEmpty();
    }
}

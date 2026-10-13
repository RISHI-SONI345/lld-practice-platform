package com.lldpractice.lld_practice_platform.llm;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LlmReviewParserTest {
    @Test
    void parsesFindingsAssessmentsAndAtMostThreeStrengths() throws Exception {
        var p = LlmReviewParser.parse("""
                {"findings":[{"category":"COHESION","severity":"LOW","subject":"A","claim":"c","why":"w","fix":"f","tradeoff":"t"}],
                 "decisionPoints":[{"id":"d1","approachUsed":"x","agreesWithRationale":true,"tradeoff":"t"}],
                 "strengths":["a","b","c","d"]}""");
        assertThat(p.findings()).hasSize(1);
        assertThat(p.assessments()).hasSize(1);
        assertThat(p.assessments().get(0).agrees()).isTrue();
        assertThat(p.strengths()).containsExactly("a", "b", "c");
    }

    @Test
    void toleratesMarkdownFences() throws Exception {
        assertThat(LlmReviewParser.parse("```json\n{\"findings\":[]}\n```").findings()).isEmpty();
    }

    @Test
    void missingFieldsBecomeNullSoTheValidatorCanDropThem() throws Exception {
        var p = LlmReviewParser.parse("{\"findings\":[{\"category\":\"COHESION\"},\"junk\"]}");
        assertThat(p.findings()).hasSize(2);
        assertThat(p.findings().get(0).subject()).isNull();
    }

    @Test
    void rejectsEmptyNonJsonNonObjectAndMissingFindings() {
        assertThatThrownBy(() -> LlmReviewParser.parse("")).isInstanceOf(LlmReviewParser.ReviewFormatException.class);
        assertThatThrownBy(() -> LlmReviewParser.parse("hello")).isInstanceOf(LlmReviewParser.ReviewFormatException.class);
        assertThatThrownBy(() -> LlmReviewParser.parse("[1,2]")).isInstanceOf(LlmReviewParser.ReviewFormatException.class);
        assertThatThrownBy(() -> LlmReviewParser.parse("{\"other\":1}")).isInstanceOf(LlmReviewParser.ReviewFormatException.class)
                .hasMessageContaining("findings");
    }

    @Test
    void thePromptShowsGenericFieldTypesAndHidesMethodBodies() {
        var model = com.lldpractice.lld_practice_platform.evaluation.TestSupport.model(
                "import java.util.*; class Car {} class Lot { private final List<Car> cars = new ArrayList<>(); int f() { return 4242; } }");
        String text = ModelTextRenderer.render(model);
        org.assertj.core.api.Assertions.assertThat(text).contains("field private List<Car> cars").doesNotContain("4242");
    }
}

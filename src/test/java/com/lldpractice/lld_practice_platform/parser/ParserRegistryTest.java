package com.lldpractice.lld_practice_platform.parser;

import com.lldpractice.lld_practice_platform.model.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ParserRegistryTest {
    private static SubmissionParser fake(String format) {
        return new SubmissionParser() {
            public String format() { return format; }
            public ParseResult parse(Submission s) { return new ParseResult(DesignModel.empty(), List.of()); }
        };
    }

    @Test
    void selectsByFormatKey() {
        SubmissionParser mermaid = fake("mermaid");
        ParserRegistry reg = new ParserRegistry(List.of(fake("java"), mermaid));
        assertThat(reg.forFormat("mermaid")).isSameAs(mermaid);
    }

    @Test
    void unknownFormatFailsFastWithClearMessage() {
        ParserRegistry reg = new ParserRegistry(List.of(fake("java")));
        assertThatThrownBy(() -> reg.forFormat("cobol"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cobol")
                .hasMessageContaining("java");
    }

    @Test
    void duplicateFormatKeyFailsAtStartup() {
        assertThatThrownBy(() -> new ParserRegistry(List.of(fake("java"), fake("java"))))
                .isInstanceOf(IllegalStateException.class);
    }
}

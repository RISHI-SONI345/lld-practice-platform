package com.lldpractice.lld_practice_platform.orchestration;

import org.junit.jupiter.api.Test;

import static com.lldpractice.lld_practice_platform.evaluation.TestSupport.model;
import static org.assertj.core.api.Assertions.assertThat;

class MermaidClassDiagramRendererTest {
    private final MermaidClassDiagramRenderer r = new MermaidClassDiagramRenderer();

    @Test
    void rendersClassesRelationshipsAndStereotypes() {
        String out = r.render(model("""
                interface Pricing { double fee(int h); }
                class Hourly implements Pricing { public double fee(int h) { return 1; } }
                class Base {} class Kid extends Base { private Hourly rate; void use(Pricing p) {} }
                """));
        assertThat(out).startsWith("classDiagram");
        assertThat(out).contains("<<interface>>").contains("Pricing <|.. Hourly").contains("Base <|-- Kid")
                .contains("Kid o-- Hourly").contains("Kid ..> Pricing");
    }

    @Test
    void genericsAndOddCharactersCannotBreakTheDiagramSyntax() {
        String out = r.render(model("import java.util.*; class Spot {} class Lot { Map<String, List<Spot>> m; List<Spot> spots() { return null; } }"));
        assertThat(out).doesNotContain("<String").doesNotContain("List<").contains("Map~String, List~Spot~~ m").contains("List~Spot~");
    }

    @Test
    void emptyModelStillGivesAValidHeader() {
        assertThat(r.render(com.lldpractice.lld_practice_platform.model.DesignModel.empty()).strip()).isEqualTo("classDiagram");
    }
}

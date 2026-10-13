package com.lldpractice.lld_practice_platform.orchestration;

import com.lldpractice.lld_practice_platform.evaluation.DiagramRenderer;
import com.lldpractice.lld_practice_platform.model.*;
import org.springframework.stereotype.Component;

/** Generates Mermaid class-diagram text from the learner's own code. Names come from parsed identifiers only. */
@Component
public class MermaidClassDiagramRenderer implements DiagramRenderer {
    @Override
    public String render(DesignModel model) {
        StringBuilder sb = new StringBuilder("classDiagram\n");
        for (ClassNode c : model.classes().values()) {
            String id = ident(c.name());
            sb.append("  class ").append(id).append(" {\n");
            switch (c.kind()) {
                case INTERFACE -> sb.append("    <<interface>>\n");
                case ABSTRACT_CLASS -> sb.append("    <<abstract>>\n");
                case ENUM -> sb.append("    <<enumeration>>\n");
                case RECORD -> sb.append("    <<record>>\n");
                default -> {
                }
            }
            for (FieldNode f : c.fields()) {
                sb.append("    ").append(mark(f.visibility())).append(safe(f.declaredType())).append(' ').append(ident(f.name())).append('\n');
            }
            for (MethodNode m : c.methods()) {
                sb.append("    ").append(mark(m.visibility())).append(ident(m.name())).append("()");
                if (!m.returnType().isEmpty()) sb.append(' ').append(safe(m.returnType()));
                sb.append('\n');
            }
            sb.append("  }\n");
        }
        for (Relationship r : model.relationships()) {
            String a = ident(r.from());
            String b = ident(r.to());
            switch (r.kind()) {
                case EXTENDS -> sb.append("  ").append(b).append(" <|-- ").append(a).append('\n');
                case IMPLEMENTS -> sb.append("  ").append(b).append(" <|.. ").append(a).append('\n');
                case HAS_A -> sb.append("  ").append(a).append(" o-- ").append(b).append('\n');
                case USES -> sb.append("  ").append(a).append(" ..> ").append(b).append('\n');
            }
        }
        return sb.toString();
    }

    private static String mark(String visibility) {
        return switch (visibility) {
            case "public" -> "+";
            case "private" -> "-";
            case "protected" -> "#";
            default -> "~";
        };
    }

    private static String ident(String s) {
        return s.replaceAll("[^A-Za-z0-9_]", "_");
    }

    /** Generics are written the way Mermaid reads them (List~Spot~); any other odd character is replaced. */
    private static String safe(String s) {
        return s.replace('<', '~').replace('>', '~').replaceAll("\\s+", "")
                .replaceAll("[^A-Za-z0-9_,~\\[\\]]", "_").replace(",", ", ");
    }
}

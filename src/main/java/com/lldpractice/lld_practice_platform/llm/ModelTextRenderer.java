package com.lldpractice.lld_practice_platform.llm;

import com.lldpractice.lld_practice_platform.model.*;

/** Renders a DesignModel as compact text for the LLM prompt. Method bodies are never included. */
public final class ModelTextRenderer {
    private ModelTextRenderer() {
    }

    public static String render(DesignModel model) {
        StringBuilder sb = new StringBuilder();
        for (ClassNode c : model.classes().values()) {
            sb.append("class ").append(c.name()).append(" [").append(c.kind()).append(']');
            if (!c.extendsTypes().isEmpty()) sb.append(" extends ").append(String.join(", ", c.extendsTypes()));
            if (!c.implementsTypes().isEmpty()) sb.append(" implements ").append(String.join(", ", c.implementsTypes()));
            if (!c.summary().isBlank()) sb.append(" - ").append(c.summary());
            sb.append('\n');
            if (!c.enumConstants().isEmpty()) sb.append("  constants ").append(String.join(", ", c.enumConstants())).append('\n');
            for (FieldNode f : c.fields()) {
                sb.append("  field ").append(f.visibility()).append(' ').append(f.declaredType()).append(' ').append(f.name()).append('\n');
            }
            for (MethodNode m : c.methods()) {
                sb.append("  method ").append(m.visibility()).append(' ');
                if (!m.returnType().isEmpty()) sb.append(m.returnType()).append(' ');
                sb.append(m.name()).append('(');
                for (int i = 0; i < m.parameters().size(); i++) {
                    if (i > 0) sb.append(", ");
                    sb.append(m.parameters().get(i).type()).append(' ').append(m.parameters().get(i).name());
                }
                sb.append(")\n");
            }
        }
        if (!model.relationships().isEmpty()) {
            sb.append("relationships:\n");
            for (Relationship r : model.relationships()) {
                sb.append("  ").append(r.from()).append(' ').append(r.kind()).append(' ').append(r.to()).append('\n');
            }
        }
        return sb.toString();
    }
}

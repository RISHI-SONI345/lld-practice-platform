package com.lldpractice.lld_practice_platform.model;

/**
 * {@code type} is the unwrapped type name(s), used for relationships. {@code declaredType} is the type as the learner
 * wrote it, generics included, so the AI review and the diagram can tell a {@code List<Elevator>} from an
 * {@code Elevator}.
 */
public record FieldNode(String name, String type, String visibility, boolean isFinal, String declaredType) {
    public FieldNode {
        declaredType = declaredType == null || declaredType.isBlank() ? type : declaredType;
    }

    public FieldNode(String name, String type, String visibility, boolean isFinal) {
        this(name, type, visibility, isFinal, type);
    }
}

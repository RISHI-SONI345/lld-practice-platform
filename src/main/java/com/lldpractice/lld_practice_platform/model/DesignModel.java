package com.lldpractice.lld_practice_platform.model;

import java.util.*;

/** The format-independent picture of a design. Every evaluator reads only this. */
public record DesignModel(Map<String, ClassNode> classes, List<Relationship> relationships, List<ParseIssue> issues) {
    public DesignModel {
        classes = Collections.unmodifiableMap(new LinkedHashMap<>(classes));
        relationships = List.copyOf(relationships);
        issues = List.copyOf(issues);
    }

    public static DesignModel empty() {
        return new DesignModel(Map.of(), List.of(), List.of());
    }

    public Optional<ClassNode> find(String name) {
        return Optional.ofNullable(classes.get(name));
    }

    /** Classes that {@code name} depends on through HAS_A or USES edges. */
    public Set<String> dependenciesOf(String name) {
        Set<String> out = new LinkedHashSet<>();
        for (Relationship r : relationships) {
            if (r.from().equals(name) && (r.kind() == RelationshipKind.HAS_A || r.kind() == RelationshipKind.USES)) {
                out.add(r.to());
            }
        }
        return out;
    }

    public Set<String> implementersOf(String interfaceName) {
        Set<String> out = new LinkedHashSet<>();
        for (Relationship r : relationships) {
            if (r.to().equals(interfaceName)
                    && (r.kind() == RelationshipKind.IMPLEMENTS || r.kind() == RelationshipKind.EXTENDS)) {
                out.add(r.from());
            }
        }
        return out;
    }

    public boolean hasMethod(String className, String methodName) {
        return find(className).map(c -> c.methods().stream().anyMatch(m -> m.name().equals(methodName))).orElse(false);
    }

    /** True if subject is a class name or Class.method that exists in the model. */
    public boolean hasSubject(String subject) {
        if (subject == null || subject.isBlank()) return false;
        int dot = subject.indexOf('.');
        if (dot < 0) return classes.containsKey(subject);
        return hasMethod(subject.substring(0, dot), subject.substring(dot + 1));
    }
}

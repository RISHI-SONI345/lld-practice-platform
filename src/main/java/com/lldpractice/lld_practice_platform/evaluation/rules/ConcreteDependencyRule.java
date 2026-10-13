package com.lldpractice.lld_practice_platform.evaluation.rules;

import com.lldpractice.lld_practice_platform.evaluation.Rule;
import com.lldpractice.lld_practice_platform.model.*;
import com.lldpractice.lld_practice_platform.problem.Problem;
import org.springframework.stereotype.Component;

import java.util.*;

import static com.lldpractice.lld_practice_platform.evaluation.rules.RuleSupport.fact;

/** A depends on concrete C, C implements interface I from the submission, and A never references I. */
@Component
public class ConcreteDependencyRule implements Rule {
    @Override
    public String id() {
        return "CONCRETE_DEPENDENCY";
    }

    @Override
    public List<Finding> check(DesignModel model, Problem problem) {
        List<Finding> out = new ArrayList<>();
        for (ClassNode a : model.classes().values()) {
            Set<String> deps = model.dependenciesOf(a.name());
            List<String> offending = new ArrayList<>();
            List<SourceRef> evidence = new ArrayList<>();
            for (String d : deps) {
                ClassNode concrete = model.classes().get(d);
                if (concrete == null || concrete.kind() == ClassKind.INTERFACE
                        || concrete.kind() == ClassKind.ABSTRACT_CLASS || concrete.kind() == ClassKind.ENUM) continue;
                List<String> interfaces = implementedInterfacesInSubmission(model, concrete);
                if (interfaces.isEmpty()) continue;
                if (interfaces.stream().anyMatch(deps::contains)) continue;
                if (interfaces.contains(a.name())) continue;
                offending.add(d + " (implements " + String.join(", ", interfaces) + ")");
                model.relationships().stream()
                        .filter(r -> r.from().equals(a.name()) && r.to().equals(d)
                                && (r.kind() == RelationshipKind.HAS_A || r.kind() == RelationshipKind.USES))
                        .findFirst().ifPresent(r -> evidence.add(r.source()));
            }
            if (!offending.isEmpty()) {
                out.add(fact(id(), Severity.LOW, a.name(),
                        a.name() + " depends directly on " + String.join("; ", offending)
                                + " and never refers to the interface.",
                        "Depending on the concrete class ties " + a.name() + " to one implementation, so a new one means editing it.",
                        "Hold, accept or return the interface type instead of the concrete class.",
                        evidence));
            }
        }
        return out;
    }

    private static List<String> implementedInterfacesInSubmission(DesignModel model, ClassNode c) {
        List<String> out = new ArrayList<>();
        for (Relationship r : model.relationships()) {
            if (r.from().equals(c.name()) && r.kind() == RelationshipKind.IMPLEMENTS) {
                ClassNode target = model.classes().get(r.to());
                if (target != null && target.kind() == ClassKind.INTERFACE) out.add(r.to());
            }
        }
        return out;
    }
}

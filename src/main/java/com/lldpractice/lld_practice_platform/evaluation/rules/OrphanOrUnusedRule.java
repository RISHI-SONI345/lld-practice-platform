package com.lldpractice.lld_practice_platform.evaluation.rules;

import com.lldpractice.lld_practice_platform.evaluation.Rule;
import com.lldpractice.lld_practice_platform.model.*;
import com.lldpractice.lld_practice_platform.problem.Problem;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

import static com.lldpractice.lld_practice_platform.evaluation.rules.RuleSupport.fact;

@Component
public class OrphanOrUnusedRule implements Rule {
    @Override
    public String id() {
        return "ORPHAN_OR_UNUSED";
    }

    @Override
    public List<Finding> check(DesignModel model, Problem problem) {
        List<Finding> out = new ArrayList<>();
        for (ClassNode c : model.classes().values()) {
            if (problem.entryTypes().contains(c.name())) continue;
            if (c.kind() == ClassKind.INTERFACE) {
                if (model.implementersOf(c.name()).isEmpty()) {
                    out.add(fact(id(), Severity.LOW, c.name(),
                            "Interface " + c.name() + " has no implementer in the submission.",
                            "An interface nobody implements adds indirection without any benefit.",
                            "Add an implementation or remove the interface.", List.of(c.source())));
                }
                continue;
            }
            boolean connected = model.relationships().stream()
                    .anyMatch(r -> r.from().equals(c.name()) || r.to().equals(c.name()));
            if (!connected) {
                out.add(fact(id(), Severity.LOW, c.name(),
                        "Class " + c.name() + " is not connected to any other class in the submission.",
                        "A class nothing uses, and that uses nothing, is usually dead code or a missing relationship.",
                        "Connect it to the design or remove it.", List.of(c.source())));
            }
        }
        return out;
    }
}

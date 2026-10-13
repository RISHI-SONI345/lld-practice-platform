package com.lldpractice.lld_practice_platform.evaluation.rules;

import com.lldpractice.lld_practice_platform.evaluation.CapabilityMatcher;
import com.lldpractice.lld_practice_platform.evaluation.Rule;
import com.lldpractice.lld_practice_platform.model.*;
import com.lldpractice.lld_practice_platform.problem.Capability;
import com.lldpractice.lld_practice_platform.problem.Problem;
import org.springframework.stereotype.Component;

import java.util.*;

import static com.lldpractice.lld_practice_platform.evaluation.rules.RuleSupport.fact;

/** Every required capability has an owner. Matches name fragments, so it can miss an unconventional name. */
@Component
public class CapabilityOwnershipRule implements Rule {
    @Override
    public String id() {
        return "CAPABILITY_OWNERSHIP";
    }

    @Override
    public List<Finding> check(DesignModel model, Problem problem) {
        if (model.classes().isEmpty()) return List.of();

        // The entry type's own public methods (park, step, ...) are the doorway into the design, not its owners. A
        // method in ANOTHER class that has the same name (for example Elevator.step behind ElevatorSystem.step) is
        // exactly the delegation we want to see, so it does count.
        Set<String> entrySignatures = new HashSet<>();
        for (String entry : problem.entryTypes()) {
            model.find(entry).ifPresent(c -> c.methods().stream().filter(m -> m.visibility().equals("public"))
                    .forEach(m -> entrySignatures.add(entry + "." + m.name() + "/" + m.parameters().size())));
        }
        String anchor = problem.entryTypes().stream().filter(model.classes()::containsKey).findFirst()
                .orElse(model.classes().keySet().iterator().next());

        List<Finding> out = new ArrayList<>();
        for (Capability cap : problem.capabilities()) {
            ClassNode ownerClass = null;
            MethodNode ownerMethod = null;
            search:
            for (ClassNode c : model.classes().values()) {
                for (MethodNode m : c.methods()) {
                    if (m.name().equals(c.name())) continue;
                    if (entrySignatures.contains(c.name() + "." + m.name() + "/" + m.parameters().size())) continue;
                    if (CapabilityMatcher.matches(m.name(), cap.nameHints())) {
                        ownerClass = c;
                        ownerMethod = m;
                        break search;
                    }
                }
            }
            Implicit implicit = ownerMethod == null ? implicitEvidence(model, problem, cap) : null;
            if (implicit != null) {
                // Not named like a method, but the design clearly mentions it (an enum constant, a field, a class).
                out.add(fact(id(), Severity.MEDIUM, implicit.subject(),
                        "No method is named like it can " + cap.description() + ", but " + implicit.what()
                                + " suggests it is handled inside another method.",
                        "A responsibility that is only implied is hard to find, test and change on its own.",
                        "Give it an explicit method (for example named after one of: "
                                + String.join(", ", cap.nameHints()) + ") on the class that should own it.",
                        List.of(implicit.source()), cap.id()));
            } else if (ownerMethod == null) {
                out.add(fact(id(), Severity.HIGH, anchor,
                        "No method in the submission looks like it can " + cap.description() + ".",
                        "The requirements need this ability, and without an owner nobody is responsible for it.",
                        "Add a method (for example named after one of: " + String.join(", ", cap.nameHints())
                                + ") on the class that should own it.",
                        List.of(model.find(anchor).get().source()), cap.id()));
            } else {
                out.add(fact(id(), Severity.INFO, ownerClass.name() + "." + ownerMethod.name(),
                        "'" + cap.description() + "' is owned by " + ownerClass.name() + "." + ownerMethod.name() + ".",
                        "Knowing who owns each ability helps you check that responsibilities sit in the right place.",
                        "No change needed unless this class is not the right owner; the AI review looks at that.",
                        List.of(ownerMethod.source()), cap.id()));
            }
        }
        return out;
    }

    private record Implicit(String subject, String what, SourceRef source) {
    }

    /**
     * Looks for the capability's words in places other than method names: enum constants (DOORS_OPEN), field names
     * (balance), and class names (DoorController). This is what keeps "no method is called open()" from being
     * reported as "nobody handles doors" when the design clearly does.
     */
    private static Implicit implicitEvidence(DesignModel model, Problem problem, Capability cap) {
        for (ClassNode c : model.classes().values()) {
            for (String constant : c.enumConstants()) {
                if (CapabilityMatcher.matches(constant, cap.nameHints())) {
                    return new Implicit(c.name(), "the enum constant " + c.name() + "." + constant, c.source());
                }
            }
        }
        for (ClassNode c : model.classes().values()) {
            for (FieldNode f : c.fields()) {
                if (CapabilityMatcher.matches(f.name(), cap.nameHints())) {
                    return new Implicit(c.name(), "the field " + c.name() + "." + f.name(), c.source());
                }
            }
        }
        for (ClassNode c : model.classes().values()) {
            if (!problem.entryTypes().contains(c.name()) && CapabilityMatcher.matches(c.name(), cap.nameHints())) {
                return new Implicit(c.name(), "the class " + c.name(), c.source());
            }
        }
        return null;
    }
}

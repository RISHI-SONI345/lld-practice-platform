package com.lldpractice.lld_practice_platform.evaluation.rules;

import com.lldpractice.lld_practice_platform.evaluation.Rule;
import com.lldpractice.lld_practice_platform.evaluation.RuleThresholds;
import com.lldpractice.lld_practice_platform.model.*;
import com.lldpractice.lld_practice_platform.problem.Problem;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static com.lldpractice.lld_practice_platform.evaluation.rules.RuleSupport.fact;

@Component
public class TypeSwitchRule implements Rule {
    private final RuleThresholds t;

    public TypeSwitchRule(RuleThresholds t) {
        this.t = t;
    }

    @Override
    public String id() {
        return "TYPE_SWITCH";
    }

    @Override
    public List<Finding> check(DesignModel model, Problem problem) {
        List<Finding> out = new ArrayList<>();
        for (ClassNode c : model.classes().values()) {
            for (MethodNode m : c.methods()) {
                MethodSignals s = m.signals();
                String typeSelector = s.switchSelectors().stream()
                        .filter(sel -> t.typeSwitchSelectorWords.stream()
                                .anyMatch(w -> sel.toLowerCase(Locale.ROOT).contains(w)))
                        .findFirst().orElse(null);
                boolean manyInstanceOf = s.instanceofCount() >= t.typeSwitchMinInstanceOf;
                if (typeSelector != null || manyInstanceOf) {
                    String what = typeSelector != null
                            ? "switches on '" + typeSelector + "'"
                            : "uses " + s.instanceofCount() + " instanceof checks";
                    out.add(fact(id(), Severity.MEDIUM, c.name() + "." + m.name(),
                            c.name() + "." + m.name() + " " + what + ", branching on what kind of thing it is dealing with.",
                            "Every new kind of thing means editing this method again, which breaks the open/closed principle.",
                            "Move the varying behaviour behind an interface or abstract method and let each kind implement it.",
                            List.of(m.source())));
                }
            }
        }
        return out;
    }
}

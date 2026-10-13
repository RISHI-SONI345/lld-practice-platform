package com.lldpractice.lld_practice_platform.evaluation.rules;

import com.lldpractice.lld_practice_platform.evaluation.Rule;
import com.lldpractice.lld_practice_platform.evaluation.RuleThresholds;
import com.lldpractice.lld_practice_platform.model.*;
import com.lldpractice.lld_practice_platform.problem.Problem;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

import static com.lldpractice.lld_practice_platform.evaluation.rules.RuleSupport.fact;

/** Counts members only: the published God Class strategy also needs body-level analysis we do not build. */
@Component
public class GodClassRule implements Rule {
    private final RuleThresholds t;

    public GodClassRule(RuleThresholds t) {
        this.t = t;
    }

    @Override
    public String id() {
        return "GOD_CLASS";
    }

    @Override
    public List<Finding> check(DesignModel model, Problem problem) {
        List<Finding> out = new ArrayList<>();
        for (ClassNode c : model.classes().values()) {
            long methods = c.methods().stream().filter(m -> !m.name().equals(c.name())).count();
            int fields = c.fields().size();
            if (methods > t.godClassMaxMethods || fields > t.godClassMaxFields) {
                out.add(fact(id(), Severity.MEDIUM, c.name(),
                        c.name() + " has " + methods + " methods and " + fields + " fields (limits: "
                                + t.godClassMaxMethods + " and " + t.godClassMaxFields + ").",
                        "A class that does this much usually has several reasons to change, which makes each change riskier.",
                        "Split the class by responsibility and move related fields and methods together.",
                        List.of(c.source())));
            }
        }
        return out;
    }
}

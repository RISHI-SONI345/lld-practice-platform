package com.lldpractice.lld_practice_platform.evaluation.rules;

import com.lldpractice.lld_practice_platform.evaluation.CapabilityMatcher;
import com.lldpractice.lld_practice_platform.evaluation.Rule;
import com.lldpractice.lld_practice_platform.model.*;
import com.lldpractice.lld_practice_platform.problem.Capability;
import com.lldpractice.lld_practice_platform.problem.Problem;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static com.lldpractice.lld_practice_platform.evaluation.rules.RuleSupport.fact;

@Component
public class HardcodedValueRule implements Rule {
    @Override
    public String id() {
        return "HARDCODED_VALUE";
    }

    @Override
    public List<Finding> check(DesignModel model, Problem problem) {
        List<Finding> out = new ArrayList<>();
        List<Capability> watched = problem.capabilities().stream().filter(Capability::watchLiterals).toList();
        if (watched.isEmpty()) return out;
        for (ClassNode c : model.classes().values()) {
            for (MethodNode m : c.methods()) {
                Capability owned = watched.stream()
                        .filter(cap -> CapabilityMatcher.matches(m.name(), cap.nameHints())).findFirst().orElse(null);
                if (owned == null) continue;
                List<NumericLiteral> bad = m.signals().numericLiterals().stream().filter(l -> !isAllowed(l.value())).toList();
                if (bad.isEmpty()) continue;
                String values = String.join(", ", bad.stream().map(NumericLiteral::value).distinct().toList());
                out.add(fact(id(), Severity.MEDIUM, c.name() + "." + m.name(),
                        c.name() + "." + m.name() + " hard-codes " + values + " in logic for '" + owned.description() + "'.",
                        "Values like fees and capacities change often; buried in code they are hard to find and easy to get wrong.",
                        "Move the value into a named constant, a configuration object or an injected rate table.",
                        bad.stream().map(l -> new SourceRef(m.source().file(), l.line())).toList()));
            }
        }
        return out;
    }

    /** 0, 1 and -1 are fine (the minus sign is a separate unary operator in the parse tree). */
    static boolean isAllowed(String raw) {
        String v = raw.replace("_", "").replaceAll("[lLfFdD]$", "");
        try {
            BigDecimal d = new BigDecimal(v);
            return d.compareTo(BigDecimal.ZERO) == 0 || d.compareTo(BigDecimal.ONE) == 0;
        } catch (NumberFormatException e) {
            try {
                long l = Long.decode(v);
                return l == 0 || l == 1;
            } catch (NumberFormatException e2) {
                return false;
            }
        }
    }
}

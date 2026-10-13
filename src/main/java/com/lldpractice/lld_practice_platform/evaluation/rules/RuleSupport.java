package com.lldpractice.lld_practice_platform.evaluation.rules;

import com.lldpractice.lld_practice_platform.model.*;

import java.util.List;

final class RuleSupport {
    private RuleSupport() {
    }

    static Finding fact(String ruleId, Severity severity, String subject, String message, String why, String fix,
                        List<SourceRef> evidence) {
        return new Finding(ruleId, FindingKind.FACT, severity, subject, message, why, fix, "", evidence, "rules", "");
    }

    static Finding fact(String ruleId, Severity severity, String subject, String message, String why, String fix,
                        List<SourceRef> evidence, String discriminator) {
        return new Finding(ruleId, FindingKind.FACT, severity, subject, message, why, fix, "", evidence, "rules",
                discriminator);
    }
}

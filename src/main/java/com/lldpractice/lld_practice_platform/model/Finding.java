package com.lldpractice.lld_practice_platform.model;

import java.util.List;

public record Finding(String ruleId, FindingKind kind, Severity severity, String subject, String message, String why,
                      String fix, String tradeoff, List<SourceRef> evidence, String evaluatorId,
                      String discriminator) {
    public Finding {
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
        discriminator = discriminator == null ? "" : discriminator;
    }

    public Finding(String ruleId, FindingKind kind, Severity severity, String subject, String message, String why,
                   String fix, String tradeoff, List<SourceRef> evidence, String evaluatorId) {
        this(ruleId, kind, severity, subject, message, why, fix, tradeoff, evidence, evaluatorId, "");
    }

    /**
     * Identity across attempts: wording may change, ruleId + subject does not. The optional discriminator keeps
     * two findings of one rule on one subject apart (for example two missing capabilities on the entry type).
     */
    public String fingerprint() {
        return ruleId + "|" + subject + (discriminator.isEmpty() ? "" : "|" + discriminator);
    }
}

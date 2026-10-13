package com.lldpractice.lld_practice_platform.llm;

import com.lldpractice.lld_practice_platform.llm.LlmReviewParser.RawFinding;
import com.lldpractice.lld_practice_platform.model.*;
import org.springframework.stereotype.Component;

import java.util.*;

/** Runs on every LLM response before anything is saved: only findings that point at real code survive. */
@Component
public class FindingValidator {
    public static final int MAX_FINDINGS = 8;

    /** Which rule findings an LLM category would merely restate when both are on the same subject. */
    private static final Map<String, Set<String>> RESTATES = Map.of(
            "COHESION", Set.of("GOD_CLASS"),
            "PATTERN_FIT", Set.of("TYPE_SWITCH", "CONCRETE_DEPENDENCY"),
            "EXTENSIBILITY", Set.of("TYPE_SWITCH", "HARDCODED_VALUE", "CONCRETE_DEPENDENCY", "DEPENDENCY_CYCLE"),
            "ENCAPSULATION", Set.of(),
            "TRADEOFF", Set.of(),
            "CHANGE_REQUEST", Set.of());

    public record Outcome(List<Finding> kept, Map<String, Integer> dropped) {
        public int droppedTotal() {
            return dropped.values().stream().mapToInt(Integer::intValue).sum();
        }

        public String describe() {
            if (dropped.isEmpty()) return "kept " + kept.size() + ", dropped 0";
            StringBuilder sb = new StringBuilder("kept " + kept.size() + ", dropped " + droppedTotal() + " (");
            boolean first = true;
            for (var e : dropped.entrySet()) {
                if (!first) sb.append(", ");
                sb.append(e.getValue()).append(' ').append(e.getKey());
                first = false;
            }
            return sb.append(')').toString();
        }
    }

    public Outcome validate(List<RawFinding> raw, DesignModel model, List<Finding> facts, String evaluatorId) {
        return validate(raw, model, facts, evaluatorId, true);
    }

    public Outcome validate(List<RawFinding> raw, DesignModel model, List<Finding> facts, String evaluatorId,
                            boolean changeRound) {
        Map<String, Integer> dropped = new LinkedHashMap<>();
        List<Finding> kept = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (RawFinding r : raw) {
            if (r.category() == null || r.subject() == null || r.claim() == null || r.why() == null
                    || r.fix() == null || r.tradeoff() == null) {
                drop(dropped, "missing field");
                continue;
            }
            String category = r.category().toUpperCase(Locale.ROOT);
            if (!LlmPrompts.categoriesFor(changeRound).contains(category)) {
                drop(dropped, "unknown category");
                continue;
            }
            if (!model.hasSubject(r.subject())) {
                drop(dropped, "subject not in code");
                continue;
            }
            Set<String> restated = RESTATES.getOrDefault(category, Set.of());
            if (facts.stream().anyMatch(f -> f.subject().equals(r.subject()) && restated.contains(f.ruleId()))) {
                drop(dropped, "restates a FACT");
                continue;
            }
            if (!seen.add(category + "|" + r.subject())) {
                drop(dropped, "duplicate");
                continue;
            }
            kept.add(new Finding(category, FindingKind.JUDGMENT, Severity.parseOrLow(r.severity()), r.subject(),
                    r.claim(), r.why(), r.fix(), r.tradeoff(), List.of(evidenceFor(model, r.subject())), evaluatorId));
        }
        kept.sort(Comparator.comparing(Finding::severity).reversed());
        while (kept.size() > MAX_FINDINGS) {
            kept.remove(kept.size() - 1);
            drop(dropped, "over the limit of " + MAX_FINDINGS);
        }
        return new Outcome(kept, dropped);
    }

    private static SourceRef evidenceFor(DesignModel model, String subject) {
        int dot = subject.indexOf('.');
        if (dot < 0) return model.find(subject).get().source();
        ClassNode c = model.find(subject.substring(0, dot)).get();
        String method = subject.substring(dot + 1);
        return c.methods().stream().filter(m -> m.name().equals(method)).findFirst()
                .map(MethodNode::source).orElse(c.source());
    }

    private static void drop(Map<String, Integer> dropped, String reason) {
        dropped.merge(reason, 1, Integer::sum);
    }
}

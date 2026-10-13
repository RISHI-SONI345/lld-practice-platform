package com.lldpractice.lld_practice_platform.llm;

import com.lldpractice.lld_practice_platform.evaluation.EvaluationContext;
import com.lldpractice.lld_practice_platform.model.*;
import com.lldpractice.lld_practice_platform.problem.Approach;
import com.lldpractice.lld_practice_platform.problem.DecisionPoint;

import java.util.List;

/** A neutral prompt template: no authority cues, no leading words, no praise or pressure. */
public final class LlmPrompts {
    public static final List<String> CATEGORIES =
            List.of("COHESION", "PATTERN_FIT", "EXTENSIBILITY", "ENCAPSULATION", "TRADEOFF", "CHANGE_REQUEST");

    private LlmPrompts() {
    }

    public static String system() {
        return """
                You review the structure of a Java object-oriented design. You are given class names, kinds, fields, \
                method signatures and relationships. You are not given method bodies.
                Judge the design on its own terms. A different valid design is not an error. Several approaches to each \
                decision point are acceptable, so name the trade-off and do not prefer one.
                Mention only classes and methods that appear in the model. Do not repeat the rule findings you are given.
                Reply with one JSON object and nothing else.""";
    }

    public static String user(EvaluationContext ctx) {
        StringBuilder sb = new StringBuilder();
        sb.append("PROBLEM\n").append(ctx.problem().title()).append(": ").append(ctx.problem().statement()).append("\n\n");
        sb.append("REQUIREMENTS\n");
        ctx.problem().requirements().forEach(r -> sb.append("- ").append(r).append('\n'));
        sb.append("\nDECISION POINTS (each approach listed is acceptable)\n");
        for (DecisionPoint d : ctx.problem().decisionPoints()) {
            sb.append(d.id()).append(": ").append(d.question()).append('\n');
            for (Approach a : d.approaches()) {
                sb.append("  - ").append(a.id()).append(": ").append(a.name()).append(" (trade-off: ").append(a.tradeoff()).append(")\n");
            }
        }
        Submission s = ctx.submission();
        sb.append("\nLEARNER ASSUMPTIONS\n").append(blankAsNone(s.assumptions())).append('\n');
        sb.append("\nLEARNER RATIONALE\n");
        sb.append("pattern used: ").append(blankAsNone(s.rationale().patternUsed())).append('\n');
        sb.append("pattern rejected: ").append(blankAsNone(s.rationale().patternRejected())).append('\n');
        sb.append("note: ").append(blankAsNone(s.rationale().note())).append('\n');
        sb.append("\nDESIGN MODEL\n").append(ModelTextRenderer.render(ctx.model()));
        sb.append("\nRULE FINDINGS ALREADY REPORTED (do not repeat)\n");
        List<Finding> facts = ctx.findingsSoFar().stream().filter(f -> f.kind() == FindingKind.FACT).toList();
        if (facts.isEmpty()) sb.append("none\n");
        facts.forEach(f -> sb.append("- ").append(f.ruleId()).append(" on ").append(f.subject()).append('\n'));
        if (ctx.impact() != null) {
            ChangeImpact i = ctx.impact();
            sb.append("\nCHANGE REQUEST (this is a revision of an earlier attempt)\n")
                    .append(ctx.problem().changeRequest().text()).append('\n');
            sb.append("Measured impact: added ").append(i.added()).append(", modified ").append(i.modified())
                    .append(", removed ").append(i.removed()).append(", interfaces touched ").append(i.interfacesTouched())
                    .append(", verdict ").append(i.verdict()).append('\n');
            sb.append("Include a CHANGE_REQUEST finding that says whether the change was absorbed cleanly.\n");
        }
        sb.append("""

                REPLY FORMAT
                {"findings":[{"category":"<one of %s>","severity":"INFO|LOW|MEDIUM|HIGH","subject":"ClassName or ClassName.method",\
                "claim":"...","why":"...","fix":"...","tradeoff":"when the current choice is fine"}],\
                "decisionPoints":[{"id":"<decision point id>","approachUsed":"<approach id or none>","agreesWithRationale":true,"tradeoff":"..."}],\
                "strengths":["up to three"]}
                At most 8 findings.
                """.formatted(String.join(", ", categoriesFor(ctx.impact() != null))));
        return sb.toString();
    }

    /** CHANGE_REQUEST only makes sense when the attempt is a revision for the change request. */
    public static List<String> categoriesFor(boolean changeRound) {
        return changeRound ? CATEGORIES : CATEGORIES.stream().filter(c -> !c.equals("CHANGE_REQUEST")).toList();
    }

    private static String blankAsNone(String s) {
        return s == null || s.isBlank() ? "(none given)" : s.strip();
    }
}

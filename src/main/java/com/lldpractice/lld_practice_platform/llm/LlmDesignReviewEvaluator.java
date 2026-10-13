package com.lldpractice.lld_practice_platform.llm;

import com.lldpractice.lld_practice_platform.evaluation.*;
import com.lldpractice.lld_practice_platform.model.DecisionAssessment;
import com.lldpractice.lld_practice_platform.model.Stage;
import com.lldpractice.lld_practice_platform.problem.DecisionPoint;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** The DEFERRED evaluator: one LLM review, validated, labelled JUDGMENT. */
@Component
public class LlmDesignReviewEvaluator implements Evaluator {
    public static final String ID = "llm-review";
    private final LlmClient llm;
    private final FindingValidator validator;

    public LlmDesignReviewEvaluator(LlmClient llm, FindingValidator validator) {
        this.llm = llm;
        this.validator = validator;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public Stage stage() {
        return Stage.DEFERRED;
    }

    @Override
    public EvaluationResult evaluate(EvaluationContext ctx) throws EvaluationException {
        String prompt = LlmPrompts.user(ctx);
        LlmReviewParser.Parsed parsed;
        String repairNote = "";
        String reply = call(new LlmRequest(LlmPrompts.system(), prompt, 0.0));
        try {
            parsed = LlmReviewParser.parse(reply);
        } catch (LlmReviewParser.ReviewFormatException first) {
            // One repair retry that includes the parse error.
            String repair = prompt + "\n\nYour previous reply could not be used: " + first.getMessage()
                    + ". Reply again with only the JSON object in the format above.";
            try {
                parsed = LlmReviewParser.parse(call(new LlmRequest(LlmPrompts.system(), repair, 0.0)));
                repairNote = "; reply was repaired after: " + first.getMessage();
            } catch (LlmReviewParser.ReviewFormatException second) {
                throw new EvaluationException("The AI review returned malformed JSON twice: " + second.getMessage());
            }
        }

        FindingValidator.Outcome outcome = validator.validate(parsed.findings(), ctx.model(), ctx.findingsSoFar(), ID,
                ctx.impact() != null);
        Set<String> knownDecisions = ctx.problem().decisionPoints().stream().map(DecisionPoint::id).collect(Collectors.toSet());
        List<DecisionAssessment> assessments = parsed.assessments().stream()
                .filter(a -> knownDecisions.contains(a.decisionPointId()))
                .map(a -> new DecisionAssessment(a.decisionPointId(), a.approachUsed(), a.agrees(), a.tradeoff()))
                .toList();
        String note = outcome.describe() + repairNote;
        if (outcome.kept().isEmpty()) note += "; the AI review returned no usable findings";
        return new EvaluationResult(outcome.kept(), assessments, parsed.strengths(), note);
    }

    private String call(LlmRequest request) throws EvaluationException {
        try {
            return llm.complete(request).text();
        } catch (LlmException.Timeout | LlmException.Transport e) {
            throw new EvaluationException("The AI review was unavailable: " + e.getMessage(), e, true);
        } catch (LlmException e) {
            throw new EvaluationException("The AI review failed: " + e.getMessage(), e, false);
        }
    }
}

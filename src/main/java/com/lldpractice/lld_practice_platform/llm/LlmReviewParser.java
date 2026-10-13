package com.lldpractice.lld_practice_platform.llm;

import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Turns the model's reply into plain records. Anything unusable raises ReviewFormatException. */
public final class LlmReviewParser {
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private LlmReviewParser() {
    }

    public record RawFinding(String category, String severity, String subject, String claim, String why, String fix,
                             String tradeoff) {
    }

    public record RawAssessment(String decisionPointId, String approachUsed, boolean agrees, String tradeoff) {
    }

    public record Parsed(List<RawFinding> findings, List<RawAssessment> assessments, List<String> strengths) {
    }

    public static class ReviewFormatException extends Exception {
        public ReviewFormatException(String message) {
            super(message);
        }
    }

    public static Parsed parse(String text) throws ReviewFormatException {
        if (text == null || text.isBlank()) throw new ReviewFormatException("the reply was empty");
        Object root;
        try {
            root = MAPPER.readValue(stripFences(text), Object.class);
        } catch (RuntimeException e) {
            throw new ReviewFormatException("the reply is not valid JSON: " + firstLine(e.getMessage()));
        }
        if (!(root instanceof Map<?, ?> top)) throw new ReviewFormatException("the reply is not a JSON object");
        if (!(top.get("findings") instanceof List<?> fl)) throw new ReviewFormatException("the field 'findings' is missing or not a list");

        List<RawFinding> findings = new ArrayList<>();
        for (Object o : fl) {
            if (o instanceof Map<?, ?> m) {
                findings.add(new RawFinding(str(m, "category"), str(m, "severity"), str(m, "subject"),
                        str(m, "claim"), str(m, "why"), str(m, "fix"), str(m, "tradeoff")));
            } else {
                findings.add(new RawFinding(null, null, null, null, null, null, null));
            }
        }
        List<RawAssessment> assessments = new ArrayList<>();
        if (top.get("decisionPoints") instanceof List<?> dl) {
            for (Object o : dl) {
                if (o instanceof Map<?, ?> m) {
                    String id = str(m, "id") != null ? str(m, "id") : str(m, "decisionPointId");
                    if (id != null) {
                        assessments.add(new RawAssessment(id, nz(str(m, "approachUsed")),
                                Boolean.TRUE.equals(m.get("agreesWithRationale")), nz(str(m, "tradeoff"))));
                    }
                }
            }
        }
        List<String> strengths = new ArrayList<>();
        if (top.get("strengths") instanceof List<?> sl) {
            for (Object o : sl) {
                if (o instanceof String s && !s.isBlank() && strengths.size() < 3) strengths.add(s.strip());
            }
        }
        return new Parsed(findings, assessments, strengths);
    }

    private static String stripFences(String t) {
        String s = t.strip();
        if (s.startsWith("```")) {
            int nl = s.indexOf('\n');
            if (nl > 0) s = s.substring(nl + 1);
            if (s.endsWith("```")) s = s.substring(0, s.length() - 3);
        }
        return s.strip();
    }

    private static String str(Map<?, ?> m, String key) {
        Object v = m.get(key);
        return v instanceof String s && !s.isBlank() ? s.strip() : null;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static String firstLine(String s) {
        return s == null ? "" : s.lines().findFirst().orElse("");
    }
}

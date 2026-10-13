package com.lldpractice.lld_practice_platform.llm;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Used when no Gemini key is configured, so the app still works end to end. It reads class names from the prompt
 * and returns clearly labelled placeholder JSON. It does not judge the design.
 */
public class DemoLlmClient implements LlmClient {
    private static final Pattern CLASS_LINE = Pattern.compile("^class (\\w+) \\[", Pattern.MULTILINE);

    @Override
    public LlmResponse complete(LlmRequest request) {
        Matcher m = CLASS_LINE.matcher(request.prompt());
        List<String> names = new ArrayList<>();
        while (m.find()) names.add(m.group(1));
        if (names.isEmpty()) return new LlmResponse("{\"findings\":[],\"decisionPoints\":[],\"strengths\":[]}");
        String first = names.get(0);
        String json = "{\"findings\":[{\"category\":\"TRADEOFF\",\"severity\":\"INFO\",\"subject\":\"" + first
                + "\",\"claim\":\"Demo mode: no AI key is configured, so this is a placeholder and not a real review.\","
                + "\"why\":\"Set GEMINI_API_KEY to get a real AI review.\","
                + "\"fix\":\"No change needed.\",\"tradeoff\":\"This note appears only in demo mode.\"}],"
                + "\"decisionPoints\":[],\"strengths\":[]}";
        return new LlmResponse(json);
    }
}

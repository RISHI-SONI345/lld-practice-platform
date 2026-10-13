package com.lldpractice.lld_practice_platform.parser;

import com.lldpractice.lld_practice_platform.model.Submission;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Selects the parser by format key and fails fast with a clear error on an unknown format. */
@Component
public class ParserRegistry {
    private final Map<String, SubmissionParser> byFormat = new LinkedHashMap<>();

    public ParserRegistry(List<SubmissionParser> parsers) {
        for (SubmissionParser p : parsers) {
            if (byFormat.putIfAbsent(p.format(), p) != null) {
                throw new IllegalStateException("Two parsers registered for format '" + p.format() + "'");
            }
        }
    }

    public SubmissionParser forFormat(String format) {
        SubmissionParser p = byFormat.get(format);
        if (p == null) {
            throw new IllegalArgumentException(
                    "Unknown submission format '" + format + "'. Supported: " + byFormat.keySet());
        }
        return p;
    }

    public ParseResult parse(Submission submission) {
        return forFormat(submission.format()).parse(submission);
    }
}

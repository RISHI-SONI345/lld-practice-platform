package com.lldpractice.lld_practice_platform.orchestration;

import com.lldpractice.lld_practice_platform.model.*;

import java.util.ArrayList;
import java.util.List;

/** Parser warnings are saved as FACT findings with rule id PARSE_ISSUE. */
final class ParseIssueFindings {
    static final String RULE_ID = "PARSE_ISSUE";
    private static final String WHY = "The parser could not fully read this part of the code, so rules may miss things here.";

    private ParseIssueFindings() {
    }

    static List<Finding> from(List<ParseIssue> issues) {
        List<Finding> out = new ArrayList<>();
        for (ParseIssue i : issues) {
            boolean hasFile = i.file() != null && !i.file().isBlank();
            out.add(new Finding(RULE_ID, FindingKind.FACT, i.severity(), hasFile ? i.file() : "submission",
                    i.message(), WHY, "Fix the problem and submit again.", "",
                    hasFile ? List.of(new SourceRef(i.file(), i.line())) : List.of(), "parser",
                    i.line() + ":" + i.message()));
        }
        return out;
    }

    static Finding missingEntryType(String entryType) {
        return new Finding(RULE_ID, FindingKind.FACT, Severity.HIGH, entryType,
                "The problem expects a class named " + entryType + ", but none was found in your files.",
                "Everything else is checked against this entry point, so its absence weakens every other finding.",
                "Keep the class " + entryType + " from the skeleton and build your design around it.", "", List.of(),
                "parser", "missing-entry-type");
    }
}

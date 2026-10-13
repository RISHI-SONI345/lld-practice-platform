package com.lldpractice.lld_practice_platform.model;

import java.util.List;

public record Submission(String format, List<SourceFile> files, String assumptions, Rationale rationale) {
    public static final String JAVA = "java";

    public Submission {
        files = files == null ? List.of() : List.copyOf(files);
        assumptions = assumptions == null ? "" : assumptions;
        rationale = rationale == null ? Rationale.empty() : rationale;
    }

    public int totalSize() {
        return files.stream().mapToInt(f -> f.content() == null ? 0 : f.content().length()).sum();
    }

    public boolean hasContent() {
        return files.stream().anyMatch(f -> f.content() != null && !f.content().isBlank());
    }
}

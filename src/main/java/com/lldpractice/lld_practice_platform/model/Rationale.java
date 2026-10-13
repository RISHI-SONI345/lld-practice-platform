package com.lldpractice.lld_practice_platform.model;

public record Rationale(String patternUsed, String patternRejected, String note) {
    public static Rationale empty() {
        return new Rationale("", "", "");
    }
}

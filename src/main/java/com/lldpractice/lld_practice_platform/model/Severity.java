package com.lldpractice.lld_practice_platform.model;

public enum Severity {
    INFO, LOW, MEDIUM, HIGH;

    public static Severity parseOrLow(String s) {
        if (s == null) return LOW;
        try {
            return valueOf(s.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return LOW;
        }
    }
}

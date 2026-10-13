package com.lldpractice.lld_practice_platform.model;

public record ParseIssue(String file, int line, Severity severity, String message) {
}

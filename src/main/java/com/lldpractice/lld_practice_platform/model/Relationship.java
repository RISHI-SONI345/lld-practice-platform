package com.lldpractice.lld_practice_platform.model;

public record Relationship(String from, String to, RelationshipKind kind, SourceRef source) {
}

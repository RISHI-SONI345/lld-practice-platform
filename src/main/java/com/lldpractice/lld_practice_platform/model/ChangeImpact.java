package com.lldpractice.lld_practice_platform.model;

import java.util.List;

public record ChangeImpact(List<String> added, List<String> modified, List<String> removed,
                           List<String> interfacesTouched, ContainmentVerdict verdict) {
}

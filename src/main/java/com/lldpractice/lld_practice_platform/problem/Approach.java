package com.lldpractice.lld_practice_platform.problem;

import java.util.List;

public record Approach(String id, String name, List<String> signals, String tradeoff) {
    public Approach {
        signals = signals == null ? List.of() : List.copyOf(signals);
    }
}

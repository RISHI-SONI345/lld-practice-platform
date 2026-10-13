package com.lldpractice.lld_practice_platform.problem;

import java.util.List;

public record DecisionPoint(String id, String question, List<Approach> approaches) {
    public DecisionPoint {
        approaches = approaches == null ? List.of() : List.copyOf(approaches);
    }
}

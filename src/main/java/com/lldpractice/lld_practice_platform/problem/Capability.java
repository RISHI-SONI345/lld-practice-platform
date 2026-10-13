package com.lldpractice.lld_practice_platform.problem;

import java.util.List;

public record Capability(String id, String description, List<String> nameHints, boolean watchLiterals) {
    public Capability {
        nameHints = nameHints == null ? List.of() : List.copyOf(nameHints);
    }
}

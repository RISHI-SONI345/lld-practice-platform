package com.lldpractice.lld_practice_platform.model;

import java.util.List;

public record MethodNode(String name, String returnType, List<ParameterNode> parameters, String visibility,
                         MethodSignals signals, SourceRef source) {
    public MethodNode {
        parameters = List.copyOf(parameters);
    }
}

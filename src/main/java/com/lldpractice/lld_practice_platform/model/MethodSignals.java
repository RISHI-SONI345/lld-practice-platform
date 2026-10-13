package com.lldpractice.lld_practice_platform.model;

import java.util.List;

/** Facts extracted from a method body so rules never touch source text. */
public record MethodSignals(List<String> switchSelectors, int instanceofCount,
                            List<NumericLiteral> numericLiterals, List<String> instantiatedTypes, int lineCount) {
    public MethodSignals {
        switchSelectors = List.copyOf(switchSelectors);
        numericLiterals = List.copyOf(numericLiterals);
        instantiatedTypes = List.copyOf(instantiatedTypes);
    }

    public static MethodSignals none() {
        return new MethodSignals(List.of(), 0, List.of(), List.of(), 0);
    }
}

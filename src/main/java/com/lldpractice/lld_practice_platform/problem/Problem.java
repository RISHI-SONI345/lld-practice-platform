package com.lldpractice.lld_practice_platform.problem;

import com.lldpractice.lld_practice_platform.model.SourceFile;

import java.util.List;

/** One practice problem, loaded from a data file. Knows nothing about attempts. */
public record Problem(String id, String title, String statement, List<String> requirements,
                      List<SourceFile> skeleton, List<String> entryTypes, List<Capability> capabilities,
                      List<DecisionPoint> decisionPoints, ChangeRequest changeRequest) {
    public Problem {
        requirements = List.copyOf(requirements);
        skeleton = List.copyOf(skeleton);
        entryTypes = List.copyOf(entryTypes);
        capabilities = List.copyOf(capabilities);
        decisionPoints = List.copyOf(decisionPoints);
    }
}

package com.lldpractice.lld_practice_platform.orchestration;

import com.lldpractice.lld_practice_platform.model.Finding;
import com.lldpractice.lld_practice_platform.model.FindingKind;
import org.springframework.stereotype.Component;

import java.util.*;

/** Compares two finding lists by fingerprint. */
@Component
public class FindingDiffer {
    public record Diff(List<Finding> resolved, List<Finding> added, List<Finding> persisting) {
        public String summary() {
            return resolved.size() + " resolved, " + added.size() + " new, " + persisting.size() + " persisting";
        }
    }

    public Diff diff(List<Finding> previous, List<Finding> current) {
        Map<String, Finding> prev = index(previous);
        Map<String, Finding> cur = index(current);
        List<Finding> resolved = new ArrayList<>();
        List<Finding> added = new ArrayList<>();
        List<Finding> persisting = new ArrayList<>();
        prev.forEach((fp, f) -> {
            if (!cur.containsKey(fp)) resolved.add(f);
        });
        cur.forEach((fp, f) -> {
            if (prev.containsKey(fp)) persisting.add(f);
            else added.add(f);
        });
        return new Diff(resolved, added, persisting);
    }

    /** FACT and JUDGMENT are diffed separately. */
    public Diff diff(List<Finding> previous, List<Finding> current, FindingKind kind) {
        return diff(previous.stream().filter(f -> f.kind() == kind).toList(),
                current.stream().filter(f -> f.kind() == kind).toList());
    }

    private static Map<String, Finding> index(List<Finding> list) {
        Map<String, Finding> m = new LinkedHashMap<>();
        for (Finding f : list) m.putIfAbsent(f.kind() + "|" + f.fingerprint(), f);
        return m;
    }
}

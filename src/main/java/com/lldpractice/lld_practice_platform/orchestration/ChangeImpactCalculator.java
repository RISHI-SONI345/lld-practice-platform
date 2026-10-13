package com.lldpractice.lld_practice_platform.orchestration;

import com.lldpractice.lld_practice_platform.model.*;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/** Compares the base and revised DesignModel. A renamed class counts as one removed and one added. */
@Component
public class ChangeImpactCalculator {
    private final EvaluationProperties props;

    public ChangeImpactCalculator(EvaluationProperties props) {
        this.props = props;
    }

    public ChangeImpact calculate(DesignModel base, DesignModel revised) {
        List<String> added = new ArrayList<>();
        List<String> removed = new ArrayList<>();
        List<String> modified = new ArrayList<>();
        List<String> interfaces = new ArrayList<>();
        for (ClassNode r : revised.classes().values()) {
            ClassNode b = base.classes().get(r.name());
            if (b == null) {
                added.add(r.name());
            } else if (!b.signatureHash().equals(r.signatureHash()) || !b.bodyHash().equals(r.bodyHash())) {
                modified.add(r.name());
                if (b.isInterface() || r.isInterface()) interfaces.add(r.name());
            }
        }
        for (ClassNode b : base.classes().values()) {
            if (!revised.classes().containsKey(b.name())) {
                removed.add(b.name());
                if (b.isInterface()) interfaces.add(b.name());
            }
        }
        int touched = modified.size() + removed.size();
        ContainmentVerdict verdict;
        if (touched == 0) verdict = ContainmentVerdict.ADDITIVE;
        else if (touched <= props.containedMaxTouched && touched <= added.size()) verdict = ContainmentVerdict.CONTAINED;
        else verdict = ContainmentVerdict.SPREAD;
        return new ChangeImpact(added, modified, removed, interfaces, verdict);
    }
}

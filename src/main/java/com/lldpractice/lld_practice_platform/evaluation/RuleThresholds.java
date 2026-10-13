package com.lldpractice.lld_practice_platform.evaluation;

import org.springframework.stereotype.Component;

import java.util.List;

/** Starting defaults for the deterministic rules. Tune on the three problems; keep them in this one class. */
@Component
public class RuleThresholds {
    public int godClassMaxMethods = 12;
    public int godClassMaxFields = 8;
    public int typeSwitchMinInstanceOf = 2;
    public List<String> typeSwitchSelectorWords = List.of("type", "kind", "category");
}

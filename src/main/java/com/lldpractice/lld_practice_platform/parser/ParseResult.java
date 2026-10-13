package com.lldpractice.lld_practice_platform.parser;

import com.lldpractice.lld_practice_platform.model.DesignModel;
import com.lldpractice.lld_practice_platform.model.ParseIssue;

import java.util.List;

public record ParseResult(DesignModel model, List<ParseIssue> issues) {
    public boolean usable() {
        return !model.classes().isEmpty();
    }
}

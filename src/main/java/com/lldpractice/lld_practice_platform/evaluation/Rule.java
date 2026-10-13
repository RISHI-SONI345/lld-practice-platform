package com.lldpractice.lld_practice_platform.evaluation;

import com.lldpractice.lld_practice_platform.model.DesignModel;
import com.lldpractice.lld_practice_platform.model.Finding;
import com.lldpractice.lld_practice_platform.problem.Problem;

import java.util.List;

public interface Rule {
    String id();

    List<Finding> check(DesignModel model, Problem problem);
}

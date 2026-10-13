package com.lldpractice.lld_practice_platform.parser;

import com.lldpractice.lld_practice_platform.model.Submission;

/** Extension point: a new submission format is a new parser emitting the same DesignModel. Never throws. */
public interface SubmissionParser {
    String format();

    ParseResult parse(Submission submission);
}

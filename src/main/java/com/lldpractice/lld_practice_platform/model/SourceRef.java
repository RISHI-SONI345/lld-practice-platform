package com.lldpractice.lld_practice_platform.model;

/** A location in the learner's code: file and first line of the node. */
public record SourceRef(String file, int line) {
    @Override
    public String toString() {
        return file + ":" + line;
    }
}

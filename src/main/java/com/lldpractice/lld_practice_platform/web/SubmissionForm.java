package com.lldpractice.lld_practice_platform.web;

import com.lldpractice.lld_practice_platform.model.Rationale;
import com.lldpractice.lld_practice_platform.model.SourceFile;
import com.lldpractice.lld_practice_platform.model.Submission;
import org.springframework.util.MultiValueMap;

import java.util.ArrayList;
import java.util.List;

/** Turns the editor's form fields into a Submission. File names are cleaned before they are stored. */
final class SubmissionForm {
    private SubmissionForm() {
    }

    static Submission toSubmission(MultiValueMap<String, String> form) {
        List<String> names = form.getOrDefault("fileName", List.of());
        List<String> contents = form.getOrDefault("fileContent", List.of());
        List<SourceFile> files = new ArrayList<>();
        for (int i = 0; i < Math.min(names.size(), contents.size()); i++) {
            files.add(new SourceFile(cleanName(names.get(i), "File" + (i + 1)), contents.get(i)));
        }
        String extra = form.getFirst("newFileContent");
        if (extra != null && !extra.isBlank()) {
            files.add(new SourceFile(cleanName(form.getFirst("newFileName"), "Extra.java"), extra));
        }
        return new Submission(Submission.JAVA, files, nz(form.getFirst("assumptions")),
                new Rationale(nz(form.getFirst("patternUsed")), nz(form.getFirst("patternRejected")), nz(form.getFirst("note"))));
    }

    static String cleanName(String raw, String fallback) {
        String n = raw == null ? "" : raw.strip().replaceAll("[^A-Za-z0-9_.-]", "_");
        if (n.isEmpty()) n = fallback;
        if (n.length() > 80) n = n.substring(0, 80);
        return n.endsWith(".java") ? n : n + ".java";
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}

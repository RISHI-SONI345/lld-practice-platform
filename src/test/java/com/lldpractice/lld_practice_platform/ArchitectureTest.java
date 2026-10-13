package com.lldpractice.lld_practice_platform;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Scans imports to enforce the dependency rules in the design note. It doubles as evidence that the layering holds.
 */
class ArchitectureTest {
    private static final String ROOT = "com.lldpractice.lld_practice_platform.";
    private static final Pattern IMPORT = Pattern.compile("^import (?:static )?" + Pattern.quote(ROOT) + "([a-z_]+)[.;]", Pattern.MULTILINE);
    private static final Pattern PACKAGE = Pattern.compile("^package " + Pattern.quote(ROOT) + "([a-z_]+)", Pattern.MULTILINE);

    /** Which project packages each package may import. */
    private static final Map<String, Set<String>> ALLOWED = Map.of(
            "model", Set.of("model"),
            "problem", Set.of("model", "problem"),
            "evaluation", Set.of("model", "problem", "evaluation"),
            "parser", Set.of("model", "parser"),
            "llm", Set.of("model", "problem", "evaluation", "llm"),
            "attempt", Set.of("model", "attempt"));

    private static Map<String, Set<String>> actualImports() throws IOException {
        Map<String, Set<String>> out = new TreeMap<>();
        try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            for (Path p : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                String src = Files.readString(p);
                Matcher pk = PACKAGE.matcher(src);
                if (!pk.find()) continue;
                String pkg = pk.group(1);
                Matcher im = IMPORT.matcher(src);
                while (im.find()) {
                    out.computeIfAbsent(pkg, k -> new TreeSet<>()).add(im.group(1));
                }
            }
        }
        return out;
    }

    @Test
    void innerPackagesOnlyDependOnWhatTheyAreAllowedTo() throws IOException {
        Map<String, Set<String>> actual = actualImports();
        for (var rule : ALLOWED.entrySet()) {
            Set<String> used = new TreeSet<>(actual.getOrDefault(rule.getKey(), Set.of()));
            used.removeAll(rule.getValue());
            assertThat(used).as("package '%s' imports packages it must not depend on", rule.getKey()).isEmpty();
        }
    }

    @Test
    void evaluationNeverImportsWebOrchestrationOrAttempt() throws IOException {
        assertThat(actualImports().getOrDefault("evaluation", Set.of()))
                .doesNotContain("web", "orchestration", "attempt", "persistence");
    }

    @Test
    void webTalksToServicesNeverToRepositoriesOrEvaluators() throws IOException {
        try (Stream<Path> files = Files.walk(Path.of("src/main/java/com/lldpractice/lld_practice_platform/web"))) {
            for (Path p : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                String src = Files.readString(p);
                assertThat(src).as(p.getFileName().toString())
                        .doesNotContain(ROOT + "persistence")
                        .doesNotContain(ROOT + "evaluation")
                        .doesNotContain("AttemptRepository")
                        .doesNotContain("ProblemRepository")
                        .doesNotContain("import " + ROOT + "llm");
            }
        }
    }

    @Test
    void domainRecordsDoNotDependOnSpring() throws IOException {
        try (Stream<Path> files = Files.walk(Path.of("src/main/java/com/lldpractice/lld_practice_platform/model"))) {
            for (Path p : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                assertThat(Files.readString(p)).as(p.getFileName().toString()).doesNotContain("org.springframework");
            }
        }
    }
}

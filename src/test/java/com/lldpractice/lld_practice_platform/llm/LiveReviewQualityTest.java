package com.lldpractice.lld_practice_platform.llm;

import com.lldpractice.lld_practice_platform.evaluation.*;
import com.lldpractice.lld_practice_platform.evaluation.rules.*;
import com.lldpractice.lld_practice_platform.llm.ReviewSamples.Sample;
import com.lldpractice.lld_practice_platform.model.*;
import com.lldpractice.lld_practice_platform.parser.ParseResult;
import com.lldpractice.lld_practice_platform.parser.javacode.JavaCodeParser;
import com.lldpractice.lld_practice_platform.problem.Problem;
import com.lldpractice.lld_practice_platform.problem.YamlProblemRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Opt-in check of the real AI review against hand-written good and bad designs. It only runs when GEMINI_API_KEY is
 * set, calls the live service (about a dozen calls) and writes target/review-quality.md.
 *
 * It measures four things: does the review finish, how often it invents things (findings dropped by the validator),
 * whether it names the approach a careful reader would name at each decision point, and whether good and bad designs
 * are told apart. It also runs every design twice to see how repeatable the findings are.
 */
@EnabledIfEnvironmentVariable(named = "GEMINI_API_KEY", matches = ".+")
class LiveReviewQualityTest {
    private static final int RUNS_PER_SAMPLE = 2;
    private static final Pattern DROPPED = Pattern.compile("kept (\\d+), dropped (\\d+)");

    private record Run(Sample sample, int n, boolean ok, String error, List<Finding> facts, List<Finding> judgments,
                       List<DecisionAssessment> assessments, int kept, int dropped, long millis) {
    }

    private static int weight(Severity s) {
        return switch (s) {
            case HIGH -> 3;
            case MEDIUM -> 2;
            case LOW -> 1;
            case INFO -> 0;
        };
    }

    @Test
    void realReviewSeparatesGoodFromBadAndDoesNotInventThings() throws Exception {
        YamlProblemRepository problems = new YamlProblemRepository("classpath:problems/*.yml");
        String model = Optional.ofNullable(System.getenv("GEMINI_MODEL")).filter(s -> !s.isBlank()).orElse("gemini-flash-latest");
        LlmClient llm = new GeminiLlmClient(System.getenv("GEMINI_API_KEY"), model, Duration.ofSeconds(90));
        LlmDesignReviewEvaluator reviewer = new LlmDesignReviewEvaluator(llm, new FindingValidator());
        RuleThresholds t = new RuleThresholds();
        RuleEvaluator rules = new RuleEvaluator(List.of(new GodClassRule(t), new TypeSwitchRule(t), new HardcodedValueRule(),
                new ConcreteDependencyRule(), new DependencyCycleRule(), new OrphanOrUnusedRule(), new CapabilityOwnershipRule()));
        JavaCodeParser parser = new JavaCodeParser();

        ExecutorService pool = Executors.newFixedThreadPool(4);
        List<Future<Run>> futures = new ArrayList<>();
        for (Sample s : ReviewSamples.all()) {
            for (int n = 1; n <= RUNS_PER_SAMPLE; n++) {
                int run = n;
                futures.add(pool.submit(() -> runOnce(s, run, problems.find(s.problemId()).orElseThrow(), parser, rules, reviewer)));
            }
        }
        List<Run> runs = new ArrayList<>();
        for (Future<Run> f : futures) runs.add(f.get(10, TimeUnit.MINUTES));
        pool.shutdown();

        String report = report(model, runs);
        Path out = Path.of("target", "review-quality.md");
        Files.createDirectories(out.getParent());
        Files.writeString(out, report);
        System.out.println(report);

        // ---- 1. finishes
        long ok = runs.stream().filter(Run::ok).count();
        assertThat(ok).as("runs that finished").isGreaterThanOrEqualTo((long) Math.ceil(runs.size() * 0.9));

        // ---- 2. invention rate: findings the validator had to drop because they cite code that is not there
        int kept = runs.stream().mapToInt(Run::kept).sum();
        int dropped = runs.stream().mapToInt(Run::dropped).sum();
        double dropRate = kept + dropped == 0 ? 0 : (double) dropped / (kept + dropped);
        assertThat(dropRate).as("share of AI findings dropped by the validator").isLessThanOrEqualTo(0.35);

        // ---- 3. decision points: does the named approach match what a careful reader names?
        int asked = 0, right = 0;
        for (Run r : runs) {
            for (DecisionAssessment a : r.assessments()) {
                Set<String> accepted = r.sample().acceptableApproaches().get(a.decisionPointId());
                if (accepted == null) continue;
                asked++;
                if (accepted.contains(a.approachUsed())) right++;
            }
        }
        assertThat(asked).as("decision points assessed").isGreaterThan(0);
        assertThat((double) right / asked).as("decision-point accuracy").isGreaterThanOrEqualTo(0.6);

        // ---- 4. a good design must not draw a HIGH AI finding; and each problem's bad design must score worse overall
        for (Run r : runs) {
            if (r.sample().good() && r.ok()) {
                assertThat(r.judgments()).as("HIGH AI findings on " + r.sample().name()).noneMatch(f -> f.severity() == Severity.HIGH);
            }
        }
        for (String problem : List.of("parking-lot", "elevator", "vending-machine")) {
            double good = meanScore(runs, problem, true);
            double bad = meanScore(runs, problem, false);
            assertThat(bad).as("bad design should score worse than good for " + problem).isGreaterThan(good);
        }
    }

    private static Run runOnce(Sample s, int n, Problem problem, JavaCodeParser parser, RuleEvaluator rules,
                               LlmDesignReviewEvaluator reviewer) {
        long t0 = System.nanoTime();
        try {
            ParseResult parsed = parser.parse(s.submission());
            EvaluationContext ctx = new EvaluationContext(problem, s.submission(), parsed.model(), List.of(), null);
            List<Finding> facts = rules.evaluate(ctx).findings();
            EvaluationResult r = reviewer.evaluate(ctx.withFindings(facts));
            Matcher m = DROPPED.matcher(r.note());
            int kept = r.findings().size(), dropped = 0;
            if (m.find()) {
                kept = Integer.parseInt(m.group(1));
                dropped = Integer.parseInt(m.group(2));
            }
            return new Run(s, n, true, "", facts, r.findings(), r.assessments(), kept, dropped, millis(t0));
        } catch (EvaluationException | RuntimeException e) {
            return new Run(s, n, false, String.valueOf(e.getMessage()), List.of(), List.of(), List.of(), 0, 0, millis(t0));
        }
    }

    private static long millis(long t0) {
        return (System.nanoTime() - t0) / 1_000_000;
    }

    private static double score(Run r) {
        return r.facts().stream().filter(f -> !f.ruleId().equals("CAPABILITY_OWNERSHIP") || f.severity() != Severity.INFO)
                .mapToInt(f -> weight(f.severity())).sum()
                + r.judgments().stream().mapToInt(f -> weight(f.severity())).sum();
    }

    private static double meanScore(List<Run> runs, String problem, boolean good) {
        return runs.stream().filter(r -> r.ok() && r.sample().problemId().equals(problem) && r.sample().good() == good)
                .mapToDouble(LiveReviewQualityTest::score).average().orElse(0);
    }

    private static double jaccard(Set<String> a, Set<String> b) {
        if (a.isEmpty() && b.isEmpty()) return 1;
        Set<String> inter = new HashSet<>(a);
        inter.retainAll(b);
        Set<String> union = new HashSet<>(a);
        union.addAll(b);
        return (double) inter.size() / union.size();
    }

    private static String report(String model, List<Run> runs) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Live review quality\n\nModel: `").append(model).append("`. ").append(RUNS_PER_SAMPLE)
                .append(" runs per design. Scores weight HIGH=3, MEDIUM=2, LOW=1 over rule findings (INFO ownership notes ")
                .append("excluded) plus AI findings.\n\n");
        long okRuns = runs.stream().filter(Run::ok).count();
        int kept = runs.stream().mapToInt(Run::kept).sum();
        int dropped = runs.stream().mapToInt(Run::dropped).sum();
        int asked = 0, right = 0;
        for (Run r : runs) {
            for (DecisionAssessment a : r.assessments()) {
                Set<String> accepted = r.sample().acceptableApproaches().get(a.decisionPointId());
                if (accepted == null) continue;
                asked++;
                if (accepted.contains(a.approachUsed())) right++;
            }
        }
        long highOnGood = runs.stream().filter(r -> r.sample().good())
                .flatMap(r -> r.judgments().stream()).filter(f -> f.severity() == Severity.HIGH).count();
        sb.append("## Headline\n\n");
        sb.append("- finished: ").append(okRuns).append(" of ").append(runs.size()).append("\n");
        sb.append("- AI findings dropped by the validator for citing code that is not there: ").append(dropped).append(" of ")
                .append(kept + dropped).append(String.format(" (%.0f%%)", kept + dropped == 0 ? 0.0 : 100.0 * dropped / (kept + dropped))).append("\n");
        sb.append("- decision-point approach matched a careful reader: ").append(right).append(" of ").append(asked)
                .append(String.format(" (%.0f%%)", asked == 0 ? 0.0 : 100.0 * right / asked)).append("\n");
        sb.append("- HIGH-severity AI findings on the good designs: ").append(highOnGood).append("\n\n");
        sb.append("| design | run | finished | s | rule findings | AI findings (kept/dropped) | AI severities | score |\n|---|---|---|---|---|---|---|---|\n");
        for (Run r : runs) {
            sb.append("| ").append(r.sample().name()).append(" | ").append(r.n()).append(" | ").append(r.ok() ? "yes" : "NO: " + r.error())
                    .append(" | ").append(r.millis() / 1000).append(" | ")
                    .append(r.facts().stream().filter(f -> f.severity() != Severity.INFO).count()).append(" | ")
                    .append(r.kept()).append("/").append(r.dropped()).append(" | ")
                    .append(r.judgments().stream().map(f -> f.severity().name().charAt(0) + ":" + f.ruleId()).toList())
                    .append(" | ").append((int) score(r)).append(" |\n");
        }
        sb.append("\n## Decision points\n\n| design | decision | approach named (run 1 / run 2) | acceptable |\n|---|---|---|---|\n");
        for (Sample s : ReviewSamples.all()) {
            for (var e : s.acceptableApproaches().entrySet()) {
                List<String> named = new ArrayList<>();
                for (Run r : runs) {
                    if (!r.sample().name().equals(s.name())) continue;
                    named.add(r.assessments().stream().filter(a -> a.decisionPointId().equals(e.getKey()))
                            .map(DecisionAssessment::approachUsed).findFirst().orElse("-"));
                }
                sb.append("| ").append(s.name()).append(" | ").append(e.getKey()).append(" | ").append(String.join(" / ", named))
                        .append(" | ").append(new TreeSet<>(e.getValue())).append(" |\n");
            }
        }
        sb.append("\n## Repeatability (same design, two runs; overlap of finding category + subject)\n\n");
        double sum = 0;
        int n = 0;
        for (Sample s : ReviewSamples.all()) {
            List<Set<String>> sets = new ArrayList<>();
            for (Run r : runs) {
                if (r.sample().name().equals(s.name()) && r.ok()) {
                    Set<String> fp = new TreeSet<>();
                    r.judgments().forEach(f -> fp.add(f.fingerprint()));
                    sets.add(fp);
                }
            }
            if (sets.size() == 2) {
                double j = jaccard(sets.get(0), sets.get(1));
                sum += j;
                n++;
                sb.append("- ").append(s.name()).append(": ").append(String.format("%.2f", j)).append("\n");
            }
        }
        sb.append("- mean: ").append(n == 0 ? "n/a" : String.format("%.2f", sum / n)).append("\n");
        sb.append("\n## Good versus bad (mean score)\n\n");
        for (String p : List.of("parking-lot", "elevator", "vending-machine")) {
            sb.append("- ").append(p).append(": good ").append(String.format("%.1f", meanScore(runs, p, true)))
                    .append(", bad ").append(String.format("%.1f", meanScore(runs, p, false))).append("\n");
        }
        sb.append("\n## What the AI said about the good designs\n\n");
        for (Run r : runs) {
            if (!r.sample().good() || r.n() != 1) continue;
            sb.append("**").append(r.sample().name()).append("**\n");
            for (Finding f : r.judgments()) {
                sb.append("- ").append(f.severity()).append(" ").append(f.ruleId()).append(" ").append(f.subject()).append(": ")
                        .append(f.message()).append("\n");
            }
            sb.append("\n");
        }
        return sb.toString();
    }
}

# Evaluation of the AI review

This is the evaluation approach for the JUDGMENT layer: a small, repeatable check you can re-run, what it showed, and,
just as important, what it cannot show.

## How to run it

```
$env:GEMINI_API_KEY="..."        # PowerShell
./mvnw test -Dtest=LiveReviewQualityTest
```

It is skipped when no key is set, so the normal `./mvnw test` stays offline. It makes 12 live calls (about two
minutes), writes `target/review-quality.md`, and fails if a threshold below is missed. The last full report is saved
as [review-quality-run.md](review-quality-run.md).

## What it does

Six hand-written designs, one good and one bad for each problem (`ReviewSamples`), each reviewed twice by the real
model. For every design the sample records which approach a careful reader would name at each decision point.

| Measure | Threshold | Why |
|---|---|---|
| Reviews that finish (not PARTIAL or FAILED) | at least 90% | The loop is useless if the AI stage often fails. |
| AI findings dropped by the validator because they cite code that is not there | at most 35% | How often the model invents things. The validator removes them, but a high rate would still waste the learner's time. |
| Decision-point approach named matches the label | at least 60% | Whether the rubric-style judgment is anchored to the code. |
| HIGH-severity AI findings on a good design | none | A good design should not be called badly broken. |
| Bad design scores worse than good, per problem | strictly | The layers together must tell them apart. |
| Overlap of findings between two runs of the same design | reported, not enforced | LLM output varies; the history diff matches by category and subject for this reason. |

## Result of the last run (`gemini-flash-latest`)

| Measure | Result |
|---|---|
| Finished | 12 of 12 |
| Dropped for citing missing code | 3 of 33 (9%) |
| Decision point named correctly | 34 of 36 (94%) |
| HIGH AI findings on good designs | 0 |
| Mean score, good vs bad | parking 7.5 vs 19.5, elevator 7.5 vs 16.0, vending 7.5 vs 14.0 |
| Overlap between two runs | mean 0.43 (range 0.25 to 0.67) |

Reading the numbers:

- The two decision-point misses are both on the bad vending machine, where the model said "none" for change-making and
  the label expected `inline-greedy` or `hardcoded-list`. That is a defensible reading of an inline loop, so it is a
  label disagreement more than a model error.
- The AI findings on the *good* designs were mostly true observations about the samples, not inventions: the good
  parking design really has no entry timestamp on `Ticket`, the good vending design really has no restock method, and
  `Elevator.board` really returns nothing when the limit is exceeded. "Good" here means well-structured, not complete.
- The separation between good and bad is driven mostly by the rule findings. The AI adds findings on bad designs too,
  but its own severities vary from run to run.
- An overlap of 0.43 means the AI repeats a bit under half of its findings by category and subject. Treat any single
  JUDGMENT as advisory, as the report says.

## What this does not show

- Six designs with two runs each is a smoke check, not a benchmark. Any confidence interval would be wide.
- It measures completion, obvious invention and consistency, not whether every finding is *useful* or *right*. Nobody
  graded the findings against a panel of human reviewers.
- One model, one date. Results will move when the model changes.

## What it found while being built

Writing this harness exposed a real defect in the platform, unrelated to the model: `JavaCodeParser` shared one
`JavaParser` between threads, so two overlapping submissions could make valid code fail with a bogus "syntax error".
It is fixed (one parser per thread) and covered by a 400-call, 8-thread test that failed before the fix.

# LLD Practice Platform

A small Spring Boot app where a learner practises Low-Level Design on three problems (Parking Lot, Elevator, Vending Machine), submits Java code, and gets feedback that separates **deterministic structural analysis** from **LLM-assisted judgment**.

The loop: choose a problem, design, submit, get feedback, review, try again. A change request for each problem measures how much of the learner's own design had to move.

## Run it

Requires JDK 17 or newer (built and tested on 21). Nothing else to install; Maven is bundled as `mvnw`.

```
./mvnw spring-boot:run          # Windows: mvnw.cmd spring-boot:run
```

Open http://localhost:8080. Attempts are stored in `./data/lld-practice.mv.db` and survive a restart.

### The AI review

Set `GEMINI_API_KEY` to use the real review (optional `GEMINI_MODEL`, default `gemini-2.5-flash`).

Without a key the app runs in **demo mode**: the AI stage returns a single placeholder note that says so. Everything else (parsing, the seven rules, the diagram, history, the change round) is real. Demo mode exists so the whole flow can be tried with no account.

## Test it

```
./mvnw test
```

158 tests (one is the opt-in live review check, skipped unless `GEMINI_API_KEY` is set). They cover the parser, each rule, the failure paths (timeout, malformed JSON, invented classes, nothing parses, a throwing rule, a restart mid-evaluation, retry pressed twice), the change round, the history diff, the persistence round trip, the HTTP flow including HTML escaping, and an architecture test that enforces the package dependency rules. Tests use a scripted `FakeLlmClient`, so none of them call a real model.

## What it does

| Layer | What the learner sees |
|---|---|
| FACT | Seven deterministic rules on the structure of their code, available immediately. Labelled `FACT`. |
| JUDGMENT | One LLM-assisted review, run in the background. Findings that cite classes or methods not in the code are dropped. |
| Change round | Edit the same code for the problem's change request. Shown as added / modified / removed / interfaces touched, with an ADDITIVE, CONTAINED or SPREAD verdict. |
| History | Resolved / new / persisting findings against the previous attempt, matched by fingerprint. |

Attempt states: `DRAFT → SUBMITTED → EVALUATING → COMPLETED | PARTIAL | FAILED`. If the AI stage fails after retries the attempt ends `PARTIAL`: the facts stay and a Retry button appears. If nothing in the submission parses it ends `FAILED` with the parse errors.

## Limits you should know about

- **Code is parsed, never compiled or run.** Compile errors, missing imports and unknown types are ignored. Behaviour is not tested.
- **Java only**, top-level types only. Nested types are skipped with a visible warning.
- The AI review sees class names, signatures and relationships, **not method bodies**.
- Rule thresholds (for example "more than 12 methods") are starting defaults and have not been tuned against real learner submissions. They live in `RuleThresholds`.
- `CAPABILITY_OWNERSHIP` matches name fragments, so it can miss an unconventional name.
- The blast-radius cut-offs (CONTAINED = at most 2 touched and not more than added) are heuristics, configurable in `EvaluationProperties`.
- A learner is a cookie, not an account. Clearing cookies loses access to earlier attempts in the UI (the data stays in the database).
- The real Gemini call was run once end to end (model `gemini-flash-latest`, Parking Lot skeleton): it returned 3 findings that passed validation. That is one smoke run, not an evaluation of review quality.
- While the AI review runs the page shows a spinner and an elapsed-seconds counter; buttons that trigger slow actions show a busy state.

## Layout

```
model/          immutable domain records (depend on nothing)
problem/        Problem, YAML-backed ProblemRepository
evaluation/     Evaluator, Rule ports; RuleEvaluator; evaluation/rules/ the seven rules
parser/         SubmissionParser port, ParserRegistry; parser/javacode/ JavaParser adapter
llm/            LlmClient port; prompt, reply parser, FindingValidator, review evaluator; Gemini, demo and fake clients
attempt/        Attempt, AttemptLifecycle (transition table), AttemptRepository port
orchestration/  services: AttemptService, EvaluationOrchestrator, ChangeImpactCalculator, FindingDiffer, history
persistence/    JPA adapter (submission and report stored as JSON columns)
web/            Thymeleaf controllers
src/main/resources/problems/   one YAML file per problem
```

Adding a problem means adding a YAML file. Adding a rule means adding a `Rule` class. Adding an evaluation approach means adding an `Evaluator`. Adding a submission format means adding a `SubmissionParser`. Spring picks each up as a list, so nothing else changes.

More detail: [docs/DESIGN_NOTE.md](docs/DESIGN_NOTE.md), [docs/RESEARCH_NOTE.md](docs/RESEARCH_NOTE.md), [AI_USAGE.md](AI_USAGE.md).


---

# AI usage

This file records decisions where the suggestion was not simply the spec, what was accepted or changed, and why.

## 1. Separating deterministic rules from LLM judgment

- **Core product decision:** the spec says "AI may be used where reasoning is helpful" but does not say where to draw the line.
- **Decision:** anything countable from structure (size, cycles, literals, switch on type, missing capability) stays deterministic — seven rules, instant, always reproducible. The LLM only handles what rules cannot: cohesion, pattern fit, trade-offs, and how well a change request was absorbed.
- **Why:** an LLM judging structure is inconsistent across runs and unverifiable. A rule is. Keeping them separate means the learner always knows which findings to trust unconditionally and which to treat as advisory. This also makes the system testable without a live model.

## 2. Finding fingerprint

- **Spec said:** fingerprint = rule id + subject.
- **Suggested:** add an optional discriminator.
- **Why:** `CAPABILITY_OWNERSHIP` reports one finding per missing capability, all on the entry type. With the plain fingerprint, two different missing capabilities collapse into one in the history diff, hiding real progress. A test (`twoMissingCapabilitiesHaveDifferentFingerprints`) fails without the change.
- **Decision:** accepted. Other findings keep the exact spec fingerprint.

## 3. What "restates a FACT" means

- **Spec said:** drop an LLM finding that restates a FACT on the same subject.
- **First reading:** drop any LLM finding on a subject that has any FACT.
- **Rejected:** a class flagged for one reason can still deserve different AI comments (e.g. an encapsulation concern on a class flagged as an orphan).
- **Decision:** drop only when the FACT is from a related rule, using a small category-to-rule table in `FindingValidator`. Tested.

## 4. How much of a broken file to keep

- **Problem:** JavaParser may return a partial tree even for class-level syntax errors, giving the learner misleading findings.
- **Decision:** keep a file with errors only when the errors are statement-level (JavaParser produces an unparsable-statement node); otherwise skip the whole file with a visible warning. Matches the spec's failure table. Tested for both cases.

## 5. Demo mode for the AI stage

- **Suggested:** when no `GEMINI_API_KEY` is set, return one clearly labelled placeholder finding instead of failing every attempt as PARTIAL.
- **Why:** lets a reviewer run the whole loop with no account. The placeholder says it is demo mode, so it cannot be mistaken for a real review.

## 6. Bugs found only by running real code

Three issues only surfaced when the real UI was driven against real problems — unit tests with a fake LLM passed throughout:

- **Lossy prompt (Elevator problem):** the prompt renderer printed unwrapped type names, so the model said `ElevatorSystem` held a single `Elevator` when the field was `List<Elevator>`. Fixed by keeping the unwrapped type for relationships and adding a separate declared type for the prompt and diagram.
- **Wrong capability rule (Elevator problem):** `Elevator.step()` was rejected as the mover because `ElevatorSystem.step()` shares the same name. The exclusion was narrowed to the entry type's own public methods only. Two earlier tests rewritten, three added.
- **Thread-safety bug:** `JavaCodeParser` held one `JavaParser` shared by every thread. Under concurrent use, valid code was reported as a syntax error. Fixed to one parser per thread. A test with 8 threads and 400 parses failed before the fix and passes after.

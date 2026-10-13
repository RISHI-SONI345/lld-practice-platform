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

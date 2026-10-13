# Research note

Pages read as of 2026-10-13. Each tool row below is labelled by what kind of source it comes from: **vendor page** (the product's own site) or **project listing** (a third-party listing of the product). Only what those pages say is recorded here.

## The gap

The LLD practice pages we could read score a learner's answer against a reference solution or a rubric. None of them says which feedback is produced by a repeatable rule and which is a model's opinion. That is the gap this platform is built on.

## How a learner practises

Hello Interview's preparation guide splits an LLD answer into five timed phases: requirements, entities and relationships, class design, implementation, and verification by tracing a scenario. It says pseudocode is usually acceptable and lists over-engineering and unrequested methods among common mistakes ([guide](https://www.hellointerview.com/blog/how-to-prepare-lld)). This supports two choices here: a staged attempt, and a one-line rationale for the pattern used and the pattern rejected.

## What existing tools do

| Tool | What the learner gives | Feedback | Source |
|---|---|---|---|
| AlgoMaster | Timed AI-led mock interview in four stages; in-browser code editor in the last. | Each stage scored out of 10 with notes, using the answer, the conversation and a reference solution. Attempts are saved. | vendor page: [algomaster.io](https://algomaster.io/interview/low-level-design) |
| Hello Interview | Guided step-by-step practice, 8 LLD problems. | "Instant AI feedback", rubric-based per step. | vendor page: [practice](https://www.hellointerview.com/practice) |
| Codemia | UML class diagrams and design-pattern practice. The page gives conflicting problem counts. | "AI Design Review". Whether resubmission is allowed is not stated. | vendor page: [codemia.io](https://codemia.io) |
| Coudo AI | UML diagram in a custom editor or by chat; machine-coding problems. | Instant AI review of diagrams and code, plus peer reviews. | project listing: [peerlist](https://peerlist.io/shivam_chauhan/project/coudo-ai) |
| LowLevelDesign Mastery | Code written and run in a browser playground, six languages. | AI feedback on correctness and complexity; flashcards and diagrams. | project listing: [peerlist](https://peerlist.io/vdsanku/project/lowleveldesign-mastery) |
| LLDCanvas, CodeZym, LLDHub | Not verified | Not verified | LLDCanvas blocks automated reading; searches found nothing for the other two |

Not confirmed, so not claimed: a peer library of shared solutions at Hello Interview; pattern suggestions at Coudo AI; resubmission at Codemia; anything about LLDCanvas, CodeZym or LLDHub.

## Gaps we use

1. **Fact versus judgment.** No page we read labels which findings come from rules and which from a model. Every finding here carries `FACT` or `JUDGMENT`, and the report puts facts first.
2. **No single reference answer.** AlgoMaster states it grades against a reference solution. We grade against decision points with several accepted approaches, so a different valid design is not marked wrong.
3. **Cost of change to the learner's own design.** None of the pages we read describes measuring how much of the learner's own design breaks under a new requirement. We report this as blast radius.
4. **No execution.** AlgoMaster and LowLevelDesign Mastery run code. We parse structure only and say so, which removes the sandbox but also means behaviour is never tested.

## Evidence behind the design

- **Why the split.** One recent study finds that a model judging unchanged code can change its verdict with prompt cues and across repeated runs, and recommends neutral templates and checks that can be run ([arXiv 2604.16790](https://arxiv.org/abs/2604.16790)). Another finds a judge can be very consistent across runs without being valid ([arXiv 2606.19544](https://arxiv.org/abs/2606.19544)). So anything countable stays out of the model, the prompt is neutral, temperature is 0, and the AI output is labelled advisory. Consistency would not prove it right.
- **Why the God Class rule is limited.** The published detection strategy combines access to foreign data, weighted method count and class cohesion ([Marinescu, detection strategies](https://homepages.dcc.ufmg.br/~figueiredo/disciplinas/lectures/detection-strategy_v01.pdf)). The latter two need body-level analysis we do not do, so `GOD_CLASS` counts members only and says so.
- **Why parse failures are per file.** JavaParser recovers at statement level and at file level, and a file it cannot recover keeps little beyond the token range ([JavaParser docs](https://javaparser.org/parse-error-recovery)). So a partial model is per file, not per declaration.

## What this research does not show

It shows what the vendors say on their pages, not how good their feedback is. We did not test the tools ourselves, and we did not validate our own AI review against a panel of human reviewers. Those are the weakest points of the evidence.

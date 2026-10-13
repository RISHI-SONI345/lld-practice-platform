# Live review quality

Model: `gemini-flash-latest`. 2 runs per design. Scores weight HIGH=3, MEDIUM=2, LOW=1 over rule findings (INFO ownership notes excluded) plus AI findings.

## Headline

- finished: 12 of 12
- AI findings dropped by the validator for citing code that is not there: 3 of 33 (9%)
- decision-point approach matched a careful reader: 34 of 36 (94%)
- HIGH-severity AI findings on the good designs: 0

| design | run | finished | s | rule findings | AI findings (kept/dropped) | AI severities | score |
|---|---|---|---|---|---|---|---|
| parking-good | 1 | yes | 21 | 1 | 3/0 | [M:ENCAPSULATION, M:ENCAPSULATION, L:COHESION] | 8 |
| parking-good | 2 | yes | 17 | 1 | 3/0 | [M:ENCAPSULATION, L:ENCAPSULATION, L:COHESION] | 7 |
| parking-bad | 1 | yes | 17 | 5 | 4/0 | [H:EXTENSIBILITY, M:ENCAPSULATION, M:COHESION, L:COHESION] | 22 |
| parking-bad | 2 | yes | 18 | 5 | 2/1 | [M:ENCAPSULATION, L:COHESION] | 17 |
| elevator-good | 1 | yes | 21 | 2 | 2/0 | [M:ENCAPSULATION, L:ENCAPSULATION] | 7 |
| elevator-good | 2 | yes | 29 | 2 | 3/0 | [M:ENCAPSULATION, L:COHESION, L:ENCAPSULATION] | 8 |
| elevator-bad | 1 | yes | 13 | 5 | 2/0 | [M:ENCAPSULATION, L:EXTENSIBILITY] | 16 |
| elevator-bad | 2 | yes | 11 | 5 | 2/0 | [M:ENCAPSULATION, L:EXTENSIBILITY] | 16 |
| vending-good | 1 | yes | 18 | 3 | 2/0 | [M:PATTERN_FIT, L:ENCAPSULATION] | 7 |
| vending-good | 2 | yes | 20 | 3 | 3/0 | [M:PATTERN_FIT, L:COHESION, L:ENCAPSULATION] | 8 |
| vending-bad | 1 | yes | 13 | 4 | 2/1 | [M:ENCAPSULATION, L:EXTENSIBILITY] | 14 |
| vending-bad | 2 | yes | 16 | 4 | 2/1 | [M:COHESION, L:EXTENSIBILITY] | 14 |

## Decision points

| design | decision | approach named (run 1 / run 2) | acceptable |
|---|---|---|---|
| parking-good | fit-decision | can-fit / can-fit | [can-fit, size-compare] |
| parking-good | fee-computation | rate-table / rate-table | [pricing-policy, rate-table] |
| parking-good | spot-selection | allocation-strategy / allocation-strategy | [allocation-strategy, floor-finds] |
| parking-bad | fit-decision | none / none | [instanceof-chain, none] |
| parking-bad | fee-computation | none / none | [fee-on-vehicle, none] |
| parking-bad | spot-selection | single-scan / single-scan | [none, single-scan] |
| elevator-good | car-behaviour | enum-switch / enum-switch | [enum-switch] |
| elevator-good | dispatch-strategy | nearest-car / nearest-car | [nearest-car] |
| elevator-good | stop-holding | sorted-per-direction / sorted-per-direction | [sorted-per-direction] |
| elevator-bad | car-behaviour | boolean-flags / boolean-flags | [boolean-flags] |
| elevator-bad | dispatch-strategy | round-robin / round-robin | [fixed-rule, none, round-robin] |
| elevator-bad | stop-holding | fifo-queue / fifo-queue | [fifo-queue] |
| vending-good | state-behaviour | state-pattern / state-pattern | [state-pattern] |
| vending-good | payment-model | payment-abstraction / payment-abstraction | [payment-abstraction] |
| vending-good | change-making | change-strategy / change-strategy | [change-strategy] |
| vending-bad | state-behaviour | boolean-flags / boolean-flags | [boolean-flags, none] |
| vending-bad | payment-model | coins-only / coins-only | [coins-only] |
| vending-bad | change-making | none / none | [hardcoded-list, inline-greedy] |

## Repeatability (same design, two runs; overlap of finding category + subject)

- parking-good: 0.50
- parking-bad: 0.50
- elevator-good: 0.67
- elevator-bad: 0.33
- vending-good: 0.25
- vending-bad: 0.33
- mean: 0.43

## Good versus bad (mean score)

- parking-lot: good 7.5, bad 19.5
- elevator: good 7.5, bad 16.0
- vending-machine: good 7.5, bad 14.0

## What the AI said about the good designs

**parking-good**
- MEDIUM ENCAPSULATION Ticket: Ticket does not store the entry timestamp required to compute duration at exit.
- MEDIUM ENCAPSULATION Spot: Spot provides free() to clear occupancy but lacks an occupy method to mark the spot as taken.
- LOW COHESION ParkingLotService: ParkingLotService does not expose the capability to report free spots per floor.

**elevator-good**
- MEDIUM ENCAPSULATION Elevator.board: Elevator.board returns void, providing no mechanism to communicate refusal reasons when passenger limits are exceeded.
- LOW ENCAPSULATION Elevator: Elevator encapsulates passenger count, capacity, and stop collections without accessors for dispatching.

**vending-good**
- MEDIUM PATTERN_FIT MachineState: MachineState lacks a cancel method to handle state-dependent cancellation.
- LOW ENCAPSULATION VendingMachine: VendingMachine does not expose a restocking method to satisfy operator restocking requirements.


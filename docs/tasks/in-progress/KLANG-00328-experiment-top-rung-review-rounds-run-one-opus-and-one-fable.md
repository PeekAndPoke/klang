# KLANG-00328: experiment: top-rung review rounds run one Opus and one Fable reviewer

> **Status (2026-10-23): IN PROGRESS.** Experiment until the meeting of 2026-10-30 (maintainer, 2026-10-23, via Mission Control); the meeting deletes it or turns it into a rule.

## Why this exists

The maintainer (2026-10-23, relayed by Mission Control): "No repeat runs, let us get more samples in practice. For
the max tier review rounds we always run 1 Opus and 1 Fable for the next week. This should give us some insights.
In general to me this means, that we need to always run "enough" reviewers to hedge our bets. What enough means is
up for debate next friday."

Background: the Fable experiment (MC-00026), klang's sample in
`docs/housekeeping/2026-10-23-fable-experiment-5a3.md`.

## The work

- Every review round on the top rung (round 3 and later) runs two reviewers on the SAME brief, blind to each other:
  one Opus 5.5 at max effort (`general-purpose`, `model: opus`, effort max) and one Fable (`reviewer-xhigh`).
- The round's findings record, per finding, who found it: both, Opus only, or Fable only. Plus each reviewer's
  tokens (the harness's count from the completion notice, tool calls, wall time).
- No repeat runs: a round is not run twice to settle a doubt.
- One row per round in the table below, the detail in the step's own ticket.

## Samples

| date | step (ticket) | findings: both / Opus only / Fable only | severest of each | tokens Opus / Fable |
|---|---|---|---|---|
| 2026-10-23 | MC-00026 sample, Katalyst 5a-3 round 3 (a replay, not a live round) | 5 of Fable's 6 findable: Opus found the duck MAJOR at the same severity; Fable only: the NaN body mix (outside the brief); Opus only: 9 MINORs, 4 NITs | both MAJOR (the duck) | 512k (code) and 443k (audio) / not comparable (older transcripts) |

## History

- 2026-10-23: created.

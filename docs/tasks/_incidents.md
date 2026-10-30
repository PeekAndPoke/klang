# Incidents

Capture the incident when it happens: one line here. Fix the code and the tests at once; a rule waits for the
weekly meeting, which also prunes. Damage that can't be undone (harm to ears or speakers, lost work or data, an
exposed secret, a push or a publish) gets a stopgap at once, preferably a check or a deny-list request, else one
line marked PROVISIONAL until <meeting date>; the meeting keeps or drops it, and unconfirmed it lapses.
(Rules retro, 2026-10-23, Mission Control's MC-00028; it replaces "capture now, prune later".)

**One line per incident:** the date, what happened, what let it through, what it cost, the immediate fix (code or
test), and a proposed rule, which is only a proposal. The meeting gives each line one verdict: a tool or a test, a
rule with its home (module, skill or `CLAUDE.md`), a ticket, or nothing because it was a one-off.

**Review escapes** keep their row in `.claude/skills/review-loop/escape-ledger.md` as the evidence; their line here
points to it.

**After each meeting** the decided lines move, with their verdicts, to
`docs/tasks-archive/<month>/incidents-<meeting date>.md`, and this table starts again.

| date | what happened | what let it through | what it cost | immediate fix | proposed rule |
|---|---|---|---|---|---|
| 2026-10-26 (found 2026-10-30) | Three times after a MAJOR, the next round of KLANG-00325 continued the same reviewer by a message at the same level instead of a new agent one level up (step 3a at level 2; 3b's fix step and the grouped key overview at level 1); found while checking the session log for the meeting, where the prep had said every MAJOR was cleared one level up | continuing a reviewer by message is the cheap path and keeps its model and effort; nothing checks a round's level against the ladder | three re-checks below the ladder's level; all came back clean, so no known defect | none: the steps are committed and their later steps reviewed | rule, `/review-loop` ladder (after MC-00036): a raised round is a new agent of the new level, never a message to the last one |

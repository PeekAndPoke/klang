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
| 2026-10-23 | A test of the offline render's master latency stayed green under a mutant that read the latency bound only once, because a master at time 0 is promoted before the first block; the KDoc claimed the opposite | the coordinator's claim about the scheduler, not read from `VoiceScheduler` | one test rework, one KDoc corrected, before the commit | the test's master moved to cycle 2 (cccb5f1b) | none: the mutation check caught it, as designed |
| 2026-10-23 | A defect-density ledger row (KLANG-00290 item 20) carried line counts written from memory (133 production, 286 test); git said 215 and 177 | the row was written before the numbers were computed | one correcting commit (566cf49e) | corrected from `git show --numstat` | none: the ledger is closed (MC-00021) |
| 2026-10-23 | `test-digest --expect CylinderCleanupTest` reported the spec MISSING: the file `CylinderCleanupTest.kt` holds the class `OrbitCleanupTest`, a name from before the cylinder rename | nothing checks that a class is named like its file; the audio backend audit (KLANG-00224) already lists several such files | one test run repeated | none: renaming one alone leaves the others; they go together with the check | a check (MC-00019's class names), which renames them all in one change |
| 2026-10-23 | Found, not yet fired: the sample banks' manifests load from `raw.githubusercontent.com/felixroos/dough-samples/main/*.json`, an unpinned branch, so an upstream change can alter every song that uses them without a change here | external URLs are not reviewed for pinning | nothing so far | none: pinning to a commit or mirroring to `klang-assets.finzo.de` is a decision | a ticket: pin or mirror the manifests |
| 2026-10-23 | The Fable experiment's blind review copy (MC-00026) leaked the answer: built with `git apply --index`, its index still held the fixed duck door, and a reviewer's plain `git diff` (index against tree) showed the fix | the coordinator checked the working tree and the diff file, never the index | the audio reviewer's MAJOR rating is not independent (it had found the duck as MINOR before the peek, and said so); a rerun may cost about 0.4M tokens | the index made to match the tree, by path, in the experiment worktree | none for now: a one-off of a hand-built tree; if blind trees recur, a script builds them and checks `git status` is clean against HEAD's diff |

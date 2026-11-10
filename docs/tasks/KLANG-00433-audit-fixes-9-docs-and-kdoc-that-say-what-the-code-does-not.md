# KLANG-00433: Audit fixes 9: docs and KDoc that say what the code does not do, dead code, and the tests the audit found missing

> **Status (2026-11-10): DESIGNED.** From the engine audit (KLANG-00420); maintainer, 2026-11-10: fix all findings, sound changes are fine.
>
> **Part of:** KLANG-00353 (The Motor: correct at the edges)

## Why this exists

The engine audit (KLANG-00420) found these. Every row is traced in `docs/audio-audit/2026-11-10/audit/` (the layer
report named by the id's layer, and the `disproof-*.md` that measured it); the index is `SUMMARY.md` there. The
maintainer's decision (2026-11-10): "create tickets and fix all of the findings. Sound changes are fine, for example
for the pluck."

## The findings

| Id | Severity now | Finding | Verdict and measurement |
|---|---|---|---|
| L1-09 | (read only) | The reference pages and several KDocs describe a wire that is not the one in the code | not challenged; the auditor's reading in `layer-1-wire.md` |
| L2-08 | (read only) | Docs and comments that say what the code does not do | not challenged; the auditor's reading in `layer-2-voice-lifetime.md` |
| L3-15 | (read only) | Texts that say what the code does not do | not challenged; the auditor's reading in `layer-3-sources.md` |
| L4-12 | (read only) | Comments that say what the code does not do | not challenged; the auditor's reading in `layer-4-note-stages.md` |
| L5-12 | (read only) | Dead code and stale statements in this layer | not challenged; the auditor's reading in `layer-5-ignitor-build.md` |
| L6-08 | (read only) | Three ref pages and two KDocs describe a cylinder manager that no longer exists | not challenged; the auditor's reading in `layer-6-channel-and-mix.md` |
| L7a-08 | (read only) | Ref pages and KDoc that say what the code does not do | not challenged; the auditor's reading in `layer-7a-katalyst-chain.md` |
| L7b-10 | (read only) | The docs of this layer say several things the code does not do | not challenged; the auditor's reading in `layer-7b-bus-stages.md` |
| L8-10 | (read only) | Dead and stale things at the edges | not challenged; the auditor's reading in `layer-8-master-and-house.md` |
| L9-14 | (read only) | Dead and half-wired things in the shell | not challenged; the auditor's reading in `layer-9-engine-shell.md` |
| L9-15 | (read only) | The ref pages and KDocs of this layer that say what the code does not | not challenged; the auditor's reading in `layer-9-engine-shell.md` |

## The work

Per finding: read its report and its reproduction (`src/jvmTest/kotlin/audit/` in `audio_be`, `sprudel` or `klang`,
run with `KLANG_PROBE=audit`), fix it, and turn the reproduction's row into an ordinary spec that guards the fix
(mutation-checked). A finding that turns out to need a maintainer decision is not forced: it is listed under
"Decide" below with the options, and the rest of the ticket lands without it. How the fixes are made and reviewed:
`docs/audio-audit/2026-11-10/audit/FIX-BRIEF.md`.

## Decide

(filled while the work runs)

## Acceptance

- Every row above is fixed with a guarding spec, or listed under "Decide" with its reason.
- The suites are green, the corpus render is identical except where a fix changes the sound on purpose, and each such
  change is named in this ticket with the songs it moves.
- The engine health page shows the fixed items green after a new probe run.

## History

- 2026-11-10: created.

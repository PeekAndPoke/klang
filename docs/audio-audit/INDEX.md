# Audio backend audit — ledger

Campaign brief and method: [`../tasks/audio-backend-audit.md`](../tasks/audio-backend-audit.md). Standing findings
list: [`FINDINGS.md`](FINDINGS.md) — **this is the list we go through together.**

> **Working rule for this campaign (user, 2026-08-04): ANALYSIS ONLY — do not repair anything.**
> Every defect goes on the list with its evidence; we triage the list as a batch afterwards. A fix
> applied mid-audit is an unreviewed change that the rest of the audit would then be judging.

## Status

| Subsystem                  | Specs verified | Findings | State                                            |
|----------------------------|---------------:|---------:|--------------------------------------------------|
| `voices/` (pilot)          |        13 / 13 |       15 | 🟢 **analysis complete** — awaiting joint triage |
| root / lifecycle           |              — |        — | 🔴 not started                                   |
| `cylinders/` + `katalyst/` |              — |        — | 🔴 not started                                   |
| `effects/`                 |              — |        — | 🔴 not started                                   |
| `filters/`                 |              — |        — | 🔴 not started                                   |
| `ignitor/`                 |              — |        — | 🔴 not started                                   |
| platform / audio thread    |              — |        — | 🔴 not started                                   |
| `master/`                  |              — |        — | 🔴 not started (light pass)                      |

## Baseline

- `./gradlew :audio_be:jvmTest` — **GREEN**, 943 tests, established 2026-08-04 at `f23f1acf`.
- Loop cost measured: **~4 s** for a single spec with no source change, **~14–18 s** including a production recompile.
  So a mutation check is ~20 s of wall clock, not a reason to batch or skip.

## Verified spec verdicts

Full per-claim detail in the per-subsystem files.

Every mutation below was actually applied and run — none is a hand-trace.

| Spec                                      | Verdict summary                                                | Finding                                        |
|-------------------------------------------|----------------------------------------------------------------|------------------------------------------------|
| `voices.EnvelopeDeclickSpec`              | 2/2 **LOAD-BEARING** (both RED on the live declick value)      | —                                              |
| `voices.FilterModulationTest`             | 7/10 load-bearing; **2 TOOTHLESS**, 1 structure-only           | [F3](FINDINGS.md#f3), [F4](FINDINGS.md#f4)     |
| `voices.VoiceFactoryFilterOrderSpec`      | **LOAD-BEARING** at spec level; test 2 redundant               | note                                           |
| `voices.SampleVoiceSpecificTest`          | 10/16 assert; **6 have no assertions**                         | [F6](FINDINGS.md#f6)                           |
| `voices.SynthVoiceTest`                   | 9/12 assert; **3 have no assertions**                          | [F6](FINDINGS.md#f6)                           |
| `voices.SampleVoiceRenderTest`            | 2/2 assert                                                     | —                                              |
| `voices.EnvelopeTest`                     | **2 named-but-unreachable** claims                             | [F8](FINDINGS.md#f8)                           |
| `voices.EnvelopeShapeTest`                | **1 named-but-unreachable** claim                              | [F8](FINDINGS.md#f8)                           |
| `voices.PitchModulationTest`              | **inverted glide not caught, suite-wide**; 8 vacuous           | [F10](FINDINGS.md#f10), [F12](FINDINGS.md#f12) |
| `voices.FmSynthesisTest`                  | **1000× phase error not caught, suite-wide**; ratio IS guarded | [F11](FINDINGS.md#f11), [F12](FINDINGS.md#f12) |
| `voices.VoiceLifecycleTest`               | 2 tests duplicate a sibling instead of testing their name      | [F13](FINDINGS.md#f13)                         |
| `voices.VoicePipelineTest`                | no ordering assertion anywhere; 3 assertion-free               | [F13](FINDINGS.md#f13), [F15](FINDINGS.md#f15) |
| `voices.VoiceCompressorSpec`              | tests string parsing only — no DSP                             | [F14](FINDINGS.md#f14)                         |
| `ignitor.AnalogDriftSpec` (1 of 4 claims) | **TOOTHLESS** — 10× engine crank not caught                    | [F2](FINDINGS.md#f2)                           |

**Mutations run: 16.** GREEN-when-it-should-be-RED: **7**, of which **2 stayed green against the entire 943-test suite**
([F10](FINDINGS.md#f10) inverted pitch glide, [F11](FINDINGS.md#f11) 1000× FM phase error). Plus 25 assertion-free tests
found by direct source count, and 3 confirmed zero-coverage subsystems.

### Pilot cost — the number the campaign was calibrated to find

5 Sonnet plan-writers in parallel (~9-25 min each, one coordinator) + 16 coordinator-run mutations at
~20 s each. **One subsystem, one session.** The plan-then-execute split is what made it affordable:
the workers produce exactly-appliable edits, the coordinator only runs them. The expensive step is judgement about
*which* mutation tests the claim — and that is where the coordinator's own first attempt went wrong
([F1](FINDINGS.md#f1)).

Not every planned mutation was executed — the SUSPECT flags and the suite-wide re-checks were prioritised, since those
are where information lives. The full per-test plans (claim → exact appliable mutation → predicted verdict, 110 test
cases across 13 specs) are preserved in
[`voices-pilot-plans/`](voices-pilot-plans/). The unexecuted ones are all `LIKELY-RED` confirmations of claims already
believed sound, and each carries the exact edit needed to finish it later.

## Method notes learned during the run

- **A misplaced mutation is indistinguishable from a toothless test.** The first mutation of this campaign
  (`ENV_DECLICK_SECONDS` → 0.0) left its spec green, which reads as "toothless" — but the constant turned out to be dead
  code (finding [F1](FINDINGS.md#f1)). The spec is in fact fully load-bearing. **Always confirm the mutation reaches the
  live path before recording a verdict**; where it does not, that is itself a finding.
- **Builds are locked.** `console/with-build-lock.sh` (exclusive `flock`) — added mid-run at the user's instruction. For
  a mutation check the critical section is `mutate → build → restore`, not just the build: a build that races an edit
  returns a *plausible* verdict rather than an error, so it would silently poison this ledger. See
  `.claude/BUILD-LOCK.md` and `/agent-fleet`.

# Audio backend audit — ledger

Campaign brief and method: [`../tasks/audio-backend-audit.md`](../tasks/audio-backend-audit.md). Standing findings
list: [`FINDINGS.md`](FINDINGS.md) — **this is the list we go through together.**

> **Working rule for this campaign (user, 2026-08-04): ANALYSIS ONLY — do not repair anything.**
> Every defect goes on the list with its evidence; we triage the list as a batch afterwards. A fix
> applied mid-audit is an unreviewed change that the rest of the audit would then be judging.

## Status

| Subsystem                  | Specs verified | Findings | State                                            |
|----------------------------|---------------:|---------:|--------------------------------------------------|
| `voices/` (pilot)          |        13 / 13 |       19 | 🟡 **triage in progress 2026-08-31** — `strip/pitch` (F10/F11/F12) closed; F7's solo+cut half closed (and it found [F18](FINDINGS.md#f18)); all 10 remaining re-verified, **6 corrected/withdrawn**; 3 owed mutations re-run ✅ |
| root / lifecycle           |              — |        — | 🔴 not started                                   |
| `cylinders/` + `katalyst/` |              — |        — | 🔴 not started                                   |
| `effects/`                 |              — |        — | 🔴 not started                                   |
| `filters/`                 |              — |        — | 🔴 not started                                   |
| `ignitor/`                 |              — |        — | 🔴 not started                                   |
| platform / audio thread    |              — |        — | 🔴 not started                                   |
| `master/`                  |              — |        — | 🔴 not started (light pass)                      |

## Triage log

**2026-08-31 — `strip/pitch` cluster, done first because phaseMod part 2 of the block-framing
workstream is about to change that package and these were its net.**

| Finding | Outcome |
|---------|---------|
| F10 inverted glide | ✅ **CLOSED by later work** — `AccelerateSemitoneLawSpec` now kills the mutation. Did not reproduce. |
| F11 FM 1000× slow | ✅ **FIXED** — re-confirmed live, then given a quantity oracle; the pilot's own mutation is killed. |
| F12 tautological "disabled" tests | ✅ **FIXED** — positive controls added, two FM tests merged. One half stays unfalsifiable BY DESIGN (a depth-0 `FmRenderer` is a mathematical identity, so the gate is an optimisation, not a behaviour — as [F4](FINDINGS.md#f4)). |
| F15(b) tremolo/phaser | 🟡 half closed — `TremoloRendererSpec` exists now; `StripPhaserRenderer` still has no behavioural test. |

**Standing method note added by this session:** re-run every finding's mutation before spending a
triage decision on it. Three of seventeen had moved in the four weeks the list sat unreviewed.

---

**2026-08-31 (second pass) — re-verification sweep over all ten remaining findings.** Static evidence
only: the maintainer's frontend watcher (`:jsBrowserDevelopmentRun`) was running, so no Gradle, so no
mutation could be re-run. Every finding whose evidence is a source count, a grep or a structural
argument was re-checked against the current tree; the four whose evidence is a mutation
([F3](FINDINGS.md#f3), [F4](FINDINGS.md#f4), [F8](FINDINGS.md#f8), and the measurement half of
[F17](FINDINGS.md#f17)) had their **anchors** re-checked but still owe a re-run.

| Finding | Re-verification verdict |
|---------|--------------------------|
| [F3](FINDINGS.md#f3) | anchor moved `EnvelopeCalc.kt:28` → `:31`, code unchanged. ⏳ mutation S1 not re-run. |
| [F4](FINDINGS.md#f4) | anchor moved `FilterPipelineBuilder.kt:43-46` → `:47`, guard unchanged. ⏳ mutation S3 not re-run. |
| [F5](FINDINGS.md#f5) | **CONFIRMED, all three holes.** `FilterModRenderer.kt:34` drift path, `VoiceFactory.toModulator()` at `:430`, Body/Formant exclusion at `:110`. |
| [F6](FINDINGS.md#f6) | **CONFIRMED exactly — 9 tests**, by a comment-stripped per-test census. |
| [F7](FINDINGS.md#f7) | **CONFIRMED and worse.** `VoiceScheduler.kt` 407 → **576 lines** (+41%), still zero specs; its two `commonTest` mentions are comments. solo/cut/choke still zero. |
| [F8](FINDINGS.md#f8) | anchor moved `EnvelopeRenderer.kt:126` → `:147`, clamp unchanged. ⏳ mutation V1 not re-run. |
| [F13](FINDINGS.md#f13) | **CONFIRMED.** Both `VoiceLifecycleTest` duplicates and all three unverified `VoicePipelineTest` ordering names still present. |
| [F14](FINDINGS.md#f14) | **1 of 5 bullets WITHDRAWN** (Pedal — wrong when written, a `voices/`-scoped grep phrased suite-wide). Ducking re-checked and it holds as worded. The other three hold. |
| [F15](FINDINGS.md#f15) | **RECOUNTED 25 → 21, class (a) 13 → 10.** Three "defects" assert through custom infix matchers. Four more hits are helper-delegated false positives. |
| [F17](FINDINGS.md#f17) | **CONFIRMED open.** The deferred peak-detector RMS smoothing is still only a KDoc note at `Compressor.kt:41`. |

**2026-08-31 (third pass) — [F7](FINDINGS.md#f7)'s zero-coverage half closed, and it paid out
immediately.** `VoiceSchedulerSoloCutSpec` (11 rows) is the first spec to take the scheduler's own
logic as its subject. **Its first run found a live production defect**, now filed as
[F18](FINDINGS.md#f18): `VoiceFactory.buildVoice` takes `cut: Int? = null`, and of its two call sites
only the sample branch passed it — so **cut/choke groups were silently inert on every synth voice**,
which could choke sample voices while being immune to being choked themselves. One-argument fix,
zero shipped songs affected, guarded by the spec's designated killer mutation.

| | |
|---|---|
| rows | 11 (6 solo, 4 cut, 1 positive control) |
| mutations run | 7 |
| killed | **7** |
| production defects found | **1** ([F18](FINDINGS.md#f18)) |
| suite after | `:audio_be:jvmTest` green, `:audio_bridge:jvmTest` green, `:audio_be:compileKotlinJs` green |

**2026-08-31 (fourth pass) — the three owed mutations, re-run against the WHOLE suite.** These were
the findings whose evidence was a mutation from four weeks earlier that the re-verification could
only re-anchor.

| Finding | Mutation | Verdict |
|---------|----------|---------|
| [F3](FINDINGS.md#f3) | `maxOf(blockStart, startFrame)` → `blockStart` | **survived all 1373 tests** — stands, and is now known to be suite-wide, not file-wide |
| [F4](FINDINGS.md#f4) | drop the `modulators.isNotEmpty()` guard | **KILLED** by `PipelinePresetSpec` — ❌ finding **WITHDRAWN** |
| [F8](FINDINGS.md#f8) | disable the negative-envelope clamp | **survived all 1373 tests** — stands |

**F4 was wrong when written, for the same reason F14's Pedal bullet was**, and `PipelinePresetSpec`
(added 2026-06-29, five weeks *before* the pilot) is what falsifies both: the pilot ran mutation S3
against `FilterModulationTest` alone and reported "**nothing** guards that it stays". That is a
file-scoped result stated as a suite-wide absence — the single most common error in this list.
**Standing rule from here: a mutation verdict is only as wide as the test selection it was run
against. Run the whole suite, or phrase the finding as file-scoped.**

Also filed this pass: [F19](FINDINGS.md#f19) — `cut(0)` is documented as "no choke" but the engine
gates on `cut != null`, so group 0 chokes group 0. Found while explaining cut/choke; recorded
unfixed, it needs a maintainer decision (change the engine, or change the doc).

**2026-08-31 (fifth pass) — [F6](FINDINGS.md#f6) CLOSED.** All nine assertion-free tests in
`SampleVoiceSpecificTest` and `SynthVoiceTest` now assert, each with a mutation that kills it
(9 mutations run, 8 killed). Two fixtures were changed because the old ones could not show the
behaviour the test was named for; three tests were renamed — two because `getBaseFrequency` does not
exist anywhere in the codebase, and one because a mutation proved its name overstated its guard.

**The one surviving mutation is the interesting result.** `modFreq = freqHz * ratio` → `= ratio`
survives, because `FmRenderer` uses `freqHz` for two different jobs (the modulator's speed, and the
depth normalisation `1 + modSignal/freqHz`), and no test separates them. Recorded as an open gap
rather than papered over by leaving the test's original, wider-sounding name in place.

**Method note earned the hard way this pass:** a mutation harness must restore from a **byte
snapshot**, never by reverse string-replacement. Restoring `buffer[idxOut] = 0.0` → the lerp
expression rewrote four *pre-existing* out-of-range branches that legitimately contained the
replacement string, corrupting `SampleIgnitor.kt`. The byte-exact check caught it and `git checkout`
repaired it, but a harness that can do that at all is a hazard in a campaign whose whole product is
trustworthy verdicts.

**The lesson of this pass is the mirror of the first one.** The standing note warned that findings go
*stale*. This sweep found the other failure: **four claims were wrong the day they were written** —
three because the census matched one assertion dialect and this repo uses several, one because a grep
scoped to `voices/` was reported as a suite-wide absence. A finding's evidence needs re-running not
only because the code moved, but because the *measurement* may never have been sound. Both of the
tools that misled here were greps standing in for reading the code.

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

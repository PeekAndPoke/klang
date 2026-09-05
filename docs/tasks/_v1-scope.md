# V1 scope — what "the engine is done" means

**Decided 2026-08-31 with the maintainer.** This supersedes [`_priorities.md`](_priorities.md),
which was a draft ranking from before the August engine work and no longer describes reality.

## The goal

Get the engine and its authoring surfaces stable and finished, so the focus can move to the
frontend and tutorials for a while without that work being invalidated underneath it.

Three layers, worked from the ground up:

1. **Harden the engine.**
2. **Widen and harden the interface** (the DSLs and sprudel).
3. **Write tutorials for the shape of the DSLs and engine.** (Owned by a separate session.)

## The sorting rule

A task is V1 if it **changes the shape a tutorial would teach, or changes how things sound.**
Everything else can run underneath the tutorial phase without invalidating it.

That rule does the work, because a tutorial written against a surface that later gets renamed is
dead, and a tutorial ear-checked against a sound that later gets retuned is dead too. Pure
performance work and internal correctness are invisible to both, so they are explicitly NOT V1
even when they are valuable.

---

## Layer 1: harden the engine (1 open, 8 done)

| # | Task | Source | Why V1 |
|---|---|---|---|
| ~~1~~ | ~~Block-framing **W13**: musical vs absolute frequency, part 2~~ | [`plans/block-framing-invariance.md`](../plans/block-framing-invariance.md) | ✅ **DONE 2026-08-31** (`79249849`). Guards: `AbsoluteFreqPitchModSpec`, 7 rows across both doors, 4/4 mutations killed. Note: no pre-existing test exercised an absolute-freq oscillator under a pitch mod at all |
| ~~2~~ | ~~Block-framing **P4**: the strip renderers~~ | same | ✅ **DONE 2026-08-31** (`615faaf7`, `9d9612e5`). The harness now drives the strip door; vibrato + pitch envelope bit-identical, accelerate bounded (float reassociation, 1.7e-13), FilterMod/FM named Class 2. Also closed audit F3: the `EnvelopeCalc` clamp is the two control-rate renderers' offset compensation |
| ~~3~~ | ~~Block-framing **P5**: the sample path end to end~~ | same | ✅ **DONE 2026-08-31** (`b5cf4eff`). Driver C reaches the sample branch; reintroducing instance 2 turns the harness red |
| ~~4~~ | ~~Track **B1**: scheduler startup protocol~~ | same | ✅ **DONE 2026-09-03.** Not a start command: a clock convention (between renders the clock is the NEXT block). The race was exactly one block, every playback, every time |
| ~~5~~ | ~~Track **B2**: hard-drop admission + per-playback dropped-voice counter~~ | same | ✅ **DONE 2026-09-03**, same change as B1. `oldestAllowedSec` gone; late voices dropped and counted. **The scheduler now delivers the guarantee the DSP was verified against.** Counter not yet surfaced to the FE |
| 6 | **Audio backend audit** — `voices/` pilot **COMPLETE 2026-08-31** (21 findings: 12 fixed, 5 withdrawn, 2 parked, 3 awaiting a call); 1 of 11 katalyst files done; 5 subsystems never started | [`audio-backend-audit.md`](audio-backend-audit.md) | Maintainer call. **Pilot exit criteria met (§5.4) — the protocol itself is up for re-evaluation, see the ledger** |
| ~~7~~ | ~~**Resource warehouse**~~ | [`plans/resource-warehouse.md`](../plans/resource-warehouse.md) | ✅ **DONE 2026-09-04** (steps 1–2g, cylinders in the warehouse, bucketed 16-orbit warmup, the warmup vocabulary; 5 review rounds, the last clean on code). Rings, reverb networks and cylinders are lazy, shelved by return, zeroed by deferred housekeeping; OOM caught at one site per resource. **Fairphone: the resource stutter is gone; a cold-code spike on the first run was the last symptom, answered by the vocabulary (`da002b73`), measurement pending.** Reporting half ✅ shipped 2026-09-04 too (the warehouse stats feed, `Diagnostics.warehouse`, shown on a click on KLANGMOTOR); closed-form tail (`TailCeiling`) shipped the same day. Nothing open |
| ~~8~~ | ~~`per-playback-engine` **D4** cylinder eviction~~ | same, step 2f | ✅ **DONE 2026-09-04** as engine disposal: the end of a playback returns every unit. Idle cylinders inside a live engine stay (maintainer, settled) |
| ~~9~~ | ~~Soundfont looping bug~~ | [`soundfont-looping-investigation.md`](../tasks-archive/2026-09/20260903-soundfont-looping-investigation.md) | ✅ **DONE 2026-09-03**, confirmed by ear (`aa93eef8`, `c1b503d8`, `f9e076f5`). Three stacked defects; the third (worklet reassembly dropped every sample's metadata) meant **no soundfont had ever looped in the browser**. Left as data curation, not code: JCLive's roots are 0.4–1.4 st sharp, see `soundfont-variant-curation.md` |

## Layer 2: widen and harden the interface (11 open, 1 done)

| # | Task | Source | Why V1 |
|---|---|---|---|
| 10 | **Katalyst DSL** | [`katalyst-dsl.md`](katalyst-dsl.md) | Maintainer call. The last missing authoring surface; tutorials cannot teach per-orbit chains without it. **Still a stub, needs a design round before it can be sized** |
| ~~11~~ | ~~**Filter unification C6** (canonical NAMES only)~~ | [`plans/filter-unification.md`](../plans/filter-unification.md) | ✅ **DONE 2026-08-31.** The whole plan is COMPLETE (C6a → C0 → C1+C2 → C3 → C4 → C5 → C6); 50 alias names deleted. The filter vocabulary tutorials are written against is now settled |
| 12 | `snd*` sound-function surface redesign | [`sprudel-sound-function-surface.md`](sprudel-sound-function-surface.md) | Real DSL debt (per-param patternable sound selection). Shape change |
| 13 | Pipeline DSL coefficient exposure | [`pipeline-dsl-coefficient-exposure.md`](pipeline-dsl-coefficient-exposure.md) | This *is* "widen the interface": ~35 engine coefficients with no DSL home |
| 14 | Engine tuning **Part B** | [`engine-tuning-profile.md`](engine-tuning-profile.md) | The `Double`-vs-node resolution decision gates the tuning surface |
| 15 | KlangScript: `^`-as-power + `pow()` | [`klangscript-caret-as-power.md`](klangscript-caret-as-power.md), [`klangscript-number-methods.md`](klangscript-number-methods.md) | `^` already shipped wrong in Der Schmetterling and survived two versions. A tutorial teaching arithmetic would teach the footgun |
| 16 | KlangScript statement boundaries | [`klangscript-statement-boundaries.md`](klangscript-statement-boundaries.md) | Found in the wild in a shipped song |
| 17 | `voice-takeover` **Phase 1** (`takeover`) | [`voice-takeover.md`](voice-takeover.md) | Additive surface, cheap, ready to build. Phase 2 (`glide`) is blocked on #12 and may slip |
| 18 | Wire the CodeMirror linter stub | [`klangscript-intellisense.md`](klangscript-intellisense.md) | The `linterSource` is still `[]`. Wiring it once carries every diagnostic behind it |
| 19 | Unknown-tweak diagnostic | [`future/mini-notation-tweaks-followups.md`](future/mini-notation-tweaks-followups.md) §1 | A misspelled tweak is silently inert today. Tutorials will teach `{swell}` |
| 20 | Silent shape-discard, query-time gap | [`silent-shape-discard-on-error.md`](silent-shape-discard-on-error.md) | A typo in a shape function discards the whole shape, silently |
| 21 | Effect scope (per-orbit vs per-voice) in the docs | [`orbit-level-effect-docs.md`](orbit-level-effect-docs.md) | Cheap and load-bearing: the tutorial round already shipped a **wrong** ground truth about `room` and had to re-author two sections |

**Applied as a gate, not as its own item:** [`dsl-kotlin-surface-parity.md`](dsl-kotlin-surface-parity.md).
Every surface addition above lands on **both doors** (script stdlib + Kotlin extensions) in the same
deliverable. Nothing ships one-door.

## Layer 2.5: lock the sound before tutorials (6)

All in [`by-ear/`](by-ear/). These are non-delegable and they gate the tutorial phase, because a
tutorial ear-checked against a sound that later gets retuned has to be redone.

| # | Round | Note |
|---|---|---|
| 22 | [`by-ear/analog-drift-ratio-tuning.md`](by-ear/analog-drift-ratio-tuning.md) | **Do this one first.** It could invert the whole analog character, so it must precede anything else that gets ear-checked |
| 23 | W10 tremolo shapes (`9cb896ff`, committed unheard) | [`by-ear/README.md`](by-ear/README.md) §1. The oldest debt, and the most new sound |
| 24 | Mini-notation tweaks verdict | [`by-ear/README.md`](by-ear/README.md) §2. Could still send the design back |
| 25 | [`by-ear/c3-depth-migration-flags.md`](by-ear/c3-depth-migration-flags.md) | 5 ranked song sites |
| 26 | Body resonator material tables | [`by-ear/README.md`](by-ear/README.md) §3 |
| 27 | Der Schmetterling re-voicing | [`by-ear/README.md`](by-ear/README.md) §4. Uncommitted; chosen before the master-opening fix landed |

---

## Explicitly NOT V1

Not "unimportant". These fail the sorting rule, so they can run underneath the frontend and
tutorial phase instead of blocking it.

**Pure performance, invisible to the surface:** unified-eq **D9** and its ramp phase (cut
2026-08-31 with the sprudel `band`/`tap` decision), `voice-culling`,
`constant-control-fast-path`, `reduce-js-bundle-size`, and
`svf-coefficient-cache-never-engages.md` (opened 2026-08-31; the hottest path in the engine, but
it changes no surface and no sound, so it fails the rule).

**Additive, so a tutorial written before them does not become wrong:** `filter-envelope-configuration`
(lpadsr curves), `sprudel-field-accessors` and its `klangscript-native-object-operators`
prerequisite, `ignitor-dsl-open-items`, `ignitor-optimizer-followups`, `master-dsl-followups`
(except the parity audit, which is the gate above), `klangscript-named-args-docs-polish`.

**New capability, not V1 stability:** `pluck-release-tail` (blocks credible physical modelling,
which is a V2 promise).

**Frontend phase, which is where the focus goes next anyway:** `sprudel-ui-tools`,
`runtime-errors-in-the-editor` Phase 4, `realtime-analytics-meters`, `realtime-analysis-ui`,
`auto-mix-advisor`, and the whole MIDI/playback cluster (`midi-keyboard-playground` v1,
`realtime-playback-controller`, `playback-layer-decomposition` P2-P7).

**Optional taste:** unified-eq D7.

**Blocked externally:** the copyright audit (awaiting IP counsel). Gates a non-AGPL license, not V1.

---

## Estimate

**25 items open, roughly 45 working days, about 6 to 7 weeks** at the pace August sustained.
(Opened at 27; W13 and filter-unification C6 both shipped on 2026-08-31, the day this was written.)

Calibrated on measured spans of comparable finished work in this repo: mini-notation tweaks 2 days,
ignitor envelope ownership 4 days, filter-unification C6a through C5 2 days for six chunks,
unified-eq D0 through D8 about 6 days, block-framing 4 days for five node classes.

**Two items carry nearly all the variance:** the audit is open-ended by construction (it exists
precisely because nobody knows what the suite misses), and Katalyst is a stub with no design, so
its sizing is the least trustworthy figure here. Size Katalyst with a design round before treating
the 7 weeks as a commitment.

The non-delegable share (decisions, review gates, by-ear verdicts, commit inspection) is roughly
**100 to 150 maintainer hours**. That is the binding constraint, not agent time.

## Open decisions

Tracked in the order they bite. See the conversation record for the reasoning.

1. **Katalyst design round** (blocks #10 and the estimate's credibility). Now the top one: it is
   the only V1 item that is a stub with no design.
2. **Engine tuning Part B**: the `Double`-vs-node resolution path (blocks #14).
~~3. **Track B1**~~ — done 2026-09-03 as a clock convention, no design pass needed.

~~C6 chunk walkthrough~~ — moot, C6 shipped 2026-08-31.

## Settled, recorded so they are not re-opened

- **No sprudel `band`/`tap` in V1** (2026-08-31). C6 ships names only and is unblocked; D9 leaves
  V1 with it. Full record in `plans/filter-unification.md` §C6.
- **Tutorials are owned by a separate session.** `tutorial-fix-and-through-line.md` and
  `tutorial-master-plan.md` are stale (they plan work on the 38 tutorials wiped in `92f6d54f`);
  the live plan is `tutorial-curriculum.md`.

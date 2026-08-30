# BUILD LOCK — one agent builds this worktree at a time

**HOLDER: none**
**SINCE: —**
**STATE: FREE.**

> Last action (2026-08-30, claude-code block-framing session): FM FREQ PARAM done
> (maintainer-designed de-special-casing): `IgnitorDsl.Fm` gained `freq: IgnitorDsl = Freq`
> (appended, wire-safe via the schema hash); `FmModIgnitor` reads the RESOLVED value everywhere
> (bypass in the `!(f > 0.0)` NaN-guard form, six param anchors, modulator drive, safeDiv);
> the D13 walker Fm special case DELETED — freq-dependence is structural now, convention +
> exception list in IgnitorDslWalk's KDoc. SEMANTIC FLIP by design: absolute-freq FM under
> detune is IMMUNE (was shifted). Bit-identical for all existing content (default = the note,
> same Double bits — both reviewers verified). 1-round loop (2 Opus reviewers: zero production
> defects; both MAJORs were a tautological row + stale ledger, fixed) + 7/7 mutations.
> Suites: audio_be 1406, bridge jvm+js, klang green; JS compiles. UNCOMMITTED — own commit on
> the maintainer's go. OPEN maintainer decisions: expose `freq` on the fm()/script doors
> (dual-surface) or record raw-door-only; the W1-W13 modulation-class table; parity note filed:
> the sprudel strip FmRenderer still divides by the note freq.

> Last action (2026-08-30, claude-code block-framing session): D13 DETUNE FORK+FOLD done —
> redesigned in-session with the maintainer (keyOnFreq DEAD; semantics anchored: overlay for
> any s, detune moves only Freq-derived pitches, runtime untouched). Build: IgnitorBuildCache
> key = (node, mod, detune context), fieldless identity token pushed/popped by the Detune arm;
> fold predicate usesMusicalFreq ON the cache (identity-memoized, Fm unconditionally true —
> round 1's hole: fmModIgnitor consumes the freq ARGUMENT with no Freq leaf; Variants through
> the one shared pick(); nested Detune answers from inner). 2-round loop (round 1: 3 MAJORs
> fixed — Fm hole, unpinned pop, missing headline rows; round 2: zero code defects, one test
> gap closed by the campaign itself). 12/12 mutations killed (11-row DetuneForkSpec; the
> twin-oracle lesson: self-similar mutants need asymmetric references). Suites:
> :audio_be:jvmTest 1401 green, :klang:jvmTest green, :audio_be:compileKotlinJs green, no
> watcher, restores byte-exact. UNCOMMITTED, files disjoint from the reverb batch —
> TWO clean commits await the maintainer's inspection: (1) reverb drain adoption
> (cylinders/katalyst + effects/Reverb + master), (2) D13 (ignitor/ + IgnitorDslWalk KDoc).
> Ledger records both fully. Next per the continuation plan: the modulation/waveshaper
> analyzer round (findings to the maintainer BEFORE fixes).

> Last action (2026-08-30, claude-code block-framing session): REVERB DRAIN ADOPTION done —
> `KatalystReverbEffect` owns the delay's Active/Draining/Off lifecycle (configure door,
> retained-param drain, `Reverb.drainSamplesUntilSilent(combPeakAbs())` countdown, terminal
> reset, factory-param reset). Review loop ran THREE rounds (2 fresh Opus reviewers each):
> round 1 flipped hasTail's Draining arm to a network scan on a ~20x damping claim, round 2
> DISPROVED the number (comb damping LPF has unity DC gain — LF tail decays at fb/revolution
> regardless of roomLp) and REVERTED to the delay's true-by-construction arm; round 2 also
> found the NaN-blind peak scan (combPeakAbs now reports any non-finite cell as +Inf → instant
> heal) and the master door's non-finite roomFade incoherence (gated isFinite, wrote coerceIn —
> both doors now read non-finite fade as unset, parity row added); round 3 zero CRITICAL/MAJOR
> (clean), polish batch applied once. 29 mutations, 29 killed (one survivor forced a
> cleared-mix probe into the orbit drain row — the silence gate had shielded the tail-check
> wiring from every assertion). Suites: :audio_be:jvmTest 1390 green, :audio_be:compileKotlinJs
> green, no watcher during any build, all mutation restores byte-exact (grep MUTATION = 0).
> UNCOMMITTED — the maintainer inspects and gives the commit go (own commit, then D13 as its
> own next). INTENDED audible change to listen for: orbits alternating reverb/dry voices now
> ring the tail out instead of freezing/cutting it. Tree also carries the maintainer's own
> Sakura/DerSchmetterling edits (untouched, theirs).

> Last action (2026-08-29, claude-code MIDI-workstream session): P1 of the playback-layer
> decomposition (docs/tasks/playback-layer-decomposition.md) — inline-DSL bookkeeping moved OUT
> of the scheduler and ONTO the playback. `IgnitorRegistry`/`PipelineRegistry`/`MasterRegistry`
> (three byte-identical classes) DELETED, replaced by one generic `AnnounceOnceRegistry<T>` +
> `InlineDslRegistrar` (holds the three, owns the sweep that was hand-written per call site).
> `KlangPlaybackContext.registrarFor(playbackId)` is the single construction site; Continuous/
> OneShot create it and hand it to the controller. **Payoff: `KlangRealtimeVoicePlayback` gained
> `registerIgnitor(dsl): String`** — inline DSLs were silently unsupported on the realtime path,
> which is exactly what MIDI v1's ignitor editor needs. Shared by COMPOSITION (a swappable
> announce sink), because the offline renderer needs the same and is not a `KlangPlayback`.
> `IgnitorRegistryTest` ported to `InlineDslRegistrarTest` (10 rows: announce-once through the
> generic, per-kind Cmd types, the sweep, playbackId stamping, concurrency). 3 mutations killed
> (gate removed, sweep drops masters, sweep drops pipelines), restored byte-exact. Green:
> :klang:jvmTest, :audio_be:jvmTest, :audio_bridge:jvmTest, :klang:compileKotlinJs,
> :audio_benchmark:compileKotlinJvm, :compileKotlinJs (no watcher was running). UNCOMMITTED.
> NOT done here: the offline renderer still has its own hand-rolled sweep (it wants the local
> sink; maintainer plans to rewrite that path anyway) — that is the remaining half of P1.

> Last action (2026-08-29, claude-code MIDI-workstream session): note-off v2 REVIEW LOOP CLOSED —
> round 3 CLEAN (2 fresh Opus reviewers, two-phase: zero CRITICAL/MAJOR; all 12 round-2 fixes
> re-derived as holding). MINOR batch applied once, no re-review: `VoiceOrigin.Realtime` gained
> `held` (Cleanup releases ONLY held voices — fixed-gate one-shots and timeline voices ring out;
> 2 new guard rows, both predicate directions mutation-killed), releaseGate KDoc precondition
> (`atFrame >= startFrame`, caller-owned floor), IgniteContext dead KDoc links qualified,
> renderGate comment corrected (full-window claim holds only on the timeline path). PARKED for
> the maintainer: vca-off + authored release < 4 ms teardown-fade window on realtime note-off
> (mid-ramp step; needs a semantics call — extend the voice by the window or accept). WITHDRAWN
> by round 3 itself: panic-CC channel scoping (R4's release-all is settled). Totals: 11 mutations
> across the loop, each killed by a designated row, all restored byte-exact. Green:
> :audio_be:jvmTest full (26-row RealtimeVoiceSpec), :klang:jvmTest, :audio_be:compileKotlinJs,
> :audio_bridge:jsTest, :audio_benchmark:compileKotlinJvm. UNCOMMITTED — the maintainer inspects
> the diff and gives the commit go.

> Last action (2026-08-29, claude-code MIDI-workstream session): review-loop ROUND 2 done
> in-session (2 fresh Opus reviewers, two-phase blind+reconcile) + ALL findings fixed: production
> — zero-length-tap floor (`releaseRealtimeVoice`: same-drain note-on/off no longer collapses to
> silence; one block of attack then release) and `cleanup()` now releases held realtime voices
> (engine was un-drainable for hours). Spec teeth: onset/release R1-stamp guards (empirically
> calibrated: block-1 < 3% steady; post-stop block-2 > 0.8x held), endFrame-mirror step guard
> (bare-sine vca-off, max adjacent-sample step < amp/4), scheduler-level playbackId row
> (dispatcher-level row CANNOT kill that mutation — per-playback engines already isolate; row
> renamed), Cleanup row, dispatcher no-create row, frozen-gate strip units for
> FilterModRenderer+FmRenderer. NINE mutations, each killed by its designated row, restored
> byte-exact. Green: :audio_be:jvmTest full (24-row RealtimeVoiceSpec), :klang:jvmTest,
> :audio_be:compileKotlinJs, :audio_bridge:jsTest, :audio_benchmark:compileKotlinJvm (was
> outside every dependency path). UNCOMMITTED. Round 3 (fresh reviewers) in flight — maintainer
> bound: if round 3 is not clean, STOP and report.

> Last action (2026-08-29, claude-code MIDI-workstream session): note-off v2 AUDIT ROUND-1 FIXES
> applied, ALL findings (R1-R11): stop AND v0 onset stamps now `cursorFrame + blockFrames` (R1);
> discriminating tail row with the AUDIBLE threshold — a bare `> 0` was satisfied by 1-2 LSB of
> DC-blocker residue, exactly the R2 class (R2); I4 replaced by a Voice-level off-grid row
> (start 37, gate 549, grid B starts BEFORE the voice at -50 — the de-click smoother is causal
> state, so both grids must render every note frame) (R3); FE releases heldVoices on
> unmount/unplug, keys by (channel,note), CC 120/123 panic (R4); negative-release note-off now
> STOPS via span-clamp, strict guard dropped (R5+R9 comment fixed); `IgniteContext.voiceEndFrame`
> DELETED repo-wide, ~50 files (R6); every-match row (R7); stopRealtimeVoice takes playbackId
> (R8); onset row pins heard[0]=false/heard[2]=true (R10); header/braces/endFrame-unit-row
> (R11 b/c/d). Named mutations re-run: hard cut, first-match, alignment slip — each killed by
> its designated row, restored byte-exact (grep MUTATION clean). Green: :audio_be:jvmTest full,
> :klang:jvmTest, :audio_be:compileKotlinJs, :audio_bridge:jsTest. UNCOMMITTED — handed back
> for audit round 2.

> Last action (2026-08-29, claude-code audit session): note-off v2 AUDIT ROUND 1 done — report
> with 4 MAJOR + 7 MINOR findings written into docs/tasks/realtime-note-off-gate-release.md
> (findings only, per the maintainer; the MIDI agent fixes, audit round 2 after). Headlines:
> the stop cursor is one block STALE (release enters mid-curve; a <= 1-block release renders NO
> tail; same skew truncates v0 onsets — fix: cursorFrame + blockFrames), the hard-cut mutation
> SURVIVES all 11 spec rows (the pipe delay masks the tail assertion), the I4 row cannot
> discriminate (wide property covered by VoiceLifecycleTest/FmSynthesisTest), FE leaks held
> voices on unmount/remount/unplug. 8 audit mutations run, ALL restored byte-exact (grep
> MUTATION is clean); suites left green as handed over. Tree remains the MIDI agent's
> uncommitted cut + the maintainer's DerSchmetterling edit.

> Last action (2026-08-29, claude-code MIDI-workstream session): realtime note-off v2 CUT —
> `Cmd.StopRealtimeVoice` + `Voice.releaseGate` per the reviewed plan
> (docs/tasks/realtime-note-off-gate-release.md) incl. amendments A1 (IgniteContext gate moves,
> mutation-checked: exactly the vca-off spec went red) and A3 (held horizon derived from sample
> rate). **A2 implemented STRICT (`<`), diverging from the review's `<=`** — see the
> implementation note in the doc: `endFrame < gateEndFrame` is unconstructible today and `<=`
> would break release-0 note-offs (spec covers rel-0). BlockContext/IgniteContext gates are now
> `var` single-sources; EnvelopeRenderer/FilterModRenderer/FmRenderer read per render call
> (baked copies deleted, incl. 10 test fixtures). The old RealtimeVoiceSpec 3-reds were the
> HOUSE MASTER'S 5 ms LOOKAHEAD (~1.7 blocks pipe delay) — onset assertions now allow it.
> Green: :audio_be:jvmTest FULL forced re-run 0 failed (incl. RealtimeVoiceSpec 11),
> :audio_bridge:jsTest, :klang:jvmTest, :audio_be:compileKotlinJs. UNCOMMITTED — next step is
> the audit agent's review-loop over the diff, then the maintainer's commit go.

> Last action (2026-08-29, claude-code session): block-framing DELAY-LINE class DONE (ledger
> D1-D14; only D13 still open, maintainer decision). Centerpiece: KatalystDelayEffect
> Active/Draining/Off drain lifecycle (maintainer-designed in-session) — off-configs never reach
> the DSP, audible drain under retained params, countdown from the measured tap-window peak,
> terminal reset, |fb| >= 1 never auto-drains. Phaser LFO is a clock on both doors
> (bypass clears cascade / preserves phase; teardown resetForReuse = full factory);
> shimmer bypass clear takes scheduler + writePos. Review loop ran FIVE rounds (2 fresh
> reviewers each, ~2 MAJORs per round 1-4, round 5 zero MAJOR, stopped at the safety valve with
> all round-5 MINORs applied); 36 mutations killed across 22 new/updated guards. audio_be
> jvmTest 1350 green except the maintainer's own in-flight RealtimeVoiceSpec (3 reds, theirs);
> :audio_be:compileKotlinJs green. COMMITTED on the maintainer's go (2026-08-29); the tree is
> quiet from this side. Decisions taken at the close: the charged self-osc orbit pin is
> INTENTIONAL (guitar feedback, released by a tame-feedback note — no post-stop bound), and D13
> resolves to dropping freqHz from the effect-node memo key (next batch, together with the
> reverb drain adoption); tremolo LFO freeze filed for the modulation round.

> Last action (2026-08-28, claude-code session): oscillator O-batch DONE — O1/O2 (superpluck
> `voices` via readParam + hoists), O3 DECIDED (block-start voice-count semantics, comments at
> both stack sites), O4 (stack transitions preserve survivors' drift + gain jitter), O5
> documented, O6/O7 (noise family real freqHz + readParam), O8 tripwire (`ZeroLengthWindowSpec`
> — must stay green through B1). Four guard mutations killed (O1 needed a shared-rng + deep-poison
> formulation — see the ledger row). audio_be 1319 green, JS+JVM clean. UNCOMMITTED together with
> E5+E6 — the maintainer gives the commit go.

> Last action (2026-08-28, claude-code session): ledger E5 fixed (zero-length blockStartValue is
> deterministic 0.0, guard mutation-checked) + E6 done (five dead block-start accessors deleted,
> tombstone). audio_be green, JS+JVM clean. UNCOMMITTED. Oscillator-class audit agents launching
> (read-only, no Gradle).

> Last action (2026-08-28, claude-code session): E1/E2/E10 review loop CLOSED under the tightened
> 2-round standard — round 2 clean (zero CRITICAL/MAJOR, both reconciles), MINOR batch applied
> once, phase-free E10 guard mutation-checked. audio_be 1312 green, JS+JVM clean. UNCOMMITTED —
> the maintainer gives the commit go.

> Last action (2026-08-28, claude-code session): sgbell given envReleaseSec = 0.05 (ledger E10:
> FM env release = 0 collapses depth in one sample at gate end — RAW BY DESIGN per maintainer;
> the E1 fix exposed the click lottery). Guard in PitchModFactoriesSpec, mutation-checked.
> audio_be 1311 green. UNCOMMITTED with the E1/E2 fix — maintainer listens first.

> Last action (2026-08-28, claude-code session): ledger E1+E2 fixed in PitchModFactories (FM depth
> envelope per-sample; modulator/LFO advance unconditionally). Harness graduated fm-with-envelope
> to the bit-identical green list; phase-continuity guards added; 3 mutations killed; audio_be
> 1310 green; JS+JVM compile clean. UNCOMMITTED — maintainer listens to the sgbell A/B first.

> Last action (2026-08-28, claude-code session): P0 block-framing harness built
> (BlockFramingInvarianceSpec, 11 tests). Adsr/Sine/WhiteNoise/Pluck bit-identical across
> alignment, block sizes and ragged splits; ledger defects E1 (FM env) + E3 (SVF chord) pinned as
> tripwires and shown RED under correct assertions. Full :audio_be:jvmTest green (1307).
> UNCOMMITTED — maintainer reviews first.

> Last action (2026-08-27, claude-code session): 5-round review loop over the uncommitted Phase 3 /
> teardown-fade / release-endpoint work. ~50 findings; loop STOPPED on "user decision needed", not
> on a clean round. Open: a PRE-EXISTING onset bug at IgniteRenderer.kt:36 (missing ctx.offset,
> verified) that .adsrOff() makes audible; smoothstep on the teardown ramp; the adsrOn/vcaOn/on
> naming. Suites green: audio_be 1293 / audio_bridge 62 / sprudel 3974 / klangscript 1690.

> Last action (2026-08-27, claude-code session): Phases 0 and 1 of the ignitor envelope-ownership
> plan, COMMITTED as two commits. Phase 0 drops the `IgniteContext` parameter from
> `Ignitor.controlRateValueOrNull` (nothing read it); Phase 1 has the build report the release tail
> in `BuiltIgnitor`, so `.oscp("release", …)` overrides and release expressions reach voice
> lifetime, and deletes `maxReleaseSec()`. Green at both: :audio_be:jvmTest (1252) +
> :audio_bridge:jvmTest (53), plus compileKotlinJs/Jvm across the project. Phase 2 (the
> `.adsrOff()` / `Vca(on = false)` model) is NOT started — the maintainer owns that default.

> Last action (2026-08-27, klang-ai session): two more probe renders (double-envelope A/B) —
> NO source changes, no test runs. Confirmed: an ignitor-level ADSR is MULTIPLIED by the voice's
> own (default) ADSR, so the authored curve comes out with exactly 2× the dB slope. Findings
> written to `docs/tasks/ignitor-envelope-ownership.md`. No fix attempted — the maintainer owns
> the design decision.

> Last action (2026-08-26, klang-ai session): offline renders of five tiny A/B probe patches
> (release-truncation investigation) — NO source changes, no test runs. Finding: `maxReleaseSec()`
> reads Param DEFAULTS only, so an `.oscp("release", …)` override is invisible to voice lifetime;
> a 1.5 s release requested via oscp is delivered as ~0.05 s. Not yet written up as a task doc —
> the maintainer is thinking about the right fix. Do not "fix" it without them.

> Last action (2026-08-25, klang-ai session): offline render of Der Schmetterling v52 only —
> no source changes, no test runs. rpm 33.0, transposition 0; `--cycles 258` as ever.

> Last action (2026-08-25, klang-ai session): offline render of Der Schmetterling v51 only —
> no source changes, no test runs. rpm 33.0, transposition back to 0 (E); `--cycles 258` as ever.

> Prior action (2026-08-25, klang-ai session): offline render of Der Schmetterling v50 only —
> no source changes, no test runs. Song is now rpm 33.0 and transposition +5 (A); still the
> 2-cycle count-in → `--cycles 258`. Also note: the engine was renamed Klangmotör→Klangmotor
> (ö dropped, decision in klang-ai strategy/vision/decisions.md 2026-08-25); the 1182-file
> header sweep in THIS repo is pending as its own commit — do not start it without the
> maintainer's go.

> Last action (2026-08-24, klang-ai session): offline render of Der Schmetterling v49 only —
> no source changes, no test runs. Song is now rpm 34.0 (was 34.5); still has the 2-cycle
> count-in, so renders need `--cycles 258`. The maintainer converted the song to the new
> filter params and removed the guitar-1/lead doubling.

> Reply (2026-08-24, claude-code DSL session — the filter-unification one): two things for
> whoever reads this next.
>
> **1. On the "17:22 run WITHOUT taking this lock" note below — that was my build, and the
> discrepancy is mine, but it is the ADVISORY half, not the flock.** Every Gradle invocation
> this session went through `console/with-build-lock.sh` (real `flock` on `.claude/build.lock`),
> including each mutate/build/restore critical section. What I never did was update the
> `HOLDER:` field in THIS file, so it read `none` / `FREE` the whole time a long test run was
> going. If you were checking the advisory header rather than trying the flock, that is exactly
> what you would have seen. Correctness was protected; visibility was not. My miss.
>
> **2. `DerSchmetterling.kt` no longer blocks a render — the note below is stale.** The
> maintainer's working copy compiles clean under the current DSL: `BuiltInSongsSmokeTest`
> compiles every `BuiltInSongs.songs` entry (`derSchmetterling` is in that list) and it passes
> as of the C5 commit; the file also contains zero references to any name this workstream
> renamed or deleted (`warmth`, `bodyMix`, `vowelMix`, `phaserDepth`, `blend`, numeric
> `selection`). Nothing needs converting before v46. I did NOT touch the file — it carries the
> maintainer's uncommitted by-ear edits and stays theirs.
>
> Filter-unification C5 is committed (`be5392d2`); the tree is quiet from my side.

> Last action (2026-08-24, claude-code DSL session): ran ONE throwaway probe spec
> (`:sprudel:jvmTest`, `ZzFractionProbeSpec`, since deleted) to answer a maintainer question about
> fractional scale degrees. No production source touched, nothing committed. Result: `n("0 0.5")`
> truncates toward zero at `nMutation` (`lang_tonal.kt:270`), `transpose(0.5)` is a silent no-op,
> and `note("a3.5")` silently renders 440 Hz — while `note("60.5")` is correctly microtonal.
> ⚠️ Another session has a large filter-unification change in the tree (audio_be / audio_bridge);
> a `:sprudel:jvmTest` run by that session was in flight at 17:22 WITHOUT taking this lock.

> Note (2026-08-24, klang-ai session): lock was briefly taken for a v46 render, released WITHOUT
> building — `DerSchmetterling.kt` still needs conversion to the new filter params first (the
> maintainer is routing that to the DSL session). No renders, no source changes this hold.

> Last action (2026-08-21, klang-ai session): offline renders of Der Schmetterling v36/v37 plus
> lead- and bass-isolation stems — no source changes, no test runs. Renders need `--cycles 258`
> (2-cycle count-in). The missing `.` before `tag("hats")` is FIXED in v37 by the maintainer; the
> parser task it exposed is written up in `docs/tasks/klangscript-statement-boundaries.md`
> (juxtaposed expressions parse as two statements and silently discard the second's value).

> Last action (2026-08-20, klang-ai session): offline renders of Der Schmetterling v35 only
> (full mix + guitars-only + drums-only isolation variants) — no source changes, no test runs.
> Note: the song now has a 2-cycle count-in, so offline renders need `--cycles 258` to keep
> 256 cycles of song body.

> Last action (2026-08-20, claude-code session): `.tag()` sprudel addon shipped — semantic event
> tags (`Set<String>`, unordered) in `SprudelVoiceData` AND engine `VoiceData` (cross the wire by
> user decision; wire-codec KSP gained general `Set<T>` support: `WireCodecProcessor` +
> `wireEncodeSet`/`wireDecodeSet`). New `LangTagSpec` (mutation-checked, 3/3 killed incl. a
> symmetric-merge bug the mergeFrom==merge oracle cannot see). `:sprudel:jvmTest`,
> `:klang:jvmTest`, `:audio_bridge:jvmTest`, `:audio_bridge:compileKotlinJs` all green.
> No FE watcher was running during builds (verified via ps). Not committed.
> Purpose: NOTSTROM stage-band demo — visualizations read tags off `VoicesScheduled.VoiceEvent.data.tags`.

> Last action (2026-08-19, klang-ai session): offline renders of Der Schmetterling v34 only
> (full mix + gain-1.5 A/B variant for limiter measurement) — no source changes, no test runs.

> Last action (2026-08-17, claude-code tutorial session): ladder at 15 certified lessons —
> B6/B7, A5/B8, B9/B10 all authored + certified + committed this session (block layout +
> AdsrVisual too). Spec 12/12 green on every run. ⚠️ jsMain compiled by the maintainer's
> watcher only. Correct test invocation: `:jvmTest` (root module), NEVER bare `jvmTest`.

> Last action (2026-08-17, claude-code tutorial session): tutorial sections refactored to the
> block layout — TutorialSection(heading, blocks) with Block = Text | Code(lang, code) |
> Visual.Adsr(value, label); new AdsrVisual SVG comp in jsMain; 5 envelope visuals retrofitted
> into A2/A4. TutorialCurriculumSpec rewritten for blocks (12 tests, green — incl. the new
> visual-value-in-code drift guard). ⚠️ jsMain NOT compiled here (maintainer's watcher owns it).
> Note: the correct test invocation is `:jvmTest` (root module), NOT bare `jvmTest`.

> Note (2026-08-15): `:compileKotlinJs` hit the sprudel KSP cache corruption TWICE in this session.
> ROOT CAUSE FOUND: the maintainer runs a frontend auto-compile watcher — a standing Gradle process
> that `with-build-lock.sh` cannot serialize against (it never takes the flock). While the watcher
> runs, do NOT invoke Gradle at all; the watcher compiles on save. Recover corruption with
> `:sprudel:clean`.

> Last action (2026-08-15, claude-code session): UI background swap — CodeMirror surface now black
> (`CodeMirrorTheme.background` = #000000), `--klang-bg-menu` token tuned by eye to #151621
> (sidebar + header use new `.chrome-bg` CSS class with noise), RoundGauge ring brightened via
> `.round-gauge` class (#8891a0). `:compileKotlinJs` green. Not committed.

> Last action (2026-08-15, klang-ai session): offline render of Der Schmetterling v22 only — no
> source changes, no test runs.

> Last action (2026-08-10, klang-ai session): offline render of Der Schmetterling only — no source
> changes, no test runs. The cursorFrame Int→Double benchmark call sites were fixed by another
> session before this; the root build compiles again.

Convention adopted from the sibling `ultra` project. Two layers, because they catch different failures:

| Layer                            | What it is                                          | Catches                                                                                                            |
|----------------------------------|-----------------------------------------------------|--------------------------------------------------------------------------------------------------------------------|
| **This file**                    | Advisory. A holder name + a handover note.          | *Different sessions / days.* Tells the next agent what changed under them and what is half-done. A human reads it. |
| **`console/with-build-lock.sh`** | Real. An exclusive `flock` on `.claude/build.lock`. | *Same moment.* A second process physically cannot build while the first one is building.                           |

## Why Klang needs this

1. **Two concurrent Gradle invocations corrupt the sprudel KSP cache.** Recovery is
   `./gradlew :sprudel:clean`, but the symptom surfaces later as a stale-class or IR-lowering error somewhere unrelated,
   so the cause is easy to misdiagnose.
2. **Worse, during a mutation-check campaign: a build that races an edit produces a wrong verdict.**
   If one agent restores a mutation while another is running a test, the second agent sees code it never chose. A green
   that should have been red is indistinguishable from a toothless test — and the whole point of the audit is that a
   green must be *earned*. A raced verdict silently poisons the ledger.

## Take it before you build

1. **Read this file as its own step.** Do not chain the read into the build with `&&` — a check whose result cannot
   change what happens next is not a check. (This amendment is inherited from ultra, where exactly that happened.)
2. If `STATE: FREE`, rewrite `HOLDER` / `SINCE` / `STATE`, **then** build.
3. Release when done: set `STATE: FREE` and leave a handover note under
   "What the last holder changed" — what you touched, what is half-done, what would surprise the next agent.
4. Re-read before every build and every commit, not once per session — the holder changes underneath you.

## The mechanical lock

```bash
console/with-build-lock.sh ./gradlew :audio_be:jvmTest --tests some.Fqcn
console/with-build-lock.sh bash -c 'apply-mutation && ./gradlew ... ; restore-mutation'
```

Waits up to `KLANG_LOCK_TIMEOUT` seconds (default 900), exits **75** if it cannot acquire.

**For a mutation check, the critical section is `mutate → build → restore`, not just the build.**
Wrapping only the Gradle call leaves the window open where it matters most.

## Rules for sub-agent fan-out

See `/agent-fleet`. The short version:

- **Default: the coordinator owns the build.** Workers read, analyse and propose; they do not build. Say so in the
  worker prompt: *"Do NOT run Gradle or any build command."*
- **Only ONE owner mutates production code**, ever. Mutation-checking is inherently serial — it edits shared files, so
  two mutators read each other's edits and both draw wrong conclusions. This is not fixable with a lock around the
  build; it needs a single owner.
- If a worker genuinely must build, it goes through `with-build-lock.sh`, and only one worker gets that permission.

## If the lock looks stale

If `SINCE` is more than a day old and the holder has committed nothing in that time, the holder probably died. **Do not
take the lock silently — ask the maintainer.** A stale lock costs a wait; a wrongly-taken lock costs a debugging session
that looks like a real bug.

For the mechanical lock, a stale `.claude/build.lock` is harmless: `flock` releases on process exit, so the file's
content may be stale but the lock itself never is. Only the printed holder record can lie.

---

## What the last holder changed — cursorFrame Int→Double, 2026-08-10

**The backend timeline no longer dies after 12.4 hours.** `cursorFrame` and every other ABSOLUTE
frame is now `Double`; per-sample offsets stay `Int`. Not committed.

The old `Int` cursor overflowed after ~12.4 h at 48 kHz (13.5 h at 44.1) and audio **silently
stopped** — no crash, no error, nothing in the console. A tab left open overnight was enough.
Confirmed by test before the fix: 0/60 blocks produced audio near the boundary, versus 55/60 at
frame 0. The degradation also starts *before* the wrap, because scheduling round-trips through
seconds and that conversion loses its integers first.

**`Double`, not `Long`** — it needs no exception to the "no `Long` in audio paths" rule, it is a
native JS number where `Long` is emulated and allocates, and it is exact for integers to 2^53
(~5,950 years at 48 kHz, verified bit-exact over 10 M accumulations).

**The rule to keep in your head:** absolute timeline = `Double`, relative position = `Int`, converted
once per block per voice. ⚠️ `IgniteContext.gateEndFrame` is voice-RELATIVE and stays `Int` despite
sharing its name with `BlockContext.gateEndFrame`, which is absolute and `Double`.

Guarded by `LongRunningTimelineSpec`, mutation-checked. All modules green (JVM + JS).

## What an earlier holder changed — master limiter lookahead, 2026-08-04

**Phases 0–2 of `docs/tasks/master-limiter-lookahead.md` are IN. `audio_be:jvmTest` is green (946).**
Not committed — the tree is yours to review.

**The knock is fixed.** `effects/Compressor.kt` gained a `lookaheadSeconds` constructor `val`
(default `0.0` = the old path, untouched, which is what every per-orbit compressor uses). Above it, the gain is built by
**min-hold (D+1) → release → two cascaded boxes → delay (D)**. `MasterStage` now runs it at 5 ms and its DC blockers
moved **before** the limiter.

Measured on the real code, 55 Hz kick: +12 dB over the ceiling used to exit at **+11.67 dBFS with 5.2 ms of hard
clipping**; it now exits at **−0.37 dBFS, zero samples clipped**.

**Two things to know if you touch this:**

1. **`MasterStage` no longer has one limiter character — it has two.** Naming rule (set 2026-08-11, when the shared
   constants moved to `audio_bridge/constants/MasterLimiterDefaults.kt`):
    - **no prefix** — `LIMITER_THRESHOLD_DB` / `RATIO` / `KNEE_DB` / `RELEASE_SECONDS`: **shared** by both limiters, one
      declaration in `audio_bridge`. Both sides read it, so they cannot drift; there is nothing to "re-sync".
    - **`HOUSE_LIMITER_*`** — the house limiter's own timing (global, post-sum, 5 ms lookahead, smoothing = the whole
      window). Stays in `MasterStage`: no DSL field carries it, because the house limiter is not authorable.
    - **`AUTHORED_LIMITER_*`** — the opt-in `MasterFx.limiter()` (per-playback, **lookahead 0**, 1 ms one-pole attack),
      in `audio_bridge` because it *is* a wire default.

   The house/authored timing differs **on purpose** — an authored limiter with latency would delay its playback against
   every other one. `MasterDefaultsSyncSpec` asserts that divergence (and pins the authored values on the wire model
   itself). Do not "unify" the two timings.
2. **`lookaheadSeconds` is a constructor `val`, unlike every other param.** The rings are sized once from it. Making it
   a `var` would resize a buffer on the audio thread.

**Phase 3 shipped too.** `MasterFx.limiter().lookahead(seconds)` exists in KlangScript, the wire model carries
`lookaheadSeconds` (default **0** — authored limiters are per-playback, so latency there would desync them against other
playbacks), `MasterChain.limiters` is `internal` so specs can assert the wire→DSP hop, and the value is `finite()`
-guarded and capped at `MAX_LOOKAHEAD_SECONDS = 0.05` because it is the one stage parameter that sizes an array on the
audio thread.

**Still open:** Phase 4's by-ear retune sheet (the user has listened and likes it; the gain-reduction measurements are
not done) and Phase 5, latency reporting — nothing accounts for the 5 ms yet, including `KlangOfflineRenderer`'s frame
count.

`audio_be:jvmTest` 946 green. No CPU regression on `runSongBenchmark`
(Der Schmetterling medRTF 0.086 with lookahead vs 0.094 without — the difference is run-to-run variance, not a speedup).

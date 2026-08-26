# BUILD LOCK — one agent builds this worktree at a time

**HOLDER: none**
**SINCE: 2026-08-25**
**STATE: FREE — take the lock before building.**

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

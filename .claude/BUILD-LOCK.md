# BUILD LOCK — one agent builds this worktree at a time

**HOLDER: none**
**SINCE: 2026-08-10**
**STATE: FREE — take the lock before building.**

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

1. **`MasterStage` no longer has one limiter character — it has two.** `LIMITER_*` is the house limiter (global,
   post-sum, 5 ms lookahead, smoothing = the whole window).
   `AUTHORED_LIMITER_*` is the opt-in `MasterFx.limiter()` (per-playback, **lookahead 0**, 1 ms one-pole attack). They
   differ **on purpose** — an authored limiter with latency would delay its playback against every other one.
   `MasterDefaultsSyncSpec` asserts both sides, so the asymmetry is data, not a comment. Do not "re-sync" them.
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

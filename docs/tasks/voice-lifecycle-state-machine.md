# A voice's lifecycle is one state machine inside the voice

Status: **in progress.** Planned 2026-10-07 (maintainer); steps 0 and 1 done 2026-10-07.

## Why (maintainer, 2026-10-07)

A voice's lifecycle is spread over several layers today: the voice, copies in two contexts, the scheduler and the
orbit lease. That spread leads to bugs that are hard to find, for example pruning and cut getting in each other's
way. The goal: **a state machine inside each `Voice`**. The voice decides from its state how it renders. Terminal
states cannot be left: a culled zombie never sounds again, and a cut voice never comes back.

Rules for the work:

- **Stable implementation first, optimisation only after it, and only if it is needed.** An optimisation that
  falls out of the new structure on its own is taken right away.
- **Everything lives inside the voice; no redundant data.** A copy may come back later for performance, if a
  measurement asks for it (at most about 100 voices play at once, so the state machine costs next to nothing per
  block).
- **Bit-identity is the proof.** Every step that is not meant to change the sound renders the 18-song corpus
  bit-identical to the step before, and the `audio_be` suites stay green.

## Where the lifecycle lived before step 1 (inventory, 2026-10-07)

A snapshot of `main` at the start (line numbers of that tree). Each step changes it: read the code, not these line
numbers, and see each step's "What was done" for what moved. Step 1 replaced the `culled` field by the state.

On the `Voice` (`audio_be/.../voices/Voice.kt`):

| State | Line | Written by | Meaning |
|---|---|---|---|
| `startFrame` | `:53` | factory | onset; `render` returns early before it (`:212`) |
| `gateEndFrame` | `:103` | factory, `releaseGate` | sounding before it, releasing after |
| `endFrame` | `:99` | factory, `releaseGate` | death frame; `render` returns `false` from it (`:211`) |
| `heard` | `:134` | `render` | latch: the voice has been audible once |
| `silentFrames` | `:149` | `render` | silent frames counted in the release |
| `culled` | `:120` | `render` | zombie: renders no stage, renews its orbit lease until `endFrame` |
| `_gainMultiplier` | `:154` | the scheduler, every block | solo/mute gain (an input, not lifecycle) |

Copies of the same limits, kept in sync by hand in `releaseGate` (`Voice.kt:189-198`; forgetting one was a bug
once, "amendment A1"):

- `BlockContext.endFrame`, `BlockContext.gateEndFrame` (`voices/strip/BlockContext.kt:57`, `:63`), absolute
- `IgniteContext.gateEndFrame` (`ignitor/IgniteContext.kt:37`), voice-relative `Int`

In the scheduler (`voices/VoiceScheduler.kt`):

| Part | Line | What it does |
|---|---|---|
| `active` | `:91` | membership is "alive" |
| natural death | `:493-501` | `render` returned `false`: swap with the last, remove |
| cut | `:632-641` | `iterator.remove()`: instant death decided outside the voice, a click; removing mid-list also reorders the orbit lease succession |
| realtime note-off | `:411` | `releaseRealtimeVoice` calls `voice.releaseGate` |
| stop | `:264` | `cleanup` releases held realtime voices only; the rest rings out |
| hard kill | `:285`, `:184` | `cleanupHard` (end of the warmup handshake), `clear` |
| re-send dedup | `:323` | a playing voice (a zombie too) absorbs an identical incoming one |
| `ActiveVoice.origin` | `:82` | timeline or realtime, `held`: lifecycle facts kept beside the voice |
| culled count | `:487-491` | reads `culled` before and after `render` |

In the orbit: the lease is renewed from two paths, `SendRenderer.kt:30` (a sounding voice) and `Voice.kt:216`
(a zombie). That is why a zombie stays in the list until `endFrame`, and why a zombie keeps owning its orbit's
bus settings until then.

In the engine (`PlaybackEngine`): `stopped`, `isReleasing`, `TailRelease`. The end of a whole playback is
engine-wide, not per voice, and stays where it is.

## The states

```
Pending ──onset──▶ Sounding ──gate end / note-off──▶ Releasing ──endFrame──▶ Done
                      │                                  │
                      └──────── cut / takeover ──────────┴──▶ Fading ──fade end──▶ Done
                                                         │
                                          silent for the cull window
                                                         ▼
                                                      Zombie ──endFrame / cut / takeover──▶ Done

Any state ──endFrame reached (a release of 0, a negative release), or a hard kill──▶ Done
Pending ──cut / takeover──▶ Done (it has not sounded; promotion runs one block ahead, so a cut can reach it)
```

- `Zombie`, `Fading` and `Done` are terminal: nothing leads back to a sounding state. A cut, takeover or hard kill
  on a silent voice (`Pending`, `Zombie`) needs no fade and sends it straight to `Done`: that keeps today's orbit
  hand-over (today's cut removes zombies too, so the cutting voice takes the orbit after one block; a zombie that
  ignored the cut would keep refusing the new voice's bus settings for its whole remaining release, round-1
  review of step 1). Culling does not run in `Fading`, and a note-off in `Fading` or `Zombie` is ignored.
- `render` dispatches on the state: `Pending` returns early, `Sounding` / `Releasing` run the pipeline, `Fading`
  runs it with the fade ramp, and `Zombie` only renews the lease.
- Culling measures where it measures today: until the voice has been heard, and in `Releasing`. Never in
  `Sounding` once heard (the gate is the held part of a note and may be silent on purpose), and never in
  `Fading` or `Zombie`. That is the organic saving: the states that do not need the measurement skip it.

## Who drives which transition (maintainer, 2026-10-07)

- **Inside the voice:** every time-driven transition (onset, gate end, `endFrame`, fade end) and becoming a
  zombie. The thresholds come in as parameters (the cull window per voice, the floor a constant).
- **From outside, the scheduler:** note-off, cut, takeover, hard kill. The scheduler decides WHO (the group, the
  same-onset rule); it sends an event, and the voice decides by its state whether the event applies and WHAT
  happens.
- **Every voice ends in `Done`**, hard kill included. The scheduler asks each voice once per block whether it is
  done and removes the done ones. No removal anywhere else.

## Steps

Each step: one commit after the review loop, mutation-checked at the engine tier, and the corpus rendered
before and after.

0. **Kokon's ending (a measurement, no code). Done 2026-10-07: not the engine.** The last chord's release is
   LINEAR (`makeGuitar`: `e.curves("linear", "linear", "linear")`, 8 s): the first 6.8 s lose 10 dB, the last
   second loses about 30 dB, and the rig after the envelope holds the upper part up, so the fall bunches at the
   end. No voice ends audibly (each is 35 to 38 dB under its peak at its teardown window; keeping the voices 2 s
   longer changes the output by at most 1.1e-5), no orbit or master stage cuts, and the browser never stops the
   song (it loops after 6 s of silence). A "cube" release makes the fade steady. A song decision by ear (the
   curve is set for every guitar in `makeGuitar`). Report and WAVs: `tmp/reviews/kokon-end-report.md`,
   `tmp/kokon-end/`.
1. **The state, read-only.** Add the state and derive it from today's fields; `render` dispatches on it.
   Bit-identical.

   **What was done (2026-10-07).** `Voice.State`, a plain enum (`Pending`, `Sounding`, `Releasing`, `Zombie`,
   `Done`; a closed param-less set, no allocation per block), held in `Voice.state`. `render` calls `advance`
   (the time-driven transitions at the block's start: `Done` from the first block that starts at or after
   `endFrame`, from any state; `Pending` to `Sounding` on the first block that ends after `startFrame`; `Sounding`
   to `Releasing` on the first block that starts at or after `gateEndFrame`, in the same call when the first
   rendered block already lies past the gate) and then dispatches with an exhaustive `when`. `renderStages` runs
   the pipeline for `Sounding` and `Releasing` and moves a `Releasing` voice to `Zombie` at the block's end, the
   culling decision unchanged: measure while `!heard` or `Releasing`, count only in `Releasing`. The `culled`
   field is gone: `Voice.culled` reads `state == Zombie`, so it is false again once the zombie is `Done` (the
   scheduler's culled count reads it around one `render`, which is unaffected; `VoiceCullingSpec` now records the
   cull while it renders). `releaseGate` returns at once on a `Zombie` or `Done` voice: through the scheduler
   that is equivalent (a zombie's gate lies before the cursor the scheduler releases at, so the natural-gate
   check returned; a `Done` voice has left the active list); only a direct call with an earlier frame differs.
   Not equivalent only off the engine's path: a voice rendered at a block EARLIER than one it already rendered
   (time going backwards) keeps its later state; the scheduler's cursor only moves forward. Proof:
   `VoiceLifecycleSpec` (9 rows, 18 mutations all red), the `audio_be` JVM and browser suites, `:klang:jvmTest`,
   root `:jvmTest`; a JVM micro-benchmark (old and new interleaved) put the per-call cost of a sounding,
   a zombie and a pending block inside the run-to-run spread. The corpus render is the coordinator's.
   Report: `tmp/reviews/vl-step1-report.md`.
2. **One writer for the time limits.** The state machine owns gate end, end frame and (later) fade start, and the
   contexts read them from the voice instead of keeping copies (or, if a hot path needs a copy, one place pushes
   it). Remove the redundant fields. Bit-identical.
3. **Events from outside.** Note-off and hard kill become events on the voice; every voice ends in `Done`; the
   scheduler removes only done voices. Decide whether `held` / `liveId` move onto the voice or stay as the
   scheduler's provenance. Bit-identical.
4. **Cut becomes `Fading`** with the house teardown fade length (4 ms) instead of `iterator.remove()`. The voice
   applies the ramp itself, from a fade-start FRAME (a cut lands mid-block): it cannot rely on
   `TeardownFadeRenderer`, which `VoiceFactory.treeStages` leaves out when the tree ends in its own envelope. A sound change by
   design (no click), but no song uses cut, so the corpus stays bit-identical; `VoiceSchedulerSoloCutSpec`'s rows
   change on purpose. The open questions of `future/cut-group-semantics.md` (`cut(0)`, the reach of a group) are
   answered by the maintainer before this step or with it.
5. **The lease per state (maintainer decision, by ear).** Which states hold the orbit lease. Today a zombie holds
   it until `endFrame`; restricting it changes which voice owns a shared orbit (the culling design avoided that on
   purpose, measured on Der Schmetterling 2026-09-15). Corpus render plus listening.
6. **Then, as their own tasks:** `takeover` (the `Fading` event with its own time) and `glide` (a value the new
   voice gets at its onset), `docs/tasks/voice-takeover.md`.
7. **Optimisation, only if measured to be needed.**

## Open decisions

- Step 4: does a `Fading` voice end in `Done` at the fade end (today's cut drops its lease at once), or in
  `Zombie` until its old `endFrame`?
- Step 5: the lease rule per state.
- `cut-group-semantics.md`: `cut(0)`, and whether a group reaches the whole playback or one orbit.

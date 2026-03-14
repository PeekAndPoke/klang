# Six Hats Analysis: Sound Playground

**Date:** 2026-03-14
**Rounds:** 3

---

## White Hat — Facts & Context

- Klang has a mature synthesis engine: 8+ oscillator types (sine, saw, square, triangle, supersaw, pulse, noise), full
  ADSR envelopes, SVF filters (LP/HP/BP/Notch), FM synthesis, effects (reverb, delay, compressor, phaser, tremolo,
  bitcrush)
- Interactive UI editor tools already exist (ADSR curves, filter response, waveform selector, compressor) — currently
  accessed via right-click in CodeMirror
- Strudel DSL connects to audio via VoiceData -> SynthVoice -> Orbits pipeline with real-time parameter updates
- No standalone playground exists — all sound shaping happens through code + contextual editor popups
- Platform runs in-browser (Kotlin/JS) with audio processing in a JS worklet

---

## Discussion Summary

### Round 1 — Opening Positions

#### Red Hat — Feelings & Intuition

- The gap between "I wrote a pattern" and "why does it sound like a ringtone" is the most demoralizing moment — a
  playground bridges it
- Users need to hear sound change the INSTANT they touch something — immediacy is everything
- 30 knobs = instant tab-close; one waveform selector + play button = delight
- Existing right-click popups feel hidden — discoverability is an afterthought

#### Black Hat — Risks & Problems

- Existing editor tools are coupled to CodeMirror — extraction means duplication or regression risk
- Audio worklet latency (50-200ms) makes "instant feedback" inconsistent, especially for voice re-initialization
- Progressive disclosure is architecturally hard with a fixed VoiceData pipeline — isolating stages teaches a wrong
  mental model
- If playground doesn't generate DSL code, it's a disconnected toy that widens the gap

#### Yellow Hat — Benefits & Value

- Most engineering is done — playground is primarily a curation/sequencing problem (high value, low cost)
- Makes hidden engine complexity discoverable — transforms "neat pattern tool" into "sound design platform"
- Competitive differentiator: no live-coding platform offers integrated visual synthesis education
- Creates natural content pipeline: each concept becomes a lesson, preset, and building block

#### Green Hat — Creative Ideas

- "Sound DNA" metaphor: sounds as visual strands with mutable genes, learning backwards from desired sound
- "Spectral photography": record a sound, see spectrogram, challenge user to recreate with synthesis
- "One knob" progression: each lesson exposes exactly ONE new parameter
- "Living code": bidirectional real-time animated link between visual controls and strudel code

#### Blue Hat — Process & Next Steps

- Build order: standalone shell -> oscillator -> ADSR -> filter -> samples -> effects
- Key decisions needed: emit Strudel code vs drive VoiceData directly? Reuse widgets or build new?
- First deliverable must be standalone shell + single oscillator — forces all architectural decisions
- Validation: zero-knowledge user completes oscillator -> ADSR -> filter in 5 minutes

### Round 2 — Key Debates

The most productive clash was between Red and Black on immediacy. Red insisted users must feel powerful within 30
seconds — "touch something, sound changes NOW." Black countered that browser audio worklets have inherent latency
variance: continuous parameter changes (filter sweeps) can feel instant, but oscillator-type switches require voice
re-initialization, creating perceptible inconsistency. Red pushed back that filter sweeps should come before ADSR in the
progression precisely because they're more dramatic and instantly gratifying. This tension produced an important design
insight: lead with continuous parameters (filter cutoff, gain) where latency is imperceptible, and introduce discrete
switches (oscillator type, filter mode) only after the user is already engaged.

Green and Yellow converged on a pivotal reframe that reshaped the entire discussion. Green proposed starting from rich
presets and sculpting downward ("sculptors remove marble, they don't start with atoms"), while Yellow argued the
playground should feel like an "instrument, not a tutorial" — instruments get daily use, tutorials get abandoned.
Together they arrived at: **the playground writes code silently as a byproduct of play, revealed when the user is ready.
** This resolved Black's biggest concern about bidirectional code sync — by making it one-way (visual -> code,
read-only), the parser round-trip complexity vanishes entirely. Yellow quantified this as "80% of the bridge value at
20% of the coupling cost."

Blue absorbed these shifts and restructured the plan. The most significant process decision: don't extract existing
CodeMirror widgets at all. Build standalone Kraft components from scratch, avoiding regression risk in stable code.
Black endorsed this but demanded a time-boxed spike proving widget-to-worklet control works before any roadmap
commitment. Blue set the hard constraint: "if the oscillator doesn't play within 1 second of page load, the architecture
is wrong."

### Round 3 — Final Verdicts

| Hat    | Final Verdict                                                                                                                                                        |
|--------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Red    | Ship a playground that greets users with living sound and immediate tactile control — if the first touch doesn't spark pride, nothing else matters.                  |
| Black  | Time-box the widget-to-worklet control spike at two weeks; if it fails, no amount of pedagogical design saves this feature.                                          |
| Yellow | Launch with a single playing preset, one filter knob, and read-only code preview — delight first, education as inevitable byproduct.                                 |
| Green  | Ship a sculpt-down-from-beauty instrument where code revelation is the reward, not the starting point.                                                               |
| Blue   | Build standalone Kraft widgets that emit read-only Strudel DSL from day one, deferring bidirectional editing until the playground teaches through interaction alone. |

---

## Synthesis

Three rounds of debate produced a clear consensus that none of the hats held individually at the start. The playground
should NOT be a bottom-up tutorial (oscillator -> envelope -> filter -> effects) but a **top-down sculpting instrument
**. Users begin with a rich, beautiful-sounding preset and reshape it — every knob turn silently writes Strudel DSL
visible in a read-only panel. This "sculpt down, reveal code" loop delivers Red's 30-second pride moment, satisfies
Black's demand for code connection without bidirectional sync risk, fulfills Yellow's "instrument not tutorial" vision,
and realizes Green's creative direction of learning-by-deconstruction.

The most important evolution across rounds was the resolution of the code-bridge problem. Round 1 saw Green proposing
full bidirectional sync and Black flagging it as the highest-risk item. By Round 2, Yellow offered the "80/20" insight:
read-only code preview delivers most of the bridge value without the parser round-trip complexity. By Round 3, all five
hats had converged on one-way code generation as the right approach for launch, with bidirectional editing deferred to a
later phase. This is the kind of insight that only emerges through debate — no single perspective would have arrived
here alone.

The unresolved tension is between Black's insistence on a time-boxed technical spike and Red's urgency to ship something
emotionally compelling. Black wants proof that standalone Kraft widgets can reliably drive the audio worklet before
committing to a roadmap. Red wants users hearing sound yesterday. Blue's resolution — a 3-day prototype that must play
within 1 second of page load — is the pragmatic bridge: fast enough to maintain momentum, rigorous enough to catch
architectural failures early.

## Recommended Actions

1. **3-day prototype spike:** Build a standalone page with one Kraft oscillator widget + waveform display that plays on
   load. Validate widget-to-worklet latency. If it doesn't play within 1 second, rethink the architecture before
   proceeding.

2. **Add filter sweep as first interaction:** One knob controlling LP filter cutoff on a rich preset (supersaw or
   similar). This is the "30-second pride moment" — continuous parameter, no voice re-init latency, maximum drama.

3. **Add read-only DSL panel:** Show the Strudel code that produces the current sound, updating live as the user tweaks.
   One-way generation only (visual -> code). Include a "copy to editor" button.

4. **Layer ADSR and additional parameters:** After the sculpt-down loop is validated, progressively expose envelope
   controls, oscillator type selection, resonance, effects. Each new parameter gets its own visual widget.

5. **Defer to Phase 2:** Bidirectional code sync, spectrogram matching/gamification, preset sharing, and
   recipe/guided-lesson flows. Only pursue after the core sculpting instrument proves engaging.

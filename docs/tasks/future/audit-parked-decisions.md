# Audio backend audit: the three decisions it left parked

Status: **future / maintainer calls.** None changes how anything sounds, which is why they were not
made on the audit's behalf. Record and evidence: `docs/audio-audit/FINDINGS.md` (F14, F15, F17).

## 1. `GuitarClickHuntTest` — 78 % of suite runtime, asserts nothing, by design (F15c)

It is the standing click-diagnostic harness: 7 rows that render and print, 6 of them with no
assertion at all. That is what it is for. But it is **5.4 s of the suite's 6.9 s**, and every
mutation verdict in the audit — and every developer's red/green loop — pays it. Options: a Kotest
tag so it runs only on request, or a separate Gradle task. Keep it; just not in the default run.

## 2. `attackSeconds` means two different things in one directory (F17a)

- `Compressor.attackSeconds` is a **one-pole τ**, not a rise time: measured t10-90 is 2.33–2.54 × τ,
  so a configured 30 ms behaves like a ~74 ms attack in the units a DAW would print. The ear found
  15 ms where it wanted "30"; that is the label, not the ear, being off.
- `Ducking.attackSeconds` sets the **release** (the duck-down is instantaneous by design; the name
  was kept "for Strudel compatibility").

Parameter parity says one name, one meaning. Options: document the τ convention and rename the
ducking one, or rename both to what they are. Renames touch songs, so this waits for a moment when
the compressor surface is being changed anyway (the pipeline coefficient-exposure work is the
natural one).

## 3. `VoiceTestHelpers.createVoice` bypasses `VoiceFactory` (F14, the surviving bullet)

The helper hand-rolls a pipeline construction **parallel** to `VoiceFactory.makeVoice`, so the ~37
lifecycle and pipeline tests that use it can pass while production drifts: `gain = baseGain *
velocity`, legato and clip maths, sample loop and pitch-ratio resolution, the per-voice cutoff
tolerance, and the ADSR merge with sample metadata are all untouched by that path. Fixing it means
rewriting the foundation those tests stand on — either route the helper through `makeVoice` with
test-controlled `VoiceData`, or accept the split and write the missing `makeVoice` coverage
directly (the soundfont work already added some: `SamplePlayheadStartSpec`,
`VoiceFactoryFilterEnvWireSpec`, `VoiceFactoryBodyVowelRoutingSpec`).

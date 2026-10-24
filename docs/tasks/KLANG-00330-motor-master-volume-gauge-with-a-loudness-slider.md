# KLANG-00330: Motor: master volume gauge with a loudness slider

> **Status (2026-10-25): DESIGNED.** Decided with the maintainer 2026-10-25; next after the scroll-to-centre addition to KLANG-00322.

## Why this exists

The maintainer (2026-10-25): a volume control in the Motor component, "a smaller gauge next to the three gauges with a
speaker icon. You click it and get the slider." Today the only way to turn Klang down is the system volume.

**The exception to the stone rule** "the engine is the horse" (a frontend never gets DSP of its own): the maintainer
named this master volume as the exception (2026-10-25). It is one gain on the browser's output, after the engine, done
purely on the JS side; the engine and the wire do not learn about it. The code says so in a KDoc that names the rule.

## Decide before implementing

- **Does the oscilloscope follow the volume?** The maintainer first chose the gain after the analyser (the waveform
  and spectrum keep the song's level), then added: "maybe it would also be cool if the waveform got less loud...
  nicer probably. Still pure js side". Both are pure JS: the gain node goes before the analyser (the scope and the
  spectrum shrink with the volume) or after it (they stay). Ask once more when building; the leaning is "before".

## The work

- **Output chain** (`audio_be/src/jsMain/kotlin/JsAudioBackend.kt`, today worklet, then analyser, then destination): a
  Web Audio `GainNode` in the chain, at the place decided above. `audio_bridge/src/jsMain/kotlin/externals.kt` gets
  the few declarations it needs (`createGain`, `GainNode`, its `gain` AudioParam).
- **Setting it from the page:** a volume setter on the backend, reachable through `Player`. How the page reaches the
  JS backend is not yet looked at; find the plainest path. A change of volume ramps the gain over a few milliseconds
  (`setTargetAtTime`), so a slider drag does not click.
- **Loudness scale** (maintainer, 2026-10-25): the slider runs in decibels, mapped to linear gain, from silence up to
  0 dB (100 %), never above. The bottom end is silence (gain 0), not a very small dB value.
- **The gauge:** a fourth, smaller `RoundGauge` (about 40 px) in `PlayerMiniStats`, with a speaker icon; its needle
  shows the volume. A click opens a small popup with a slider. **Only in the Motor in the editor, not on the start
  page** (maintainer, 2026-10-25): `PlayerMiniStats` is used on both, so the Motor passes a flag.
- **Remembered:** the volume is kept in the browser's local storage (wrapped in try/catch) and applied when the player
  starts.
- **Credits:** none expected (a plain gain); check when it lands.

## Acceptance

- Turning the slider changes the loudness smoothly, without clicks; at the bottom end it is silent; it never goes
  above the song's own level.
- The gauge appears in the editor's Motor and not on the start page.
- The volume survives a reload.
- The scope and spectrum behave as decided above.
- The engine, the wire and `audio_be`'s DSP are unchanged apart from the output chain.

## Acceptance

## History

- 2026-10-25: created.

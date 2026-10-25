# KLANG-00330: Motor: master volume knob on a loudness scale

> **Status (2026-10-25): IN PROGRESS.** Built as a knob, reviewed (round 5 clean); waits for the maintainer's hand test.

## Why this exists

The maintainer (2026-10-25): a volume control in the Motor component, "a smaller gauge next to the three gauges with a
speaker icon. You click it and get the slider." Today the only way to turn Klang down is the system volume. Built as a turning knob (below).

**The exception to the stone rule** "the engine is the horse" (a frontend never gets DSP of its own): the maintainer
named this master volume as the exception (2026-10-25). It is one gain on the browser's output, after the engine, done
purely on the JS side; the engine and the wire do not learn about it. The code says so in a KDoc that names the rule.

## Decided

- **The scope and the spectrum follow the volume** (maintainer, 2026-10-25, asked once more when building): the gain
  sits between the worklet and the analyser, so what you see matches what you hear.
- **A turning knob, not a gauge with a popup slider** (maintainer, 2026-10-25, mid-build): "can we make it a turning
  knob? Pointing to 1 with a small led showing the direction into which the knob is directed".

## The work

- **Output chain** (`audio_be/src/jsMain/kotlin/JsAudioBackend.kt`, today worklet, then analyser, then destination): a
  Web Audio `GainNode` in the chain, at the place decided above. `audio_bridge/src/jsMain/kotlin/externals.kt` gets
  the few declarations it needs (`createGain`, `GainNode`, its `gain` AudioParam).
- **Where the volume lives** (maintainer, 2026-10-25: "We have to divide the volume setting from the knob position"):
  `Player.outputVolume`, a stream of the linear gain 0.0 to 1.0, from page load on. The knob derives its position
  from it (`MasterVolumeScale.positionOf`) and turns it with `Player.setOutputVolume(gainOf(position))`. The gain
  travels to `KlangPlayer` (`Options.outputVolume` at the start, `setOutputVolume` after), then to
  `AudioBackend.setOutputVolume`, where the JS backend sets its gain node; a change ramps over about 15 ms
  (`setTargetAtTime`), so turning the knob does not click.
- **Loudness scale** (maintainer, 2026-10-25): the knob runs in decibels, mapped to linear gain, from silence up to
  0 dB (100 %), never above (`MasterVolumeScale`, -60 dB to 0 dB). The bottom end is silence (gain 0), not a very small dB value.
- **The knob** (`VolumeKnob`): the gauges' ring, LEFT of the three gauges in `PlayerMiniStats`, 31 px, sized by eye
  with the maintainer, with an empty place of its size on
  the right so the middle gauge stays centred (maintainer, 2026-10-25); a small LED in the palette's cyan on
  its rim shows where it points, over the gauges' 270 degree sweep, full at the top end, dark before the Motor is online, else as bright as half at silence and full at full; its ring
  glows a quarter softer than the gauges', constant; no icon (maintainer,
  2026-10-25). A vertical drag turns it, with a mouse, a finger or a pen (pointer events, the main button only; 75 px for the
  full turn). **Only in the Motor in the editor, not
  on the start page** (maintainer, 2026-10-25): `PlayerMiniStats` takes `showVolume`, which the Motor sets.
- **Remembered:** the gain is kept in the browser's local storage (`OutputVolumeStorage`, wrapped in try/catch) when
  a turn of the knob ends, and the player starts at it.
- **Credits:** none expected (a plain gain); check when it lands.

## Acceptance

- Turning the knob changes the loudness smoothly, without clicks; at the bottom end it is silent; it never goes
  above the song's own level.
- The knob appears in the editor's Motor and not on the start page.
- The volume survives a reload.
- The scope and spectrum shrink with the volume.
- The engine, the wire and `audio_be`'s DSP are unchanged apart from the output chain.

## History

- 2026-10-25: created.
- 2026-10-25: IN PROGRESS.
- 2026-10-25: IN PROGRESS. Built as a knob (maintainer's change); the scale spec green, 4 mutants KILLED; review round 1 running.
- 2026-10-25: look tuned with the maintainer (left of the gauges, 31 px, no icon, cyan LED); review rounds 1 to 3 fixed, round 4 clean; then the volume moved into `Player.outputVolume` as the truth (maintainer), the knob derives its position; 4 more mutants KILLED.
- 2026-10-25: review round 5 clean; committed. Waits for the hand test (before testing the reload, turn the knob once: an earlier build stored the knob position under the same key).

# KLANG-00388: One animation frame scheduler

> **Status (2026-10-29): NOT SCHEDULED.** Asked for by the maintainer (2026-10-29: "file a ticket to have one animation
> frame scheduler which collects all and runs them").
>
> **Progress (2026-10-29):** the Spine's frames (85f2986cf, now in `UiWork`): `UiWork.allFrames`, one Ultra `ticker` on
> the animation frames that every subscriber shares, and `UiWork.frames`, cut off while the UI rests (KLANG-00389). Components subscribe with
> Kraft's `subscribe { step() }`, which ends with them (the maintainer: "spine exposes a single animationFrame stream and
> components subscribe to it and call their step function()"). On it: the lamp's light (and through it the plate's and
> the domes' reflections), the rain, the spectrum, the start page (was a 16 ms ticker). Still on frames of their own:
> `smooth()` and `animTicker()` (Ultra's `AnimationFrameClock` asks for one frame per `next()`, so each smoothing and
> the analyser's waveform ask separately; asked of Ultra to share one frame), three.js's loop, `KlangAdsrCurve`, the
> highlight buffer's Pixi ticker.

## Why this exists

The maintainer's trace of Klang Amp rain over the code page (2026-10-29, 13 s, Chrome DevTools) counted about 525
animation frame callbacks a second, about nine a frame: each part runs its own loop. Requesting the frames alone took
52 ms of the main thread a second (`requestAnimationFrame` self time); the loops seen:

- Ultra's `AnimationFrameClock` (the smoothing of the needles and the glow, `smooth()`), twice;
- three.js's render loop (`startLoop$frame`, the plate);
- `Spectrumeter`, `Oscilloscope`, `VoiceRain`, `VisualsBoard.followLight` (the glass domes), the highlight buffer's Pixi
  ticker in the editor.

## Decide before implementing

- **Where it lives:** Ultra's `FrameClock` (`ultra/streams/ops/FrameClock.kt`) is already a shared clock for the
  stream operators; one scheduler for every callback, the components' too, may belong there, in Kraft, or in klang's
  `workshop/` first. Ask Ultra (`/mission-control-link`, requests to Ultra).
- **What a client gives:** a callback per frame with the frame's time and the time since the last; a way to ask for
  fewer frames (30 a second for the rain, the plate when the lamp moves slowly); a way to stop when idle, so a scheduler
  with no clients asks for no frames.
- **Order and failure:** clients run in a fixed order (readings before drawings); one that throws is reported and does
  not stop the others.

## The work

1. The scheduler: one `requestAnimationFrame` loop while any client is registered, none otherwise.
2. The parts moved onto it, one at a time, each measured: the smoothing clock, the spectrum, the scope, the rain, the
   domes, the plate (three.js's loop through its `onFrame`), the highlight buffer's ticker.
3. A per-client frame rate where a part needs fewer frames.

## Acceptance

- In a trace of Klang Amp rain playing, one animation frame callback a frame, and `requestAnimationFrame`'s own time
  near nought.
- No part looks different; a part that asks for fewer frames moves smoothly enough for the maintainer.

## History

- 2026-10-29: created from the maintainer's trace, after the Motor's pause while a visual covers the page (14964a0f2)
  and the gauges drawn once (d329990b9).
- 2026-10-29: `Frames` in the Spine (85f2986cf); the lamp, the rain, the spectrum and the start page moved onto it.

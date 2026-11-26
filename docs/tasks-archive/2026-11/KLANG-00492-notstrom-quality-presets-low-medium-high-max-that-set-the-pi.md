# KLANG-00492: notstrom quality presets low medium high max that set the picture's settings

> **Status (2026-11-26): DONE.** Four presets and custom; the machine test chooses one and pauses while the window is away.

## Why this exists

The maintainer (2026-11-26), asked whether the machine test should also choose the reflections: "i think we should
have three/four general quality setting: low, medium, high, max and they select the different sub-settings", and:
"Ok actually we need to four levels and a 'custom' level, when the user changes an individual setting and then has a
combination that is none of the presets."

And of the picture test: "we have to drop outliers and also stop when the window loses focus, as this prevents
drawing and we will measure something like 100 seconds for one frame."

## The presets

| Preset | Oversampling | Fog | Shadows | Reflections |
|--------|--------------|-----|---------|-------------|
| Low    | 1x           | low | low     | off         |
| Medium | 1x           | high | high   | mid         |
| High   | 2x           | high | high   | mid         |
| Max    | 4x           | high | high   | high        |

Medium is the picture the show starts with today at 1x, High the same at 2x: the machine test's two parts measure
exactly these two, so it chooses among Low, Medium and High as it chose the oversampling before, and a dense display
(where the test never draws 2x) gets Medium, the picture it gets today. Max is never the test's choice (as 4x was
not), only the visitor's.

First proposed with Medium at fog low and reflections low (2026-11-26, in the session); changed before building so
that the test maps onto the presets and no machine gets less than today. Measured in the headless browser the same
day: software drawing, a frame or two a second, the numbers noise (fog low slower than high); the presets' real
costs are for the machine test on a real card, and the table for the maintainer to tune at the screen.

## Decided

- **Custom:** a combination of the four that is none of the presets shows as Custom. Choosing a preset sets all four;
  changing one by hand (or the live act's own step down of the oversampling while the show plays) can make it Custom.
- **The drawer:** the five as the main choice (Custom only shown, not chosen), the four settings below it as now.
- **The address:** `?quality=low|medium|high|max` sets the four; a switch of one of them (`?fog=low`, ...) wins over it.
- **The machine test** keeps the preset it chose (instead of the fog and the oversampling); its version is raised.
- **The picture test's frames:** it pauses while the window has no focus or the page is hidden (nothing is drawn
  then): that time does not count toward the test, and the frame across the gap is dropped; and the slowest frame it
  reports leaves out outliers.

## The work

Done 2026-11-26.

- `PicturePreset` (klangviz): the four, what each sets, `matching` (none: custom); `?quality=` (`Switch.Quality`),
  read by `FogQuality.of`, `ShadowQuality.of`, `ReflectionQuality.of` and the new `Oversampling.of` where their own
  switch is missing (an `?oversample=` of any value is taken; the other three fall back on a word that names none).
- `Picture.choosePreset`; notstrom's drawer: a row "Quality" with Low, Medium, High, Max and "Custom" lit where the
  settings are none of them.
- The machine test: its first part is the medium picture at 1x, its 2x part the high one (as before); the fog part
  is gone. `presetFor` chooses high where the 2x part holds (and 1x does), medium where 1x holds 55 frames, else low.
  `StoredTest` keeps `quality` (version 6; a version-5 text does not decode, and the test runs again);
  `showSwitches` hands `quality=` on (medium sets nothing); the result's line names the preset.
- The picture test counts drawn time (`FrameClock`, apps/common): while the window has no focus or the page is
  hidden it pauses (the checking screen says so: "Paused while this window is in the background. Click here to go
  on."), the frame across the time away and the stage's first frame do not count; the wait for the band to stand
  is not a failure while the page is away. `FrameStats.slowestMs` leaves out frames longer than 8 medians.
- apps/common's `PictureQuality` lost the fog's choice (`measuresFog`, `fogFor`): nothing asks for it now.

For the maintainer at the screen: the table above, tuned by eye and by the test's results on a real card; and
whether pausing on a window that lost the focus but is still seen (two screens) is wanted, or only on a hidden page.

## Acceptance

- `PicturePresetSpec`, `FrameClockSpec`, `FrameStatsSpec`, `StoredTestSpec`, `ShowHelpersSpec`, `SwitchSpec`;
  klangviz js and jvm, notstrom js, apps/common js and jvm green.
- 5 mutants killed (the frame across a time away counted, no outliers, the test's medium dropped, the quality
  ignored by the fog, an own switch losing to the quality).
- Review: round 1 CLEAN with five minors (listeners on cancellation, stale wording, the band's wait while away, a
  word on the checking screen while paused, a version-5 text pinned), all applied; round 2 CLEAN (reviewer-opus-high).

## History

- 2026-11-26: created.
- 2026-11-26: done: the presets, custom, the test's choice and its pause; on the screen to be tuned.
- 2026-11-26: DONE.

# KLANG-00411: The code highlights' strength, a knob in the Settings tool window, 66 percent by default

> **Status (2026-11-05): IN PROGRESS.** Built, reviewed and mutation-checked, the on/off switch (D4) too. Waits for
> the maintainer's look at the Settings tool window.

## Why this exists

The maintainer (2026-11-05): "for the code highlights in the real editor we need a setting on how strong they should
appear. Current is 100% but i would default to 66%. Put the option to where the highlights / event already lives in
the UI."

## Decided (maintainer, 2026-11-05)

- **D1, the place.** A "Strength" knob in the song page's Settings tool window (`SongSettings`), under "Highlights per
  sound". 0 to 100 percent, 66 by default, kept in the browser by `EditorSettings` like the other editor settings.
- **D2, how.** The whole highlight layer is dimmed at once (the Pixi stage's alpha): nothing more per frame. At 0
  nothing is scheduled.
- **D3, where it applies.** Every editor with the highlights: the song page, the tutorial examples (they share the
  editor settings), and the minimap's bands of KLANG-00410.
- **D4, a switch for them all** (the maintainer, 2026-11-05, after seeing the minimap's: "We could also need a
  'enable code highlights' setting, to full enable / disable it. As the first setting in the side-tool."). "Code
  highlights", on by default, the first setting in the Settings tool window. Off, nothing is scheduled or drawn, in the
  editor and in the minimap; on again, the song's voices are sent again and the highlights pick up where it is.

## Acceptance

- The knob changes the highlights' strength at once, while a song plays; the choice survives a reload.
- A first visit shows them at 66 percent, switched on.
- Switched off, no highlight shows anywhere; switched on, they come back while the song plays.
- Specs: the setting's default, range and storage; the buffer's layer follows it.

## History

- 2026-11-05: created from the maintainer's request.
- 2026-11-05: D4, the on/off switch, added at the maintainer's request.
- 2026-11-05: built: the knob and the switch in `SongSettings`, `EditorSettings`, `CodeMirrorHighlightBuffer.strength`
  and `enabled`. The two knobs stay live while the switch is off, so they can be set before switching on. Reviewed,
  mutation-checked. Not checked by a spec: the Pixi stage's alpha and the schedule's early return (both need WebGL or
  the window's focus, which the test browser has not).

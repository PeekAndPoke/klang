# KLANG-00336: Visual script: KlangScript that makes the UI react to the music

> **Status (2026-10-25): IDEA.** The maintainer's idea for the very long run, 2026-10-25. Future idea sector; kept
> in mind while the new theme is built (KLANG-00334 (UI workbench)).

## The idea, in the maintainer's words

"What i would also like to do in the veeeeery long run is to have another kind of script (also in KlangScript)
which would give visual coding and could for example influence how the ui looks when certain musical event
happen ... but this is something for the future idea sector, but maybe good to keep in mind while building the new
theme."

## What it asks of the theme now (cheap to prepare)

- **Roles addressable by name at run time.** Besides the typed Kotlin fields, the theme can be read and set as
  role name to value, so a script can say "the lamp colour" without knowing the Kotlin class.
- **Live values without a re-render.** The roles reach the page as CSS custom properties (as `--klang-*` does
  today), so changing one on a beat repaints without touching the Kraft tree.
- **A stack of layers.** App default, then the user's theme, then a song's override (KLANG-00334 item 28), then
  what a script sets while the song plays. Taking a layer away restores the one below; nothing is lost.

## Notes

- **Where the events come from:** the frontend already gets the played events (the editor highlights per event),
  so the script runs in the frontend on what the frontend knows. The backend still never learns about cycles
  (root rules).
- **Eyes, like ears:** a script can make the screen flash. A guard keeps flashes under three per second
  (WCAG 2.3.1, three flashes or below threshold) and respects "reduce motion", the way the Motor stays raw for
  sound but the eyes get a limit. Decide with the maintainer.
- **KlangScript:** a second kind of script in the same language, its own library (like `sprudel` is one); the
  `/klangscript-knowhow` and `/dsl-design` rules apply when it is designed.

## Decide before implementing

Everything; this is an idea.

## The work

Not planned while this is an idea.

## Acceptance

Not set while this is an idea.

## History

- 2026-10-25: created from the maintainer's idea during the KLANG-00334 design conversation.

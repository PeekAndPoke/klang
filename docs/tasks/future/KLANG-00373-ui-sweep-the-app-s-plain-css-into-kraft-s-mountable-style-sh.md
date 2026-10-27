# KLANG-00373: UI sweep: the app's plain CSS into Kraft's mountable style sheets

> **Status (2026-10-27): IDEA.** the maintainer's idea, 2026-10-27
>
> **Part of:** KLANG-00334 (UI workbench: the Motor-centred layout, tool windows and the first-visit trail)

## Why this exists

The maintainer, 2026-10-27: "I see you doing a lot with "real" css... please consider using the mountable style
sheets that Kraft provides, if this is possible. Maybe also a future task with a full ui sweep." And: "This might
uncover some gaps in the Kraft StyleSheets though".

klangui's controls already live in Kraft style sheets (`KlangControlStyles`, `KlangLookAndFeel.Styles`): mangled
class names, mounted with the code that uses them, nothing global. The app itself still has `src/jsMain/resources/
css/klang.css` (575 lines, about 80 rules on 2026-10-27): the Fomantic overrides, the global gold `a` rule with
`!important`, and the app's own classes (`kl-app-link`, `kl-quiet-key`, ...), plus inline `css {}` blocks in pages.
The Learn area's first sheet, `LearnStyles` (its track panel), shows the way.

## Gaps in Kraft's style sheets met so far

- A rule cannot hold a media query: "reduce motion" variants and keyframes go into a `RawStyleSheet` beside the sheet
  (klangui's `motion`, `LearnStyles.motion`), with one class more to win the tie.
- Mount order decides ties between sheets (`KlangControlStyles` notes it); nothing names or orders them.
- The global `a` rule with `!important` forces `!important` on every class that colours a link (`kl-app-link`, the
  workbench's mark); the sweep is the moment to drop it rather than carry it over.

Each gap found in the sweep goes to Ultra-Main-Dev as a request (`/mission-control-link` section 5).

## Decide before implementing

- What stays global (the Fomantic overrides, the look's roles on `:root`) and what becomes classes.
- One sheet per area (Learn, the song page, the docs) or per component.

## The work

Not planned yet.

## Acceptance

Not set yet.

## History

- 2026-10-27: created.

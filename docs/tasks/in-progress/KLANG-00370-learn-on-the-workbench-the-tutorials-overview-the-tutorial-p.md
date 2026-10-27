# KLANG-00370: Learn on the workbench: the tutorials overview, the tutorial page and the example player on the new parts

> **Status (2026-10-27): IN PROGRESS.** started from the maintainer's approved design
>
> **Part of:** KLANG-00334 (UI workbench: the Motor-centred layout, tool windows and the first-visit trail)

## Why this exists

After the song page (KLANG-00349) the Learn area is the next screen: the second entry in the Motor's drawer, where a
new user goes from the start page. Today it is still on the old Fomantic parts: a filter menu in the column, three
rows of filter chips, each track a card of small numbered buttons; a tutorial's examples each carry a form of their
own (Play, Stop, Reset, the cycle, RPM and EVT as browser number fields).

The design (the maintainer, 2026-10-27: "Yes looks good! Please proceed with implementing this design. Should you
feel that we need a new design element somewhere let me know"): the canvas "Klang Learn on the workbench"
(https://claude.ai/artifact/LYqJskQYDcheSQxpQrMo5n), boards "Learn: the overview", "Learn: a tutorial", "The parts".
It follows the epic's board F: The Klang Path as a tech tree in the left column.

## Decided (the design, 2026-10-27; changed where the maintainer looked)

- **The overview**: the column holds The Klang Path's lessons as a list, a lamp each (the tree of strands and stages
  first drawn there: "Too much and not clear what it shows"), and the other tracks with their progress. The middle: a
  "Continue" tile with the next lesson, a search by words with two key groups (level, depth), the tracks as rows of
  lesson tiles; the tracks beside the main path fold to a row of lamps.
- **A lesson's lamp**: done (on), open (off), later (a ring: a stage not reached yet; still clickable, nothing is
  locked). The next lesson's lamp is off too, its title bold and its tile says "Next": the gold "try me" is only where
  one presses, the Continue tile and "Done. On to ..." on the next lesson (one of Learn's gold lamps at a time).
- **The tutorial page**: the column the track's lessons with lamps (no "On this page"), a way back to Learn
  (`KlangBackLink`); the content centred, the prose as wide as the examples; at the end a quiet key on the left and
  the action on the right: "Done. On to ..." / "Mark as done, stay here", done "Not done after all" / "Next", on a
  track's last lesson "Back to Learn". The bottom panel has the Quick help and the Lexikon, as on the song page.
- **The example player**: the song page's transport, small, over a small screen; EVT from the editor setting; one
  example plays at a time. The docs pages' examples use it too, with the Quick help panel.
- **The Lexikon tab**: the terms as a list on the left, the term on the right, each side scrolling on its own; no way
  back needed.
- **The stages** of The Klang Path are data.

## New design elements

- `KlangLamp.State.Unlit`: a ring in the lamp's off colour, for "not reached yet" (the later lessons). Named to the
  maintainer when found (2026-10-27).
- `KlangBackLink`: the common way back, a pill with a chevron and the place's name (the maintainer, 2026-10-27: "a
  common "navigate back" element that is a bit more obvious").
- Later, not now: lesson tiles as links (a new tab with a key held) need a link form of `KlangButton`.

## The work

1. Data and logic (common, JVM-tested): The Klang Path's stages; a lesson's state for a user's progress (done, next,
   open, later) and a track's progress.
2. klangui: `KlangLamp.State.Unlit`, in the kitchen sink.
3. The overview page and its column.
4. The tutorial page and its column; the bottom panel's Quick help and Lexikon shared with the song page.
5. The example player on the new parts (also used by the docs pages).
6. Specs, mutation checks, the review loop; screenshots against the design; the maintainer's look in the browser.

## Acceptance

- The Learn overview and a tutorial follow the design on klangui parts, in every look; no Fomantic parts left on them.
- Progress (done, next, open, later) shows the same in the column, the tiles and the tutorial page.
- An example plays, updates, stops and goes back to its original; the Quick help works on its code.

## History

- 2026-10-27: created from the maintainer's approval of the design; IN PROGRESS.
- 2026-10-27: built. The Klang Path's stages as data (`TutorialStage`); `TutorialProgress` (done, next, open, later;
  one next, on the main path) and `TutorialStrand`, JVM-specced; `KlangLamp.State.Unlit`; `TutorialStorage.done` as a
  stream; the overview (`TutorialsListPage`: continue, level/depth keys, tracks of tiles, folded tracks as
  lamps), the column (`LearnTracksColumn`, the sidebar's Learn column: the search and the tracks), the track page, the tutorial page and its column
  (`TutorialColumn`); the example player on the song page's parts (`PlayableCodeExample`, also on the docs pages);
  the bottom panel's help shared (`QuickHelpPanel`: the song page, a tutorial, the docs pages). Changes from the
  maintainer's look: the content centred, the prose as wide as the examples; the column only the track's lessons (no
  "On this page"); a fading page no longer empties the next one's column; the lesson's end one clear action ("Done.
  On to ..." with the try-me lamp, "Mark as done, stay here"); the docs pages' examples with the Quick help and Alt+Q.
  The old Learn menu, the completion and view filters and the tutorial helpers gone. `LearnPagesSpec` (the pages on
  hash routes), mutation-checked. The interactive visuals idea: KLANG-00371.
- 2026-10-27: reviewed in two rounds (level 2), both clean; their MINORs applied (screen reader words for the lamps,
  the search by words, valid list ARIA, focus kept in the Lexikon, specs for every rule; the columns in files of their
  own). Changes from the maintainer's looks: the overview's column a list, `KlangBackLink`, the Lexikon split into a
  list and the term, gold only where one presses, "Back to Learn". Committed.
- 2026-10-27: the column lists only the tracks, with the search above them (it lost its focus on the first letter on the page; checked in a browser with real key presses: the field in the column keeps it); every track starts folded, its key first and of one width; Continue stays.

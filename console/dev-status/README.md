# Dev status pages

Two flat HTML pages about where Klang stands, built from the docs and served next to the SPA:

- `src/jsMain/resources/klang-topic-map.html`: every open task and plan in `docs/tasks` and `docs/plans` as a
  zoomable graph, grouped by area, with the links between the docs; the archive behind toggles.
- `src/jsMain/resources/klang-mission-log.html`: every archived task on a timeline by the date in its file
  name, and a guessed completion date for each open one.

Both share `hud.css` (the UI palette of `KlangLookAndFeel`, the Chakra Petch and Share Tech Mono
faces, the HUD pieces); the white paper, `src/jsMain/resources/klang-whitepaper.html`, uses the same palette
and faces but is edited by hand.

## Rebuild

    python3 console/dev-status/build.py

Then commit the two pages. They sit in the SPA's static resources, so the dev server serves them
and `console/deploy-finzo.sh` uploads them to the site root with the rest of that folder; the
Resources page links the three pages under "Dev status".

## What is data and what is judgement

`build.py` reads the docs: title, status line, length, last commit, and the links between them.
`curation.py` is judgement and needs a look on every rebuild: the area and real state of each
open doc, the notes, the dashed "related" edges, which archived docs were won't-implement or
closed without being built, and the date guesses of the mission log (fixed dates for single docs,
ranges for groups, milestones). The build prints a WARNING for every open doc curation.py does
not know yet (it guesses meanwhile) and for every entry that names a doc that is gone.

Archived docs are placed in an area by keywords in their file name, with overrides in
`ARCHIVE_AREAS`; the dates of archived docs come from their file names.

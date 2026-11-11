# Dev status pages

Two flat HTML pages about where Klang stands, built from the docs and served next to the SPA:

- `src/jsMain/resources/klang-topic-map.html`: every open task and plan in `docs/tasks` as a
  zoomable graph, grouped by area, with the links between the docs; the archive behind toggles.
- `src/jsMain/resources/klang-mission-log.html`: every archived task on a timeline by the date in its file
  name, and a guessed completion date for each open one.

A third page is built by its own script from the DSP probes' results (KLANG-00420):

- `src/jsMain/resources/klang-engine-health.html`: every probe with a light, its items with a light each, and the
  measured rows behind an item. `python3 console/dev-status/engine_health.py` reads, per probe, the newest run folder
  in `docs/audio-audit/<date>/` that holds its JSON (a rerun of one probe adds a folder with that probe's files only) and the judgement in `docs/audio-audit/verdicts.json`. A light is a
  rule over a measurement (the rules are the `*_items` functions in the script): green nothing notable, yellow a lead
  nobody has judged, red bad on its face or confirmed, grey accepted by decision, blue a measurement without a rule.
  The aliasing meter's items judge by the healthy limit (`aliasing-limit.json`): green when the aliasing below 5 kHz
  stays under -60 dB re the harmonics up to a note of 5 kHz or higher.
  Rebuild it after a probe run or a verdict.

All share `hud.css` (the UI palette of `KlangLookAndFeel`, the Chakra Petch and Share Tech Mono
faces, the HUD pieces); the white paper, `src/jsMain/resources/klang-whitepaper.html`, uses the same palette
and faces but is edited by hand.

## Rebuild

    python3 console/dev-status/build.py

When a page changed its look, refresh the card previews too (needs Chrome and ImageMagick):

    console/dev-status/previews.sh

Then commit the pages and `src/jsMain/resources/images/dev-status/`. The pages sit in the SPA's
static resources, so the dev server serves them and `console/deploy-finzo.sh` uploads them to the
site root with the rest of that folder. The SPA shows them as cards on the Dev status page
(`/dev-status`, `DevStatusPage.kt`, in the sidebar after Credits). Each card is one link that
stops its click from reaching the router: the router listens on `window`, handles every href it
does not take for external (relative paths, and localhost URLs) and ignores `target`.

## What is data and what is judgement

`build.py` reads the docs: title, status line, length, last commit, and the links between them.
`curation.py` is judgement and needs a look on every rebuild: the area and real state of each
open doc, the notes, the dashed "related" edges, which archived docs were won't-implement or
closed without being built, and the date guesses of the mission log (fixed dates for single docs,
ranges for groups, milestones). The build prints a WARNING for every open doc curation.py does
not know yet (it guesses meanwhile) and for every entry that names a doc that is gone.

Archived docs are placed in an area by keywords in their file name, with overrides in
`ARCHIVE_AREAS`; the dates of archived docs come from their file names.

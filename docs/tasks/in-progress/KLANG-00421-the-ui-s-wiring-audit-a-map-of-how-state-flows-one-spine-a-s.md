# KLANG-00421: The UI's wiring audit: a map of how state flows, one Spine, a state model that can roam

> **Status (2026-11-10): IN PROGRESS.** Opened for the audit; the tool first.

## Why this exists

The maintainer (2026-11-10), before KLANG-00413 (the app's workbench parts) is built: "I need you to audit the ui.
Not so much the details in each view but the general structure. My goal is to keep the code that builds the ui
clean and understandable and open for new additions. Most important is how data flows through the ui. [...]
Everything that is not temporary component state should flow from the spine to the components / page through
streams. [...] Think of a control cabinet where each cable is nicely arranged and held and not loosely hanging
through the air."

The goal behind it (the same day): "imagine some day we have the server. I want all state to be central, so that
it could be sent to the server and stored there. Then the user switches to another computer and finds the ui in the
exact same state."

KLANG-00389 (the Spine) holds a sweep of 2026-10-29: who holds state. It is a list of holders, counted by hand, and
it has drifted (40 stream sources then, 44 now; 107 subscriptions then, 136 now). This audit adds the cables between
the holders and makes the count repeatable.

## The maintainer's directions (2026-11-10)

- **One Spine.** "There should only be one global object if at all and components will find what they need through
  `spine.xyz` instead of multiple global objects. We could also register the spine in the app's properties like the
  `Component<*>.popups` etc are done."
- **No hand-written serialization.** "Things like KeymapJson which defines its own json mapping. We should not do
  this, but rather [...] use Slumber or kotlinx serialization [...] to get a descriptive data model, that mostly
  consists of data classes that can be easily understood and round-tripped. It is also fine to write custom
  serializers / deserializers but as 'plugins' for kotlinx-serialization / slumber."
- **No KSP for the UI** (asked and answered the same day): the UI's trouble is where state lives, not how much code
  it takes; the storage objects' one repeating shape is covered by a plain generic class. Looked at again if the
  map shows many equal shapes a generic class cannot cover.
- **Lateral talk is allowed where it is needed** ("cases where components need to communicate laterally, like the
  page and the tools"), but it gets a named way.

## What the audit says about each piece of state

| Kind | Examples | Goes to a server |
|---|---|---|
| Follows the user | settings, looks, the keymap, recents, the workbench's layout, tutorial progress | yes |
| Belongs to this machine | the MIDI devices chosen, permissions granted | no |
| Lives for this session | the engine's status, the Motor's readings, the Console's log, the song that plays | no |
| Temporary component state | a hover, an open dropdown | no; it stays out of the Spine |

Which kind a piece is, is often the maintainer's call: the map lists a proposal for each.

## Facts found before the start

- The app module has kotlinx.serialization (core and json) on `commonMain` already; no file in `src/jsMain`,
  `src/commonMain`, `klangui` or `klangscript-ui` uses `@Serializable`.
- Slumber's codec is JVM code (62 files under `jvmMain`, 5 shared ones with annotations and helpers). So the client
  round-trips with kotlinx.serialization, and the data classes live in `commonMain` for a server to share.
- Hand-written JSON in six files: `KeymapJson`, `LookStorage`, `RecentsStorage`, `WorkbenchLayoutStorage`,
  `VersionController`, `KlangLookText`. Other settings are single values under keys of their own (`EditorSettings`).
- Nine global objects in `spine/`, twelve storage objects in `spine/storage/` and `storage/`.

## The work

1. **A tool that lists every cable**, `console/tools/ui-wiring/`: per UI file, the streams it reads and from which
   holder, state it holds itself, browser storage it touches, lateral references, global objects, raw DOM events,
   hand-written JSON. A table and a graph file. It reads text, so a stream handed on through a prop is traced by
   hand.
2. **Six flows traced by hand**, from where the value is kept to where it is drawn: the highlights' strength, the
   Console's unseen errors, the song that plays, a page's bottom tabs, the Motor's pause under a visual, the editor
   and the Quick help.
3. **The map**: the structure as a cabinet (storage, Spine, layout, pages, components, `klangui`), the cables by
   kind, the loose ones marked and listed with file and line; for each holder its kind (above), how it is kept
   today, and its place on the future `spine.` tree.
4. **The proposal**: a few rules (which cable between which rails, the named way for lateral talk), what breaks
   them today, ranked, and an order of work. A rule the maintainer decides can become a check of the tool.

## Acceptance

- The tool's counts can be run again and match a hand count on three files.
- The map and the proposal are with the maintainer; a reviewer has checked the map against the code.

## History

- 2026-11-10: created.

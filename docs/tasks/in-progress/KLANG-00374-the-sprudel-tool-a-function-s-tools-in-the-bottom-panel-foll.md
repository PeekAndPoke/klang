# KLANG-00374: The Sprudel tool: a function's tools in the bottom panel, following the cursor

> **Status (2026-10-27): IN PROGRESS.** steps 1 and 2 committed; step 3 (panel only) next
>
> **Part of:** KLANG-00334 (UI workbench: the Motor-centred layout, tool windows and the first-visit trail)

## Why this exists

The maintainer, 2026-10-27: "move the parameter tools into the bottom tools as well. Whenever the cursor is inside the
parenteses if a function that supports tools we should so the tool. This might mean we need to rethink the
@param-tool annotation as this is no longer really a param tool but a "sprudel function tool". This means we need a
"Sprudel" tool in the bottom tool."

## How the tools work today (mapped 2026-10-27)

- `@param-tool <param> A, B` in sprudel's KDocs (49 lines, 19 files, 77 tool names) is parsed by
  `klangscript-ksp`'s `KDocParser` into `KlangParam.uitools`. 12 tools edit the whole call (filter, ADSR, compressor,
  delay, reverb, distort, phaser, tremolo, pluck, superpluck, supersaw, the filter's ADSR), anchored on one parameter
  and handed the call through `KlangUiToolContext.call`; the rest are value tools, each paired with a sequence editor.
- `KlangUiToolRegistry` maps names to tools (`registerSprudelUiTools`); a tool renders into a modal (`render`, with
  Cancel/Reset/Update) or, embeddable, without buttons, committing on every change (`renderEmbedded`). Two tools are
  modal-only (note, scale degree, on `MnEditorBase`).
- The editor finds the argument under the MOUSE (`ArgFinder`, `AnalyzedAst.argumentAt`): a badge after a hover, a
  right-click menu; `KlangCodeEditorComp.openTool` opens a modal or a popover. Positions are captured when it opens;
  each commit is its own transaction and undo step; tools read their value once.

## Decided (the maintainer, 2026-10-27)

- **The annotation: `@tool`, two forms.** `@tool SprudelFilterEditor` on the function names the tool for the whole
  call; `@tool amount: SprudelGainEditor, SprudelGainSequenceEditor` names a parameter's tools. The panel shows the
  call's tool when it has one, else the tools of the parameter at the cursor.
- **Panel only.** The Sprudel tab is the one place a tool shows; the hover badge, the modal and the popover go. A
  right-click on an argument opens the Sprudel tab at it. The two modal-only tools get an embedded form first.
- **Live, one undo.** The code changes as one drags, a playing song updates (a few times a second at most), one drag
  is one undo step.

## The work

1. The panel on today's data: the editor reports the call around the cursor (the innermost call with tools, also
   with the cursor on its parentheses or between its arguments, also empty); a "Sprudel" tab beside the Quick help
   and the Lexikon shows its tool, embedded; positions found again from the code at each commit; the tool re-keyed when
   the code changes by other hands; its own changes ignored; one undo per drag; the playback updated, throttled.
2. The annotation: `@tool` in its two forms in `KDocParser` and the generated docs (a function-level tool list on the
   callable), the 49 lines rewritten, `@param-tool` retired (heads-up to Klang-Main-Dev, whose KDocs these are).
   Klang-Main-Dev's notes (2026-10-27, no objection): KSP is in the mandatory mutation tier (a mutant for the
   function form and one for the retirement, each killed by the right spec); an old `@param-tool` line fails the
   build loudly, never ignored; a line in `docs/retired-names.md`; `sprudel/ref/uitools.md` and
   `sprudel/ref/dsl-conventions.md` updated; the docs pages' specs checked (they show the tools per parameter).
   They wait with KLANG-00348 (KSNode.validate) until step 2 is committed: tell them.
   Found in step 1 (2026-10-27): only one variant of a function carries the tags today (`gain`'s pattern variant,
   not `String.gain` or the mapper's), so `"c3".gain(0.5)` shows no tool; the `@tool` lines should reach every
   variant of the function.
   Found in step 2's review (2026-10-27): the top-level `note(vararg note)` names the tool; `SprudelPattern.note` and
   `String.note` call the parameter `noteName`, so the merge by name leaves them without one. Settle it in step 3,
   when the note tool is embedded.
3. Panel only: the badge, the right-click menu (it opens the tab), the modal and the popover retired; the two
   modal-only tools embedded.
4. Specs for each; mutation checks; review rounds; the maintainer's look in the browser.

## Acceptance

Not set yet.

## History

- 2026-10-27: created.
- 2026-10-27: step 1 committed. The editor follows the cursor (`ToolCallFollower`) and finds the call through
  `toolCallAt` and `CallText` (klangscript). A site keeps one anchor, the call's `(`, and finds its argument again
  from the text at each read and commit (by the parameter's name, else its place). It looks again only after a cursor
  move or an edit by other hands, never after a tool's own. Four review rounds: a stale range, a lookup that orphaned
  ranges, a nested literal, the tool dropped by its own edit, and the vararg place, all fixed with specs; five mutation
  campaigns, every mutant killed. Open for the maintainer: a "Used on" jump from the Quick help into a call with a
  tool flips the panel to the Sprudel tab.
- 2026-10-27: step 2 committed after two review rounds. `@tool` in both forms (`KDocParser`, `EditorTools`, `KlangCallable.uitools`); a
  function's tools reach every variant; the 49 lines rewritten (19 got a call-level `@tool`); an old `@param-tool`
  fails the build (checked with a planted line). The editor lists the call's tools first, the parameter's only with
  a literal to edit or in an empty call, and none of the call's when the variant is ambiguous (an untyped receiver). Each
  whole-call tool is named on its anchor parameter too, so an ambiguous call keeps it in scalar mode. A `@tool`
  naming no parameter, or on a class or a property, fails the build. 13 mutants, all killed.

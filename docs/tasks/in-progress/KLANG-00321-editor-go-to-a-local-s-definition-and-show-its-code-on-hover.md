# KLANG-00321: Editor: go to a local's definition, and show its code on hover

> **Status (2026-10-21): IN PROGRESS.** Started: a worker builds the analyzer query, the jump and the hover.

## Why this exists

The maintainer's request (2026-10-21): Ctrl-click on a locally defined name jumps to its definition, and hovering
it shows its code. Today both treat a local like a library symbol: `AnalyzedAst.symbolAt` hands the editor a
synthesised `KlangSymbol` (`synthesizeLocalSymbol`), so Ctrl-click opens the docs page and the hover shows only the
kind and the type. Follows KLANG-00319 (editor local symbol completion), which made the analysis know the locals in
scope.

## Depends on / unblocks

Builds on KLANG-00319's `localsAt` and its stale-text mapping. Unblocks nothing.

## Decide before implementing

1. **Where the decision lives:** in the analyzer (`klangscript/intel`, rules register: AST walkers never in UI
   modules). The editor asks one question and moves the cursor.
2. **What the hover shows for a local:** today's kind and type line, then the declaration's code read from the
   current document: the whole `let` / `const` / `export` statement, at most 12 lines (then an ellipsis line), and
   for an arrow parameter the line that holds it. Recommendation as written; the cap is a constant.
3. **While the text does not parse:** the editor keeps the last good analysis (KLANG-00319). The query takes the
   current text and maps the cursor across the edit, as `localsAt` does; a declaration inside the edited stretch is
   not offered (no jump rather than a wrong one).

## The work

1. **Analyzer.** `AnalyzedAst.definitionAt(pos, text = source): LocalDefinition?`: for an identifier bound to a
   local, the offset of the declared name and the source range of its declaration (from the declaration node's
   `location`, recorded during the walk next to `LocalBinding`); null for a library name, a member, a literal, or the
   declared name itself. Shadowing follows the binding map, so the inner declaration wins.
2. **Ctrl/Cmd-click** (`DslEditorExtension.onClick`): a local moves the cursor to its declared name, scrolls it into
   view and keeps focus; Ctrl+Shift+click on a local does the same (no new tab). A library symbol still opens its
   docs page. The Ctrl-hover underline marks locals too.
3. **Hover** (`DslEditorExtension` hover, `hoverContent`): a local's popup adds the declaration's code (point 2),
   rendered like the docs' code samples.
4. **Tests** (`klangscript/src/jvmTest/kotlin/intel/`, hand-built registry, mutation-checked light tier):
   - a reference to a `let`, `const` and `export` finds its declaration;
   - an arrow parameter inside its body finds the parameter;
   - an inner declaration shadows an outer one;
   - a library name, a member name and the declared name itself find nothing;
   - a multi-line initializer gives the whole statement's range;
   - stale text: an edit above keeps the jump correct, and an edit around the declaration gives nothing.

## Acceptance

- The specs above green and each mutation-checked.
- Hand test (the maintainer): in Der Schmetterling, Ctrl-click a use of `pickupHumbucker` lands on its `let`; the
  hover over a use shows its code; Ctrl-click on `lowpass` still opens the docs.

## Out of scope

Go to the definition of a song's import (another song's export), and "find usages".

## History

- 2026-10-21: created; designed the same day (maintainer's request).
- 2026-10-21: IN PROGRESS. Started: a worker builds the analyzer query, the jump and the hover.

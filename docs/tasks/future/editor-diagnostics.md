# Editor diagnostics: the umbrella

Status: **parked by the maintainer (2026-09-29): "we will revisit the whole diagnostics topics later".** Opened
2026-09-29 as the one place that lists every diagnostics item, so the topic can be picked up as a whole. This file
holds no design of its own; each item keeps its detail in its own task, linked below.

## What "diagnostics" means here

Telling the author, in the editor, when a script says something the engine will not do, or when something failed
silently: a warning or an error with a source location, instead of a sound that is quietly different.

## The items

| item | where the detail lives | state (2026-09-29) |
|---|---|---|
| The analyzer's diagnostics in the editor (unknown functions, wrong-context calls, argument errors), the web worker | [`../klangscript-intellisense.md`](../klangscript-intellisense.md) | step 0 (the linter wired to `AnalyzedAst.diagnostics`) DONE 2026-09-09; the further analyzer tiers and the worker open |
| A misspelled mini-notation tweak is silently inert (`note("c3{swel}")`) | [`mini-notation-tweaks-followups.md`](mini-notation-tweaks-followups.md) section 1 | open; parked here 2026-09-29 |
| Warnings for voice doors an instrument does not hear, a string in a wet slot (phase 3 step 11) | [`editor-voice-door-diagnostics.md`](editor-voice-door-diagnostics.md) | deferred 2026-09-27 ("diagnostics tools come later once the design is fully settled") |
| Runtime errors in the editor with a clickable location | [`../runtime-errors-in-the-editor.md`](../runtime-errors-in-the-editor.md) | phases 1 to 3 shipped 2026-08-22; phase 4 has one decision left and the known gap below |
| A typo in a shape function discards the whole shape; errors at query time are not captured yet | [`../silent-shape-discard-on-error.md`](../silent-shape-discard-on-error.md), and the "KNOWN GAP" section of the runtime-errors task | open |
| Editor code completion for local symbols (a neighbour: the analyzer's scope, not a diagnostic) | [`../editor-local-symbol-completion.md`](../editor-local-symbol-completion.md) | open, a maintainer call |

## When it is picked up

Read the items together first: they share the analyzer (`klangscript`'s `AnalyzedAst`), the editor's linter source
(`klangscript-ui/.../codemirror/CodeMirrorComp.kt` and `CodeMirrorLinterDocument.kt`, with the offset
conversion in `klangscript/.../intel/AnalyzerDiagnosticOffsets.kt`) and sprudel's `SprudelDiagnostics`, and a design round over
all of them is cheaper than five separate ones. The V1 rows 19 and 20 in [`../_v1-scope.md`](../_v1-scope.md) point here.

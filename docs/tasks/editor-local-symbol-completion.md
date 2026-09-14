# Editor: code completion for local symbols (`let`, `const`, `export`, lambda params)

Opened 2026-09-14. Maintainer request during the Der Schmetterling guitar-rig session: the song now
declares some twenty stage presets (`pickupHumbucker`, `cab4x12`, `makeGuitar`, ...) and the editor
offers none of them. Completion today knows library symbols only.

## What exists

The analyzer already tracks locals; only the "visible at the cursor" query is missing.

| Piece | Where | State |
|---|---|---|
| Lexical scope stack mirroring the interpreter | `klangscript/src/commonMain/kotlin/intel/TypeScope.kt` | Built during analysis, discarded afterwards. `LocalBinding(name, type, kind, declPos)` already carries the declaration offset |
| Identifier -> binding map | `intel/AnalyzedAst.kt` `bindingMap` | Kept; used by hover |
| Local symbol synthesis (kind chip + inferred type) | `AnalyzedAst.synthesizeLocalSymbol` | Hover shows locals today, so the display side exists |
| Completion provider | `intel/CompletionProvider.kt` | `topLevelCompletions(prefix)`, `memberCompletions(type, prefix)`, `importCompletions(...)`. Library symbols only |
| Editor completion source | `klangscript-ui/src/jsMain/kotlin/codemirror/DslCompletionSource.kt` | Re-parses immediately (`processCodeImmediate`), then: after a dot -> members, else -> top-level library symbols |
| Stale-analysis policy | `klangscript-ui/.../EditorDocContext.kt` `processCode` | On a parse failure `lastAnalysis` is kept as-is ("stale AST is better than none for hover") |

## Design

Analyzer owns the decision (`klangscript/ref/intel-analyzer.md`); the editor stays dumb.

1. **`AnalyzedAst.localsAt(pos): List<TypeScope.LocalBinding>`** (`intel/AnalyzedAst.kt`).
   During the build, record one scope snapshot per block scope with its source range (the
   program itself, every arrow body, every block). `localsAt(pos)` walks from the innermost
   enclosing scope outwards and returns bindings with `declPos < pos`, inner shadowing outer, one
   entry per name. Arrow parameters are bindings already (`LocalKind.PARAM`), so `x => x.` inside a
   `configure` lambda offers `x` and `e` for free.
2. **`CompletionProvider.localCompletions(locals, prefix)`** (`intel/CompletionProvider.kt`).
   One `CompletionSuggestion` per binding: `kind = PROPERTY` for a value, `FUNCTION` when the
   inferred type is a function, detail `let` / `const` / `export` / `param` plus the inferred type
   when known (reuse `synthesizeLocalSymbol` so hover and completion say the same thing).
3. **Editor** (`DslCompletionSource.kt`, the non-member branch): prepend
   `localCompletions(docContext.lastAnalysis.localsAt(from), typed)` to the library suggestions.
   Locals first: the user typed them, they are the most likely intent.

## The half-typed line

While the user types `let gui`, the program does not parse, and `processCode` keeps the previous
`lastAnalysis`. Decision (maintainer, 2026-09-14, tentatively): **keep the last successful
analysis** and complete from it. Consequences to accept and test:

- Offsets in the stale analysis refer to the old text. Declarations *before* the edit point keep
  their offsets, so `localsAt(cursor)` still returns them; a declaration typed after the last good
  parse is simply not offered yet, which is fine, nobody completes a name with itself.
- Offsets *after* the edit point drift by the length of the edit. For a top-level cursor that only
  ever hides declarations further down, which are not visible anyway. Inside a block it can make
  the innermost scope resolve to the wrong block; the fall-back is the enclosing scope, so the
  result is "fewer locals", never wrong ones from a sibling. Cover with a test that edits inside a
  lambda body.
- If that turns out too loose, the next step is parser error recovery (parse to the last complete
  statement), not a change to the completion. Out of scope here.

## Tests (mutation-checked, engine-light tier)

In `klangscript/src/jvmTest/kotlin/intel/`:

- top-level `let`/`const`/`export` offered after their declaration, not before it;
- inner `let` shadows an outer one of the same name (one entry, inner type);
- arrow params visible inside the arrow body only;
- a binding declared in a sibling block is not offered;
- a local with an inferred type (`let g = Osc.saw()`) carries `IgnitorDsl` in the detail, a
  local with an unknown type carries none;
- stale analysis: `localsAt` on a program analysed before an insertion still returns the
  declarations above the insertion point.

## Out of scope

Completion of members on a local receiver (`guitar.` after `let guitar = ...`) already works
through `receiverTypeBeforeDot` when the type was inferred; do not touch it. Exports of *another*
song (`import { makeGuitar } from "peekandpoke/guitar-rig"`) are library symbols once imported
and are a separate topic (`future/federated-song-sharing.md`).

## Links

- Analyzer architecture: `klangscript/ref/intel-analyzer.md`.
- The intellisense plan this sits next to: `klangscript-intellisense.md`.
- The other editor-tools item: `editor-tools-named-arguments.md`.

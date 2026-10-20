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
- a local with an inferred type (`let g = Ignitor.saw()`) carries `IgnitorDsl` in the detail, a
  local with an unknown type carries none;
- stale analysis: `localsAt` on a program analysed before an insertion still returns the
  declarations above the insertion point.

## Out of scope

Completion of members on a local receiver (`guitar.` after `let guitar = ...`) already works
through `receiverTypeBeforeDot` when the type was inferred; do not touch it. Exports of *another*
song (`import { makeGuitar } from "peekandpoke/guitar-rig"`) are library symbols once imported
and are a separate topic (`docs/tasks/future/federated-song-sharing.md`).

## Done 2026-10-19

Built as designed, in three parts, with two adaptations (below).

- **`AnalyzedAst.localsAt(pos, text = source)`.** The analysis walk records a tree of `LocalScopeRange`s
  (`intel/LocalScopeRange.kt`): one per scope `TypeScope` opens (the program, every arrow body, every `if` and `else`
  block, every loop), with its source range and its bindings in source order. `localsAt` descends to the innermost
  range holding the cursor and walks outwards; a binding is offered when its `declPos` is before the cursor, the inner
  one wins per name, the innermost scope comes first. `declPos` is now set (it was declared but never filled): the
  `let` / `const` / `export` keyword when the initializer is an arrow (a recursive local sees itself), the end of the
  initializer otherwise (the interpreter defines the name after evaluating it: `let osc = run(os` offers no `osc`,
  `let g = g.gain(2)` in a block reads the outer `g`), the arrow's `=>` for a parameter. A name a closure only sees
  because it is declared further down is not offered.
- **`CompletionProvider.localCompletions(locals, prefix)`.** One suggestion per binding, `FUNCTION` when the inferred
  type is a function type, else `PROPERTY`; the detail is the kind in the hover's word, lower case, plus the type as
  the hover renders it (`let . IgnitorDsl`, `const`, `param`). The hover's synthesised symbol is not reused as an
  object, the two share the words (`LocalKind.display`, `KlangType.render()`).
- **Editor** (`DslCompletionSource.kt`, the non-member branch): the locals at the word's start come first, then the
  library names. CodeMirror sorts by match quality, then by label, so the order alone does not put them first; a
  local carries `boost = 1`, which lifts it above a library name that matches as well and below a better match.

**Adaptation 1, scope ends.** The AST keeps no closing brace (an arrow's location is its `=>`, a block keeps only its
statements), so a range runs from its opening token to the last token the walk met inside it. Explicit completion
(Ctrl+Space) on the blank stretch between a block's last statement and its `}` reads the enclosing scope: fewer
locals. A word being typed is part of the parse, so it never meets this. Recording the `}` would have meant new
fields on six AST nodes; not done.

**Adaptation 2, the stale analysis.** Checked against the code, "a declaration typed after the last good parse is
simply not offered" holds, but "offsets after the edit point only ever hide" does not: with the old offsets, typing
`const gu` on a blank line right above `let b = 2` offered `b` (its old offset lies before the new cursor), and an edit
at the end of one lambda could land the cursor in the old range of a sibling lambda and offer its parameter. So the
editor passes its current text and `localsAt` maps the cursor back across the edit (the common prefix and suffix of
the two texts): a cursor before or after the edit reads the same place in the analysed text, a cursor inside it the
edited stretch (the innermost scope that holds all of it, the declarations above it). When the typed text repeats
the text after it (`let q = foo(b)` typed in front of `let b`), the shared prefix and suffix overlap and the edit's
start is ambiguous; the stretch then starts at the earliest place it can (review round 1). The decision stands (complete
from the last good analysis); the mapping makes its stated consequence true: fewer locals at worst, never one from
below or from a sibling. Several separate edits since the last good parse count as one stretch, so they give fewer
locals, never wrong ones.

**Tests** (`klangscript/src/jvmTest/kotlin/intel/LocalSymbolCompletionSpec.kt`, hand-built registry), one per row
of the list above plus seven:

- "top-level let, const and export are offered after their declaration, not before it"
- "an inner let shadows an outer one of the same name: one entry, the inner type"
- "arrow parameters are visible inside the arrow body only"
- "a binding declared in a sibling block is not offered"
- "a local with an inferred type carries it in the detail, a local with an unknown type carries none"
- "stale analysis: an insertion that does not parse keeps the declarations above it, never one below it"
- "stale analysis: an edit inside a lambda body keeps that body's locals above it, never one below it"
- "stale analysis: an edit at the end of a lambda body never offers a sibling lambda's parameter"
- "stale analysis: typed text that repeats the text after it never offers the declaration after the cursor"
- "a let with a plain initializer is not offered inside it, an arrow initializer sees its own name"
- "inside its own initializer a shadowing let offers the outer binding it reads"
- "a scope two levels deep is found past its parent's own last token"
- "an arrow inside a template interpolation offers its parameter nowhere else" (the parser reads `${...}` on its
  own, so offsets there count from the interpolation; the walk gives a scope opened there an empty range)

Mutation-checked, each RED and restored (`cmp` clean): the `declPos < from` filter dropped, the per-name dedupe
dropped, the parameters' `declPos` moved past the body, the range end check dropped, the type left out of the detail,
the `FUNCTION` kind never chosen, the stale mapping replaced by the raw cursor, the interpolation guard dropped.
Review round 1 added: the edit start taken as the shared prefix again (red: the repeated-text test), every
declaration visible from its keyword (red: the two initializer tests), every declaration visible only after its
initializer, arrows included (red: the arrow and the shadowing tests), a child scope no longer widening its parent
(red: the two-levels test). The stale tests above `let b` and `below` use arrow initializers, so they still bind
the raw-cursor mutant now that a plain initializer moves `declPos` to its end.

**Hand test for the maintainer** (the editor part is not covered by a test):

1. In Der Schmetterling, below the stage presets, type `pick` on a new line: `pickupStock`, `pickupSingle`,
   `pickupHumbucker` and `pickupNeck` are offered with `let` in the detail, above any library name starting with
   `pick`. On a line above them (say under `let snareHz`), they are not offered.
2. Inside a configure lambda, `Ign.saw(x => x.gain(` then type `x` ... the lambda's `x` is offered (`param`, with
   the builder type when the callee declares it), and not on a line outside the lambda.
3. Inside a `const f = () => { let inner = 1 ... }`, a new line in the body offers `inner`; a line after the `}` does
   not.
4. On a new line above an existing `let b = ...`, type `const gu` (no `=` yet, the text does not parse) and press
   Ctrl+Space: names declared above are offered, `b` is not.
5. A local named like a library function (`let gain = ...`, then type `gai`): both entries show, the local first
   with `let` in the detail (see the open question).

**Hand test result (maintainer, 2026-10-20, before v0.7.2): passed.** Steps 1, 2, 3 and 5 as written. Step 4 as
written offers nothing, because the list keeps only names that start with the typed word and nothing in the song starts
with `gu`; a poor choice of letters in the step, not a fault. Tried as `const gu = ` plus Ctrl+Space (the text does
not parse there either), it offers the names declared above and not `b`. Nothing offered while a new name is being
typed is how it stays (maintainer).

**Open, for the maintainer.**

- A local that shadows a library name is offered next to the library entry (two `gain` rows). Dropping the library
  row would be a provider decision (`CompletionProvider`), not the editor's; not done.
- A local bound to an arrow (`const makeGuitar = (x) => ...`) shows as a variable without a type: the analyzer does
  not infer an arrow's type, so the `FUNCTION` kind only comes from a function-typed library value.

## Links

- Analyzer architecture: `klangscript/ref/intel-analyzer.md`.
- The intellisense plan this sits next to: `docs/tasks/klangscript-intellisense.md`.
- The other editor-tools item: `docs/tasks-archive/2026-09/20260927-editor-tools-named-arguments.md` (done 2026-09-26; the tool leftovers are in `docs/tasks/sprudel-ui-tools.md`).

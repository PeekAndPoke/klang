# KLANG-00321: Editor: go to a local's definition, and show its code on hover

> **Status (2026-10-21): DONE.** Hand-tested by the maintainer 2026-10-21: go to definition works.

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

## Done 2026-10-21

Built as designed, in three parts, with four adaptations (below).

- **`AnalyzedAst.definitionAt(pos, text = source): LocalDefinition?`.** The walk records a `DeclarationSite` (the
  declared name's offset, the declaration's start and end) on every binding, as `TypeScope.LocalBinding.site`, next to
  `declPos`. The query reads the word at the cursor in `text`; it answers only for an `Identifier` node of exactly that
  range and name that the walk bound to a local (`bindingMap`, so the inner declaration wins and a closure's read of a
  name declared further down finds it there). A member, a literal, a keyword and the declared name are no such node. The
  answer: `namePos`, `start`, `end` (exclusive), `code` and `symbol`, every offset in `text`. While `text` is ahead of the
  analysis, the word and the declaration are mapped across the edit with `localsAt`'s `editTo`: in place before it, moved
  after it, and a word or a declaration the edit touches answers null.
- **Ctrl/Cmd-click** (`DslEditorExtension.onClick`): a local moves the cursor to `namePos`, scrolls it into view and
  focuses the editor, Shift or not; a library name still opens its docs page. The Ctrl-hover underline marks locals,
  as it did (their word resolves).
- **Context menu** (review round 1): right-clicking a local offers "Go to definition: <name>", which moves the cursor
  as Ctrl/Cmd-click does; a library name keeps "Go to docs"; a local `definitionAt` cannot place offers neither.
- **Hover**: `hoverContent` takes the code as a second argument (`FlowContent.(symbol, declarationCode) -> Unit`,
  through `KlangScriptEditorComp` and `KlangCodeEditorComp`), and `KlangSymbolDocsComp` shows it under the signature
  in a "Declaration" block, the popup's code-sample block with its copy button. The code: the whole lines of the
  statement, their common indentation removed, at most `LocalDefinition.MAX_CODE_LINES` (12) and then a `...` line;
  for a parameter the line that holds it. The popup is rebuilt when the word's range or what it names changes
  (review round 1; it was keyed by the word's text, so moving from the local `gain` onto the member `gain` in
  `run(gain.gain)` kept the local's popup). The docs-hover extension `HoverPopupCtrl.scheduleShow(doc, ...)`
  (`klangui/.../KlangDocsHoverPopupCtrl.kt`) had no caller left and is removed.

**Interpolation collision, fixed (review round 1).** An AST node is a data class, and a `${...}` is parsed on its own,
so the `x` at 2:3 of an interpolation equals the `x` at 2:3 of the source. Written into the binding map later, it
replaced that one's entry: in `` let x = 1\n  x\nconst f = (x) => `${\n  x}` ``, line 2's `x` jumped to the parameter.
Identifiers inside an interpolation now stay out of `bindingMap`; the named-argument check still treats those that read
a local as locals (`TypeMapBuilder.interpolationLocals`, joined to its set), so its results do not change. What it
changes elsewhere: `symbolAt` on such a node shows the library name (or nothing) instead of the local, and
`localBindingOf` no longer finds it. Both only matter where the editor's absolute cursor lands on a node indexed at
its relative offset, which is the same collision: hover, the param tools and completion inside an interpolation never
worked by position and work no worse. Completion (`localsAt`, `namedArgumentsAt`) does not read `bindingMap` there.
Left as they are: the type map and `AstIndex`'s offset and parent maps, keyed by node too, still collide (recorded in
`klangscript/MEMORY.md`, Lessons).

**Adaptation 1, where the range comes from.** The design reads the range "from the declaration node's `location`".
That location is the `let` / `const` / `export` keyword alone, an arrow's is its `=>`, a parameter is a bare string with
no location, and no node keeps a closing bracket. So the walk reads the rest back from the source, by the lexer's rules
(the lexical helpers of `ArgumentSlotAt`, now `internal`, reused): the name is the word after the keyword (blanks and
comments read past); the end runs from the last token the walk met in the statement until every bracket opened since
the keyword is closed (`foo(\n  a,\n)` ends after its `)`); a parameter's offset is read back from the text in front of
the `=>` and checked against the parsed names. No AST change. Limits: a parameter list with a comment in it gets no site
(no jump); a statement whose last line holds only tokens the walk does not meet outside any bracket (`: null` of a
ternary on its own line) loses that line in the hover.

**Adaptation 2, the answer carries the code and the symbol.** The code is cut in the analyzer (`LocalDefinition.code`),
so the popup only renders a string and the cut is tested on the JVM. The symbol is there because `symbolAt(pos)` reads
the raw cursor in the analysed text: while the text does not parse, it can name another word than the one under the
mouse. The editor asks `definitionAt` first and, for a local, shows the definition's symbol with its code, so the jump,
the popup and its code always describe the same local.

**Adaptation 3, a local with no place opens nothing.** Ctrl/Cmd-click on a local `definitionAt` cannot place (the edit
since the last parse touches it) opened the docs search for its name before; it now does nothing ("no jump rather than
a wrong one"; `symbol.origin` is `Local`, the same switch `KlangSymbolDocsComp` uses for "View docs").

**Adaptation 4, an edit that starts at a declaration's end.** "An edit around the declaration gives nothing" is read as
"overlaps it". An edit that starts exactly where the statement ends keeps it: typing on a new line right below a
declaration makes `editTo` start the edit at the end of that statement (the shared newline is ambiguous), and the jump
there is still right.

**Tests** (`klangscript/src/jvmTest/kotlin/intel/LocalDefinitionSpec.kt`, hand-built registry), one per row of work
step 4 plus one:

- "a reference to a let, const and export finds its declaration"
- "an arrow parameter inside its body finds the parameter"
- "an inner declaration shadows an outer one"
- "a library name, a member name and the declared name itself find nothing" (a literal and a keyword too)
- "a multi-line initializer gives the whole statement's range"
- "stale text: an edit above keeps the jump correct, and an edit around the declaration gives nothing"
- "stale text: a word inside the edited stretch finds nothing, even where the old text has the same word in place"
  (review round 1: a renamed parameter and typing at the end; mapped in place, the body's `a` would jump to the outer
  `let a` instead of the parameter)
- "an identifier inside a template interpolation never takes the place of an equal one outside it" (review round 1)
- "the hover's code: the declaration's whole lines without their common indentation, at most 12, then an ellipsis"

The multi-line row also checks an arrow's block body: `const f = () => {\n  run(1)\n}` ends with its `}` line
(review round 1: with braces not counted every row stayed green).

Mutation-checked with `mutate.py` (11 mutants, each KILLED for its own row's clue; restored byte-identical, final run
green): the name offset set to the keyword; `export` left without a site; the parameters read in reverse order; the
binding looked up by name at the top level (shadowing); the binding looked up by name among the locals in scope (a
member named like a local); the closing brackets dropped from the statement's end; the declaration not moved by an edit
above; a declaration the edit touches kept; the word not mapped back across the edit; no line cap; the indentation
kept. Review round 1 added three, each KILLED for its row (restored byte-identical, final run green): interpolation
identifiers written into the binding map again (namePos 25, the parameter); braces not counted; a word inside the
edited stretch mapped in place. A sweep over the 15 builtin songs (a temporary spec, deleted): all 787 references to a local get a definition,
each `namePos` holds the name; in Der Schmetterling the `pickupHumbucker` in `rhythmRig` lands on its `let` (line 45)
and `lowpass` answers null.

**Hand test for the maintainer** (the editor part is not covered by a test):

1. In Der Schmetterling, Ctrl-click (Cmd on a Mac) the `pickupHumbucker` in `rhythmRig`'s `serial(...)`: the cursor
   lands on the name in `let pickupHumbucker` (line 45), scrolled into view, the editor keeps focus. Ctrl+Shift-click:
   the same, no new tab.
2. Hover that use: the popup shows the LET chip, the signature and a "Declaration" block with the statement's three
   lines; the copy button copies them.
3. Ctrl-click `lowpass`: the docs page opens as before (Shift: a new tab); its hover has no "Declaration" block.
4. Hold Ctrl and move over the code: locals and library names are underlined.
5. A lambda parameter (`x => x.lowpass(...)`): Ctrl-click the `x` in the body lands on the `x` before `=>`; the hover
   shows that line.
6. While the text does not parse: type `const gu` on a new line above `let pickupHumbucker`, then Ctrl-click the use in
   `rhythmRig`: it lands on the moved `let`. Then break the `let pickupHumbucker` statement itself (add a `(` after
   `x => x`): Ctrl-click on the use does nothing until the text parses again.
7. Hover a use of a declaration longer than 12 lines: 12 lines, then `...`.
8. Right-click a use of `pickupHumbucker`: "Go to definition: pickupHumbucker" moves the cursor to its `let`;
   right-click `lowpass`: "Go to docs: lowpass" as before.
9. In a line like `run(gain.gain)` with a local `gain`, hover the first `gain` (its Declaration popup), then move
   straight onto the member `gain`: the popup changes to the member's docs.

**Open, for the maintainer.**

- `symbolAt` still reads the raw cursor while the text does not parse (as before this ticket): a library name's hover
  and Ctrl-click below an unparsed edit can name the word that stood there in the old text. Mapping its cursor the
  same way would fix it; not done (outside this ticket).

## History

- 2026-10-21: created; designed the same day (maintainer's request).
- 2026-10-21: IN PROGRESS. Started: a worker builds the analyzer query, the jump and the hover.
- 2026-10-21: built (`AnalyzedAst.definitionAt`, the jump, the hover's code), `LocalDefinitionSpec` green and
  mutation-checked; four adaptations recorded under "Done"; the hand test is the maintainer's.
- 2026-10-21: review round 1 clean of CRITICAL and MAJOR; its MINOR batch applied: the interpolation collision in the
  binding map, rows for the block body's `}` and a word inside the edit, "Go to definition" in the context menu, the
  hover popup keyed by range and meaning, the unused docs-hover extension removed.
- 2026-10-21: IN PROGRESS. Built and reviewed (round 1 clean, the MINOR batch applied, 14 mutants KILLED); waits for the maintainer's hand test.
- 2026-10-21: DONE. Hand-tested by the maintainer 2026-10-21: go to definition works.

# KlangScript named arguments — docs-polish remainder

> Split from `klangscript-named-arguments.md` (~85% done). The core feature — parser, runtime,
> `createFunction` builders, KSP default extraction, the `NamedArgumentChecker`, and optional-param
> signature rendering — **shipped**. Full record: `../tasks-archive/2026-06/20260616-klangscript-named-arguments.md`.
> Priority: **low** (small polish on a done feature).

## Open items

- **7.2 — Usage-styles panel.** In the library docs page, render both a positional and a named-arg
  example per callable (`buildPositionalExample` / `renderUsageStyles`). Not built.
- **7.3 — KDoc conventions + `@sample` sweep.** Create `klangscript/ref/kdoc-conventions.md` and add
  `@sample` blocks across the stdlib so generated docs carry runnable examples. Not built.
- **6.4, param-name completion inside `(`.** Done 2026-10-19, see below.

## Done

### 6.4, param-name completion inside `(` (done 2026-10-19)

Where an argument starts in a call to a callable the analysis knows, completion offers the callable's parameters that
no argument names yet, as `name = `. The blocker named here (the `[]` linter stub of `klangscript-intellisense.md`)
was gone since 2026-09-09.

**Built.** `AnalyzedAst.namedArgumentsAt(pos, text)` returns the unfilled parameters in declaration order;
`CompletionProvider.namedArgumentCompletions(params, prefix)` turns them into suggestions; the editor
(`DslCompletionSource.kt`, the non-member branch) puts them first. The pieces under it:

- `intel/ArgumentSlotAt.kt`: reads the argument list around the cursor from the editor's text alone (the innermost
  open `(`, its slots, which are `name = value`, which positional), with the lexer's rules for comments, strings and
  backtick strings.
- `callableCalledBy` (in `NamedArgumentChecker.kt`): the checker's own callable resolution, moved out of the class so
  the check and the completion share it and cannot drift apart.
- `AnalyzedAst.editTo`: the edit mapping of `localsAt` (common prefix and suffix), factored out; `localsAt` and
  `namedArgumentsAt` both use it.

**Decisions**, each with its reason:

1. **Mixing.** The check refuses a call that mixes positional and named arguments (all-or-nothing, decision 2 of the
   archived record). So a positional argument anywhere in the call, before or after the cursor, means nothing is
   offered: a name there is always an error. The brief's "filled by a positional one before it" therefore never
   arises in a call that can still take a name. Kotlin would allow positional-then-named; KlangScript does not.
2. **Where.** The cursor (the start of the word being typed) must stand right after the `(` or a `,` of the innermost
   open `(`, with nothing but blanks, comments and that word in its slot, before the word and after it. So nothing
   after a value has begun (`freq = 8|`), inside a string, a comment or a template literal, inside `[...]` or
   `{...}`, at the start of a name already written (`(|freq = 1)` would become `freq = freq = 1`), or in front of a
   value that is not one word (`(|"c")`). A value of one word right after the cursor (`(|800)`, `(|x)`) reads as the
   word being typed, so the names are offered there, and accepting one gives `freq = 800`. The innermost `(` is the
   call: in a nested call's list the nested callable's parameters apply, and once it is closed the outer call's
   again. Only the call whose own `(` stands there answers: the index covers a call from its callee to its last
   argument (the `(` token is zero-width), so at a grouping paren the node found is the call around it.
3. **Which callable.** The check's resolution, so the completion offers exactly the parameters the check checks
   against. An unknown name, a receiver of unknown type (`x => x.lowpass(`) and a local function offer nothing; a
   local shadows a library name as everywhere in the analysis.
4. **Varargs.** Offered by name like any other parameter: the check accepts it, and by name it takes an array
   (`children = [...]`). The detail says `vararg <type>`, the description says it takes an array. A positional
   vararg call offers nothing (rule 1).
5. **Text and analysis.** While the text does not parse the editor keeps the last good analysis, which cannot know
   what was typed since. So the filled names come from the text, and only the callee from the analysis, and only
   when the edit cannot have changed it: the edit lies after the call's `(`, and the call is not inside an arrow
   that a `let` / `const` / `export` binds. An edit in front of the `(` can change the receiver (`Ign.saw()` edited
   to `note("c")`), turn a grouping paren into a call, or declare a local that shadows the callee; an arrow a
   declaration binds can read a declaration from further down, the edit's included. With the edit after the `(` the
   text up to the `(` is the analysed text, offsets included, so no mapping is needed. A call inside a template
   interpolation is never used (its offsets count from the interpolation). Everything else offers nothing until the
   text parses again; with closeBrackets that is usually at once (`foo(` becomes `foo()`, and a trailing comma
   parses). Review round 1 found the earlier, looser rule (the `(` mapped back across an edit in front of it, the
   callee's name compared) offering a wrong callable's parameters in two probes; both are tests now.
6. **Shape.** The list shows `name =`, accepting inserts `name = `, the CodeMirror type is `property`. The detail, in
   the `a . b` style of the other completions: the type as the signature renders it, then `required`,
   `default <value>` (the documented default), or `optional`; a vararg shows `vararg <type>` alone. `required` is
   worth showing: the check reports a required parameter a named call leaves out.
7. **Ranking.** Above the song's locals (`boost = 1`) and the library names, in declaration order: the boost is
   `1 + n - index` for the n offered names. At an argument start of a known call its own parameter names are the
   most specific suggestion, and the signature's order reads better than CodeMirror's alphabetical one. CodeMirror
   adds the boost to the match score, whose kinds of match lie 100 apart, so a local or library name that matches
   better (an exact case match against a case-folded one) still comes first.
8. **No list on `(` alone.** The existing rule stays: an empty word opens the list only on Ctrl+Space. A list after
   every `(` would get in the way of the many positional calls (`note("c e g")`), and Enter would accept a name.

**Tests** (`klangscript/src/jvmTest/kotlin/intel/NamedArgumentCompletionSpec.kt`, hand-built registry mirroring real
signatures: `Ign.saw`, `IgnitorDsl.lowpass` and `adsr`, `Ign.variants`, sprudel's `note` and `gain`):

- "at the start of an argument list every parameter is offered, in declaration order"
- "after named arguments the unfilled parameters are offered, never one named before or after the cursor"
- "commas inside strings and backtick strings split no arguments, an escaped quote ends no string"
- "a positional argument anywhere in the call offers nothing: the check refuses mixing the two styles"
- "a value begun in the slot, or anything after the word in it, offers nothing"
- "inside a string, a backtick string or a comment nothing is offered"
- "a call inside a template interpolation never answers for the call at its offset"
- "a nested call offers its own parameters, the outer call's once the nested one is closed"
- "an unknown callable, a local function, a bracket and an arrow body offer nothing"
- "a method call on a receiver offers the method's parameters"
- "a vararg parameter is offered by name, where it takes an array; a positional vararg call offers nothing"
- "a suggestion shows `name =`, inserts `name = `, and says the type and the default"
- "stale analysis: the call comes from the last good analysis, the filled names from the text being typed"
- "stale analysis: an edit in front of the call's `(` offers nothing until the text parses again"
- "a grouping paren is no call: the call around it does not answer for it"
- "stale analysis: a grouping paren the edit made a call offers nothing"
- "stale analysis: a call in an arrow a declaration binds offers nothing, an arrow passed as an argument does"

Mutation-checked after review round 1: 30 mutants, each applied, run, restored with `cp` (`cmp` clean) inside one
lock call, every one RED, every test red under at least one: the word at the cursor not skipped; the filled names not
filtered; a positional argument no longer stopping the offer; `==` and `=>` each read as a named argument's `=`; a
value begun before the cursor ignored; anything after the word ignored; a cursor at the end of a line comment counted
outside it; the interpolation guard dropped; the outermost bracket taken for the innermost; a closing bracket no
longer closing; a nested call's commas splitting the slots after the cursor; a local no longer shadowing the library
name; the scan accepting `[` and `{`; the receiver type unused; `vararg` left out of the detail; the vararg note left
out of the description; the label without ` =`; `required` left out; the prefix not filtering; the edit rule dropped
(red: the edit in front of the `(`); the call's own `(` check dropped (red: the grouping paren); both dropped
together (red as well: the aligned grouping-paren probe, which each of the two refuses alone); the declared-arrow
rule dropped; that rule taking any arrow (red: the arrow passed as an argument); backtick strings not skipped;
backslash escapes not honoured; an unterminated block comment ending right after its `/*`; the call's `(` taken as
the end of its callee (red: blanks before the `(`); a blank slot after a trailing comma read as positional.

**Open, for the maintainer.**

- After a named argument only a name is valid at a slot start (a value there mixes the styles), yet the list still
  shows the locals and the library names below the parameter names. Dropping them there would be a provider
  decision; not done.
- A value that is not one word is not offered a name in front of it (`(|"c")` to `pattern = "c"`); a value of one
  word is (`(|800)` to `freq = 800`).
- Adding a named argument as a new line inside a multi-line call offers nothing until a comma is typed:
  `lowpass(\n    freq = 800,\n    |q\n    configure = 1,\n)` and `lowpass(\n    |\n    freq = 800,\n)` read the next
  argument as part of the cursor's slot. A possible remedy: end the check of the rest of the slot at a newline when
  the next line starts with `name =`. Not built (review round 1).
- A call on line 1 is hidden by any template interpolation elsewhere in the file:
  ``Ign.saw().lowpass(|)\nlet s = `${Ign.saw().adsr(1, 2)}` `` offers nothing, the same call on line 2 works. The
  parser reads an interpolation on its own, so its nodes carry offsets counted from the interpolation; the deeper
  interpolated node wins the index lookup and is refused. Safe (fewer names), a quirk of the existing index.
- While the text does not parse, an edit anywhere in front of the call, or a call inside an arrow a declaration
  binds (`const lead = () => Ign.saw().lowpass(|)`), offers nothing: the price of never offering a wrong callable's
  parameters (decision 5).
- A local arrow function's parameters are not offered: the analysis keeps no parameter list for a local, though the
  runtime accepts named arguments on script arrows. A possible follow-up.

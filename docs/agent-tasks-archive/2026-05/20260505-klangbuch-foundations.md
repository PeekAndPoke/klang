# Projekt Klangbuch — Foundations

> **Archived**: 2026-05-05
> **Status**: Done. Foundation lands; remix mechanism proven end-to-end.
> **Vision memos** (still active, not archived): `.claude/vision/projekt-klangbuch.md`,
> `.claude/vision/projekt-klangbuch-rollen.md`.

## Context

Built the language and infrastructure foundations for **Projekt Klangbuch** — the
internal codename for Klang's planned canon of code-as-music. Each of the original
inline songs in `BuiltInSongs.kt` was split into its own file, and a new `export name = expr`
form was added to KlangScript so songs can expose named parts that other songs import
under namespaced URIs (e.g. `peekandpoke/tetris`). A first cross-song remix was written
to prove the chain end-to-end.

Bigger picture is in the vision memos. This doc records what was actually built.

## What was built

### 1. KlangScript: `export name = expr` declaration form

New top-level statement combining a `const`-style immutable binding with auto-export
under the same name. Replaces the verbose `const x = ...; export { x }` pattern with
a single line that makes the public surface obvious at the declaration site.

- **AST**: `ExportDeclaration(name, initializer, location)` in
  `klangscript/src/commonMain/kotlin/ast/Ast.kt`.
- **Parser**: `parseExportDeclaration()` in
  `klangscript/src/commonMain/kotlin/parser/KlangScriptParser.kt`. Disambiguates
  `export {` (re-export) from `export <name> =` (declaration) by peeking after the
  `export` token.
- **Interpreter**: branch in `executeStatement` of
  `klangscript/src/commonMain/kotlin/runtime/Interpreter.kt`. Evaluates the
  initializer, defines the binding immutably, and calls `env.markExports`.
- **Three other Statement-visitors** updated to handle the new node:
  `ast/AstIndex.kt`, `intel/AnalyzedAst.kt`, `intel/NamedArgumentChecker.kt`.
- **Returns the bound value** (unlike `let` and `const` which return `null`). This
  lets a script ending on `export song = stack(...)` flow the song out as the script's
  return value — no trailing reference needed.
- **Tests**: 21 cases in
  `klangscript/src/commonTest/kotlin/ExportDeclarationTest.kt`. Parsing, evaluation,
  immutability, regression coverage that let/const remain null-returning,
  multi-line return-value, importable from another module, namespaced URI imports,
  forward-compat error for unresolved namespaced URIs.

### 2. KlangBlocks: `KBExportStmt`

New statement type mirroring `KBLetStmt`/`KBConstStmt`. Codegen emits `export name = ...`,
canvas component renders the keyword. Round-trip test in
`klangblocks/src/commonTest/kotlin/model/ExportDeclarationRoundTripTest.kt` (12 cases).

### 3. BuiltInSongs refactor: per-song files

`src/commonMain/kotlin/BuiltInSongs.kt` shrunk from 696 → 54 lines. Each of the 11
original entries lives in its own file under
`src/commonMain/kotlin/builtinsongs/<SongName>.kt`. `BuiltInSongs` is now a thin
registry: 11 typed `val` references plus `songs: List<Song>`. Same external API
(`BuiltInSongs.derSchmetterling.id` etc.) — all callers (`StartPage`, `SidebarMenu`,
`CodeSongPage`, `BuiltInSongsModule`) work unchanged.

`TestTextPatterns.kt` trimmed: `tetris`, `aTruthWorthLyingFor`, `strangerThingsNetflix`,
and `smallTownBoy` migrated into per-song files; `TestTextPatterns.active` (a stale
playground toggle) removed; `Main.kt` updated to use `BuiltInSongs.aTruthWorthLyingFor.code`.

### 4. Cross-song imports: `BuiltInSongsModule.kt`

`src/commonMain/kotlin/BuiltInSongsModule.kt` registers each built-in song as a
klangscript library under an explicit `peekandpoke/<song-slug>` URI (no aliasing, no
auto-derivation from `Song.id` — the URI is the contract, permanent once published).
Wired into all three engine setup sites (`Main.kt`, `Cli.kt`, `player.kt`).

```klangscript
import { song }   from "peekandpoke/der-schmetterling"
import { bass }   from "peekandpoke/tetris"
import { lead, bass } from "peekandpoke/tetris@1.0"   // version pin syntax parses; resolver ignores in v0
```

### 5. Source-tagging: ghost-highlight fix

When `import { x } from "peekandpoke/tetris"` runs, the imported library used to be
parsed with `source = null`, identical to the main script's locations — so when an
imported pattern's events fired, the editor rendered highlight marks at line/column
positions that exist only in the imported file, not in the file shown.

Two changes:

- `Interpreter.executeImport` now passes `importStmt.libraryName` to
  `KlangScriptParser.parse(...)` so every SourceLocation produced from imported
  code carries `source = "peekandpoke/tetris"`. (Sprudel mini-notation already
  preserved `source = base.source` on derived atom locations — verified, no fix
  needed in sprudel.)
- `CodeMirrorHighlightBuffer` gained a `var currentSource: String? = null` and a
  `.filter { it.source == currentSource }` step in `scheduleHighlight`. Plumbed
  through `KlangCodeEditorComp` as a prop with a `setCurrentSource(source)` setter.

Ghost highlights from imports are now silently dropped when the editor's source
identity doesn't match.

### 6. Convention: parts must be arrangement-free

When refactoring a song into the new export form, **arrangement timing
(`filterWhen`, time-gating predicates) lives at the song level — never inside the
exported parts**. An importer wants the part fully voiced and ready to play; baked-in
`filterWhen` forces every consumer to inherit the original song's arrangement.

Saved as a project-memory entry:
`/home/gerk/.claude/projects/-opt-dev-peekandpoke-klang/memory/feedback_klangbuch_parts_arrangement_free.md`.

### 7. Tetris rewrite + dub remix (proof-of-concept)

`builtinsongs/Tetris.kt` rewritten to expose:

- **Patterns** (`leadPattern`, `bassPattern`, `subPattern`, `drumsPattern`) — raw musical content.
- **Shapes** (`leadShape`, `bassShape`, `subShape`, `drumsShape`) — synthesis-chain functions.
- **Parts** (`lead`, `bass`, `sub`, `drums`) — fully voiced, arrangement-free.
- **Song** (`export song = stack(...)`) — assembled with `filterWhen` arrangement at the stack level.

`builtinsongs/TetrisRemix.kt` (new) — "Echo um Echo", a dub-style remix. Imports
`leadPattern` and `bassPattern` from `peekandpoke/tetris`, applies different
sound shaping (sine lead with tape-delay + big reverb, sub-heavy bass, dub one-drop
drums + offbeat skank). First Klangbuch entry to demonstrate cross-song imports
end-to-end.

Registered at `peekandpoke/tetris-echo`.

### 8. KlangScript docs

Section 2 (Variables & Constants) and Section 12 (Imports & the Standard Library)
in `klangscript/src/commonMain/kotlin/docs/KlangScriptDocContent.kt` updated:

- New runnable example for `export name = expr` in Section 2.
- Section 12's old comment-only "Selective and aliased imports" example replaced
  with three separate examples (selective / aliased / namespace) plus a
  "Defining a library" example showing `export` + namespaced URI shapes.
- `klangscript/ref/language-design.md` and
  `klangscript/language-features/01-literals-and-variables.md` updated to mention
  the new form and its expression-return behaviour.
- `klangscript/ref/feature-catalog.md` row added for `ExportDeclaration`.
- `klangscript/MEMORY.md` recent-work note added.

## Test coverage

- **klangscript** (JVM + JS): full suite green. `ExportDeclarationTest` (21 tests),
  `ExportImportTest` (15 tests, including a regression that imported library AST
  locations carry the library URI as `source`).
- **klangblocks** (JVM + JS): full suite green. `ExportDeclarationRoundTripTest`
  (12 tests).
- **klang module smoke test** (new, in `src/jvmTest/kotlin/BuiltInSongsSmokeTest.kt`):
  every song in `BuiltInSongs.songs` (12 entries including the new `tetrisRemix`)
  compiles to a non-null `SprudelPattern` under the same engine setup the user-facing
  app uses (sprudel + builtin-songs registered + no-op Osc registrar). Catches
  regressions in any future song refactor.

## Build config side-effect

The klang root `build.gradle.kts` was missing `useJUnitPlatform { }` configuration
on its test tasks — kotest tests in `src/jvmTest/` were compiled but never discovered.
Added `Deps.Test.configureJvmTests()` call inside the `tasks { }` block (matching the
pattern in `audio_be` and `klangscript`). This was a pre-existing gap, surfaced
because no klang-module tests existed previously.

## What's deferred

Per the vision memos — this foundation is *one feature* in a longer arc. Out of
scope for this archive entry, listed only so the trail is clear:

- **Refactor of the other 9 BuiltInSongs entries** to the new export-form. Currently
  only Tetris is rewritten. The other 9 still use the inline `stack(...)` style
  with no exposed parts. Each one is mechanical — a per-song decision about
  granularity (which parts to expose, what shapes to factor).
- **Klangbuch product surface** — no canon browser, no contributor flow, no
  "view library source" panel in the editor. The infrastructure supports it but
  the UI doesn't exist yet.
- **Hash-pinning / version semantics** — `peekandpoke/foo@1.0` parses cleanly but
  the resolver ignores the version. Real versioning waits until there's a community
  asking for it.
- **Network resolution** — only locally-registered libraries resolve. Remote
  Klangbuch comes after Drop #5 (per `project_sound_first` memory).

## File pointers (quick index)

### Vision (active)

- `.claude/vision/projekt-klangbuch.md`
- `.claude/vision/projekt-klangbuch-rollen.md`

### Language

- `klangscript/src/commonMain/kotlin/ast/Ast.kt` — `ExportDeclaration`
- `klangscript/src/commonMain/kotlin/parser/KlangScriptParser.kt` — `parseExportDeclaration`
- `klangscript/src/commonMain/kotlin/runtime/Interpreter.kt` — eval branch + import source-tagging
- `klangscript/src/commonTest/kotlin/ExportDeclarationTest.kt`
- `klangscript/src/commonTest/kotlin/ExportImportTest.kt` — regression for source-tagging

### Block editor

- `klangblocks/src/commonMain/kotlin/model/KBBlock.kt` — `KBExportStmt`
- `klangblocks/src/commonMain/kotlin/model/KBCodeGen.kt` — codegen
- `klangblocks/src/commonMain/kotlin/model/AstToKBlocks.kt` — conversion
- `klangblocks/src/jsMain/kotlin/ui/KlangBlocksVariableStmtComp.kt` — UI overload
- `klangblocks/src/commonTest/kotlin/model/ExportDeclarationRoundTripTest.kt`

### Canon registry

- `src/commonMain/kotlin/BuiltInSongs.kt` — pure registry
- `src/commonMain/kotlin/BuiltInSongsModule.kt` — explicit registration under `peekandpoke/<slug>`
- `src/commonMain/kotlin/builtinsongs/*.kt` — 12 per-song files (11 originals + TetrisRemix)
- `src/commonMain/kotlin/Song.kt` — unchanged
- `src/jvmTest/kotlin/BuiltInSongsSmokeTest.kt` — sweep test

### Editor

- `src/jsMain/kotlin/codemirror/CodeMirrorHighlightBuffer.kt` — `currentSource` filter
- `src/jsMain/kotlin/comp/KlangCodeEditorComp.kt` — prop + setter

### Docs (KlangScript-internal)

- `klangscript/src/commonMain/kotlin/docs/KlangScriptDocContent.kt` — sections 2 & 12
- `klangscript/ref/language-design.md`
- `klangscript/ref/feature-catalog.md`
- `klangscript/language-features/01-literals-and-variables.md`

# CodeMirror bump: the browser checklist

Status: **open (2026-10-18), for the maintainer's hand test** of the dependency bump on branch `deps-ultra-0.110`
(Kotlin 2.4.21, Ultra 0.110.0, and the CodeMirror minors: state 6.7.6, view 6.43.14, commands 6.11.1,
language 6.13.1, autocomplete 6.20.3, lint 6.9.7, @lezer/highlight 1.2.5; codemirror 6.0.2, lang-javascript
6.2.5 and the minimap 0.5.2 unchanged). Archive this file when the test passes; it can be reused for the next bump.

One editor component serves every host (`klangscript-ui/src/jsMain/kotlin/codemirror/CodeMirrorComp.kt`). The app
binds no keymap of its own: Play and Run are buttons.

| Host | Route | Hover, menu, tools | Autocomplete | Lint | Minimap | Playback highlight |
|---|---|---|---|---|---|---|
| Song editor | `/song/code` | yes | yes | yes | yes | yes |
| Tutorial and library examples | `/manuals/tutorials/...` | yes | yes | yes | no | yes |
| KlangScript REPL | `/manuals/klang-script`, executable library samples | no | yes | yes | no | no |
| MIDI playground | `/midi-playground` | no | yes | yes | no | no |

## 1. `/song/code` (the full set)

- [ ] The editor mounts, the code shows, typing changes the playing pattern.
- [ ] Loading or switching a song replaces the code.
- [ ] Syntax colours: calls blue, strings green, comments grey, keywords violet, numbers chalky.
- [ ] Hover a DSL name (`note`, `s`): the docs popup, top or bottom right of the visible area.
- [ ] Hold Ctrl/Cmd over a function: gold underline; it clears on keyup and when the mouse leaves.
- [ ] Ctrl/Cmd+click opens the docs; Ctrl/Cmd+Shift+click opens them in a new tab.
- [ ] Right-click an argument: the menu ("Go to docs" and the tools).
- [ ] The small gold tool badge appears just above an argument that has a tool.
- [ ] A tool commit: press Update twice with values of different lengths; the second replaces exactly the first.
- [ ] Autocomplete: type `no`, the popup with icons, detail line and docs panel.
- [ ] Autocomplete after a dot: `note("c").` lists the members.
- [ ] Autocomplete in `import ... from "`: the library names. Nothing pops up inside other strings.
- [ ] Lint: an unknown argument name gives a squiggle, a gutter marker, and the panel opens at the bottom.
- [ ] Clicking a panel entry moves the cursor to the error and focuses the editor.
- [ ] Squiggles move with the text while typing (they do not jump).
- [ ] Play with a runtime error: its squiggle appears and the analyzer squiggles stay.
- [ ] Minimap beside the editor, overlay shown; it does not overlap the playback highlight.
- [ ] Play: gold boxes pulse on the playing tokens, stay aligned while scrolling vertically and horizontally,
      and never cover the line numbers.
- [ ] Stock features: Ctrl+F search panel (styling too), undo/redo, Ctrl+/ comment, fold gutter, bracket matching,
      auto-close brackets, active line, multiple selections.
- [ ] Theme in dark mode: selection, cursor, tooltip arrow, autocomplete popup, lint panel's selected entry,
      lint points.

## 2. A tutorial page

- [ ] As section 1, without the minimap.

## 3. `/manuals/klang-script` REPL

- [ ] Run with an error: a squiggle; the next edit clears it.
- [ ] Autocomplete works.

## 4. `/midi-playground`

- [ ] Load a saved instrument: the editor mounts again.
- [ ] Autocomplete and lint work.

## Where a bump is most likely to break (from the inventory, 2026-10-18)

- **Two copies of a package.** If `@codemirror/state` or `view` is loaded twice, facets stop matching: completions,
  the minimap or the linter silently do nothing, or the console shows "Unrecognized extension value" or "multiple
  instances of @codemirror/state". Check the browser console first. `@codemirror/autocomplete` is now a direct
  dependency of `klangjs` (before it came only through `codemirror` and lang-javascript).
- **CSS on CodeMirror's internal class names** (`CodeMirrorTheme.kt`): `.cm-selectionLayer .cm-selectionBackground`,
  `.cm-tooltip-arrow`, `.cm-panel-lint li.cm-diagnostic[aria-selected=true]`, `.cm-lintPoint-*::after`.
- **The lint plumbing** (`CodeMirrorComp.kt` around 405 to 490): `needsRefresh`, `forEachDiagnostic` handing back the
  same objects, an empty dispatch plus `forceLinting` to rerun.
- **`basicSetup` used as a JS array** (`basicSetup.asDynamic().concat(...)`, `CodeMirrorComp.kt:185`): if it ever
  stops being one, no editor starts.
- **The playback highlight** assumes `view.dom` does not scroll and `scrollDOM` does (`CodeMirrorHighlightBuffer.kt`).
- **The minimap** is pre-1.0; it needs the same state and view instances as the editor.
- **The playback highlight's Pixi overlay** (found 2026-10-18): webpack 5.108 dropped pixi's side-effect import
  `app/init.mjs`, so `app.ticker` was undefined ("Failed to initialize PixiJS highlight overlay ... reading 'add'")
  and the page froze. Klang first fixed it with a webpack rule (`webpack.config.d/04-pixi-side-effects.js`, commit
  0684195c); since Ultra 0.112.0 the pixijs addon registers pixi's extensions itself and the rule is gone. After a
  pixi.js, webpack or Ultra bump, check that the highlights show and the console is clean.

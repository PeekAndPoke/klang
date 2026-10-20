# KLANG-00317: Minimap in the code song editor

> **Status (2026-10-17): DONE.**

**Done 2026-10-17 (maintainer checked it in the browser: "looks great").**

## What we built

A minimap (a small overview of the whole song, like the one in VS Code) on the right of the CodeMirror editor on
`CodeSongPage`. The embeddable editors (`PlayableCodeExample` and friends) stay without one.

- **Package:** `@replit/codemirror-minimap` 0.5.2 (MIT). CodeMirror 6 has no minimap of its own. All its CodeMirror
  and Lezer dependencies are peers, so the build keeps one copy of `@codemirror/state` and `@codemirror/view`.
  Added in `Deps.kt` (`codemirrorMinimap()`) and `klangjs/build.gradle.kts`; `kotlin-js-store/yarn.lock` updated
  with `kotlinUpgradeYarnLock`.
- **Externals:** `klangjs/src/jsMain/kotlin/codemirror/ext/Minimap.kt` (`showMinimap`, `MinimapConfig`).
- **Opt-in flag:** `showMinimap: Boolean = false` on `KlangScriptEditorComp` and `KlangCodeEditorComp`; only
  `CodeSongPage` sets it. Settings: `displayText = "blocks"`, `showOverlay = "always"`.
- **Scrolling:** the minimap mounts into `view.scrollDOM` and follows its scroll position, so on `CodeSongPage` the
  editor now scrolls itself instead of the page's outer div. The outer div clips (`overflow: hidden`) and carries
  `klang-editor-fill`, a rule in `klang.css` that passes the height down to `.cm-editor` (the minimap's own theme
  sets that to `height: 100%`). The highlight overlay already offsets by `view.scrollDOM.scrollTop`, so it needed
  no change.
- **Credits:** `CREDITS.MD` and `CreditsPage.kt`.

## What the package lets us steer

- Config: `displayText` (`blocks` or `characters`), `showOverlay` (`always` or `mouse-over`), `gutters` (line to
  colour strips left of the minimap), `eventHandlers`, `create` (the container element).
- From our theme for free: text colours (syntax highlighting) and the selection colour.
- CSS: `.cm-minimap-overlay` (the viewport box, grey at 0.2 opacity), `.cm-minimap-gutter`,
  `.cm-minimap-box-shadow`.
- Fixed in the package: the zoom (`Scale.SizeRatio = 4`, used by drawing, overlay, click and drag math), the
  maximum width (120 px), the lint colours (`#d11`, `orange`, `#999`).

## Left open, not planned

- **Zoom:** not configurable. A CSS scale would break click and drag. The ways out are an upstream PR adding a
  `scale` option, vendoring the one-file package (unusual Gradle wiring, a "complexity is the enemy" consult), or a
  Kotlin port. The maintainer is fine with 4x (2026-10-17).
- **Ideas:** `gutters` could mark sounding lines or `arrange` sections; the overlay could take the accent colour.

## Gotcha

Inside a `jsObject { }` lambda in a Kraft component, `dom = ...` binds to the component's own `dom` property, not
the object being built. The minimap's `create` builds its result as a plain `dynamic` holder for that reason.

## History

- 2026-10-20: numbered KLANG-00317 (was `docs/tasks-archive/2026-10/20261017-code-song-minimap.md`).

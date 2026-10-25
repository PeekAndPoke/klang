# KLANG-00333: Editor: headline comments shown in the minimap, to jump between sections

> **Status (2026-10-25): IDEA.** The maintainer's idea, to visit later.
>
> **Part of:** KLANG-00332 (UX comfort: the everyday helpers of the editor)

## Why this exists

The maintainer (2026-10-25), from IntelliJ's minimap, which shows the names of classes and their parts as readable
headlines over the scaled-down code: "It would be cool if we have a special comment type in KlangScript like
"# HEADLINE 1" or "## HL 2" ... markup style ... and be able to show these in the minimap as well. This would be handy
for defining sections that can then easily be navigated to."

A song is one long file; its parts (intro, drums, the bridge, the master) are found today by scrolling. A headline the
author writes, readable in the minimap and one click away, makes a long song easy to move around in.

## What we know (2026-10-25)

- **KlangScript comments are `//` and `/* */`** (the lexer, `klangscript/src/commonMain/kotlin/parser/KlangScriptParser.kt`);
  `#` alone is no comment, so a headline must live inside a comment to stay valid code: for example `// # Drums` and
  `// ## Fill`, markdown style. The lexer and the interpreter need no change: the editor reads the headlines from the
  text.
- **The minimap is `@replit/codemirror-minimap` 0.5.2** (`klangjs/src/jsMain/kotlin/codemirror/ext/Minimap.kt`). Its
  options: `displayText`, `showOverlay`, `gutters` (coloured marks left of the minimap, line number to CSS colour),
  `eventHandlers` on its element, and `create`, which hands us the element it draws into. It has **no text labels**:
  a mark per headline line works out of the box, a readable headline needs our own layer over the minimap, placed by
  the line's position in it.

## Decide before implementing

- The syntax: `// #` and `// ##` (markdown levels), or a word (`// HEADLINE`, `// HL 2`). How many levels?
- In the minimap: marks only, readable labels (our own layer), or both. Does a click on a label jump there?
- In the editor itself: is the headline line styled (bigger, bold, its own colour) so it reads as a heading there too?
- Navigation beyond the minimap: a list of the song's sections (a dropdown, a key from the key map, KLANG-00325) and
  "next / previous section"?
- Do headlines show anywhere else: the song's page, the Klangbuch, the tutorials?

## The work

To be written when the decisions above are made.

## Acceptance

To be written with the work.

## History

- 2026-10-25: created as an idea (the maintainer's note, from IntelliJ's minimap); part of the epic UX comfort.

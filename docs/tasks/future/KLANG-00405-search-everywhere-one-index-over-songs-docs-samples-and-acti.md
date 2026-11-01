# KLANG-00405: Search everywhere: one index over songs, docs, samples and actions for the Motor menu

> **Status (2026-11-01): IDEA.** Its own ticket by the maintainer's word (2026-11-01: "yes search index needs its own
> ticket, this is quite a complex topic").

## Why this exists

The Motor menu's search field (KLANG-00403; Shift Shift, Ctrl+K, `KlangKeyBindings.SearchEverywhere`) keeps what one
types and nothing reads it: `WorkbenchLayout`'s `query` goes nowhere. The redesigned menu shows the results in four
columns (songs, docs, samples, actions), which needs an index over all of them. Until this ticket lands, the field
stays as it is today.

## What exists to build on

- `LibraryDocSearch` (`KlangScriptLibraryDocsPage.kt`): terms and prefixes (`category:`, `tag:`, `function:`) over the
  KlangScript library's symbols.
- `LearnSearch` (`LearnTracksColumn.kt`): the Learn column's search text, a stream.
- The songs (the built-ins and the user's, `SongStorage`), the samples' index (`Samples.Index`), the key actions
  (`KlangKeyBindings`).

## Open questions

- **What is in it:** songs (titles, maybe their code), docs (Lexikon, Sprudel, Stdlib, KlangScript, the manuals and
  tutorials), samples (banks and names), actions (the key actions and the destinations).
- **Matching and ranking:** words, prefixes, typos; what ranks first across kinds; the existing prefixes kept.
- **Building it:** at start, lazily on the first search, or ahead of time in the build; its size in the bundle.
- **Where it lives:** global state, so in the Spine (KLANG-00389); a page's own search (the docs, Learn) asks the
  same index for its part.
- **Keys:** arrows through the results across the columns, Enter opens; what the empty field shows (the menu as
  designed).

## History

- 2026-11-01: created, split from KLANG-00403's search columns.

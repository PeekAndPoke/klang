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
- The songs (the built-ins and the user's, `spine.songs` since KLANG-00422), the samples' index (`Samples.Index`), the key actions
  (`KlangKeyBindings`).

## What it finds (the maintainer's list, 2026-11-01, and proposals)

The maintainer: "1. docs entry, f.e. 'lpf' ... will show the brief when found 2. lexikon entries ... will show a brief
as well 3. appearance in current song, with line number and line preview 4. Anything else?"

| Kind | Matches on | Shows | Opens / does |
|---|---|---|---|
| Docs entry (Sprudel, Stdlib, KlangScript functions) | the name and its aliases (`lpf` is `lowpass`'s), tags | the brief | the entry |
| Lexikon entry | the word, its aliases | the brief | the entry |
| The current song (on a song page only) | the code, line by line | the line number and the line | the editor at that line |
| Songs (the maintainer: "4. would find songs too") | the title (the built-ins and yours) | the RPM, built-in or yours | the song |
| Tutorials (proposal) | a lesson's and a track's title | the track, done or not | the lesson |
| Samples (proposal) | a bank's and a sound's name (`bd`, `piano`) | the bank, how many | a preview on the `sounds` lane (KLANG-00406) |
| Actions and pages (proposal) | the key actions' titles (`KlangKeyBindings`), the pages' names | the key | runs it, goes there |
| Later: examples in the songs | a function's name in the built-in songs' code | the song, the line | the song at that line |

## Ranking (proposal)

- **How well it matches:** the whole name, then the start of the name, then the start of a word in it, then anywhere
  in it, then a near miss (a typo); an alias counts as the name.
- **Where it matches:** the name before a second line (a brief, a sub), before the body (a song's code).
- **On a tie, by kind, always in the same order:** docs entries, Lexikon, the current song, songs, tutorials, samples,
  actions and pages. Docs first because the search is mostly used while writing code; the order fixed (not by the
  page one is on) so a hand learns where things are, as with the menu itself.
- **How it shows:** one ranked list, each row with its kind's mark; the brief of the row under the cursor beside the
  list, as IntelliJ's search does. Replaces the four columns of the menu's mockup (KLANG-00403), which cannot rank
  across kinds.

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
- 2026-11-01: what it finds, the maintainer's three kinds and proposals for more; a ranking with a fixed order of kinds
  on a tie; one ranked list with a preview in place of the columns.

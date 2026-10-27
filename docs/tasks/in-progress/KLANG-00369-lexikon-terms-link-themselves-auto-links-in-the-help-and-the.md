# KLANG-00369: Lexikon terms link themselves: auto-links in the help and the Lexikon, the hand-written links swept

> **Status (2026-10-27): IN PROGRESS.** started
>
> **Part of:** KLANG-00334 (UI workbench: the Motor-centred layout, tool windows and the first-visit trail)

## Why this exists

The maintainer, 2026-10-27: "Should we auto-apply links to the lexikon in the help prose? Would be nice ... less
manual links to manage", and that it "also need[s] a docs-sweep". Today 81 links to the Lexikon are written by hand in
26 KDoc files by a first count (that count held generated build sources too: the sources had 26 links in 21 files), and a new entry is linked nowhere until
someone writes the links.

## Decided (the maintainer, 2026-10-27)

1. "Each lexikon entry should announce alternative words that link to it." Each entry lists the words that link to it
   (`linkWords`); its own term among them where that is a clear name. Category words ("Mapping", "Mixing", "Tuning")
   link only where listed, so ordinary prose is not linked all over.
2. Hand-written links stay allowed, "but then we need to be careful that the autolinking does not place links inside
   links ... but this is anyway the case for any link in the docs." The auto-linking never touches a link, its text,
   code, or a word a hand-written link already links.
3. The Lexikon entries' own texts link to each other ("yes it should"), never to themselves.

## The rules

- First mention per text only (a description, a parameter's description, an entry's detail).
- Whole words, any case, the longest word first ("orbit bus" before "orbit").
- Never inside code (inline or fenced), a link, its text, or a URL; a slug a hand-written link in the same text
  already points to is not linked again.
- A pure function on the markdown, in common code, tested on the JVM.

## The work

1. `LexikonEntry.linkWords`, filled for the entries; the auto-linking function and its spec.
2. The Quick help's descriptions and parameters linked; the Lexikon's details rendered as markdown and linked, a
   link in the Lexikon tab staying in the tab.
3. The sweep, after a heads-up to Klang-Main-Dev (sprudel, klangscript-libs): the hand-written links the
   auto-linking covers removed; a check that every word they linked is still linked.
4. Whether the docs pages get it too: proposed, one call more, when the help has proven it.
5. Review loop, mutation checks; the maintainer's look in the browser.

## Acceptance

- A Lexikon term in a help text or an entry links to its entry without a hand-written link; no link lands inside a
  link or code; the hand-written links the auto-linking covers are gone.

## History

- 2026-10-27: created from the maintainer's request and decisions; IN PROGRESS.
- 2026-10-27: built. `LexikonEntry.linkWords` (32 entries; the category words left out), `lexikonAutoLinks` (common,
  `LexikonAutoLinksSpec`), used by the Quick help (description and each parameter), the hover popup and the docs
  pages' descriptions, the Lexikon tab and the Lexikon page (an entry's detail now markdown, linked, never to
  itself; a link followed stays in the tab, or in the app on the page). The sweep: the first count of 81 included
  generated build sources; the sources held 26 hand-written links, 25 removed in 20 KDoc files (sprudel,
  klangscript-libs; Klang-Main-Dev agreed), each word checked to link itself; one kept as an example in a KDoc of
  `QuickHelpView`. `LexikonSpec`'s link row now checks that hand-written links resolve and that the scope words
  (voice, orbit bus, send) link themselves. A repeated alternation in the link pattern overflowed the JVM's stack on
  a whole source file: unrolled, and a row guards it. sprudel (3501 tests) and klangscript-libs (1118) green after
  the sweep; the new rows mutation-checked.
- 2026-10-27: review round 1 (level 2): 1 MAJOR, bare "voice"/"voices" linked a unison's and the super oscillators'
  copies inside one note to Voice (one sounding note), and took unison's "per voice" link away; the Voice entry now
  links only "per voice". "per orbit bus" linked as "per orbit" with "bus" outside: its own word now. KDoc references
  (`[orbit]`) are never auto-linked (the hover popup and the docs pages saw them raw). The pattern built once for the
  real Lexikon; no lookbehind (older Safari). The "each word checked" above held for 23 of 25 then; with these fixes
  the swept phrases link again.
- 2026-10-27: review round 2 (level 2): clean (the real KDocs run through a port: no bare voice link left, the 25
  swept phrases link whole, no reference linked). Its MINOR applied: a row on the real Lexikon pins the two data
  fixes; its NIT: an unclosed `[` swallows nothing. Committed.

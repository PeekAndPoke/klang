# KLANG-00365: The bottom panel's tabs: Quick help and the Lexikon, Lexikon links that stay on the page

> **Status (2026-10-27): IN PROGRESS.** next, after the Quick help round
>
> **Part of:** KLANG-00334 (UI workbench: the Motor-centred layout, tool windows and the first-visit trail)

## Why this exists

The maintainer, 2026-10-27, on a Lexikon link in the Quick help ("orbit bus" in `reverb`'s description): "Another
nice tool-window would be the lexikon, so that when i click here [...] i do not leave the editor page but another tab
in bottom tool is opened with the lexikon entry. This means that the Lexikon linking must be context-sensitive."

And on where the tabs go: "the tabs should be here" (in the bottom panel's header, where its title is now); "the
tool specific options need to go away from the header, only pin and close stay there". A pin and a close per tab
would only mean "pin all or none and close all or none", so the panel keeps one of each.

And for a new user: "a new user should see the quick help open and pinned", its empty text explaining what to do
(that text made larger and centred the same day, under KLANG-00349).

## Decided (the maintainer, 2026-10-27)

- The bottom panel's header holds its tabs, then one pin and one close for the whole panel.
- A tab's own options (the Quick help's "Show on hover" and its Alt+Q hint) move into the tab's content.
- On the song page a Lexikon link in the help opens the Lexikon tab at that entry; elsewhere a Lexikon link still
  goes to the Lexikon page.
- With no kept layout (a first visit), the Quick help is open and pinned.

## Decide before implementing

- Built as proposed (the maintainer saw it in the browser): Alt+Q always shows the Quick help tab; a hover updates the
  Quick help but does not switch away from the Lexikon. The Lexikon tab without an entry shows the terms to pick
  from; the open tab is kept with the layout.

## Later, when a second case comes

The maintainer, 2026-10-27: "link handlers similar to the key handlers ... But let us not overdo this now... Should
this kind of linking come up more often then we can build this idea. Now we have only one case so it would be
overkill." Today the page that shows the help decides: `QuickHelpView`'s `onLexikon`, given only by the song page.
The idea for later: a page with a Lexikon tab registers as the Lexikon's opener, as the keys register in layers, and
every Lexikon link anywhere (the docs, the tutorials, the entries themselves) lands there while that page is open.

Where it would likely live (the maintainer, 2026-10-27): "It would roughly fit into the KeyManager when you could
register other actions than key presses... basically gestures", so "a likely place to live for this is the
KeyManager which would then need a new name like InteractionManager or similar... there we would reuse all the
existing things like that layers etc." That is: the key manager (`KeysManager`, workshop, KLANG-00325) grows from
key presses to interactions in general (a link followed, a gesture), renamed to fit, and a Lexikon link becomes one
more interaction a layer can answer, with the layers, the modals' layers over the page and the rest as they are.
Klang-Main-Dev (2026-10-27, noted in KLANG-00325's out of scope, 4df2093f): the key manager lives in `workshop/`,
bound for Kraft, so growing its scope and renaming it is settled with Ultra-Main-Dev before it moves, not after.

The second case came (the maintainer, 2026-10-27, on the aliases in the Quick help linked to their docs pages: "Here
we have the second case for "catching" a link i guess, so the InteractionManager comes closer"). The places that
catch a plain click on a link today, each with its own check of the keys held: `appLink` (`comp/AppLink.kt`, Learn
and the aliases), `KlangBackLink` (klangui), the Lexikon tab's `followInPlace` (`LexikonPanel`) and the Quick
help's Lexikon links (`QuickHelpView`'s `onLexikon`). One rule for "a plain left click, no key held" in one place is
the first step, whoever owns the manager then.

## The work

1. klangui: the bottom panel takes tabs (`KlangWorkbench.Bottom` with tabs, the header's title slot drawing them; a
   tab is a key, not a drag handle); the open tab in `KlangWorkbench.Layout`.
2. The Quick help tab: "Show on hover" and the Alt+Q hint in its content.
3. The Lexikon tab: an entry view on the looks' roles; the help's Lexikon links routed to it on the song page.
4. The first visit's layout: the Quick help open and pinned.
5. Specs, mutation checks, the review loop; the maintainer's look in the browser.

## Acceptance

- The song page's bottom panel has the tabs Quick help and Lexikon in its header, then only pin and close.
- A Lexikon link in the Quick help opens its entry in the Lexikon tab and the page stays.
- A first visit shows the Quick help open and pinned; a reload keeps the tab that was open.

## History

- 2026-10-27: created from the maintainer's requests while the Quick help was tuned (KLANG-00349).
- 2026-10-27: IN PROGRESS. The tabs in the tool window's header (`KlangToolWindow.Tab`), the bottom panel's tabs and
  the tab kept with the layout (`KlangWorkbench.Tab`, `Layout.bottomTab`); `showBottom(tab, onlyIfClosed)`, so Alt+Q
  shows the Quick help and a hover leaves the Lexikon open; the Quick help's options at its headline's end, its Docs
  a quiet link after the labels; `LexikonPanel` (the terms by domain with a search, an entry with "All terms" at the
  left); a Lexikon link in the help opens the tab on the song page (a plain click; with a key held, the page); a
  first visit with the Quick help open and pinned; a Lexikon entry for Perlin noise, linked from `perlin`'s KDoc.
- 2026-10-27: a Lexikon entry for Pitch (the maintainer: "We need a lexikon entry for "Pitch" as well").
- 2026-10-27: the Quick help's parameters one under the other, the description under the signature, not indented, a
  thin line between them (a table with a grid tried and dropped); KDoc references (`[semitones]`) shown as code. One
  search for the Lexikon page and tab (`LexikonEntry.matchesSearch`). Reviewed (level 2), clean, its MINORs applied:
  the tabs pressed keys in a group (the tab pattern promised arrow keys they do not have), the focus kept after a
  switch in the Lexikon tab, the brushed role's own swatch in the kitchen sink, the Perlin entry's "never repeats"
  taken back. The new rows mutation-checked. Committed with the brushed metal (KLANG-00366).
- 2026-10-27: IN PROGRESS.

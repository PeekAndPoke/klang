# KLANG-00365: The bottom panel's tabs: Quick help and the Lexikon, Lexikon links that stay on the page

> **Status (2026-10-27): SCHEDULED.** next, after the Quick help round
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

- Proposed: Alt+Q always shows the Quick help tab; a hover updates the Quick help but does not switch away from the
  Lexikon while the user reads there, so the mouse passing over the code does not take the entry away.
- Proposed: the Lexikon tab without an entry shows the terms to pick from; the open tab is kept with the layout.

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

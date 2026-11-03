# KLANG-00409: The bottom panel: one entry point for a page's tabs, the tools on every page owned by the layout

> **Status (2026-11-03): IN PROGRESS.** Steps 1 to 5 built, reviewed and mutation-checked (the maintainer: "work on
> the central tool window registration"; D1 to D5 taken as proposed, D3 with one change, below). Waits for the
> maintainer's look at the panel across pages.

## Why this exists

The maintainer (2026-11-03): "How does the bottom bar know which tabs to show. Does a page register the tabs it needs
and are there tabs that are always present? Let us streamline this mechanism, so that new pages have a simple
entrypoint. Please draft a ticket and impl design proposal". Just before that: "this icon here should always be the
same, the toolbox icon. It appears to be different on different pages" (fixed at once in `BottomTools`, see History).

## How it works today

- **The slots.** `WorkbenchSlots` (`src/jsMain/kotlin/layouts/WorkbenchSlots.kt`, KLANG-00349) holds three streams a
  page fills: `column`, `tools` (tool windows) and `bottom`, a whole `KlangWorkbench.Bottom` (title, icon, tabs, the
  walkthrough's beacon step). `WorkbenchLayout` reads them and draws `KlangWorkbench`.
- **The tabs on every page.** `WorkbenchLayout` passes the page's panel through `BottomTools.with(page, unseenErrors)`,
  which adds the MIDI tool (KLANG-00402) and the Console (KLANG-00407) after the page's tabs. A page with no panel gets
  a panel of its own, "Tools", with `ambient = true`: it never opens by itself, only when one of its tabs was the last
  one chosen.
- **The pages' tabs.** Three pages fill the bottom slot, all with the same panel, `QuickHelpPanel.bottom()`: title
  "Help and tools", the wrench icon, the tabs Quick help, Lexikon, Sprudel (the song page only) and Visuals, and the
  first-visit walkthrough's step on its key.
  - `CodeSongPage`: also the column and the Settings tool window.
  - `TutorialPage`: also the column.
  - `KlangScriptLibraryDocsPage`: the bottom only.
- **Each page fills and empties by hand.** On mount it skips the inert copy fading out after a page change, builds its
  parts, remembers them and puts them in. On unmount it empties the slots only when they still hold its own parts.
  The three pages each carry a copy of this (about 25 lines each), with small differences: two empty everything with
  `clear()`, one only the bottom.

What this costs:

- **No simple entry point.** A new page with a tab needs the slot dance and the guards, and must build a whole
  `Bottom` (title, icon, walkthrough step) for what is only a list of tabs.
- **Visuals is in the wrong place.** It shows the app's visuals, not anything of the page, yet it is there only on the
  three help pages; on the songs list or the Looks page there is no Visuals tab.
- **The panel's look varies by page.** Its title says "Help and tools" on three pages and "Tools" on the rest. Its
  icon did too, until today. Its key wears the walkthrough's beacon only where the help panel is.
- **Two behaviours.** A page's own panel, pinned, comes back with the next page that has one; flying in, it goes with
  its page (KLANG-00349). The "Tools" panel never opens by itself (`ambient`). Which one you get depends on whether
  the page has tabs.

## The proposal

**A page gives tabs, never a panel.** The panel itself (title, the toolbox, the walkthrough's beacon, the count) is the
layout's, the same on every page. A page says only which tabs it adds.

**Two kinds of tab:**

- **The app's tabs, on every page**, in this order: Visuals, MIDI, Console. A list in one place (`BottomTools`, or a
  `BottomTabs.always`), owned by the layout.
- **A page's tabs**: Quick help, Lexikon, Sprudel. They come first, before the app's tabs, as they are about what
  the page shows.

**One entry point for a page:** a small helper that owns the fill and empty dance, so a page writes one call:

```kotlin
// In a page's init: the helper fills the slots on mount, empties what it filled on unmount, and skips the inert
// copy fading out after a page change
private val workbench = PageWorkbench(this) {
    column { SongColumn(ctrl = ctrl, builtIn = builtIn, onReset = { askToReset() }) }
    tool(id = "settings", title = "Settings", icon = { sliders_horizontal }) { SongSettings() }
    bottomTabs(helpPanel.tabs())
}
```

- `PageWorkbench` (in `layouts/`) takes the component, adds its own `lifecycle { onMount; onUnmount }`, and keeps the
  inert check and the "only what I filled" check in one place.
- `WorkbenchSlots.bottom` becomes `bottomTabs: List<KlangWorkbench.Tab>`, empty for none.
- `QuickHelpPanel.bottom()` becomes `tabs()`: Quick help, Lexikon and Sprudel, no title, no icon, no Visuals.
- The Visuals tab's content moves out of `QuickHelpPanel` into its own small file (`visuals/VisualsTab.kt`), listed
  with MIDI and the Console.
- `BottomTools.with(pageTabs, unseenErrors)` builds the one `Bottom`: title "Tools", the toolbox, the walkthrough's step
  on its key, the page's tabs then the app's, the count on the Console.

A page that adds nothing writes nothing: it gets the app's tabs.

## Decide before implementing

- **D1: Visuals on every page.** Proposal: yes, with MIDI and the Console. The visuals walkthrough's first step (the
  panel's key) then works from any page, not only from a help page.
- **D2: One title.** Proposal: "Tools" everywhere, the toolbox everywhere (done today for the icon).
- **D3: Does the panel open with a page?** Today a page's panel reopens with the page when it was open, and the
  "Tools" panel never opens by itself. With one panel on every page, proposal: it stays as you left it, open or
  closed, across pages; a page never opens it on its own. When it is open on a tab that the next page does not have,
  it shows the first of the app's tabs. **Built (2026-11-03) with one change:** it shows the next page's first tab
  (its own first, the Quick help on a help page; the Visuals where a page has none), the tab chosen is kept, and back
  on a page that has it, it shows again. Flying in or pinned alike: only a workbench with no panel at all still
  closes one flying in.
- **D4: Order.** Proposal: the page's tabs first, then Visuals, MIDI, Console. Alternative: the app's tabs first, so
  they never move.
- **D5: Tool windows and the column.** Proposal: through the same helper (`column`, `tool`), as above, so a page has
  one place for all its parts. Their behaviour stays as it is.

## The work

1. `PageWorkbench` in `src/jsMain/kotlin/layouts/`: the builder (`column`, `tool`, `bottomTabs`), the lifecycle, the
   inert check, emptying only what it filled. Specs: fills on mount, empties on unmount, leaves a newer page's parts
   alone, does nothing from an inert copy.
2. `WorkbenchSlots.bottom` becomes `bottomTabs`; `BottomTools.with` takes the page's tabs and builds the one panel
   (title, icon, beacon step, count). The Visuals tab moves to `visuals/VisualsTab.kt` and joins the app's tabs.
   `BottomToolsSpec` follows.
3. `QuickHelpPanel.bottom()` becomes `tabs()`; the three pages use `PageWorkbench` and lose their own fill and empty
   code.
4. The panel's opening rule as decided in D3, in `KlangWorkbench` (the `ambient` flag may go). `KlangWorkbenchSpec`
   follows.
5. `WorkbenchSlots`' and `PageWorkbench`'s KDoc say in three lines how a page adds a tab (not `/kraft-knowhow`:
   that skill is shared through Mission Control, and this is Klang's own layout).

## Acceptance

- A new page adds a tab with one `PageWorkbench` call; no slot code in the page.
- The panel's key, title and icon are the same on every page; Visuals, MIDI and the Console are on every page.
- The song page, a tutorial and the docs page show their help tabs as before; Alt+Q still shows the Quick help.
- The panel's open state across pages follows D3, with a spec.

## History

- 2026-11-03: created. Fixed at once beforehand, outside this ticket: the panel's key wears the toolbox on every page
  (`BottomTools.ICON`, the maintainer: "should always be the same, the toolbox icon").
- 2026-11-03: IN PROGRESS. The maintainer: "work on the central tool window registration"; D1 to D5 taken as
  proposed. The same day, before it: the key's icon became the tools the song page had (the maintainer: "i was wrong
  please replace it with the other 'tools' icon that we already had on the song page"), commit f2e8961a5.
- 2026-11-03: steps 1 to 5 built. `PageWorkbench` (`layouts/`) with its spec; `WorkbenchSlots.bottomTabs`;
  `BottomTools.with(pageTabs, unseenErrors)` builds the one panel ("Tools", the tools icon, the walkthrough's step,
  the page's tabs, then Visuals, MIDI, Console); `visuals/VisualsTab.kt`; `QuickHelpPanel.tabs()`; the song page, a
  tutorial and the docs page each one `PageWorkbench` call; `KlangWorkbench.Bottom.ambient` gone, D3 in
  `KlangWorkbenchSpec`.
- 2026-11-03: review round 1 (level 1): no BLOCKER or MAJOR; its MINORs and NITs applied in one batch (braces in
  `PageWorkbench`, each slot's guard and the Visuals tab's walkthrough step under test, comments and two tickets,
  KLANG-00389 and KLANG-00402, pointed here). Mutation-checked: 11 mutants, 11 killed for the expected reason.

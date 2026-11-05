# KLANG-00412: Alt+P opens the page of the song that plays

> **Status (2026-11-05): IN PROGRESS.** Built, reviewed and mutation-checked. Waits for the maintainer's try of the
> key.

## Why this exists

The maintainer (2026-11-05): "We have the status bar now. Clicking on a song that plays sends the user back to that
page, which is good. A keyboard shortcut would be handy. Which shortcut would you use?"

## Decided (maintainer, 2026-11-05)

- **D1, the key: Alt+P**, the song that Plays. It joins Alt+M (the Motor menu), Alt+V (the volume) and Alt+Q (the
  help); nothing in Klang uses it, and no browser menu does (Alt+S would, Firefox's History).
- **D2, when.** Only while the status bar shows a song (KLANG-00406): not on that song's own page, not with nothing on.
  It does what a click on the title does, and its key hint shows on the status bar like the other hints.

## Acceptance

- Alt+P anywhere else opens the playing song's page; on its own page, or with nothing playing, it does nothing.
- Specs: the binding opens the page, and stays off when the status bar shows no song.

## History

- 2026-11-05: created from the maintainer's request.
- 2026-11-05: built: `KlangKeyBindings.OpenPlayingSong` (Alt+P), bound in the status bar's title button while the bar
  shows (`statusBarView(openKey = true)`); the kitchen sink's and the Looks page's bars bind none. Reviewed,
  mutation-checked.

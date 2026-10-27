# KLANG-00372: Lock the song page while live-coding: no navigating away by accident

> **Status (2026-10-27): IDEA.** the maintainer's idea, 2026-10-27
>
> **Part of:** KLANG-00334 (UI workbench: the Motor-centred layout, tool windows and the first-visit trail)

## Why this exists

The maintainer, 2026-10-27: "on the code song page we should have some feature that locks the ui, so that the user
cannot accidentally navigate away. Would be terrible during live-coding when the music suddenly stops. We could maybe
achive this through a router middleware that would reject all navigation... not sure if this is already possible."

Leaving the song page stops the playback. During a live set one stray click (the drawer, a link in the help, the
Motor's mark, Back) ends the music.

## What Kraft has today (read 2026-10-27)

- Route middleware exists (`kraft/core/src/jsMain/kotlin/routing/middleware.kt`, `Router.navigateToWindowUri`):
  a `RouterMiddlewareFn` mounted around routes returns `Proceed` or `Redirect`. There is no "reject".
- It runs for the route being navigated TO, after the browser's address has already changed (the strategy pushed
  it), so even a redirect back to the song would leave a history entry and remount the page, which stops the music.
- So a lock needs something Kraft does not have: a guard asked before the address changes, able to say no and leave
  the page and the address as they are (for `navToUri`, link clicks and `popstate`, the Back key). That is a request
  for Ultra (`/mission-control-link` section 5), as a router feature every app can use.

## Decide before implementing

- How the lock is switched: a key on the song page ("Lock"), on by itself while playing, or both; how it shows that
  it is on (a lamp).
- What happens on an attempt to leave: nothing, a quiet note, or a question ("Stop the music and leave?").
- The browser's own ways out (reload, closing the tab, typing an address): only `beforeunload`, which asks with the
  browser's own words; on while the lock is on.
- What stays allowed inside the page: the help, the Lexikon, the tool windows; everything that does not leave it.

## The work

Not planned yet. First the router guard, asked of Ultra.

## Acceptance

Not set yet.

## History

- 2026-10-27: created.

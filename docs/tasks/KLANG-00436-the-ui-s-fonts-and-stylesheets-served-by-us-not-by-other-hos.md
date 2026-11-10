# KLANG-00436: The UI's fonts and stylesheets served by us, not by other hosts

> **Status (2026-11-10): NOT STARTED.** A follow-up from the maintainer, 2026-11-10; after the frontend refactoring (KLANG-00422).

## Why this exists

The maintainer (2026-11-10): "how do we serve the fonts for the ui? They should be served by us not through an online
vendor like google fonts. Please make this a follow up ticket."

It came up when the maintainer's browser showed other fonts on the whole page and a code editor that was not
monospace. That went away with a browser update and was not reproduced (a fresh build in headless Chrome showed
Lato loaded and a monospace editor), so its cause is not known. What it showed: the app's look depends on hosts that
are not ours.

## How it is today (read 2026-11-10)

- **The app sets almost no font itself.** `index_template.html` loads Fomantic's stylesheet from
  `cdn.jsdelivr.net` (`fomantic-ui@2.8.8/dist/semantic.min.css`). That stylesheet imports Lato from
  `fonts.googleapis.com`, whose font files come from `fonts.gstatic.com`; it also names its icon fonts beside itself
  on the CDN and emoji pictures on `twemoji.maxcdn.com`. Everything else in the UI inherits; the code editor uses the
  browser's own `monospace`.
- **A copy of Fomantic's stylesheet is in the repo** (`src/jsMain/resources/css/fomantic-ui@2.8.8/`) but the template
  does not use it, and it holds the same import of Lato from Google.
- **The flat pages** (the white paper and its figures, the blog, the dev status pages: 50 files) load Chakra Petch
  and Share Tech Mono from Google Fonts; a few also load something from `cdnjs.cloudflare.com`.
- **Also from another host,** and not part of this ticket unless decided: the visit counter's script (`gc.zgo.at`,
  GoatCounter).

## Why it matters

- **Privacy.** A font fetched from another host tells that host the visitor's address on every visit. For Google
  Fonts this has been held against site owners in Germany (to be checked with the copyright and legal audit,
  KLANG-00222, before it is written anywhere public).
- **It breaks apart from us.** A blocked or slow host changes the look of the whole app, and the app cannot be used
  offline or from a local build without the net.

## Proposal

- **The fonts as files of ours:** Lato, Chakra Petch and Share Tech Mono as `woff2` in
  `src/jsMain/resources/fonts/`, each with an `@font-face` in a stylesheet of ours, only the weights and styles the
  UI uses. All three are under the SIL Open Font License, which allows this; their licence texts go beside the files.
- **Fomantic's stylesheet from our own resources,** its import of Lato taken out, its icon fonts beside it; the
  emoji pictures it names are not used by the UI and need no copy.
- **The flat pages** link the same font stylesheet; their generators (`console/dev-status/`) and the hand-written
  pages are changed together.
- **A check for the future:** no `fonts.googleapis.com`, `fonts.gstatic.com` or CDN stylesheet in
  `src/jsMain/resources` or in a template (a rule of `docs-lint`'s local file, or a spec).
- **Credits:** the three typefaces and Fomantic UI in `CREDITS.MD` and on the Credits page, if they are not there yet.

## Decide before implementing

- **D1, the visit counter's script:** stays on its own host (it is the service), or is in this ticket too.
- **D2, the scripts some flat pages take from `cdnjs.cloudflare.com`:** copied in as well, or left.
- **D3, the editor's font:** stays the browser's own `monospace`, or becomes a typeface of ours (Share Tech Mono is
  there already), so it looks the same on every machine.

## The work

1. The font files, their licences, the `@font-face` stylesheet; which weights the UI uses.
2. Fomantic from our resources, without its import; the template changed; a look at every page by screenshot, before
   and after.
3. The flat pages and their generators.
4. The check, and the credits.

## Acceptance

- With the network limited to our own host, the app and every flat page look as they do today.
- A page load asks no font host and no CDN for a stylesheet.

## History

- 2026-11-10: created from the maintainer's question.

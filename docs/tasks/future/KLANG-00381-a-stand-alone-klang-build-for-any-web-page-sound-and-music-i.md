# KLANG-00381: A stand-alone Klang build for any web page: sound and music in the whitepaper and the blog

> **Status (2026-10-28): IDEA.** the maintainer, 2026-10-28: for Q4 or Q1 2027
>
> **Part of:** KLANG-00364 (Out in the world: legal, sharing, outreach)

## Why this exists

The maintainer, 2026-10-28: "stand-alone engine js-build so that it can be pulled into a normal website, like the
whitepaper or the blog-post, and play sounds and music there". The whitepaper (`klang-whitepaper.html`) and the blog
posts (`src/jsMain/resources/blog/`) are plain pages that talk about sound but cannot play any: a reader has to open
the app. A small build that any page includes with one script tag would let a paragraph about drift, a filter or a
song play right where it is explained.

## Decide before implementing

- **What it plays**, which decides its size:
  - **code:** KlangScript, sprudel and the Motor, as the app's playable examples do; the most useful and the
    heaviest (the app's production bundle is 8.1 MB; KLANG-00106 works on its size);
  - **events:** the Motor only, fed a voice stream rendered ahead by the app; light, but nothing editable;
  - **audio files:** rendered offline, no engine at all; the lightest, for pages where nothing changes.
  A mix is likely: code where the reader may edit, files elsewhere.
- **The page's side:** a custom element (`<klang-player>` with the code inside) or a small JS API
  (`Klang.play(code)`); a start on the first click (the browser's autoplay rule); the look of the play and stop
  controls, styled by the page.
- **Loading:** lazily, on the first play, so a page stays light until the reader wants sound; the AudioWorklet
  module must come from the same origin or a blob URL; samples and soundfonts from the sample mirror need CORS
  (KLANG-00182).
- **Where it lives:** served from klang.finzo.de with the version in the path, so an old blog post keeps playing the
  sound it was written for (a later Motor may change sounds: the trade-off of `_v1-scope.md`).
- **The license:** AGPL for our own pages is simple; other sites including it are bound by the AGPL's network
  clause. Whether a permissive or separate license for the embed build is wanted belongs to the copyright audit
  (KLANG-00118).
- **When:** Q4 or Q1 2027, the maintainer to decide.

## The work

Not planned while this is an idea.

## Acceptance

Not set while this is an idea.

## History

- 2026-10-28: created from the maintainer's idea.

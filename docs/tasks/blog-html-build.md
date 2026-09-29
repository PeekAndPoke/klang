# Blog: render the Markdown posts to static HTML for deployment

Status: **queued 2026-09-29 by the maintainer, to start after the whitepaper rewrite.**

## The ask (maintainer, 2026-09-29)

"Build a harness for the blogs. The markdown for writing them is fine, but we need to render them html files and an
index.html into `src/jsMain/resources/blog/` so it can be deployed through the deploy console command. If a post is
not in 'published' state add a tag 'draft' (or similar) in the list in the index.html and also on the blog page
itself."

## What exists to build on

- The posts: `docs/blog/<date>-<slug>/index.md` with YAML front matter (`title`, `subtitle`, `date`, `slug`, `tags`,
  `summary`, `authors`, `hero`, `status: draft | published`, `references`) and their assets in the same folder. The
  contract is `docs/blog/howto-write-a-post.md` (section 2, front matter; section 7, the Markdown constraints and the
  one allowed HTML block, the inline interactive figure frame).
- The precedent for a static page built from docs: `console/dev-status/build.py` (Python, stdlib only) writes
  `src/jsMain/resources/klang-topic-map.html` and `klang-mission-log.html`, with the shared look in
  `console/dev-status/hud.css` (the palette and faces of `KlangLookAndFeel`, which the whitepaper uses too).
- Deployment: `console/deploy-finzo.sh` uploads everything under `src/jsMain/resources/` to the site root, so
  `src/jsMain/resources/blog/` is deployed with no change to the script.

## What to build

- A build command (next to the dev-status one, e.g. `console/blog/build.py`) that renders every post to
  `src/jsMain/resources/blog/<slug>/index.html`, copies the post's assets (images, interactive figure files) next to it,
  and writes `src/jsMain/resources/blog/index.html`: every post, newest first by `date`, with title, subtitle, date,
  tags, summary and the hero image as the card.
- **Drafts are marked**: a post whose `status` is not `published` gets a visible DRAFT tag on its card in the index
  AND on its own page (near the title). Nothing else about drafts changes (they are still built and deployed, per
  the ask).
- **Rendering**: CommonMark/GFM (tables, fenced code with syntax classes) plus the one raw HTML block the guide allows
  (the figure frame) passed through; relative links between posts (`../<dir>/index.md`) rewritten to the rendered
  pages; the `references` front matter rendered as the reference list anchors the text points to (`#whitepaper` and
  friends); the body's repeated H1 and subtitle not duplicated by the template.
- **The look**: the site's own (the `hud.css` palette and faces), readable prose width, light and dark; a post page
  links back to the index. The template carries the one small listener that sizes interactive figure frames
  (`message` events of type `klang-figure-height`, see the `visualizer` agent).
- **Idempotent and reviewable**: re-running with no post changed produces byte-identical files; the output is
  committed like the dev-status pages.

## Decision to make before building (complexity is the enemy: stone rule)

A CommonMark renderer is not in the Python standard library, which the dev-status build relies on. Options:

1. **Python with one small dependency** (`markdown-it-py`, CommonMark-compliant, GFM tables as a plugin, raw HTML
   passes through): the dev-status shape, one `pip install`.
2. **Kotlin on the JVM** (`org.commonmark:commonmark` plus its GFM tables extension) as a small main in the existing
   JVM source set, run through Gradle: no new language toolchain, but Gradle wiring for a docs tool.

Recommendation to discuss: option 1 (the same shape and place as the dev-status pages, no build-system change).

## Guards

A spec or a check in the build that fails when: a post's front matter misses a required field, an internal link or an
anchor does not resolve, a draft is rendered without its DRAFT tag, or an HTML block other than the allowed figure
frame appears in a post.

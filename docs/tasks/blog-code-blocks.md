# Blog: the right language on every code block, and highlighting on the site

Status: **queued 2026-09-30 by the maintainer** ("for later").

## The ask

"In the post The Position That Survived we have code blocks that say 'Javascript' while it should be KlangScript and
some of the blocks do not have code highlighting. We need to revisit all of them and fix them where necessary."

## What is there (counted 2026-09-30)

Across the 25 posts in `docs/blog/`: 72 blocks tagged `kotlin`, 5 tagged `javascript`, 1 `html`, and about 11 blocks
with no language at all. The site build (`console/blog/build.py`) highlights nothing: it only prints the language as a
label (`data-lang`). The whitepaper's code frames are highlighted by hand (`kw`, `st`, `cm` spans).

## What to do

1. **Tag every block with its real language.** KlangScript and sprudel code (songs, patterns, `Osc.*` instruments)
   gets `klangscript`, not `javascript`; Kotlin stays `kotlin`; shell, text, output and diagrams get their own tag
   (`sh`, `text`). Every post, not only the one named. A block quoted from the tree stays verbatim; only the tag changes.
2. **Highlight on the site.** The build colours code by language, in the whitepaper's palette (`--spark` keywords,
   `--code-str` strings, `--code-cm` comments). Pick the lightest way that holds: Pygments is installed (2.21.0) and
   has Kotlin; KlangScript would need a small lexer (it follows Kotlin conventions per `klangscript/MEMORY.md`, with
   JavaScript-like `let` and `=>`). A small hand-written highlighter for the three token kinds is the alternative;
   choose after trying both on two posts ("complexity is the enemy").
3. **A guard**: the build fails on a code block without a language tag, and on `javascript` (the posts contain no real
   JavaScript; the one exception, if one appears, is tagged on purpose and whitelisted by name).

Rebuild, `--check`, commit the output. The repo browser (GitHub) will show `klangscript` blocks unhighlighted; that is
fine.

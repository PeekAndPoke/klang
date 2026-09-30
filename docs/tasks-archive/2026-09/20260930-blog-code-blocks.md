# Blog: the right language on every code block, and highlighting on the site

Status: **DONE 2026-09-30**, the day it was queued. Was: queued by the maintainer ("for later").

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

## What was done

- **Tags.** 11 blocks changed their tag, the code in them did not: the 5 `javascript` blocks are `klangscript`
  (The Position That Survived, The Body That Played a Thousand Times, The Same Word, The Fundamental Lottery, Loop
  Shape Beats Pass Count); the 5 formula and arithmetic blocks are `text`; the `git log` line is `sh`. All
  72 `kotlin` blocks are Kotlin (a few are KDoc quoted without its `/**`), the one `html` block is quoted HTML and
  stays. The "about 11 untagged" of the count were 6.
- **Highlighting: hand-written, not Pygments.** Tried both on The Triplet That Never Drifted and The Promise Is a
  Margin. Pygments' Kotlin lexer marks `Long` and `Boolean` as keywords, leaves `private` plain, colours `is`,
  `for`, `object` inside a KDoc quoted without its `/**`, splits interpolated strings into many tokens, and has no
  KlangScript (a custom `RegexLexer` would be needed anyway). One regex per language in `build.py` (`HIGHLIGHT`)
  gives the three kinds of the whitepaper (`kw`, `st`, `cm`) with no new dependency. Two rules keep it honest: a
  Kotlin soft keyword (`data`, `value`, `private`, ...) is a keyword only in front of another word that is not
  `as`/`in`/`is` (`data class` yes, `val data:` and `inner is Eq` no), and a Kotlin block that starts with `*` is a
  KDoc up to its `*/`. Known limit: a Kotlin string sees one level of `${...}`. KlangScript's keywords are the table
  of `KlangScriptParser.kt`. `sh` and `html` got the same three kinds; `text` stays plain and unlabelled.
- **Guard.** The build refuses an untagged fence, an indented block, an unknown tag and `javascript` outside
  `JAVASCRIPT_POSTS` (empty). Mutation-checked by breaking one post four ways (each refused at the right line) and
  one indented block.
- **Docs.** `docs/blog/howto-write-a-post.md` section 7 and `console/blog/README.md` state the rule.

- **Review.** One blind round, clean (no critical or major). Two minors applied: KlangScript `"`/`'` strings may span
  lines (as the parser allows), `sh` single quotes have no escapes. Left as known limits, none hit by a post: a Kotlin
  soft keyword before an infix call (`value until n`) is coloured, Kotlin's nested block comments end at the first
  `*/`, and in `html` a bare `>` or quoted prose in text content is coloured.

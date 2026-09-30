---
name: writing-loop
description: Use when writing or rewriting a substantial published text (the whitepaper, a blog post, a long doc a person reads): a READER agent reads and hands over problems and goals, a WRITER agent drafts and refines from that brief, a fresh reader reads the result cold, and a fact check closes it. Keeps text production and critique in separate agents. Trial since 2026-09-29.
---

# The writing loop

**Status: a trial (maintainer, 2026-09-29), to be judged on its first runs.** The maintainer's idea: separate text
production from text critique, the way a human writer and editor work, "otherwise the agents might push the gas pedal
and the brakes at the same time". All three agents run on Claude Sonnet 5.5 at effort xhigh, also a trial
(`.claude/agents/reader.md`, `writer.md`, `visualizer.md`).

**Interactive figures are inline** (maintainer, 2026-09-29): a self-contained figure file, embedded as a frame. In a
blog post, the one allowed HTML block (`docs/blog/howto-write-a-post.md` section 7); in the whitepaper, the same frame.
"Self-contained figures are good. Most flexible."

**The figures are a design lab** (maintainer, 2026-09-30): they are also experiments in how the main app might look
once it grows into a full DAW, dense and using every pixel. Keep doing figures in every writing round and every blog
round; nothing is settled, so experiment. The detail is in `.claude/agents/visualizer.md`. When a figure's idea looks
ready for the app, the maintainer names it and it becomes a future task.

## The roles

| role | agent | does | never does |
|---|---|---|---|
| reader | `reader` | reads as the intended reader would (skim, map, questions, in depth), judges comprehension, tone, feel and where pictures would help, hands over a brief of problems and goals | writes replacement text; rules on facts |
| writer | `writer` | turns the brief into text and pictures in the house voice, in two passes (draft, refine); outside the loop it also takes any writing task directly | re-judges the original on its own; invents facts |
| visualizer | `visualizer` | builds stand-alone interactive figures from a figure brief (explorable diagrams, sliders that redraw a curve, data a reader can filter), self-contained HTML/SVG/JS, silent, readable without JavaScript | writes the prose; invents data; plays sound |
| fact check | a reviewer (`/review-loop`, docs tier) | checks every claim the new text makes against the code and the records | touches style |
| coordinator | the session | writes the briefs, keeps the scope, relays nothing unverified, commits | writes the text itself |

## The rounds

1. **Reader, round 1**: reads the document, writes the brief (problems and goals, ranked, each with its location).
2. **The maintainer sees the brief** when it proposes structural change (sections moved, merged or cut); smaller
   briefs go straight on.
3. **Writer, round 1**: draft, then refine, for the scope the brief covers. Where the brief asks for a figure, the
   writer writes the text around it and a FIGURE BRIEF (what it shows, what the reader can do with it, where the
   numbers come from); the visualizer builds it, and the writer fits caption and text to the result. Large documents go in scopes (a part or a
   few sections per run), so each run can read its scope in full.
4. **Reader, round 2, a FRESH reader**: reads the new text cold, without the old version or the brief, the way a real
   reader meets it. Its brief covers only what still does not work.
5. **Writer, round 2**, and so on. Stop when a cold reader's brief holds only small points; the coordinator applies
   those.
6. **Fact check** on the final text (a claim the writer added or changed is checked at its source: the review-loop
   rule "a claim is verified before it is written" applies to facts in prose too).

## The handoff

- The reader's brief states PROBLEMS and GOALS, never the sentence to write. A brief that dictates wording turns the
  writer into a typist and brings the critic back into the production.
- The writer's report says, per brief point: done, done differently (why), or declined (why).
- Both write to files under `tmp/reviews/` (git-ignored), named `<doc>-reader-rN.md` and `<doc>-writer-rN.md`, so a
  round can be read and re-run.

## What to watch while this is a trial

Record in the task or the maintainer's notes after each run: did the cold reader find real problems the first missed;
did the writer's second pass improve on the first; did Sonnet 5.5 hold the voice and the facts. If a run shows the
split costing more than it gives, say so; won't-keep is a valid outcome.

**First run, the whitepaper (2026-09-29/30).** Reader brief, writer in five scoped passes, 13 figures from four
parallel visualizers, a cold reader, a writer round on its brief, two parallel fact checkers, a correction round.

- The cold reader found real problems the first could not: figures that duplicated a table or a listing, a figure at
  the wrong depth for its section, caption contrast, phone widths, unlinked cross-references.
- Building figures from the code is itself a fact check: the visualizers found six wrong facts in the text
  (harmonic partials called inharmonic, a stale pan comment, a benchmark number with no record, the wrong command
  name, a block length, "exactly" for an approximation). Keep the rule "real numbers, cite the file".
- The fact check still found seven WRONG claims, most copied from stale internal records (a "lock-free SPSC ring",
  a "binary" codec, a deleted golden file). A record is a source that can be stale: the fact check traces a claim to
  the code, and the coordinator fixes the stale record too, not only the page.
- Sonnet 5.5 held the voice and flagged its own doubts honestly; it did not invent facts, but it did repeat stale
  ones. Parallel work was safe with one owner per file (writer: the page; visualizers: the figure files).

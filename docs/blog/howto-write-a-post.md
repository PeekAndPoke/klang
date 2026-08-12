# How to write a klang blog post

> Distilled from writing the first seven posts (2026-08-12). This is the working guide — and the
> seed for a future `/blog-post` skill. The backlog of candidates lives in [`BACKLOG.md`](BACKLOG.md).

## 1. Identity & layout

- One directory per post: `YYYY-MM-DD-slug/` — the **date is the milestone's date**, not the writing date (the phase
  pool post is dated the day the pool shipped; the origin post is dated the first commit). Two posts may share a date;
  sort order is `(date, slug)` — slug is the tiebreak.
- The post is `index.md`; every asset (figures, audio) lives **in the same directory**, referenced by plain relative
  links (`![...](figure.png)`). No shared asset folders, no absolute paths — the directory is self-contained and
  survives any site generator.
- `BACKLOG.md` tracks status; a post goes in as `planned`, becomes `in progress`, then
  `published-draft` when written. The front-matter `status` field is the actual publish gate.

## 2. Front matter — the machine-readable contract

Everything a site generator needs, and nothing it has to parse out of prose:

```yaml
---
title: "The Fundamental Lottery"          # short, evocative, no colon-subtitle
subtitle: "Why every supersaw note was a dice roll, and how we rigged the game"
date: 2026-08-12                          # milestone date = sort key
slug: the-fundamental-lottery             # matches the directory suffix
tags: [ engine, dsp, supersaw, klang ]      # lowercase, kebab, reusable across posts
summary: >                                # 2-4 sentences; the teaser card / RSS text
  ...
authors: [ peekandpoke, claude ]
hero: spectrogram-before-after.png        # one asset filename; the card image
status: draft                             # draft -> published is the publish gate
references: # only if the post cites; mirrors the end-of-post list
  - id: luff2022
    text: "Luff, G. (2022). Designing a straightforward limiter. Signalsmith Audio."
    url: https://signalsmith-audio.co.uk/writing/2022/limiter/
---
```

Keep the H1 + italic subtitle repeated at the top of the body — viewers that ignore front matter still get a complete
document.

## 3. Structure — a paper skeleton with blog sentences

The house arc, bendable but proven:

1. **The problem** — concrete, preferably *measured*. Open with the human experience of the bug ("some notes have bass,
   some don't"), then the numbers. If there are no numbers, get some before writing.
2. **Prior approaches** — what the field already does, each with its honest failure mode. This is where citations live.
   The best section when it ends on a bridge: *someone solved this in another domain and nobody told us* (Selected
   Mapping, differential testing, Signalsmith).
3. **The bridge / the insight** — the one paragraph that explains why the solution became possible ("scoring is free
   once it's analytic").
4. **The method** — what was built, with real code where code carries the story. Quote source **verbatim** from the tree
   (whole hot loops are fine; this is our code) and name the file.
5. **Results** — the receipts. Before/after tables, measured, same units as the problem section.
6. **Open questions / lessons** — what's still unknown, what transferred. Honesty here is what makes section 5
   believable.
7. **References** — numbered list, `<a id="..."></a>` anchors, cited inline as `[[1]](#luff2022)`.

Length target: 120–220 lines of markdown. Shorter is fine (the parity post); longer means it's two posts.

## 4. Voice

- First-person plural, past tense for history, present for how things work now.
- **Numbers over adjectives.** "sd 6.0 → 2.7 dB" beats "much more consistent" every time.
- **Keep the failures in.** The prototype that clipped, the metric that didn't move, the review flag that contradicted
  measurement — the negative results are what make the positives credible (and they're usually the best paragraphs).
- Credit by name when a design came from somewhere (Luff, Szabó, McKeeman) — and record *why*
  the credit is owed, not just a link.
- One good closing line beats three closing paragraphs. Don't summarize what was just read.
- **American English spelling** — center, color, behavior, analog. Never centre/colour/cheque. (Verbatim code quotes
  stay faithful to the source, even where the source says CENTRE.)
- Cross-link related posts with relative links (`../2026-08-03-the-same-word/index.md`) — the posts form a web, and
  forward/backward references ("Q2 treated the symptom; Q3 found the disease") are half the pleasure of a series.

## 5. Figures

- **Real data first.** If the engine can render it, render it and measure it — the generating patterns/scripts live in
  the klang-ai session dirs, referenced from the backlog entry. Simulations are allowed for *constructions* (gain
  trajectories, release curves) and must say
  "simulation" in the caption.
- Every figure gets a caption in italics: `*Fig. N — what you are looking at, and what to notice.*`
- Hygiene checklist, learned the hard way:
    - **Contrast serves the story**: honest-but-flat dynamic range can hide the point (65 dB of spectrogram range hid
      the flickering fundamental; 36 dB showed it). Tune display range to the phenomenon, never alter the data.
    - Labels never sit on top of data; suptitles get clearance (`tight_layout(rect=...)`, `pad`).
    - **Read every figure back visually before shipping** (open the PNG, look at it) — the first version of a figure is
      usually wrong in a way only eyes catch: a label collision, a lucky window, a caption claiming something the pixels
      don't show.
    - If the raw waveform doesn't show the effect, plot the *component that does* (the fundamental-overlay figure exists
      because four near-identical waveforms proved nothing).
- Consistent palette across a post: red/crimson = before/problem, green = after/fix, gray = reference/neutral, gold =
  special cases.
- ~130 dpi, white background, `figsize` around 9–11 inches wide.

## 6. Citations

- **Verify every citation online before writing it down** — title, authors, year, venue. No from-memory references; one
  wrong citation poisons the post's authority.
- Mirror citations into front-matter `references:` (for the generator) AND a numbered
  `## References` list at the end (for the reader). Inline: `[[1]](#refid)`.
- Internal sources (task docs, the white paper) can be cited the same way with relative URLs.

## 7. Markdown constraints

The posts must render correctly in the repo browser today and in an unknown generator tomorrow:

- **CommonMark/GFM only.** No kramdown `{#id}` attributes, no Hugo/Jekyll shortcodes, no HTML beyond `<a id="..."></a>`
  anchors.
- Heading links rely on auto-slugs (`## References` → `#references`).
- **Code blocks that contain markdown fences** (KDoc with sample blocks) need a **4-backtick outer fence**. Never put
  backtick runs inside a shorter inline code span — rephrase instead.
- When quoting a declaration, include enough to be real: annotation + full signature + `...` as the body. A KDoc
  floating above nothing looks broken.
- Don't hand-wrap to a column width — the repo's formatter reflows prose lines anyway. Write natural paragraphs and let
  it.

## 8. Pre-publish checklist

```
[ ] front matter complete (title, subtitle, date, slug, tags, summary, authors, hero, status)
[ ] slug matches directory name; date is the milestone date
[ ] every figure viewed with actual eyes after final regeneration
[ ] every citation verified online; references mirrored in front matter
[ ] fence balance checked:  grep -n '`\{3,\}' index.md  (odd count = broken page)
[ ] spelling sweep:         grep -inE 'centre|colour|behaviour|cheque|normalise|analogue' index.md
[ ] cross-links to related posts resolve (relative paths)
[ ] code quotes are verbatim from the tree, with the file named in prose
[ ] status: draft — flipping to published is the author's call, not the writer's
```

## 9. Where material comes from

The best posts so far were written *from artifacts, not memory*: task docs with measured tables
(`master-limiter-lookahead.md` was practically a finished post), the dev diary and
`docs/history/` for narrative arcs, git log for real dates (`first commit 2025-12-20`,
`sprudel rename 2026-03-21`), the klang-ai measurement sessions for figures, and the source tree for specimens. If a
claimed fact has no artifact behind it, either find one or cut the claim.

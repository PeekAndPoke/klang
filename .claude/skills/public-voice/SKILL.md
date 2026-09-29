---
name: public-voice
description: Use when writing or reviewing any text Klang publishes on the net - the whitepaper, a blog post, the README, release notes, site copy. Defines the project's public voice (friendly, calm and humble, inviting, human, "we"), what it is not (hype, overstated claims), before/after pairs from real edits, and a grep list for reviewers. Not for tutorials or in-app text, which have their own style.
---

## Why this voice (the maintainer, 2026-09-28)

Klang is a passion project. The maintainer has worked on it for nine months, with real intensity, and hopes it
goes a long way from here. The public texts should read like that: written by people who care about the work,
telling honestly where it stands, what was hard and what they hope for. Down to earth. Human. No glossy
brochure ("no Hochglanz-rubbish"). Factual, but not cold.

## What the voice is

- **Friendly.** A colleague explaining their work over a coffee, not a vendor on a stage.
- **Calm and humble.** State what exists and how it works; let the reader judge whether it is good.
- **Inviting, welcoming.** Open the door for a newcomer: introduce a term before using it (orbit, Katalyst,
  slot) or link to where it is explained. Say what a reader can try.
- **Human and honest about the journey.** The struggles, the dead ends and the hopes belong in the text. A hope
  is welcome when it reads as a hope ("we hope this goes a long way"), never as a forecast.
- **Concrete.** Numbers and mechanisms over adjectives: "sd 6.0 → 2.7 dB" says more than "much more consistent".
- **Honest about what is rough.** Keep the failures and the open questions in: the prototype that clipped, the
  metric that did not move, the review flag that contradicted a measurement. Admitted limits are what make the rest
  believable, and they are usually the best paragraphs (the whitepaper's "What's rough" section is the model).
- **Generous with credit.** Name who a design or an idea came from (Strudel, Tidal, Luff, Szabó, McKeeman) and
  record why the credit is owed, not just a link; credit the collaboration ("what we built").
- **"We".** The voice is "we" throughout (maintainer, 2026-09-28). Where the origin story would tempt an "I",
  tell it as the project's story ("It began as a rebuild of Strudel"). An "I" appears only in text the
  maintainer writes themselves.

## What the voice is not

- **Not hype.** No marketing or tech-hype speak: no "game-changer", "revolutionary", "blazing", "seamless",
  "next-level", no superlatives about ourselves.
- **Not overstating.** Be on the cautious side with every claim. Describe only what exists today and label plans
  as plans ("a pad controller, sheet music and tab are ideas for later", not "the first of several frontends").
- **Not promissory.** No "never", "always" or "guarantee" about sound, performance or behaviour. Describe the
  mechanism and let the reader draw the conclusion.
- **Not insight theatre.** No rhetorical setups that sell a plain fact ("X is not a hypothesis", "This is not
  theoretical tidiness", "carries the whole design"). State the fact.
- **Not comparative at others' expense.** Say what other tools do well; do not position Klang against them.
  "Klang is not here to replace any of it" holds everywhere.
- **No record of retired surfaces** (maintainer, 2026-09-28: "remove these mentions fully, no need to keep record in
  non published posts or the white-paper"). The whitepaper and every unpublished draft describe today's engine: no
  retired door, class or spelling (the list is the "Retired" section of `CLAUDE.md`), no "since then" or "no X any
  more" notes, and code only in current, verified syntax. A post keeps its story and tells it in words that are true
  today. A published post is the one exception: it keeps what it said at its date.
- **Not stale.** A status claim ("shipped", "still open", "measured") is checked in its primary record under
  `docs/`, not in a summary table: both MAJORs of the 2026-09-28 whitepaper review were stale status lines
  copied from a summary.
- **Not "corrected" on a hunch.** A writer's doubt about a number is a question, not an edit: verify it at the
  source before the text changes. On 2026-09-28 two right numbers were turned wrong that way ("fourteen
  optimizations" counted as thirteen; "72 fields to 82" softened to 79), and review had to restore both.

## Where it applies

The whitepaper (`src/jsMain/resources/klang-whitepaper.html`), blog posts (`docs/blog/`, whose structure and
publishing rules live in `docs/blog/howto-write-a-post.md`), the README, release notes, site copy. **Not** the
tutorials or in-app text: they have their own style (maintainer, 2026-09-28).

House rules that apply on top: no em- or en-dashes (`/code-style` §22); American English spelling in the blog
(`howto-write-a-post.md`).

## Before and after (real edits, dated)

Each pair is an edit that happened (some between drafts of the 2026-09-28 rewrite, so not every "was" is in git).
**Keep the warmth too.** A sweep can over-correct: the 2026-09-28 review restored vivid lines a writer had
flattened ("off the instrument, lying on the bench", "grows at conversation speed", "the network shrugs"). A
concrete, friendly image is the voice; only hype and promises go.
Add one whenever the maintainer corrects a public text.

| was | became | why | date |
|---|---|---|---|
| That sentence is the whole signal-flow redesign of September 2026, and it is what keeps the picture in §5 short. | This is the shape the engine redesign of September 2026 settled on, and the picture in §5 follows it. | insight theatre | 2026-09-28 |
| Two things in there carry the whole design. | Two things in there are worth a closer look. | insight theatre, inviting | 2026-09-28 |
| ... which is why a beginner's first `.reverb(0.3, 5)` just works. | ... so a `.reverb(0.3, 5)` on a pattern that declares no chain reaches the classic chain's room. | "just works" is a promise; say the mechanism | 2026-09-28 |
| This is not theoretical tidiness: ignoring it produced a `roomSize` that was 10× different ... | Ignoring it once produced a reverb size that was 10× different ...; the bug was found by ear. | keep the fact, drop the framing | 2026-09-28 |
| It's a naming convention, but a load-bearing one: it keeps four genuinely different scopes (…) from collapsing into the word "effect". | It is a naming convention, kept because it names four different places (note, instrument, bus, output) that would otherwise all be called "effect". | plain reason instead of emphasis | 2026-09-28 |
| ... so two patterns taking turns on one orbit never click. | ... a safety net against clicks when two patterns take turns on one orbit, not a guarantee. | promissory "never"; the mechanism is a safety net | 2026-09-28 |
| ... and a live edit swaps chains without a click. | A live edit is a chain swap: the leaving chain's input ramps down over 60 ms while the arriving chain's output ramps up, and the leaving chain keeps running until its tail has drained. | describe the mechanism, let the reader conclude | 2026-09-28 |
| Music-as-code is not a hypothesis … it is a working scene ... | Music-as-code is a working scene ... | insight theatre | 2026-09-28 |
| today it is one frontend of the engine below it, the first of several. | today it is one possible frontend of the engine below it. | overstating: "several" promised what does not exist (corrected 2026-09-29: a small MIDI keyboard playground exists beside sprudel, so check what exists before writing either way) | 2026-09-28 |
| `// A chain slot: the knob a pattern moves with katp(), gliding, never stepping` | `// A chain slot: the knob a pattern moves with katp(); it glides to a new value over 50 ms` | a flat "never", and not literally true (the first configure snaps) | 2026-09-28 |
| And there's a subtler version of the problem that most systems don't even attempt. | And there is a subtler version of the problem. | comparative at others' expense (blog sweep) | 2026-09-28 |
| Culling is not a discount on the engine; it is a refund on silence, and the refund is exactly as large as the silence a song carries. | Culling is a refund on silence, and the refund is as large as the silence a song carries. | insight theatre ("not X; it is Y") | 2026-09-28 |
| Maintain those five by hand and they *will* diverge … not might, will. | Maintain those five by hand and they drift apart. | insight theatre | 2026-09-28 |
| running a stranger's song must never be able to touch the filesystem or the network. | a stranger's song should not be able to reach the filesystem or the network on its own: a script can only call what is explicitly registered from Kotlin. | a flat security promise; state the intent and the mechanism | 2026-09-28 |
| The song will keep getting heavier, because that is what songs do when the instrument works. | The song will probably keep getting heavier; that is what songs do when the instrument works. | a forecast told as a hope | 2026-09-28 |

## Grep list for reviewers

A reviewer of any public text greps the CHANGED lines for these, case-insensitive, and justifies or rewrites
each hit. The list below is one pattern written across lines; this runs it (the repo's `grep` is ugrep, and
`-niE` works with it):

~~~
git diff -U0 main -- <file> | grep '^+' | grep -niE "$(sed -n '/^```regex$/,/^```$/{/```/d;p}' .claude/skills/public-voice/SKILL.md | tr -d '\n')"
~~~

On a whole page (a sweep) drop the `git diff` and grep the file; expect legitimate hits ("guaranteed" in code,
"not a guarantee" as a hedge) and judge each one.

```regex
just works|load-bearing|not a hypothesis|game.changer|revolutionary|blazing|seamless|next.level|
cutting.edge|world.class|best.in.class|effortless|magic|the whole design|never (click|drop|glitch)|
guarantee|always (works|sounds)|first of (several|many)|—|–|&mdash;|&ndash;
```

Superlatives about Klang itself ("the fastest", "the most") are hits too; a superlative about someone else's work,
given as credit, is fine.

## Reviewing a public text

The review brief names this skill; the reviewer checks tone on every changed line, the grep list, and every
status and engine claim against its primary source. A tone slip in a prominent place (a heading, an opening
paragraph, a caption) is MAJOR; elsewhere MINOR.

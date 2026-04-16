# Project Disco — Strategic Plan

**Written:** 2026-04-15
**Author:** music-platform-strategist
**Status:** Decisions locked unless marked "open question"
**Owner:** solo founder, 3–4 hrs/week sustainable social budget

---

## Preamble — Honest framing

Before I answer the eleven questions, three honest observations that shape everything below.

**First, you are not actually "marketing a product."** You are trying to seed a subculture around an engine. The product
pitch ("Kotlin Multiplatform audio engine with live-coding DSL") is narrow; the cultural pitch ("a mascot that makes
weird little songs in code and shows its work") is broad. Project Disco only works if you lean into the cultural pitch
and let the engine be the reveal, not the lede. This is the Behind Glass principle applied to marketing: the engine is
visible but never the ask.

**Second, a cold-start solo founder at 3–4 hrs/week cannot win attention.** You can only earn it slowly. Any plan that
depends on "going viral" is a plan that depends on luck. The plan below assumes you will *not* go viral until drop #4 or
#5, if ever. Everything is designed so that six months of effort with no viral moment still leaves you with a real
asset (archive, mascot IP, reusable scene, a tiny-but-loyal audience, and GitHub Sponsors revenue). That is the success
condition, not an escape hatch.

**Third, the biggest risk is not that Disco flops. It is that you build Disco #1 perfectly and then discover two months
in that you hate posting.** Everything below biases toward *posting being the easy part* and *building being where your
energy goes*. If that inversion ever flips — if a drop requires you to spend more time tweeting than making — the plan
has failed on its own terms.

With those framings locked, here are the deliverables.

---

## 1. Mascot Design Brief

### Identity

The mascot is not the brand. Motör is the brand. The mascot is Motör's **house character** — a persona that appears
inside Motör's world and makes songs. This distinction matters because it lets the mascot evolve, travel, change
outfits, even have friends, without putting brand equity on its silhouette. If the mascot ever needs to retire, Motör
survives.

**Working concept:** A small robot who lives *inside* the Motör engine and periodically climbs out to make music. Not a
mascot of the engine — an *inhabitant* of it. This framing is load-bearing: it explains why the mascot can wear a disco
outfit, a kimono, a snorkel, etc., without breaking character. The engine is its home, not its identity.

### Visual direction

- **Silhouette first.** The mascot must read as itself in 32×32 pixels, backlit, or in one-color stencil. That is the
  only test that matters for watermarks, avatars, and cross-platform reuse.
- **One signature feature** that survives every outfit change. Recommend: the head. Options: a translucent dome with a
  visible tiny flame (the ignition), a single glowing cyclopean "cylinder eye," or a cylinder-shaped head with a
  rotating band. Pick one and never change it.
- **Proportions:** chibi-ish. Big head, small body. This scales well to short-form video thumbnails and is readable at
  Twitter avatar size. Adult-friendly cute, not childish.
- **Material palette:** brushed metal + one accent color. The accent changes per outfit/theme. This is how you get
  variety without breaking brand.
- **Style:** low-poly 3D with a painterly shader, OR flat vector with strong line weight. Pick one. **Recommendation:
  low-poly 3D,** because the whole Disco premise is "a scene per drop" and 3D scenes are your reusable framework. A 2D
  mascot in a 3D scene will look glued on.

### Name candidates

Ranked by my conviction, with rationale:

1. **Pip** — one syllable, soft, pronounceable in every language, evokes "pip of ignition," tiny, affectionate. Not
   overused in tech. Works as "Pip from Motör."
2. **Klanko** — brand-tied, suffix "-o" makes it feel like a character. Pronounceable. Slight kid-TV risk; mitigate with
   adult-aesthetic art direction.
3. **Motörchen** — "little Motör" in German diminutive form. Charmingly nerdy for a German founder, plays with the
   umlaut branding, obscure enough to feel discovered rather than marketed. Risk: non-German speakers pronounce it
   awkwardly.
4. **Cyl** (short for Cylinder) — ties to engine terminology. Harder: too generic, googles badly.
5. **Revvy / Revv** — from RPM. Cute, energetic, but generic.

**Recommendation: Pip.** If you need German heritage represented, fallback to Motörchen. Do not call it Klanko unless
you are sure you want the brand name inside the character name (risk: ties mascot fate to brand name if you ever want to
rename).

Decision rule: whichever name passes handle-availability check first (see section 9) wins. Do not let naming drag past
one afternoon.

### Personality traits

Three axes, chosen to keep the writing voice consistent across 50+ posts without you having to "be clever" each time:

1. **Curious, not clever.** Pip discovers things. It does not explain things. ("look what I found" not "let me teach
   you")
2. **Earnest, not ironic.** No winking at the audience. No "lol we all know this is absurd." Straight-faced disco is
   funnier than ironic disco.
3. **Small, not grand.** Every scene Pip is in is sized down to Pip. No epic conquests. Even "Pip goes to space" is a
   small trip, a tiny rocket, one moon rock brought back.

This is the *exact* tone antidote to cringe-dev-Twitter. You are not making jokes. You are documenting Pip's adventures.
The adventures happen to be generated by your audio engine.

### What Pip can and cannot do

**Can:** travel, change outfits, carry a tiny instrument, react to the music, be surprised, be sleepy, be proud, bring
friends along.

**Cannot:** speak (never put words in Pip's mouth — all copy is narrator voice, never Pip's voice), break the fourth
wall, endorse products, have strong opinions, be sarcastic.

This rule protects you from ever having to write Pip dialog, which would burn your time and your brand coherence.

### Reusability across themes

Every drop's theme is expressed through (in priority order):

1. The scene around Pip
2. Pip's outfit / prop
3. The song
4. The lighting + particle accent
5. Pip's pose

The mascot's rig and base model never change. If a drop needs a pose that isn't in the animation library, you add it to
the library — it's now available for every future drop.

### Vendor survival

The mascot is 100% your IP. No stylistic borrow from any existing character. To stress-test: if someone asked "which
existing character did you base this on?" the honest answer must be "nothing." Before committing, do a reverse-image
search on the concept art. If it resembles any well-known mascot (Bender, Wall-E, Baymax, Cozmo, etc.), iterate.

### Production approach (lens check: does this survive 3–4 hrs/week?)

**Do not design this alone in week 1.** Either:

- **Option A:** Hire a character designer for a one-week commission (€600–1500). Lock mascot in 2 weeks. This is the
  right answer if you have cash and want quality.
- **Option B:** Sketch internally until you have a silhouette that reads. Then commission just the 3D model once the
  silhouette is locked. Cheaper, slower, higher risk of scope creep.

**Recommendation: Option A.** Mascot quality is the single highest-leverage asset in this entire plan. It shows up in
every drop, every watermark, every avatar, every reply. Pay for it once; reap for years.

---

## 2. Reusable Scene Architecture

### Design principle

**One scene, thirty outfits.** Not "one scene per drop." The scene is a modular room with pluggable slots. Per drop, you
change slot contents, not the scene.

### Shared (never changes)

- Camera rig + default cinematics (slow orbit, dolly-in on mascot, cut to instrument close-up, cut to wide)
- Mascot rig + animation library (idle, play-instrument, look-around, dance, react-beat, sit, stand, walk-loop,
  surprise)
- Ground plane + ambient occlusion baseline
- Post-processing stack (bloom, color grading baseline, film grain)
- Watermark overlay system (osci/spectrum, Motör wordmark, Pip-sized corner avatar)
- Audio-reactive driver (which visual properties respond to which audio features)
- Intro/outro card template (2–3 second title card, 2–3 second "where to find more" outro)

### Swappable per drop (pick from preset)

- **Background:** skybox or enclosing geometry. Library of presets: disco-ball-room, sakura-grove, ocean-floor,
  data-center, desert-highway, snowy-cabin, arcade, library, moon-surface.
- **Mascot outfit/prop:** disco-jumpsuit, kimono, scuba-gear, hoodie+glasses, biker-jacket, winter-coat, etc. Prop
  attaches to defined hand slot.
- **Particle preset:** disco-confetti, falling-sakura, bubbles, binary-rain, dust-devils, snow, arcade-sparks,
  floating-papers, moondust.
- **Lighting preset:** disco-strobe, golden-hour, underwater-caustics, cold-neon, sunset, moonlight, arcade-RGB,
  candlelight, sunrise.
- **Instrument in mascot's hands:** glow-mic, koto, shell-horn, keytar, tape-deck, etc.

### Bespoke per drop (budget: 0–1 items max)

- One signature prop or one hero-beat visual moment that makes this drop feel unique. Never two. If you're tempted to
  add two, the drop is scope-creeping — cut one.

### Asset checklist per drop (use this as a gate)

```
[ ] Background preset selected or newly created (≤4 hrs if new)
[ ] Outfit + prop selected or newly created (≤4 hrs if new)
[ ] Particle preset selected or newly created (≤2 hrs if new)
[ ] Lighting preset selected or newly created (≤2 hrs if new)
[ ] Mascot pose selected from library (or added, ≤2 hrs)
[ ] Song written, mixed, loopable (separate budget — track in dev hours, not social hours)
[ ] Scene rendered, one 20–30s hero clip exported
[ ] 9:16 and 1:1 crops exported for Reels/Shorts/Tweets
[ ] Watermark verified legible at YouTube Shorts compression
[ ] Tracking UTMs generated (section 6)
[ ] Drop page wired up at klang.art/disco/{id}
[ ] Archive page updated
[ ] Scheduling queue filled for launch day (section 4)
```

**Time target: 12–16 hrs total build time per drop, after scene framework exists.** If a drop exceeds 20 hrs, it's
wrong. Cut.

### Scene framework milestone

The scene framework is a prerequisite for drop #1. Budget 3–4 weeks of dev time for it before drop #1 ships. This
investment amortizes across every future drop. Do not cut corners here; cut corners per-drop.

---

## 3. Three-to-Four Month Warm-Up Content Plan

### The shape of the warm-up

You have roughly 12–16 weeks between now and drop #1. The warm-up is not "building audience to launch to." Cold-start,
you cannot. The warm-up is:

- **Making the account not look like a graveyard on drop-day.** A 12-week archive of small, coherent posts gives a new
  visitor confidence that this is a real thing. This alone converts maybe 10× better than an empty account.
- **Developing your posting muscle so drop-day is not the first time you've ever posted.**
- **Seeding a small cluster of curious followers (target: 50–200) from Reddit + in-community replies** so drop #1
  doesn't post to zero.
- **Building reusable visual language** you can point to when drop #1 lands.

### Weekly budget

- **1 hr/week:** scheduling + writing (use Buffer)
- **1 hr/week:** replies, community participation
- **1–2 hrs/week:** creating 2–3 small visual/audio snippets

Total: 3–4 hrs/week. This is the floor, not the ceiling. If you can't sustain 3 hrs, cut to 2 and extend warm-up to 16
weeks.

### Post cadence (across platforms)

- **Twitter/X + Bluesky:** 3 posts/week (cross-posted, not reworded)
- **Reddit:** 1 post every 2 weeks max, only when genuinely useful in a relevant thread. Never "check out my thing."
- **YouTube:** 1 Short every 2 weeks (same clip reused on Reels + TikTok if you're on those)
- **GitHub:** 1 release-notes post / dev-log per month (low effort, high signal to right audience)

### Content types (the rotation)

I am giving you six types. Rotate through them. Never invent on the fly.

**Type 1: "Tiny wins" clips (40% of posts)**
15–30 seconds of something working in the engine. A fader, an oscillator, a beat, a pattern. Silent-with-audio.
Watermarked. Example tweet:

> a tiny drum pattern, written in 6 lines of code
> [15s clip]
> klang.art

No more. No less. You are not explaining; you are showing.

**Type 2: Mascot sketches (15% of posts)**
Once Pip exists — WIP sketches, turnarounds, pose tests, "here's Pip's face options," "Pip's first test animation." The
audience that likes a mascot is different from the audience that likes audio engines; you want both.

**Type 3: Dev-log micro-thread (15% of posts)**
Once a month, a short (3–5 tweet) thread on something you solved. Real engineering content. This is what earns you a
handful of Hacker News and Kotlin Slack followers. Keep it tight: problem → approach → outcome → one GIF.

**Type 4: Community replies (0 post cost, 30 min/week budget)**
Find 3–5 conversations per week in the creative-coding, algorave, Kotlin, audio, or livecoding orbit. Reply with real
value — a question, a small observation, a "this reminded me of X." Never pitch. Never link. Your handle + mascot avatar
do the linking.

**Type 5: "What I'm listening to / reading" (10% of posts)**
Periodically share a livecoding set you watched, an audio paper you read, a track you loved. This anchors you inside the
culture you want to be part of. Critical for not looking like a lone self-promoter.

**Type 6: "Behind the glass" peeks (10% of posts)**
Once a month, a deeper peek into engine internals. A gauge moving, a voice stealing, a chord resolving. Behind Glass
principle applied to social: the engine is visible but never demanded.

### What to NOT post

- No "building in public" daily standups. Nobody cares, and you'll burn out.
- No "please follow me" / "please RT." Ever.
- No abstract brand tweets. No slogans floating alone. If you're tempted to post just "Sound First!" — don't. Slogans
  land *with* work, never alone.
- No opinions on unrelated tech. You are a musical engine project. Stay in lane.
- No apologies for being quiet. If you go silent for a week, just resume.

### Warm-up milestones

- **Week 1–4:** account setup, first 6–10 "tiny wins" clips, first Reddit comment participation, no mascot yet.
- **Week 5–8:** mascot WIP posts, scene framework dev updates, first 1–2 mini-clips with mascot (no full song yet).
- **Week 9–12:** first drop-preview tease (scene tests, no full drop yet), Pip's first full animated loop, a "something
  is coming" post that does not oversell.
- **Week 13:** drop #1.

### Honest check

If by week 8 your follower count on Twitter+Bluesky combined is under 30 and Reddit is cold, the warm-up is working *as
designed* — you are building credibility, not audience. Reassess only if:

- You hate every post (see section 11)
- You have below 10 followers at week 12 (indicates handle/positioning problem, not a cadence problem)

---

## 4. Drop #1 Plan

### Theme: "Motör-bot went to the disco"

Keep the theme. It's already right. It is silly, it has a strong visual vocabulary, and "disco" is the best first theme
because it's the only one where **people expect visual excess** — so a maximalist first scene feels appropriate rather
than try-hard.

### The song brief

- **Genre:** classic four-on-the-floor disco, ~120 BPM, 32-bar loop (32s if 120 BPM at 4/4).
- **Instrumentation (in code):** kick, hi-hat, clap, a plucky bass, a string swell, a filter-sweep lead. All from the
  Motör engine.
- **Hook:** one repeating 4-bar riff that is recognizable within 8 seconds. This is the TikTok ear-hook. Test: can you
  hum it after one listen?
- **Mix:** loud enough for phone speakers, no sub-bass-only hooks.
- **Code:** readable. Aim for <100 lines. The code is part of the hero visual (visible in the editor on the page), so it
  has to *look* good, not just sound good. Comments in the code are part of the brand voice — keep them spare and
  in-character.

### The scene

- **Background:** disco club interior with a mirror ball centerpiece. Low fog. Low-poly geometry. Reflective floor.
- **Particles:** confetti + light-flecks from the mirror ball, audio-reactive.
- **Lighting:** alternating colored spotlights, audio-reactive (kick triggers flash).
- **Mascot:** Pip in a jumpsuit and sunglasses, holding a mic. Pose: a single repeating dance loop (knee-bounce). Do not
  over-animate — rhythm + fixed loop beats over-animated and costs less.
- **Signature moment:** on the first chorus hit, all lights snap to red and Pip does a small spin.

### The page (klang.art/disco/1)

**Hero block:**

- Scene autoplays (muted by default, tap to unmute — legal requirement for autoplay)
- Big play button overlay
- Title: "Pip goes to the disco"
- Subtitle: "a song in Motör — 32s loop"

**Below hero:**

- The code, syntax-highlighted, in `KlangCodeEditorComp`. Read-only feels wrong here; let visitors *edit and replay.*
  The reason someone stays on the page is because they can mess with it.
- A "how this works" thin-glass one-liner pointing to the engine docs.

**Bottom:**

- One line: "Motör is an open-source audio engine. Support on GitHub Sponsors."
- Link to archive (if drop #1 is alone, link to "coming soon" for drop #2)

**No banner. No newsletter signup. No Patreon.** This page is for making someone smile and think "wait, this is all
code?" Any CTA beyond that dilutes the moment.

### Launch day playbook

**T-minus 1 week:** page is complete on a staging URL. Drop #2 is 30%+ built (scene chosen, song sketched).

**T-minus 3 days:** tease post. One visual still, caption like "pip found the disco." No dates. No "link in bio." Just a
vibe.

**Launch day (Tuesday recommended — Monday is noisy, Wednesday is meeting-day, weekends underperform for dev audiences):
**

- **T-0 (10am your local time):** Post on Twitter/X + Bluesky simultaneously.
    - Tweet copy: "new drop: pip goes to the disco. 32 seconds of code-made music. klang.art/disco/1"
    - Attached: the 20–30s hero clip, vertical and horizontal versions.
- **T+30 min:** YouTube Short goes live (same clip, 9:16).
- **T+2 hrs:** Reddit post — pick ONE subreddit, not all. Recommendation: **r/creativecoding.** Not r/Kotlin (too
  specific, wrong audience for disco-pip). Frame: "I made a little disco scene where a robot plays a song I wrote in
  code. It's all from my own audio engine." Link to klang.art/disco/1.
- **T+4 hrs:** If no traction, no panic. Do not re-post. Reply to comments.
- **T+24 hrs:** second Reddit post in a different sub (r/livecoding or r/algorithmicmusic) with slightly different
  framing, only if you have energy. If not, skip.
- **T+48 hrs:** "thank you for listening" post, shares best comment/remix if any.

### What NOT to do on launch day

- Do NOT post to Hacker News with drop #1. HN as "Show HN" is a one-shot gun; save it for drop #3 or later when you have
  2–3 drops in the archive and a polished narrative.
- Do NOT DM anyone asking for a retweet. Cold DMs for boosts read as desperate.
- Do NOT buy any promotion.
- Do NOT check metrics hourly. Check at T+24h and T+7d.

### Success gate for drop #1

See section 10. Spoiler: a "good" drop #1 is 500 page views in week 1 and one paying GitHub Sponsor by week 4. Anything
above that is gravy.

---

## 5. Drop #2–6 Roadmap

### Principle: theme variety, format consistency

Each drop should feel fresh, but the page template, scene architecture, and launch playbook stay identical. If you
re-invent the format, you are scope-creeping.

### The six drops

**Drop #1: Disco (month ~4)**

- Background: disco club
- Outfit: jumpsuit + mic
- Particle: confetti
- Light: strobe
- Signature: mirror-ball glints
- New assets: ~all of them (first drop)
- Budget: 3–4 weeks (includes scene framework)

**Drop #2: Japan / Sakura (month ~5)**

- Background: sakura grove, temple gate
- Outfit: simple kimono
- Particle: falling petals
- Light: golden hour
- Signature: a small koi pond reflection
- New assets: background, outfit, particle, lighting preset. No new animation needed (Pip sits and plays koto — sit pose
  reused).
- Budget: 1–2 weeks. **This drop is the proof the framework works.** If drop #2 takes 3+ weeks, something is wrong with
  the framework.

**Drop #3: Seaside / Underwater (month ~6)**

- Background: ocean floor with coral
- Outfit: scuba mask + snorkel
- Particle: rising bubbles
- Light: underwater caustics
- Signature: a single curious fish swims past
- Song: dub/ambient, slower tempo — proves the engine isn't just disco
- **Show HN moment if previous drops are strong.** Frame: "Show HN: Pip, a little robot that makes songs in my homegrown
  audio engine."

**Drop #4: Arcade / 8-bit (month ~7)**

- Background: retro arcade cabinet interior view
- Outfit: hoodie + headphones
- Particle: pixel sparks
- Light: CRT RGB
- Signature: scoreboard with ascending numbers on beat
- Song: chiptune, high energy
- This is your TikTok play. Chiptune + visual gag = short-form gold.

**Drop #5: Moon / Space (month ~8)**

- Background: moon surface, Earth in background
- Outfit: bubble helmet
- Particle: slow-floating moondust
- Light: cold blue + Earth-glow
- Signature: small flag planted
- Song: ambient / slow, atmospheric
- **Metrics review checkpoint (section 10).**

**Drop #6: Rainy Cabin / Cozy (month ~9)**

- Background: wooden cabin interior, window with rain
- Outfit: cozy sweater
- Particle: rain streaks on window
- Light: firelight
- Signature: mug of something steaming
- Song: lo-fi
- Closes the arc: from peak extroversion (disco) to peak introversion (cabin). By this point, the audience has seen
  Pip's range.

### Reusable asset budget across six drops

By drop #6, you should have:

- ~6–8 scenes (reusable — rainy-cabin can become rainy-street, etc.)
- ~6 outfits
- ~6 particle presets
- ~6 lighting presets
- ~3 custom props (fish, flag, mug)
- Pip animations: +2–3 new poses beyond launch set

That is a meaningful content library. If you decide to stop at drop #6, you have an evergreen archive.

### What to avoid

- **Never two maximalist drops back-to-back.** Disco → Sakura (calm) → Underwater (medium) → Arcade (maximalist) →
  Moon (calm) → Cabin (calm). The rhythm matters; the audience needs breathing room.
- **Never a drop without a strong silhouette.** If you can't describe the hero frame in one sentence, don't ship it.

---

## 6. Funnel Details

### Watermark strings (exact copy)

**On klang.art hero scene (oscilloscope overlay, bottom-right, always visible):**

```
Klang Audio Motör · klang.art
```

The interpunct is non-negotiable (visual rhythm, prevents it reading as a URL). Always Motör with umlaut. On clips under
10s, shorten to just `klang.art` — name recognition matters more than brand copy at short durations.

**On exported video files (burned-in, bottom-right, 80% opacity):**

```
klang.art · made with Motör
```

Same intent. Slight reword because "Klang Audio Motör" on an exported MP4 looks redundant when the mascot + scene
already look like Motör.

### End-of-song-loop one-liner (plays once after loop #3 on the drop page)

Two options. Use whichever lands better with you:

- **Option A (warm):** "Motör is the engine. If you like this, it lives on at klang.art."
- **Option B (dry):** "made in Motör · see more at klang.art/disco"

**Recommend B.** Warmer copy is fragile in text form without voice; dry copy scales.

### Archive page Patreon prompt (month 3+ only; leave blank until then)

Top-right of archive listing page, under drop cards:

> 47 people sponsor Motör on GitHub. If that were 100, I could ship a drop every 3 weeks instead of
6. → [GitHub Sponsors]

Do not launch with a number you don't have. When GitHub Sponsors goes live at launch, leave this prompt absent until you
have at least 5 sponsors. Then the line reads "5 people sponsor Motör..." and is truthful.

### finzo.de project page CTA

The `klang.finzo.de` page is the "deep dive" surface — for someone who already came from klang.art and is curious. Here,
the tone can be more direct:

**Top of page:**
> klang.art is the demo. This is the project. Motör is a live audio engine written in Kotlin Multiplatform. It runs in
> the browser, on the JVM, and on native targets. It is open-source. Support it on GitHub Sponsors.

**Bottom of page:**
> Questions or want to talk shop? Reach out — @yourhandle on Twitter/Bluesky.

No Patreon here before month 3. GitHub Sponsors badge from day 1.

### Tracking plumbing

**UTM convention:**

```
utm_source={platform}
utm_medium={drop|post|archive|watermark|reply}
utm_campaign=disco-{drop-number}-{drop-theme}
utm_content={optional, for A/B}
```

Example: a tweet linking to drop #1: `?utm_source=twitter&utm_medium=drop&utm_campaign=disco-01-disco`

A reply linking to klang.art in community engagement: `?utm_source=twitter&utm_medium=reply&utm_campaign=warmup-2026Q2`

**Referrer taxonomy to track (on klang.art server logs / analytics):**

- Direct
- twitter.com / x.com / t.co
- bsky.app
- youtube.com / youtu.be
- reddit.com + subreddit
- news.ycombinator.com
- google.com (check whether search is discovering you)
- github.com (sponsor badge clicks)

**Dashboard (keep it simple):**

One spreadsheet or one Umami/Plausible board with three tabs:

1. Per-drop: views, avg time on page, % reached end-of-loop
2. Per-source: sessions by referrer
3. Per-drop: GitHub Sponsor count delta (yes, just count this manually)

**Tracking disclosure:** one-line footer on klang.art — "Analytics: minimal, cookie-free. No tracking of individuals."
Use Plausible or Umami. Avoid Google Analytics; wrong brand signal and GDPR friction.

---

## 7. Release Model Decision — Archive vs Delete

### Decision: Archive, with a nudge

Keep every drop live forever at `klang.art/disco/{id}`. Archive page at `klang.art/disco` lists all drops. **Featured
drop = most recent**, with a clear visual treatment (larger card, "new" label).

### Why

The user brought this up as an open question with a clear lean toward archive. The lean is correct. Here is the fuller
reasoning:

- **Scarcity/delete does not work cold-start.** Scarcity drives urgency among people who already care. You do not have
  those people yet. Deleting drop #1 at month 5 means the two people who found you in month 6 have no context.
- **Archives compound.** By drop #6 the archive is the real asset — it's what someone shares when they want to introduce
  a friend to Motör. A deleted archive is a hostile funnel.
- **The "only the current one is easy to find" framing gives you 80% of scarcity's urgency for free.** Featured drop
  gets the big slot; old drops require a click. That's enough nudge.
- **Archive opens the retrospective content surface.** "One year of Pip" montages, "the top 3 most-played drops," "remix
  contest of any drop" — none of which work if the archive is deleted.

### Tradeoff acknowledgment

You lose the FOMO-driven urgency of scheduled deletion. Recoup it with:

- "Featured this month" treatment (visual spotlight, top of archive)
- Newsletter/GitHub sponsor update that lands *when* a new drop goes live, creating a small moment even if archive is
  permanent
- Annual "Pip's year in review" compilation video, which is free content harvested from the archive

### Rule

Never delete. Never paywall. Old drops may be *remixed* into newer content (e.g., end-of-year compilation,
featured-throwback), but the originals stay.

---

## 8. Platform Decision Lockdown

### Ranked by ROI at 3–4 hrs/week

| Rank | Platform            | Effort                        | Expected ROI                                             | In scope months 1–6?                        |
|------|---------------------|-------------------------------|----------------------------------------------------------|---------------------------------------------|
| 1    | **Twitter/X**       | Low (cross-post)              | Medium-High (primary dev-Twitter orbit, still has reach) | **YES** — primary                           |
| 2    | **YouTube Shorts**  | Low (clip reuse)              | Medium (discovery algo, long tail, watermark benefit)    | **YES** — primary                           |
| 3    | **Reddit**          | Medium (per-post care)        | High for drops, low for warm-up                          | **YES** — drops + occasional                |
| 4    | **Bluesky**         | Low (cross-post with Twitter) | Low-medium, but aligned audience                         | **YES** — cross-post only                   |
| 5    | **GitHub**          | Low (already there)           | Medium (right audience, free to maintain)                | **YES** — always                            |
| 6    | **Hacker News**     | Low once, but huge stakes     | High variance — use sparingly                            | **YES** — drop #3 earliest, not before      |
| 7    | **TikTok**          | Medium-High (format pressure) | High variance upside                                     | **CONDITIONAL** — only if you have 5th hour |
| 8    | **Instagram Reels** | Low (cross-post)              | Low for dev audience, but free                           | **YES** — cross-post only                   |
| 9    | **LinkedIn**        | Medium                        | Wrong tone for campaign                                  | **NO** (per brief)                          |
| 10   | **Mastodon**        | Medium                        | Small dev-specific reach                                 | **NO** — defer                              |
| 11   | **Discord server**  | High (moderation + presence)  | Medium (community loyalty)                               | **NO** for now — revisit at month 6         |

### Lockdown for months 1–6

**Primary (active posting, active engagement):**

- Twitter/X
- YouTube Shorts
- GitHub (README + Sponsor + release notes)

**Secondary (cross-post only, minimal engagement):**

- Bluesky (mirror Twitter)
- Instagram Reels (mirror YouTube Shorts)

**Tactical (per-drop only):**

- Reddit (1 drop post per drop, to the best-fit sub)
- Hacker News (drop #3 or later as Show HN, one shot)

**Deferred / not in scope:**

- TikTok (only if you find you have capacity)
- LinkedIn (per brief)
- Mastodon (too fragmented for cold-start)
- Discord (high maintenance, wait for audience signal)

### Why TikTok is deferred despite high-reach potential

The TikTok format demands native-TikTok style — faces, captions-on-screen, trending audio, personality-driven. You are
building a project account with a mascot, not a founder-face account. You *can* succeed on TikTok with mascot content,
but it demands consistency and algo-chasing behavior that conflicts with "3–4 hrs/week sustainable." If cross-posting
Reels to TikTok performs on drop #3+ without extra effort, open the channel. Otherwise, skip.

---

## 9. Handle Audit Plan

### Decision rule

**One handle, all platforms, no exceptions.** If the preferred handle is not available on any one of the critical five (
Twitter/X, YouTube, Reddit, Bluesky, Instagram), move to the next candidate.

### Candidates in priority order

1. `klangart`
2. `klang_art`
3. `klangmotor`
4. `klang_motor`
5. `motorklang`
6. `pipandmotor` (if Pip is mascot name — only after mascot is locked)

### Platforms to check

Critical (must have):

- twitter.com (x.com)
- youtube.com (channel handle)
- reddit.com (u/)
- bsky.app
- instagram.com

Also check:

- github.com (already probably have an org; make sure it aligns or is redirected)
- tiktok.com (even if not active, reserve)
- threads.net (reserve, free)
- namechk.com (bulk check)

### Process

1. **Today, not later.** Handle availability erodes. This is the single most time-sensitive action item in this entire
   plan.
2. Run `klangart` through namechk.com + direct check on each critical platform. Write down status.
3. If `klangart` free on all critical five: lock it everywhere. Done.
4. If `klangart` taken on any critical platform: check the next candidate.
5. Continue until you find one free on all five.
6. If you go through all six candidates: stop. Email me. Or pick `klang.art` → consider `klangartproject` or
   `klangmusicengine` as adjuncts.

### Domain alignment

`klang.art` is the hero domain. Every handle points there in bio. Short bio template:

> Little songs made in code. Motör audio engine. Open source. klang.art

This bio works on every platform without modification.

### Anti-dilution rules

- Never use the founder's personal handle for Motör. Project separate from founder.
- Never use an alt-handle for "English content" vs "German content." One handle.
- If a handle gets suspended or stolen, escalate to platform support; do not reuse a slightly-different variant.

### Reserve, even if not active

Create accounts on the deferred platforms (TikTok, Threads, Mastodon). Post nothing. Just plant the flag. Takes 20
minutes total, saves you a potential naming collision six months out.

---

## 10. Success Metrics

### Per-drop metrics (measured at T+7 days)

| Metric                                 | Baseline (drop 1) | Working (drop 3) | Strong (drop 5) |
|----------------------------------------|-------------------|------------------|-----------------|
| Page views (klang.art/disco/N)         | 500               | 2,000            | 8,000           |
| Unique visitors                        | 350               | 1,400            | 5,500           |
| % who reach end-of-loop                | 25%               | 35%              | 40%             |
| Twitter/X impressions on drop tweet    | 5,000             | 25,000           | 100,000         |
| YouTube Short views                    | 1,000             | 8,000            | 40,000          |
| Reddit upvotes (primary sub post)      | 30                | 150              | 600             |
| GitHub Sponsor count delta             | +1                | +3               | +8              |
| New followers across primary platforms | +30               | +150             | +500            |

These are targets, not promises. Cold-start, even "baseline" numbers are earned.

### Cumulative metrics (measured at drop #5)

| Metric                             | Working | Strong |
|------------------------------------|---------|--------|
| Total GitHub Sponsors              | 10      | 30     |
| Twitter/X followers                | 300     | 1,200  |
| YouTube subscribers                | 100     | 500    |
| Archive page monthly traffic       | 3,000   | 12,000 |
| Press/blog/community mention count | 1       | 5      |

### Qualitative signals (weight these higher than numbers)

- Did anyone write a blog post about Motör without being asked? (stronger than 10,000 views)
- Did anyone submit a PR to the repo? (stronger than any social metric)
- Did anyone remix a Disco song on their own? (strongest possible signal)
- Did anyone from the algorave / livecoding community interact without being prompted? (cultural signal you're landing
  in the right neighborhood)

### Decision gates

**At drop #3:**

- If page views <200 and GitHub Sponsors = 0: this is not working as shaped. Consider pivot. Likely culprit:
  distribution, not product. Try one paid distribution test (€100 Reddit promote on drop #4) before declaring failure.
- If baseline hit: proceed.

**At drop #5 (explicit review moment):**

- If "working" row met or exceeded: **continue** — you have found traction, double down on what landed.
- If between baseline and working: **continue for 3 more drops at lower cadence** — this is a slow burn, not a pivot.
- If below baseline on all metrics: **pivot or pause.** Candidates: (a) change the format — drop the mascot, go pure
  technical, try HN as the primary funnel; (b) pause social for 3 months, build the engine, try again; (c) hand off
  distribution to someone else. Do not just "try harder." Trying harder on a failing plan is how you burn out in month
  7.

### Ignore these numbers

- Vanity view counts on cross-posted Reels/TikToks without any funnel result. If Reels gets 50k views and klang.art gets
  0 clicks, Reels is noise.
- Competitor follower counts. You are not them.
- Your own follower count week over week during warm-up. It is a lagging indicator, not a steering wheel.

---

## 11. Anti-Burnout Rules

All rules here are **if X, then Y**, with the Y decided now so you don't have to think when fatigued.

### Cadence rules

- **If a week's social budget hits 4 hours and there are still posts unwritten:** stop. Post nothing more that week. No
  guilt, no catch-up next week.
- **If you find yourself reading analytics more than once a day:** close the tab, schedule a calendar block ("check
  metrics Friday 5pm"). Never check metrics before noon.
- **If a post takes longer than 20 minutes to write:** you are overthinking it. Delete it. Post one of the six content
  types from rotation instead.

### Drop-level rules

- **If drop #1 flops** (hits <40% of baseline on every metric): continue to drop #2 unchanged. One flop is noise. Two
  consecutive flops is signal.
- **If drop #2 flops:** reduce drop cadence from every 6 weeks to every 8 weeks. Do not add features. Ask: is the format
  wrong, or is the distribution wrong? Run the thought exercise; act on one answer.
- **If drop #3 flops** (and reach tested with small paid test): take a 4-week pause. Do not post. Come back with drop #4
  only if you want to.
- **If drop #5 flops:** honest review. Use the section-10 decision gate. Pivot or pause. Not "try harder."

### Morale rules

- **If you dread posting for 2 weeks in a row:** drop cadence 50%, post only mascot content and dev-logs. Skip "tiny
  wins" clips until the dread passes.
- **If you dread posting for 4 weeks in a row:** stop. Full pause. Resume when you want to, or don't. The archive stays
  live. GitHub Sponsors stays live. Pip doesn't vanish; he's just on vacation.
- **If you get a harassing comment or pile-on:** mute aggressively. Do not reply. Do not quote-tweet to defend. Do not
  screenshot and sub-tweet. The algorithm rewards you for engaging with fights; your brand does not.

### Content rules

- **If you're tempted to post a hot take on unrelated tech/politics/industry drama:** don't. This is a mascot project.
  Stay in lane.
- **If you're tempted to gate a drop behind a follow or email signup:** don't. Ever. The openness is the brand.
- **If someone offers a paid partnership in the first 6 months:** say no politely. You cannot afford to confuse the
  brand before it's built. Revisit at drop #8+.

### Financial rules

- **If GitHub Sponsors revenue hits €200/month:** keep doing exactly what you're doing. Do not scale up operation. Do
  not hire. Pocket it; cover hosting and mascot commissions.
- **If GitHub Sponsors revenue hits €500/month:** you are funded to open Patreon. Do so, with the tier structure from
  the brief.
- **If GitHub Sponsors revenue hits €1,500/month at drop #6+:** quit your cope and commit a workweek to Motör. This is
  the signal.
- **If GitHub Sponsors revenue stays flat at zero through drop #5:** the audience likes the art but isn't willing to
  fund the tool. Don't push. Monetization is a downstream signal of value-match, not a lever. Consider what they'd pay
  for that you're not offering (stems? tutorials? live sessions?).

### Identity rules

- **If you find yourself writing posts as "we" when you are a solo founder:** stop. "I" is fine. Or narrator-voice about
  Pip. The honesty is part of the brand.
- **If you get asked "is this really just you?":** answer truthfully. Solo founder is a feature, not a liability, for
  this audience.
- **If you find yourself claiming Motör is something it isn't (more mature than it is, faster than it is, more polished
  than it is):** rewrite the post. The engine's state should always be representable truthfully without undercutting the
  brand.

### The floor rule

If after one year of the plan, monthly GitHub Sponsors revenue is under €100 and you have no energy, **this plan has run
its course.** Do not grind further. Assets stay live (archive, mascot, framework). You have learned something. This is
not failure; it is a ceiling, and ceilings are useful data.

---

## Appendix A — First-month action list

In order, for weeks 1–4:

1. **Today:** run handle audit (section 9). Lock handle.
2. **Today:** create GitHub Sponsors profile. Minimal viable tiers.
3. **This week:** commission mascot (section 1). Give the designer this document.
4. **This week:** set up Buffer or Hypefury. Schedule first 4 posts.
5. **This week:** set up Plausible/Umami on klang.art.
6. **Week 2:** write first 6–10 "tiny wins" clip scripts. Record 3.
7. **Week 2:** audit Reddit subs, follow 30 relevant accounts on Twitter, join Kotlin + TOPLAP Discord/Slack
   communities.
8. **Week 3:** begin scene framework dev (parallel to warm-up posts).
9. **Week 3:** first Bluesky + YouTube account setup + cross-post test.
10. **Week 4:** mascot concept art delivered, begin 3D model commission / production.

---

## Appendix B — Post templates (copy-paste ready)

### Tiny wins clip

> [one concrete statement about what's shown]
> [15s clip]
> klang.art

Example: "a kick + snare + hat pattern, 8 lines of code."

### Mascot WIP

> Pip in progress. Disco outfit, maybe.
> [image]

### Dev-log micro-thread (opener)

> spent last weekend teaching the voice stealer to be less greedy.
> the problem:
> [one-sentence problem statement]

### Community reply

Do not template. Read three posts in the thread first. Reply to the specific observation being discussed. Your handle
does the branding; the reply does not need to.

### Drop launch tweet

> new drop: [theme one-liner]
> [length] of code-made music.
> klang.art/disco/[n]

Example: "new drop: pip goes to the disco. 32 seconds of code-made music. klang.art/disco/1"

### Reddit drop post

**Title:** "[Project] I built a scene where a robot plays a song I wrote in my own audio engine"

**Body:**
> Motör is an open-source audio engine I've been building in Kotlin Multiplatform. Every few weeks I put out a little
> drop — a song and a scene, both made from scratch. This one is a disco. The code is visible on the page and you can edit
> it to change the music.
>
> klang.art/disco/1
>
> The engine is open-source. Happy to answer questions about how any of it works.

No "please upvote." No "any feedback welcome" (it's weak). Just show up, post the thing, leave.

---

## Appendix C — Known unknowns (flagged for founder review)

These are the decisions I'm making with incomplete information. If your gut disagrees, override:

1. **Mascot species/form.** I've recommended "small robot inhabitant of the engine." You may have a stronger idea — a
   walking cylinder, a cassette-tape creature, a tiny person. The species matters less than the silhouette discipline.
2. **Name Pip vs Motörchen.** Pip is the handle-safer, culturally-broader choice. Motörchen is more German-charming and
   more memorable for a niche. Your call.
3. **Scene style (low-poly 3D vs flat vector).** I've pushed low-poly 3D. If your art skills are stronger in vector,
   flat-vector can absolutely work — but the scene framework becomes a 2D compositor, not a 3D scene. Pick once; don't
   hybrid.
4. **Primary subreddit per drop.** I've listed r/creativecoding for drop #1. Your read of which sub is *friendly* in the
   week you post may be better than mine. Pick the sub *that week*, not in advance.
5. **Drop cadence.** I've assumed ~6 weeks per drop after warm-up. If your build velocity is faster and you want every 4
   weeks, do it. If slower and 8 weeks is realistic, do that. 6 is a guess, not a law.
6. **Whether to add email newsletter.** Not addressed above because not requested. My weak opinion: wait until drop #3.
   If there's pull, a single monthly email with "latest drop + a glimpse of what's next" is high-value, low-maintenance.

---

**End of strategic plan.**

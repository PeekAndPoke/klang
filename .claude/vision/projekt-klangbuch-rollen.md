# Projekt Klangbuch — Die Rollen

> Status: Draft — 2026-05-03
> Companion to `projekt-klangbuch.md`. The role taxonomy is broader than the
> Klangbuch itself — it shapes onboarding, features, progression, and contribution
> across the whole platform.
> Future / parking work. Names are working terms, not committed product copy.

---

## Why role taxonomy

A vivid music community needs different roles to coexist. Different humans engage
with music differently. Most music-platform failure modes come from collapsing
everyone into a single role assumption ("everyone here is a producer," "everyone
here is a listener"). Naming the roles up front lets the platform serve each on
its own terms.

These are **pure types**. Real humans blend them.

The founder's own example: not a live-coder by training, builds PoCs others can
use on stage. That is an Archivar, possibly with Klangbauer in the mix — not a
Performer or a Komponist. The taxonomy must accommodate this honestly, not assume
everyone aspires to the stage.

---

## The roles

### Der Hörer — *The Listener*

- Plays back others' work. Discovers new music. Never writes code.
- **Biggest population by raw count.** Most humans landing on Klang will only ever be Hörer.
- Needs: discovery feed, "what's good right now," playback that doesn't require an editor, mobile-friendly listening.
- Cultural analog: Spotify listener, Bandcamp browser, radio audience.
- **Implication**: the home page can't assume the visitor wants to write code.

### Der Komponist — *The Composer*

- Writes klangscript. Builds songs from scratch. Works at home, on their own time.
- Needs: great editor, great engine, save/versions, optional publish-into-the-Klangbuch (gated through Archivar review).
- Cultural analog: bedroom producer, songwriter, Strudel pattern-writer.

### Der Archivar — *The Archivar*

- Curates the canon. Writes tutorials. Builds PoCs others can use on stage. Decides what enters Das Klangbuch.
- *Doesn't have to perform or even compose original songs.*
- Needs: review queue, tutorial authoring, attribution / metadata workflow, taste.
- Cultural analog: editor, record-label A&R, museum curator, sample-pack designer.
- **Founder is the Archivar-in-chief.** Healthy precedent — Rick Rubin isn't a rapper, Brian Eno's bio includes more
  curators than composers.
- The contributor-gate reframe (*"Become an Archivar — and contribute to Das Klangbuch"*) frames the gate as **conferred
  title, not restriction.** Aspiration, not friction.

### Der Performer — *The Performer*

- Live-codes on stage. Pulls from Das Klangbuch during sets. Improvises in front of an audience.
- Smaller population than Komponist, but **punches above its weight** — Performers are the videos, the streams, the "
  wait what is this" moments that recruit everyone else.
- Needs: live / performance mode, low latency, fast access to the canon, "pull this into my set" workflow.
- Cultural analog: DJ, jazz improviser, algorave performer.
- **Project Disco is a Performer surface,** even when the founder isn't one.

### Open question — Der Klangbauer — *The Sound Builder*

Candidate fourth role. Builds Ignitors, Katalyzers, sample kits, tunings — but
never writes a song with them. The synth-patch designer, not the producer. The
drum-kit maker, not the rapper.

**Reasons to name it as its own role:**

- The engine architecture already treats Ignitors and Katalyzers as first-class artifacts (per `project_engine_naming`
  memory).
- Some musicians are sound-designers first and only — they'd build a great Ignitor but never call themselves "
  composers."
- Brand-coherent: *Klangbauer* literally means "sound-builder," stays in the German vocabulary family.

**Reasons it might just be a sub-role of Komponist:**

- Adds complexity. Four pure roles is more to design for than three.
- Most platforms collapse "patch designer" and "track maker" into one role and survive fine.

**Status: parked. Decide when it matters.**

---

## Two patterns the taxonomy implies

### Blending

Real humans are mixtures.

- Karsten = Archivar + Klangbauer
- A working live-coder = Performer + Komponist
- A teacher = Archivar + Komponist
- A bedroom-producer fan = Hörer + Komponist (rarely shipping)

The platform should let people *signal* multiple roles, not pigeonhole.

### Progression

Most Hörer never move. Some become Komponist. A few of those become Performer.
A rare few become Archivar by invitation. The platform should make progressions
**visible and aspirational**: "what does it look like to become an Archivar?
Here are the people who already are."

This is the same gating mechanic as the no-external-samples rule — a *promotion
ceremony*, not a content restriction. Aspiration, not friction.

---

## Open questions

- Klangbauer: yes / no / parked? *(Currently parked.)*
- Exact contributor-gate wording. *"Become an Archivar of Das Klangbuch"* works grammatically but reads stiff in
  mixed-language form. Cleaner: *"Become an Archivar."* Open.
- Whether roles are user-facing taxonomy in the UI, or just internal vocabulary for the team. **Probably the latter at
  first** — roles inform feature shape, but users don't need to pick a tribe to use the product.
- How role-progression is signaled. Badges? Profile titles? Earned ceremony? Open.

---

## See also

- `projekt-klangbuch.md` — the corpus thesis these roles serve.

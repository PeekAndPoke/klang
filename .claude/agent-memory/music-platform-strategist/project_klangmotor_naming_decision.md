---
name: klangmotor-naming-decision
description: DECIDED 2026-08-13 — the audio engine is "Klangmotör" (ASCII "klangmotor"), replacing "Klang Audio Motör"; reasoning, ruled-out alternatives, and open clearance items
metadata:
  type: project
---

# Engine renamed to "Klangmotör" — DECIDED 2026-08-13

**The decision:** The audio engine is called **Klangmotör**. It replaces **"Klang Audio Motör"** everywhere.
The ASCII spelling **`klangmotor`** is the sanctioned identifier form — for domains, handles, package names, search —
and is *the same name undressed*, not a second brand. Codebase rename executed 2026-08-13 (diary/history files
deliberately excluded, so they preserve the old name as a historical record).

**Why:**

1. **Inner-voice first-impression primacy — the decisive argument.** A new brand name is *always* subvocalized: an
   unknown token cannot be skimmed by shape recognition, it has to be phonologically decoded. So the awkwardness of
   "Klang Audio Motör" was not an occasional cost paid by the founder in daily speech — it was paid **once by every
   new reader**, at the exact moment they decide whether the thing feels coherent. (I initially mis-weighted this at
   ~20 % by anchoring on the founder's own speech; Karsten corrected it and was right.)
2. **The one-word / one-stress law.** Strong brands are single orthographic tokens with one unambiguous stress.
   Multi-word names get abbreviated by users — Native Instruments → NI, Teenage Engineering → TE — which means **you
   lose control of your own name**. "Klang Audio Motör" was already collapsing to "the Motör" in practice.
   *Word-count is the law; syllable-count is the symptom* — the "three syllables" intuition is a proxy for
   "one Germanic compound," and 2–3 syllables is the real range, not 3 exactly.
3. **Unresolved stress = friction.** KLANG audio motör / klang AUDIO motör / klang audio moTÖR — the reader had to
   *choose*. "Klangmotör" resolves automatically to initial stress (German compound default): KLANG-mo-tör.
4. **"Audio" was semantic dead weight.** "Klang" already said sound. The inner voice did work and got no information
   back — close to the literal definition of the clunky feeling.
5. **It didn't parse as a name.** "Klang Audio Motör" has the grammar of a product *category* — [brand] [category]
   [type], like "Bosch Audio Motor". A first-contact reader never experienced it as a proper noun. This is the
   strongest form of the argument.
6. **The old name was a weak wordmark anyway.** Three near-descriptive words in a row (sound + audio + motor), crowded
   and hard to protect — already recorded in [[brand-architecture-klang-motor]]. Compressing to one compound is a
   modest but real gain in distinctiveness. Speakability fix and trademark fix pointed the same way.
7. **The ö earns its keep as a name-signal.** Beyond the standing "Motör always with ö" convention, the umlaut does
   *visual* work at first contact: it flips the inner voice's parse from "sound motor" (a description) to
   "Klangmotör" (a proper noun).
8. **Behind-glass compatibility.** One word can be a badge on a gauge, a splash, a status bar. Six syllables cannot.
   And "Motör" survives as a living morpheme, so the Cylinder / Injection / Ignitor / Katalyst vocabulary still hangs
   off a named keystone, with "the Motör" as natural dev-facing shorthand. See [[behind-glass-design-principle]].

**Ruled out:**

- **"Klangmotor" (no umlaut) as the name** — In German loudspeaker engineering, *"Motor"* is the actual technical
  term for a driver's magnet-and-voice-coil assembly. So "Klangmotor" reads to a German audio person as a transparent,
  near-generic compound meaning "the sound-producing drive unit of a speaker." Descriptive in exactly our sector (bad
  for a mark) and bad positioning (makes the software sound like a speaker part). Also would have broken the standing
  ö convention. **Retained only as the ASCII identifier spelling, never as the name.**
- **Bare "Motör" as the engine name** — Would remove the Klang-root stutter in "Klangmotör by Klang.art", but walks
  straight back into the Motörhead exposure recorded in [[motorhead-trademark-risk]]. Standalone Motör + music sector
  + umlaut is the exact signal that forced "Motör Hits" → "Klang Hits". Absorbed as the second morpheme of a German
  compound, behind glass and never an app-store title, the risk is materially lower.
- **Keeping "Klang Audio Motör"** — see reasoning above.

**Accepted trade-off:** the Klang root now appears twice in "Klangmotör by Klang.art". Judged acceptable — it reads as
one family, which is what a house brand wants.

**Still open (clearance, none blocking):**

1. **DPMA / EUIPO register lookup for "Klangmotor" / "Klangmotör."** Not done — those registers are JS-driven and were
   not directly queryable. Everything below is open-web evidence only.
2. **The one same-name artifact found is dead.** "Klangmotor" — a 2014 tablet-app *portfolio concept* for
   stroke-patient fine-motor rehab by Sammy Schuckert (now Design Lead at IBM since 2019; his site's last post is
   2018). No distribution, no app store, no company, no trademark surfaced, twelve years dormant. Under §4 MarkenG an
   unregistered mark needs real *Verkehrsgeltung* (acquired market recognition), which a never-distributed concept has
   none of. **Assessed as not blocking.**
3. **ASCII domain/handle sweep for `klangmotor`** — .com/.de/.art plus GitHub, Twitter, Bluesky, YouTube. Not verified;
   whois needed, search cannot answer it. **First decide whether it is even needed** — the engine is behind glass and
   klang.art is the anchor, so a dedicated engine domain may be defensive-only.
4. Minor search noise only: "klangmotorik" (dormant MySpace artist).

**How to apply:**

- Write **Klangmotör** in all prose, UI, marketing and docs. The ö is non-negotiable in display text — this extends,
  not replaces, the "Motör always with ö" convention.
- Use **`klangmotor`** for anything that must be ASCII (URLs, handles, package/namespace identifiers). This is
  *sanctioned*, not a violation of the ö convention.
- The full brand reading is now **"Klangmotör by Klang.art"** — see [[brand-architecture-klang-motor]].
- Note the division of labor: **Klang.art** is the mark that carries consumer equity and should aspire to the
  opaque/coined standard (Ableton, Zalando); **Klangmotör** is the engine name, where transparency is an *asset*
  because it tells a curious developer what it is. Do not judge the engine name by consumer-brand criteria.

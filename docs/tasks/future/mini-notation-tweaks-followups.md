# Mini-notation tweaks: what is left after phases 0-4

Status: **future / follow-ups — the feature SHIPPED 2026-08-31, these are the loose ends.**
Design and build record: `docs/tasks-archive/2026-08/20260831-mini-notation-tweaks.md`.

## What already works

A tweak is a named bundle of per-note modifications. Mini-notation attaches the **name**, a later
`tweaks(...)` binds the name to a transform and applies it:

```klangscript
note("c3 e3{swell} g3{swell bend}").tweaks({
    swell: x => x.adsr(attack = 0.3).gain(0.6),
    bend:  x => x.detune(20).accelerate(0.5),
})
```

Shipped in commits `a8b0b4f6`, `5ceda001`, `dbe2dc75`: `SprudelVoiceData.tweaks: List<String>?`,
`pattern/TweaksPattern.kt`, `tweak()`/`tweaks()` in `lang_structural_addons.kt`, and the removal of
the old `{key=value}` attribute block. Works on `note()`, `n()`, `s()`, chords, and through
`scale()`.

## 1. The unknown-tweak diagnostic (the one that matters)

**A misspelled tweak is silently inert.** `note("c3 e3{swel}")` plays the note with no swell and says
nothing at all.

This is correct *runtime* behaviour and must stay: at query time nothing can distinguish a typo from
a name an outer palette will claim later, and unbound names passing through untouched is exactly what
lets an inner and an outer `tweaks(...)` compose. But it means the feature has no safety net, and it
is the same silent-inert class the design was written to avoid.

Only the analysis layer sees the whole script and can prove that *no* binding anywhere claims a name.
Build it there, reusing the levenshtein helper from commit `09783f50`
(`suggestNames`, `klangscript/src/commonMain/kotlin/runtime/NameSuggestions.kt:25`), reported as an
editor diagnostic: *unknown tweak 'swel', did you mean 'swell'?*

Until this lands, tweaks work but will bite someone.

## 2. Nobody has heard it yet

Every claim in the design record is backed by tests, which in this project is half a verification.
Worth a session with a real song, asking two things a test cannot:

- Is `{swell}` legible at a glance in a dense line, or does it crowd the notes?
- Does *naming a treatment* hold up as a way of working once a song has five of them, or does it turn
  into a dictionary you have to keep in your head?

The second question is the one that could still send the design back.

## 3. No tweak chip in the visual mini-notation editor

`sprudel/src/jsMain/kotlin/ui/MnSharedPanels.kt` offers `mnModChip` entries for `*`, `/`, `@` and
`?`, and nothing for the brace block. Not a regression, the attribute block never had one either, but
braces are a first-class feature now so the gap shows.

Needs a design thought first: a tweak chip is a free-text name, not a number with a default and a
step like every existing chip, so it does not fit the `mnModChip` shape as-is.

## 4. Phase 6 docs, half done

Done: `sprudel/ref/dsl-addons.md` rows, and the KDoc on all forms including the verb-noun caveat
(`.tweak("bend")` reads as a verb but only marks).

Not done: tutorials, and the editor popup category.

## 5. Group semantics: settled mechanically, not by ear

`[c4 e4]{swell}` applies the transform to each note separately, and `note("[c3,e3]{swell}")` is pinned
in `LangTweaksSpec`. Whether per-note is what a musician *wants* there, rather than the transform
applying once to the group as a unit, is a taste call nobody has made.

## 6. Should tweaks reach the UI after all?

They are deliberately kept off the wire: a tweak never reaches synthesis, so it would be dead weight
in the wire format, and `toVoiceData` does not copy it. `tags` are on the wire because visualizations
consume them.

Revisit only with a concrete consumer, e.g. a visualization that wants to draw bent notes differently.
Adding it later is one line in `audio_bridge/src/commonMain/kotlin/VoiceData.kt` plus the copy.

## 7. `tweak` vs `tweaks` ergonomics

Singular marks, plural binds-and-applies. The split is type-safe (String vs Object), so a mix-up is a
type error rather than a silent behaviour change, but it is subtle. If it reads badly in a real song,
the fallback applier name is `withTweaks`.

Depends on item 2: this cannot be judged without writing music with it.

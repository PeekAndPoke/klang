# Soundfont variant curation: the index decides what plays, and some of it is wrong

Status: **data item, no code.** Maintainer's rule (2026-09-03): *"no special handling in the code —
the data must be correct."* So this lives in the mirror's `index.json`, not in `SampleIndexLoader`.

## The problem

Each `gm_*` instrument has several variants (FluidR3_GM, GeneralUserGS, JCLive, Aspirin, Chaos …)
and **the index's order decides which one `s("gm_accordion")` plays** — `variantIndex =
(request.index ?: 0) % variants.size`, so variant 0 is the default and `.n(k)` picks another.

Measured 2026-09-03 against the decoded audio (autocorrelation over the loop region):

| instrument | variant 0 today | recorded pitch vs declared root | loops |
|---|---|---|---|
| `gm_accordion` | **JCLive** | **+0.4 to +1.4 st sharp**, every zone; zone 8 (keys 81–84) +1.43 | 1–5 ms single-cycle, 0.13–0.39 s samples |
| `gm_accordion` `.n(1)` | FluidR3_GM | within ±0.5 st, zones 4–5 within 0.12 | 1–6.6 s real sustain loops |
| `gm_violin` | Aspirin | −0.65 to −0.96 st flat | 150–210 ms loops |
| `gm_violin` `.n(2)` | FluidR3_GM | (not yet measured) | 170–190 ms loops on all 14 zones |

A declared root that is a semitone off cannot be corrected by the engine: `2^((note − root)/12)` is
the right formula and it is being fed a wrong root. Only the data can fix it.

## What to do, when someone curates

1. **Measure every variant of every sustained instrument** the way the investigation did — recorded
   fundamental over the loop region vs `effectivePitchCents()` — and record the max deviation.
2. **Reorder each instrument's variant list** so the most accurate, best-looped font is index 0.
   FluidR3_GM is the safe first guess where it exists (it is the most consistently curated font in
   the set), but measure rather than assume — Chaos and some GeneralUserGS zones have *no* loop at
   all (`0402_GeneralUserGS` violin: 14 zones, all one-shot, 0.18–0.26 s).
3. Consider **dropping variants that cannot sustain** from sustained instruments, or at least never
   letting one be index 0.

Everything here is a `index.json` edit in the mirror. The engine already does the right thing with
whatever it is handed — as of `aa93eef8` and the loop/envelope change that followed it.

# Marks against AI training: Katalyst stages that cloak or tag published audio

Status: **future, idea, not designed.** Created 2026-09-30 (maintainer): "we might consider adding a Katalyst
node / nodes to be used on the master, for example for applying the HarmonyCloak approach or similar things."

## The idea

Music made with Klang may end up in the training data of text-to-music systems (Suno, Udio and the like). Two
things an author might want from the audio they publish:

1. **Cloak**: make the audio useless to learn from, so a model trained on it does not pick up its style.
2. **Tag**: hide a mark in the audio that a model trained on it reproduces, so a model's output can be traced
   back to the training data.

Both should be inaudible. Both would be authored stages, most likely on the master
(`master(Katalyst(k => ...))`), where they see the finished mix.

## What the research says (checked online 2026-09-30)

- **HarmonyCloak** (Meerza, Sun, Liu, IEEE S&P 2025, <https://ieeexplore.ieee.org/document/11023354/>).
  The cloak. It adds imperceptible *error-minimizing* noise: the perturbation is optimized so that a
  model's training loss on the song is already near zero, and the model believes there is nothing left
  to learn. Tested against MuseGAN, SymphonyNet and MusicLM; listeners rated the cloaked and original
  songs similarly, and the models' outputs degraded as more of their training songs were cloaked.
  **Not a DSP stage**: the noise comes from an optimization with gradient access to a (surrogate) model,
  run offline per song. A Katalyst stage cannot compute it on the audio thread.
- **Hidden Echoes Survive Training** (Tralie, Amery, Douglas, Utz, AAAI 2025 Workshop on AI for Music,
  <https://arxiv.org/abs/2412.10649>). The tag. Classical echo hiding: a faint, delayed copy of the signal is
  mixed back in, and the delay can be read from the cepstrum. Models trained on echoed audio (DDSP, RAVE,
  Dance Diffusion) reproduce the echo in their outputs. It survived fine-tuning, mixing and demixing, and
  pitch shift augmentation. A single echo was the most robust; time-spread echo patterns carry more bits.
  **A plain DSP stage**: a short fixed delay at a low level. It fits the Katalyst directly.
- **Latent Watermarking of Audio Generative Models** (San Roman, Fernandez, Deleforge, Adi, Serizel,
  ICASSP 2025, <https://arxiv.org/abs/2409.02915>). The model owner marks their own training data, so the
  model's outputs carry the mark (over 75 % detected at a false positive rate of 1e-3, even after
  fine-tuning). Context only: it shows marks in training data can survive training, but it assumes the
  marked data is a large share of the training set.
- Post-hoc output watermarks (AudioSeal, WavMark, SilentCipher) mark a file so that *that file* can be
  recognized later. They say nothing about training, and they are neural embedders, not a DSP node.
- Not read yet: "SoK: How Robust is Audio Watermarking in Generative AI models?"
  (<https://arxiv.org/abs/2503.19176>). Read it before designing anything.

## The honest limits

- **Share of the training set.** Every positive result above trains on data where the marked part is
  large. Our songs would be a tiny fraction of a large commercial model's data. Nobody has shown that a
  mark from a small contributor survives that.
- **Codec tokenization.** Large text-to-music models likely train on neural codec tokens, and a codec
  keeps what the ear hears and drops the rest. The echo experiments are on audio-to-audio models; whether
  an echo survives a codec plus a text-to-music model is open.
- **An arms race.** A cloak designed against one model family may not transfer to another, and
  training pipelines can learn to remove known perturbations.
- **Stronger tools exist.** The dated git history of every song is provenance, and matching melodies and
  arrangements in suspect outputs (Content ID style) finds copying without any mark. This task is a
  complement to both, not a replacement.

## Constraints from the rules register

- **The Motor stays raw.** No stage is ever on by default, and the engine never marks a user's audio on
  its own. An author opts in, song by song.
- **Sound first.** "Imperceptible" is a claim for the ear to test: a by-ear A/B on a finished song before
  anything ships, the echo's delay and level tuned by listening.
- **Taste is also what you do not do.** Won't-implement is a valid outcome of the prototype below.
- If it is built: the `/dsl-design` rules apply as for any stage (two doors, parameter parity, `wet`
  first, a wire type, one word per concept). The house limiter in `MasterStage` stays after every
  authored stage; check that it does not smear the echo.

## Shape, if it is built

- **Tag: an echo-mark stage.** A Katalyst stage (name to decide) with a fixed short delay and a low
  level, perhaps a time-spread pattern for more bits. It works on an orbit too, like every Katalyst
  stage. The existing delay machinery (`KatalystDelayEffect`) is close; this may be a thin stage on top.
- **Detection is half the feature.** A reader that takes a WAV and reports the cepstral peak at the
  mark's delay, with a false-positive rate against unmarked music. Probably an offline tool next to the
  recording pipeline (`/klang-music-recording`), not an engine node.
- **Cloak: an export step, not a stage.** A HarmonyCloak-style perturbation is computed offline against
  a model. If it is wanted, it belongs to the WAV export (render, then cloak), with its own model
  dependency, which is a big step for this project (see "Complexity is the enemy"). A Katalyst stage
  could at most play back a precomputed perturbation, which ties it to one fixed render and does not fit
  live playback.

## First step when picked up

A prototype outside the engine, no engine code: echo-hide an offline render of a builtin song in a
notebook, listen A/B, read the mark back from the cepstrum, then round-trip the marked file through MP3
and a neural codec (EnCodec or similar) and read it again. If the mark does not survive the codec, stop
here and record why.

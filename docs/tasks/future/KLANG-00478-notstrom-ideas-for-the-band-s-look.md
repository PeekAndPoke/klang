# KLANG-00478: Notstrom: ideas for the band's look

> **Status (2026-11-19): IDEA.** A place to collect ideas for how the band and the stage look; each one becomes a
> ticket of its own when it is taken up.
>
> **Part of:** KLANG-00447 (Live act: a band on a stage, played by the song)

## Why this exists

Ideas for the live act's look come up while the band is watched, and get lost in a session. They are kept here, each
with who had it and when, until one is chosen and gets its own ticket.

## The guiding feeling

The maintainer, 2026-11-19: "The whole style should always elude two feelings at once: f.e. beauty and fear ... On
the edge between them so it is hard to decide what it is." Every idea below is judged by it: never only beautiful,
never only frightening.

## Keep (the maintainer, 2026-11-19)

What the figures already have and must not be lost in any change of style or body:

- **Sirene's head:** its shape makes her look like an alien: "This we need to keep somehow."
- **Skalpell's shadow:** its shape "has something handsome we need to keep too".
- **Rune:** the Medusa hair in her shadow, and from the front she looks a little like the creature of the Alien
  films.
- **The ladies' eyes sit low in their heads** (Sirene, Rune): keep these proportions or stay close to them.

## The characters (a start)

The true selves on the wall need the characters first (the maintainer, 2026-11-19: "Before we can show the true self
we need the real figures... And their characters..."):

- **Rune:** mysterious ("geheimnisvoll"); her true self is the Medusa.
- **Sirene:** attention seeking; her eyes go onto the wall (below).
- **Klaue:** could be the band's "manager". The maintainer, 2026-11-19, on a Shadow Play restyle whose wall
  showed a boy with tousled hair behind him: "Klaue could be very styled hair (manager hair) but in reality he is
  still a child with 'wild' hair somehow." So: on the figure, sculpted, lacquered manager hair (faceless head
  kept); his true self on the wall, a wild-haired child in the same stance.
- **Skalpell, Beben, Wucht:** open.

The critic's proposals (2026-11-19, not decided; turnarounds, body changes and each true self painted by Gemini in
the third section of the gallery page): Skalpell the Surgeon (beauty and cruelty; his shadow holds a sword), Beben
the Watcher (tenderness and dread; a heron-giant whose neck bends down over the band), Sirene the Lure (a winged
siren with talons, her eyes in the shadow), Wucht the Bull (innocence and violence; a minotaur), Rune the Oracle
(the Medusa), Klaue the Manager (charm and predation; a wolf pulling strings, before the maintainer's child).
Below the neck all six share one body today (mitten hands, flipper feet, tube legs); the critic gives each its own
hands, shorter feet and a posture. Gemini gave Wucht and Klaue faces, which loses the uncanny: keep the heads
faceless.

## The ideas

### Sirene's eyes move to her shadow

The maintainer, 2026-11-19: "I like the idea that when the front light is on Sirene eyes move to the shadow."

When the low front lamp throws the band's big shadows on the back wall, Sirene's glowing eyes leave her face and
appear in her shadow on the wall: the shadow is her true self (the wall shadows already show each member's alter ego:
Rune's Medusa, Sirene's afro, Klaue's quiff), and the eyes say so. With the front lamp off, they are back in her face.

It came from a Gemini restyle of the warm still (direction A, Shadow Play) that put eyes into her wall shadow by
mistake; the maintainer liked the mistake.

Open: whether the eyes cross over (fade in the face, fade in on the wall as the lamp comes up) or jump with the lamp;
whether the eyes on the wall look where her face looks or at the camera; whether only Sirene does it (the frontwoman)
or the others too, rarely.

### Four style directions from a blind critique

2026-11-19: a Claude agent that was told nothing about the images judged six stills of the band (one per lighting
condition) and six close-ups, and proposed four directions; Gemini (Nano Banana 2.1) restyled the stills in each. The
stills and the restyles are in `prototypes/band/ignored/previews/` (git-ignored, on the maintainer's machine), the
side-by-side page is `prototypes/band/ignored/previews/gemini/index.html`.

- **A, Shadow Play** (the critic's pick): matte black figures with a thin rim light, a lit single-colour back wall at
  every lighting condition, crisp hard alter-ego shadows, a calm matte floor. The shadows become the signature.
- **B, Black Lacquer and Gold Inlay:** mirror-black figures, each member's pattern as gold inlay, a polished stone
  floor with reflections.
- **C, Blacklight:** everything matte black, each member's pattern glowing in their own neon colour; the most
  musical (the lines can pulse with each part), but the wall shadows go.
- **D, Ink Noir:** cel shading, thick ink outlines, halftone; solves readability, but looks like many games.

What the critic found weak in the renders as they are: the floor fog reads as cloudy smears with banding at the
riser and the ankles; the glitter on the skin reads as noise; black figures on the dark speaker-tile wall get lost
(the chain-link wall is much better); the instruments look primitive (headstocks, flat cymbals, the marimba's plain
blocks); the drum's lettering looks like a placeholder. It asked for alter egos as strong as Rune's Medusa for Wucht
and Beben.

The maintainer, 2026-11-19, on the four: "There are styles I like and quite few things I would avoid, f.e. the
cartoonish outlines. Also don't like the daft punk like glowing suites... But I like the gloomy light behind the
drummer with the realistic looking wire mesh and the reflective floor." So: no ink outlines, no cel shading, no
glowing suits; keep from B the gloomy glow behind the drummer, the wall as a realistic wire mesh, the mirror floor.

### Two worlds

The maintainer, 2026-11-19: "I would really like to express 'two worlds'... day and night or good and evil." The
critic, asked again with what was liked, proposed four ways in the realistic, gloomy style (lacquer and gold that
only catches light, a wire-mesh wall, a mirror floor); Gemini restyled the stills in each (the second section of the
gallery page). The maintainer: "The two worlds shots are amazing."

- **The fence** (the critic's pick): the mesh is the border between two worlds; inside each wall shadow one looks
  through the fence into a cold, moonlit world, so each alter ego is a window, not a dark patch; Sirene's eyes cross
  over into her shadow. In real time: the wall shader opens where the low lamp's shadow falls, a backdrop behind.
- **The underworld in the mirror:** the floor reflects the other world, cold, with the alter egos in the
  reflection; for one moment of a show (a bridge, the camera low), not all the time.
- **The divided stage:** day on one side, night on the other, the seam through the drummer (half sun, half moon);
  the seam can move with the song, and a member changes world as it passes.
- **Day and night as two stage states**, plus "ember" for good and evil: one time-of-day value drives the glow, the
  lacquer, the gold, the low lamp and the alter egos over a song.

The critic's recommendation: the fence as the core, driven over a song by the time-of-day value; the mirror for one
moment. Not decided.

## History

- 2026-11-19: created, with the eyes in Sirene's shadow and the four style directions.
- 2026-11-19: the guiding feeling, what to keep, the characters as far as named, the maintainer's choice among the styles, and the two worlds.
- 2026-11-19: Klaue's styled hair over a wild child; the critic's characters for all six.

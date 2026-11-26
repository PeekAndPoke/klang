/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact

import io.peekandpoke.klang.viz.liveact.lights.ShadowQuality
import io.peekandpoke.klang.viz.liveact.lights.WorldCourse
import io.peekandpoke.klang.viz.liveact.stage.AroundCourse
import io.peekandpoke.klang.viz.liveact.stage.AroundLook
import io.peekandpoke.klang.viz.liveact.stage.BlurLook
import io.peekandpoke.klang.viz.liveact.stage.EyesLook
import io.peekandpoke.klang.viz.liveact.stage.FloorLook
import io.peekandpoke.klang.viz.liveact.stage.FogQuality
import io.peekandpoke.klang.viz.liveact.stage.GhostLight
import io.peekandpoke.klang.viz.liveact.stage.HazeLook
import io.peekandpoke.klang.viz.liveact.stage.LookFlip
import io.peekandpoke.klang.viz.liveact.stage.LookVariants
import io.peekandpoke.klang.viz.liveact.stage.OutlineLook
import io.peekandpoke.klang.viz.liveact.stage.ReflectionQuality
import io.peekandpoke.klang.viz.liveact.stage.RippleLook
import io.peekandpoke.klang.viz.liveact.stage.SkinLook
import io.peekandpoke.klang.viz.liveact.stage.StageLook
import io.peekandpoke.klang.viz.liveact.stage.StoneLook
import io.peekandpoke.klang.viz.liveact.stage.UnderworldLook
import io.peekandpoke.klang.viz.liveact.stage.WorldLook

/**
 * Every switch a page's address can give the live act ([Switches]), with what it does. [key] is its name in the
 * address (`?nosky`, `?t=62.1`); the keys stay as they are, the maintainer and the test plans use them. A switch is
 * read only through its entry here, so a new one is listed before it can be read.
 */
enum class Switch(val key: String) {
    // The show

    /**
     * `?seed=7`: the performance's seed, a whole number ([Show.seed]). Read by the demo page: without it a new seed
     * is drawn every time, or 0 where [Time] freezes the show.
     */
    Seed("seed"),

    /** `?t=62.1`: the show frozen at that time, in seconds, for stills ([Show.frozen]); what is no number runs. */
    Time("t"),

    /** `?test`: the demo page plays the test notes instead of the recorded song. */
    Test("test"),

    /**
     * `?meet=rune`: the demo page shows "Meet the Band" instead of the show: the band round a hexagon of fence panels,
     * one member at a time at the front in the light of a lamp on the floor, their true self in the shadow behind; a
     * member's name (`sirene`, `rune`, ...) brings them to the front first, any other value, or none, Sirene. The left
     * and right arrow keys turn the carousel to the member before or after.
     */
    Meet("meet"),

    // The picture

    /** `?nohud`: the line of text at the top left (what the camera does) is hidden and not written. */
    NoHud("nohud"),

    /**
     * `?oversample=2`: the picture starts drawn with that many samples for each pixel shown: 0.5, 1, 2 or 4 (the key o
     * steps through them, [Oversampling]); any other value is 1.
     */
    Oversample("oversample"),

    /** `?sharp`: the picture keeps the screen's own pixels and does not get coarser when the frames get slow. */
    Sharp("sharp"),

    /**
     * `?shadows=low`: how finely the lamps' shadows are drawn: low or high ([ShadowQuality]); any other value is high,
     * the shadows as they were made.
     */
    Shadows("shadows"),

    /**
     * `?reflections=off`: whether the mirror floor's reflections are drawn, off or on ([ReflectionQuality]), where the
     * look has the mirror floor ([Floor]); any other value is on, at every fog.
     */
    Reflections("reflections"),

    // The camera

    /** `?still`: no camera choreography, the wide view only (or the fixed look [View], [Cam] or [Head] ask for). */
    Still("still"),

    /**
     * `?move=circle`: every round of the camera's choreography is of that kind: looks, arc, circle, flight, crowd or
     * reverse; another name gives looks, an empty one leaves the choice to the show.
     */
    Move("move"),

    /**
     * `?view=side`: a fixed camera: side, stadium, or a player's name (`?view=sirene`); front, empty or another
     * name: no fixed camera.
     */
    View("view"),

    /** `?cam=1.2,1.7,2.6`: the camera held at that place (x, y, z in metres), looking at [At]. */
    Cam("cam"),

    /** `?at=1.7,0.95,0.4`: with [Cam], what the camera looks at (x, y, z in metres); 0,1,0 without it. */
    At("at"),

    /**
     * `?head=sirene`: a close look at one player's head (a player's name), under a plain inspection light, to judge
     * the head's shape; no part of the show.
     */
    Head("head"),

    /**
     * `?from=front`: with [Head], the side the head is seen from: side (without it), front, back or threequarter;
     * another name stops the show with an error.
     */
    From("from"),

    /** `?down=0.3`: with [Head], how far below the head the camera looks, in metres (0 without it). */
    Down("down"),

    /** `?far=1.8`: with [Head], how far off the camera stands, in metres (1.2 without it). */
    Far("far"),

    /** `?outline`: with [Head], the bare shape, black on white: no light, no stage, no instrument, nobody else. */
    Outline("outline"),

    // The band

    /**
     * `?warm=1`: everyone's eyes held at that warmth, from 0 (at rest) to 1 (their full colour), to look at them;
     * what is no number leaves the eyes to the playing.
     */
    Warm("warm"),

    /** `?snakes`: Medusa's snakes, her hair as only her shadow has it, are drawn as well, to look at them. */
    Snakes("snakes"),

    /** `?nosnakes`: no snakes at all, not even in her shadow. */
    NoSnakes("nosnakes"),

    /**
     * `?straight=1`: the string players held straightened up from their instruments by that share, from 0 (as they
     * play) to 1 (fully up), instead of now and then.
     */
    Straight("straight"),

    /**
     * `?eyes=shadow`: where Sirene's eyes glow while the front lamp is on ([EyesLook]): shadow, in the head of her
     * shadow on the wall or behind the fence; any other value, or none, in her face as she was made. The key i turns
     * it over while the show plays ([LookVariants]).
     */
    Eyes("eyes"),

    /**
     * `?skin=lacquer`: what the band's bodies are made of ([SkinLook]): lacquer, deep black lacquer with each member's
     * pattern as thin gold that only catches the light, and the instruments lacquered and polished; any other value,
     * or none, the dark suit with its glitter as it was made. The key l turns it over while the show plays
     * ([LookVariants]).
     */
    Skin("skin"),

    // The lights

    /** `?rig=inner`: the back lamps' rig held all through: all, outer or inner; another name leaves it to the show. */
    Rig("rig"),

    /**
     * `?fcolor=ff8800`: the front lamp's colour, as a hexadecimal number (no #), unless a colour is tried by hand;
     * what is no such number is black.
     */
    FrontColor("fcolor"),

    /**
     * `?front=1`: the front lamp held at that level, from 0 to 1, and never strobing; what is no number (`?front`)
     * reaches the lamp as no number (NaN), as in the prototype.
     */
    Front("front"),

    /** `?nostrobe`: the front lamp never strobes (it does while the bass drum runs fast). */
    NoStrobe("nostrobe"),

    // The stage and the theatre

    /** `?nosky`: no night sky over the theatre. */
    NoSky("nosky"),

    /** `?nofog`: no fog on the floor, as `?fog=off`. */
    NoFog("nofog"),

    /**
     * `?fog=low`: how finely the fog on the floor is drawn: off, low or high (0, 4 or 8 level sheets, [FogQuality]);
     * any other value is high (also the former `medium` and `xhigh`). It wins over [NoFog].
     */
    Fog("fog"),

    /** `?fullfog`: the floor's fog worked out by every pixel for itself instead of from its maps, to compare. */
    FullFog("fullfog"),

    /**
     * `?stage=mesh`: what stands behind the band ([StageLook]): mesh, a wire-mesh fence with a dark surface behind
     * it, a haze between the two in place of the fog on the floor, and a low glow behind the drummer; any other
     * value, or none, the wall as it was made. The key m turns it over while the show plays ([LookVariants]).
     */
    Stage("stage"),

    /**
     * `?floor=mirror`: the floor ([FloorLook]): mirror, polished dark stone that mirrors the stage, drawn where the
     * reflections are on ([Reflections]); any other value, or none, the floor as it was made. The key p turns it over
     * while the show plays ([LookVariants]).
     */
    Floor("floor"),

    /**
     * `?haze=parts`: the haze behind the fence ([HazeLook]): parts, it draws back where the front lamp throws the
     * band's shadows on the surface behind; any other value, or none, the haze as it was made. Only with the fence
     * ([Stage]). The key h turns it over while the show plays ([LookVariants]).
     */
    Haze("haze"),

    /**
     * `?world=night`: the two worlds' time of day ([WorldLook]): day, twilight, night, ember, ghosts (the stage dark, all the
     * light in the haze behind the fence, the band's shadows pale figures in it; its colour: [Ghosts]) or drift (from
     * day to night and back on its own, a round in about four minutes); it drives the sun behind the drummer, the
     * haze, the lamps, the lacquer and, behind the fence, a cold world seen through the band's shadows. Any other value, or none, off: the
     * stage as it was made. The key n steps through them while the show plays ([LookVariants]).
     */
    World("world"),

    /**
     * `?ghosts=red`: with [World] at ghosts, its colour ([GhostLight]): cold (a moonlit blue white), red (a deep red,
     * the figures pale rose) or green (a sea green with a violet depth); any other value, or none, cold. No key: an
     * app's controls choose it ([LookVariants]).
     */
    Ghosts("ghosts"),

    /** `?drift=30`: with [World] at drift, one round from day to night and back in that many seconds (240 without it). */
    Drift("drift"),

    /**
     * `?opening=ember`: the show opens in that world ([WorldLook.opening]): before the song (the wait for the click,
     * the walk-in) the stage stands in it from its first frame, and when the song begins it turns slowly to the time
     * of day of [World] ([WorldCourse]). Only where [World] is not off, and
     * only in a show seen before its song (not with [Time] at 0 or later). A world's word (ember, night, day); off,
     * any other value, or none: no opening.
     */
    Opening("opening"),

    /**
     * `?underworld=on`: the mirror floor shows the other world ([UnderworldLook]): its reflection turns cold, darker
     * and stiller, the sun in it a cold white moon, and Sirene's eyes, while they are in her shadow, shine in her
     * reflection too. Only where the mirror floor is drawn ([Floor]); any other value, or none, off. The key v turns it
     * over while the show plays, fading in over 2 s and out over 3 s ([LookVariants]).
     */
    Underworld("underworld"),

    /**
     * `?ripple=faint`: how uneven the mirror floor's stone is ([RippleLook]): none, faint, gentle or strong (the ripple
     * as it was first made); it bends the reflection and tilts the floor's own shading alike. Any other value, or none,
     * gentle. Only where the mirror floor is drawn ([Floor]). The key b steps through them while the show plays
     * ([LookVariants]).
     */
    Ripple("ripple"),

    /**
     * `?blur=crisp`: how blurred the mirror floor's reflection is ([BlurLook]): crisp, soft or stone (the blur as it
     * was first made); near the floor it is crisp at every level. Any other value, or none, crisp. Only where the
     * mirror floor is drawn ([Floor]). The key c steps through them while the show plays ([LookVariants]).
     */
    Blur("blur"),

    /**
     * `?stone=plain`: the mirror floor's stone ([StoneLook]): plain, the stone as it was made, all one; any other
     * value, or none, marble, dark polished marble with soft veins. Only where the mirror floor is drawn ([Floor]).
     * The key k turns it over while the show plays ([LookVariants]).
     */
    Stone("stone"),

    /**
     * `?edge=on`: a working aid, a thin bright line on the floor along the stage's edge, where the mirror ends
     * ([OutlineLook]); any other value, or none, off. (Not `?outline`: that is the head's bare shape, [Outline].) The
     * key j turns it over while the show plays ([LookVariants]).
     */
    Edge("edge"),

    /**
     * `?around=ruins`: what surrounds the stage and the audience ([AroundLook]): large dark shapes lit from below,
     * left and right of the stage behind the fence and as a horizon round the audience. forest, ruins or stones: that
     * one all through; off: none; turns, or any other value: they come by turns, the forest first, the next each time
     * the ghosts visit, when the stage is all but dark ([AroundCourse]). Without it, the new look ([Look]) has them by
     * turns and the look as made none. No key: an app's controls choose it ([LookVariants]).
     */
    Around("around"),

    // The look as a whole

    /**
     * `?look=new`: the band's new look as a whole ([LookVariants.NEW]): the fence, the mirror floor, the haze giving
     * way to the shadows, Sirene's eyes in her shadow, the lacquer and the drifting time of day. A switch of a single
     * variant beside it wins (`?look=new&world=night`). Any other value, or none, the look as it was made. The key x
     * flips between the two while the show plays ([LookFlip]).
     */
    Look("look"),
}

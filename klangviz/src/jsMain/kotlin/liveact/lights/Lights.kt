/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.lights

import io.peekandpoke.klang.viz.liveact.fromTyping
import io.peekandpoke.klang.viz.liveact.Show
import io.peekandpoke.klang.viz.liveact.Three
import io.peekandpoke.klang.viz.liveact.vec
import io.peekandpoke.klang.workshop.threejs.autoUpdate
import io.peekandpoke.klang.workshop.threejs.copied
import io.peekandpoke.klang.workshop.threejs.createColor
import io.peekandpoke.klang.workshop.threejs.lerp
import io.peekandpoke.klang.workshop.threejs.setTo
import io.peekandpoke.klang.workshop.threejs.tuneShadow
import io.peekandpoke.kraft.addons.threejs.js.Color
import io.peekandpoke.kraft.addons.threejs.js.Scene
import io.peekandpoke.kraft.addons.threejs.js.SpotLight
import io.peekandpoke.kraft.addons.threejs.js.Vector3
import kotlinx.browser.window
import org.w3c.dom.events.Event
import org.w3c.dom.events.KeyboardEvent

/**
 * A lamp of the stage: its [light], the intensity it gives at full ([base]), its own number for its flicker
 * ([seed]), the lamps it belongs to ([group]), and for a lamp behind the band where it hangs ([place]).
 */
class Lamp(val light: SpotLight, val base: Double, val seed: Double, val group: LampGroup, val place: BackPlace? = null)

/**
 * The stage's lamps: the back light (two main lamps behind and one between them), a faint fill from the front, the
 * low front lamp, and the stroboscope it becomes while the bass drum runs fast (the song's tag [kick]: see
 * [LightPlan]). Adds them to [scene]. Of the three
 * behind not all are always on (see [BackRig]).
 *
 * The lamps are analog: old stage lamps on a tired supply. Each one's brightness wanders slowly on its own,
 * breathes a little, shivers faintly, and now and then sags for a moment as if something else drew current; none
 * is ever quite steady and no two do the same (see [lampFlicker]).
 *
 * Four lamps cast shadows (the two main lamps behind, the one between them, and the low front lamp), as finely as
 * [shadows] asks; at another quality while the show plays: [shadows].
 *
 * Listens for the key f on the window (trying the front lamp's colours): [dispose] stops that.
 */
class Lights(show: Show, scene: Scene, kick: String, shadows: ShadowQuality = ShadowQuality.High) {

    /** One of the two main lamps behind: its place as built, and which side of a mood it takes. */
    private class MainLamp(val lamp: Lamp, val home: Vector3, val side: (Mood) -> MoodLamp)

    private val plan = LightPlan(show = show, kick = kick)

    /** Every lamp: the two main ones behind first, then the one between them, the fill, and the front lamp last. */
    val lamps: List<Lamp>

    /** The low front lamp, the last of [lamps]: it throws the band's big shadows on the wall. */
    val front: Lamp

    private val mains: List<MainLamp>

    /** The lamps behind the band, where each hangs and its light at full: how brightly they light the stage ([Sun.lit]). */
    private val behind: List<Pair<BackPlace, Double>>

    /** The lamps that cast a shadow at [ShadowQuality.High]: at another quality, some of them may cast none. */
    private val casters: List<Lamp>

    /** How finely the shadows are drawn now. */
    private var shadowQuality: ShadowQuality = shadows

    /** The front lamp's colour tried by hand: an index into [FrontColors.TRY], -1 while the show has the lamp. */
    private var tried = -1

    /** The key f: the front lamp in the next colour to try, after the last back to the show. */
    private val onKey: (Event) -> Unit = { event ->
        val key = event as? KeyboardEvent

        if (key != null && !event.fromTyping() && key.key.lowercase() == "f" && !key.ctrlKey && !key.metaKey && !key.altKey) {
            tryFront()
        }
    }

    /**
     * The front lamp by hand, as the key f: full on in the next colour to try ([FrontColors.TRY]), after the last
     * back to the show's own. With it the band's shadows stand behind them, so they can be looked at at any time.
     */
    fun tryFront() {
        tried = if (tried + 1 < FrontColors.TRY.size) tried + 1 else -1
    }

    private val hue = Three.addon.createColor(0xffffff)
    private val shift = vec()

    /** The faint light everywhere, a breath of blue ([ambient]). */
    private val ambient = Three.addon.createAmbientLight(color = 0x8090ff, intensity = AMBIENT)

    /** The colour of the time of day the lamps lean toward this frame ([WorldLight.lampTint]). */
    private val tint = Three.addon.createColor(0xffffff)

    /**
     * Each lamp's colour as built, in the order of [lamps]: the lamps that no mood sets (the one between the two main
     * ones behind, the fill) get it back each frame before the time of day leans them ([shine]).
     */
    private val built: List<Color>

    init {
        scene.add(ambient)

        val all = mutableListOf<Lamp>()
        val castsAtHigh = mutableListOf<Lamp>()

        fun spot(
            x: Double,
            y: Double,
            z: Double,
            color: Int,
            intensity: Double,
            targetX: Double,
            targetZ: Double,
            shadow: Boolean,
            place: BackPlace?,
        ) {
            val light = Three.addon.createSpotLight(
                color = color, intensity = intensity, distance = 30.0, angle = 0.5, penumbra = 0.7, decay = 1.2,
            )
            light.position.set(x, y, z)
            light.target.position.set(targetX, 1.0, targetZ)
            light.castShadow = shadow && shadows.casts(place)
            light.tuneShadow(mapSize = shadows.mapSize, bias = -0.0005)
            scene.add(light)
            scene.add(light.target)
            all.add(
                Lamp(
                    light = light,
                    base = intensity,
                    seed = all.size * 7.31 + 1.7,
                    group = if (z < 0) LampGroup.BACK else LampGroup.FILL,
                    place = place,
                )
            )

            if (shadow) {
                castsAtHigh.add(all.last())
            }
        }

        spot(
            x = -4.5, y = 5.0, z = -4.0, color = 0x3070ff, intensity = 260.0, targetX = -2.0, targetZ = 0.0, shadow = true,
            place = BackPlace.OUTER,
        )
        spot(
            x = 4.5, y = 5.0, z = -4.0, color = 0xff3070, intensity = 260.0, targetX = 2.0, targetZ = 0.0, shadow = true,
            place = BackPlace.OUTER,
        )
        spot(
            x = 0.0, y = 5.5, z = -4.5, color = 0x70a0ff, intensity = 180.0, targetX = 0.0, targetZ = -1.0, shadow = true,
            place = BackPlace.INNER,
        )
        spot(
            x = 0.0, y = 6.0, z = 8.0, color = 0xfff0e0, intensity = 25.0, targetX = 0.0, targetZ = 0.0, shadow = false,
            place = null,
        )

        // the lamp at the front: on the floor before the stage, looking up at the band and the wall, wide. In the
        // stage's middle: it stood 0.6 m to the right, and as it throws everything some three times its way from the
        // lamp, the band's shadows all stood nearly 2 m left of the band, the outermost on the left half out of the
        // picture (the maintainer, 2026-11-21: "the shadows and ghosts are all shifted a bit to the left")
        val low = Three.addon.createSpotLight(
            color = 0xff8420, intensity = 95.0, distance = 40.0, angle = 0.62, penumbra = 0.5, decay = 1.0,
        )
        low.position.set(0.0, 0.25, 8.6)
        low.target.position.set(0.0, 2.6, -8.0)
        // the band's big shadows on the wall are the look: it casts at every quality
        low.castShadow = shadows.casts(null)
        low.tuneShadow(mapSize = shadows.mapSize, bias = -0.0006, near = 1.0, far = 30.0)
        scene.add(low)
        scene.add(low.target)
        all.add(Lamp(light = low, base = 95.0, seed = 31.7, group = LampGroup.FRONT))
        castsAtHigh.add(all.last())

        lamps = all
        built = all.map { it.light.color.copied() }
        front = all.last()
        casters = castsAtHigh
        behind = all.mapNotNull { lamp -> lamp.place?.let { place -> place to lamp.base } }
        mains = listOf<(Mood) -> MoodLamp>(Mood::left, Mood::right).mapIndexed { index, side ->
            val lamp = lamps[index]

            MainLamp(lamp = lamp, home = lamp.light.position.clone(), side = side)
        }

        window.addEventListener("keydown", onKey)
    }

    /** The two main lamps behind in the mood of [t]: their colours and where they hang. */
    fun mood(t: Double) {
        val mix = plan.moodAt(t)
        val u = mix.share

        for (main in mains) {
            val a = main.side(mix.from)
            val b = main.side(mix.to)
            main.lamp.light.color.setHex(a.color).lerp(hue.setHex(b.color), u)

            val from = a.offset
            val to = b.offset
            main.lamp.light.position.copy(main.home).add(
                shift.set(
                    from.first + (to.first - from.first) * u,
                    from.second + (to.second - from.second) * u,
                    from.third + (to.third - from.third) * u,
                )
            )
        }
    }

    /**
     * Keeps the front lamp's shadow drawn this frame where it is still [read] after the lamp went dark: the haze's
     * clearings and the windows into the cold world behind the fence read its shadow while they fade out after it
     * (some 1.6 s), and without it they would hold the lamp's last shadow while the band moves on (the maintainer,
     * 2026-11-20: "the shadows stop updating while they are fading out"). Call it after [shine], each frame; where
     * nothing reads it any more, the lamp's shadow is drawn as [shine] says (not while it is dark). three.js draws a dark
     * lamp's shadow as any other's: it skips none for its intensity.
     */
    fun keepFrontShadow(read: Boolean) {
        if (read) {
            front.light.shadow.autoUpdate = true
        }
    }

    /** How much the front lamp is on at [t], and whether it strobes; [waiting]: before the click. */
    fun frontAt(t: Double, waiting: Boolean): FrontLight = plan.frontAt(t, waiting = waiting, trying = tried >= 0)

    /**
     * Sets every lamp's light for [t]: [house] 0 dark to 1, [front] the front lamp's state (from [frontAt]). The
     * front lamp takes its colour; each lamp flickers on its own. The time of day [world] ([WorldLight]; off: the lamps
     * as made) leans the lamps behind and the fill toward its colour and scales them, and makes the front lamp weaker,
     * warmer and its shadows softer by day; its colours and moods stay the show's (and the key f's).
     */
    fun shine(t: Double, house: Double, front: FrontLight, world: WorldTime = WorldTime.OFF) {
        val rig = plan.rigAt(t)

        tint.setHex(WorldLight.lampTint(world))

        lamps.forEachIndexed { index, lamp ->
            val light = lamp.light
            val scale = when (lamp.group) {
                LampGroup.FRONT -> WorldLight.frontScale(world)
                LampGroup.BACK -> WorldLight.backScale(world)
                LampGroup.FILL -> WorldLight.fillScale(world)
            }

            if (lamp.group == LampGroup.FRONT) {
                val made = if (front.strobing) FrontColors.BLUE else plan.frontColor(t, tried)

                light.color.setHex(WorldLight.frontColor(world, made = made))
                light.shadow.asDynamic().radius = WorldLight.frontSoftness(world)
            } else {
                // the two main lamps behind took their mood's colour this frame (mood); the others start from theirs
                if (mains.none { it.lamp === lamp }) {
                    light.color.setTo(built[index])
                }

                val lean = if (lamp.group == LampGroup.BACK) WorldLight.backLean(world) else WorldLight.fillLean(world)

                if (lean > 0.0) {
                    light.color.lerp(tint, lean)
                }
            }

            val own = plan.own(lamp.seed)
            val place = lamp.place
            val lit = if (place != null) rig.share(place) else 1.0

            light.intensity = lamp.base * lampFlicker(t, own) * house * lamp.group.share(front.level, front.strobing) * lit * scale
            // the front lamp is dark most of the show, and so is a lamp behind that its rig leaves out: a dark lamp's
            // shadow is not drawn (see shadowDrawn)
            val shadow = lamp.light.shadow

            shadow.autoUpdate = shadowDrawn(intensity = light.intensity, made = shadow.map != null)
        }
    }

    /**
     * The sun behind the drummer at [t] (`?stage=mesh`, [Sun]): sets [into] to its colour in the mood and under the
     * front lamp's state [front] (from [frontAt]), and gives how bright it is, from 0: [house] 0 dark to 1, dimmed
     * under the front lamp, following how brightly the lamps behind light the stage ([Sun.follow]), and flickering
     * like the lamps. The time of day [world] gives it its own colour and strength ([WorldLight.sunColor],
     * [WorldLight.sunStrength]); by day the front lamp dims it less ([WorldLight.sunDimming]).
     */
    fun sun(t: Double, house: Double, front: FrontLight, into: Color, world: WorldTime = WorldTime.OFF): Double {
        val lamp = if (front.strobing) FrontColors.BLUE else plan.frontColor(t, tried)
        val lit = Sun.lit(rig = plan.rigAt(t), lamps = behind)
        val made = Sun.color(mood = plan.moodAt(t), front = lamp, frontLevel = front.level)

        into.setHex(WorldLight.sunColor(world, made = made, front = lamp, frontLevel = front.level))

        val dimmed = Sun.share(front = front.level * WorldLight.sunDimming(world), strobing = front.strobing)

        return lampFlicker(t, plan.own(SUN_SEED)) * house * dimmed * Sun.follow(lit) * WorldLight.sunStrength(world)
    }

    /**
     * The shadows at [quality], where it is another than now: each lamp's map is freed where its size changes or the
     * lamp casts no more (three.js makes the new one at the next frame), and the lamps cast as [quality] says. A
     * choice by hand: where the number of lamps that cast changes, every lit material is built anew once (a short
     * hitch, the first time a quality is drawn).
     */
    fun shadows(quality: ShadowQuality) {
        if (quality == shadowQuality) {
            return
        }

        shadowQuality = quality

        for (lamp in casters) {
            val light = lamp.light
            val shadow: dynamic = light.shadow
            val casts = quality.casts(lamp.place)

            if (!casts || shadow.mapSize.x != quality.mapSize) {
                shadow.map?.dispose()
                shadow.map = null
            }

            light.tuneShadow(mapSize = quality.mapSize)
            light.castShadow = casts
        }
    }

    /**
     * The faint light everywhere at [level], 0 to 1: the stage's light coming up in a show that opens, from pitch black
     * (the lamps and the sun take the house level by themselves, [shine], [sun]); 1 otherwise.
     */
    fun ambient(level: Double) {
        ambient.intensity = AMBIENT * level
    }

    /** Stops listening for the key f on the window. */
    fun dispose() {
        window.removeEventListener("keydown", onKey)
    }

    private companion object {
        /** How bright the faint light everywhere is. */
        const val AMBIENT = 0.05

        /** The sun's own number for its flicker, as a lamp's seed: none of the lamps has it. */
        const val SUN_SEED = 43.9
    }
}

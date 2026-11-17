/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.lights

import io.peekandpoke.klang.viz.liveact.fromTyping
import io.peekandpoke.klang.viz.liveact.Show
import io.peekandpoke.klang.viz.liveact.Three
import io.peekandpoke.klang.viz.liveact.vec
import io.peekandpoke.klang.workshop.threejs.createColor
import io.peekandpoke.klang.workshop.threejs.lerp
import io.peekandpoke.klang.workshop.threejs.tuneShadow
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
 * low front lamp, and the stroboscope it becomes while the bass drum runs fast. Adds them to [scene]. Of the three
 * behind not all are always on (see [BackRig]).
 *
 * The lamps are analog: old stage lamps on a tired supply. Each one's brightness wanders slowly on its own,
 * breathes a little, shivers faintly, and now and then sags for a moment as if something else drew current; none
 * is ever quite steady and no two do the same (see [lampFlicker]).
 *
 * Listens for the key f on the window (trying the front lamp's colours): [dispose] stops that.
 */
class Lights(show: Show, scene: Scene) {

    /** One of the two main lamps behind: its place as built, and which side of a mood it takes. */
    private class MainLamp(val lamp: Lamp, val home: Vector3, val side: (Mood) -> MoodLamp)

    private val plan = LightPlan(show)

    /** Every lamp: the two main ones behind first, then the one between them, the fill, and the front lamp last. */
    val lamps: List<Lamp>

    private val mains: List<MainLamp>

    /** The front lamp's colour tried by hand: an index into [FrontColors.TRY], -1 while the show has the lamp. */
    private var tried = -1

    /** The key f: the front lamp in the next colour to try, after the last back to the show. */
    private val onKey: (Event) -> Unit = { event ->
        val key = event as? KeyboardEvent

        if (key != null && !event.fromTyping() && key.key.lowercase() == "f" && !key.ctrlKey && !key.metaKey && !key.altKey) {
            tried = if (tried + 1 < FrontColors.TRY.size) tried + 1 else -1
        }
    }

    private val hue = Three.addon.createColor(0xffffff)
    private val shift = vec()

    init {
        scene.add(Three.addon.createAmbientLight(color = 0x8090ff, intensity = 0.05))

        val all = mutableListOf<Lamp>()

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
            light.castShadow = shadow
            light.tuneShadow(mapSize = 2048, bias = -0.0005)
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

        // the lamp at the front: on the floor before the stage, looking up at the band and the wall, wide
        val low = Three.addon.createSpotLight(
            color = 0xff8420, intensity = 95.0, distance = 40.0, angle = 0.62, penumbra = 0.5, decay = 1.0,
        )
        low.position.set(0.6, 0.25, 8.6)
        low.target.position.set(0.0, 2.6, -8.0)
        low.castShadow = true
        low.tuneShadow(mapSize = 2048, bias = -0.0006, near = 1.0, far = 30.0)
        scene.add(low)
        scene.add(low.target)
        all.add(Lamp(light = low, base = 95.0, seed = 31.7, group = LampGroup.FRONT))

        lamps = all
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

    /** How much the front lamp is on at [t], and whether it strobes; [waiting]: before the click. */
    fun frontAt(t: Double, waiting: Boolean): FrontLight = plan.frontAt(t, waiting = waiting, trying = tried >= 0)

    /**
     * Sets every lamp's light for [t]: [house] 0 dark to 1, [front] the front lamp's state (from [frontAt]). The
     * front lamp takes its colour; each lamp flickers on its own.
     */
    fun shine(t: Double, house: Double, front: FrontLight) {
        val rig = plan.rigAt(t)

        for (lamp in lamps) {
            if (lamp.group == LampGroup.FRONT) {
                lamp.light.color.setHex(if (front.strobing) FrontColors.BLUE else plan.frontColor(t, tried))
            }

            val own = plan.own(lamp.seed)
            val place = lamp.place
            val lit = if (place != null) rig.share(place) else 1.0

            lamp.light.intensity = lamp.base * lampFlicker(t, own) * house * lamp.group.share(front.level, front.strobing) * lit
            // the front lamp is dark most of the show, and so is a lamp behind that its rig leaves out: a dark lamp's
            // shadow is not drawn (see shadowDrawn)
            val shadow: dynamic = lamp.light.asDynamic().shadow

            shadow.autoUpdate = shadowDrawn(intensity = lamp.light.intensity, made = shadow.map != null)
        }
    }

    /** Stops listening for the key f on the window. */
    fun dispose() {
        window.removeEventListener("keydown", onKey)
    }
}

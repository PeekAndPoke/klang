/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.peekandpoke.kraft.addons.registry.AddonRegistry.Companion.addons
import io.peekandpoke.kraft.addons.registry.addons
import io.peekandpoke.kraft.addons.threejs.ThreeJsAddon
import io.peekandpoke.kraft.addons.threejs.js.Object3D
import io.peekandpoke.kraft.addons.threejs.threeJs
import io.peekandpoke.kraft.components.NoProps
import io.peekandpoke.kraft.components.PureComponent
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.testing.TestBed
import io.peekandpoke.kraft.testing.eventually
import io.peekandpoke.kraft.utils.jsObject
import io.peekandpoke.kraft.vdom.VDom
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.html.Tag
import kotlinx.html.div
import org.w3c.dom.Element
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.events.Event
import org.w3c.dom.events.KeyboardEvent
import org.w3c.dom.events.KeyboardEventInit
import kotlin.time.Duration.Companion.seconds

private var loaded: ThreeJsAddon? = null

@Suppress("TestFunctionName")
private fun Tag.ThreeCatcher() = comp { ThreeCatcher(it) }

private class ThreeCatcher(ctx: NoProps) : PureComponent(ctx) {
    private val three by subscribingTo(addons.threeJs)

    override fun VDom.render() {
        loaded = three
        div { }
    }
}

private suspend fun withThree(block: suspend (ThreeJsAddon) -> Unit) {
    TestBed.preact(appSetup = { addons { threeJs() } }, view = { ThreeCatcher() }) {
        block(eventually(timeout = 5.seconds) { loaded.shouldNotBeNull() })
    }
}

/** Whether a key pressed on [target] counts as typing, as a listener on the window sees it. */
private fun typingOn(target: Element?): Boolean {
    var seen: Boolean? = null
    val listener: (Event) -> Unit = { seen = it.fromTyping() }

    window.addEventListener("keydown", listener)

    try {
        val event = KeyboardEvent("keydown", KeyboardEventInit(key = "f", bubbles = true, composed = true))

        if (target != null) {
            target.dispatchEvent(event)
        } else {
            window.dispatchEvent(event)
        }
    } finally {
        window.removeEventListener("keydown", listener)
    }

    return seen.shouldNotBeNull()
}

/** [element] in the page for the length of [block]. */
private fun <T> inPage(element: Element, block: () -> T): T {
    document.body!!.appendChild(element)

    try {
        return block()
    } finally {
        element.remove()
    }
}

class ThreeHelpersSpec : StringSpec({

    "a key pressed in a field, a text area, a choice or an editable element is typing" {
        for (tag in listOf("input", "textarea", "select")) {
            val field = document.createElement(tag)

            inPage(field) { typingOn(field) } shouldBe true
        }

        val editable = document.createElement("div")
        val inside = document.createElement("span")
        editable.setAttribute("contenteditable", "true")
        editable.appendChild(inside)

        inPage(editable) { typingOn(inside) } shouldBe true
    }

    "a key pressed on the page itself, on a plain element or on the window is no typing" {
        val plain = document.createElement("div")

        inPage(plain) { typingOn(plain) } shouldBe false
        typingOn(document.body) shouldBe false
        typingOn(null) shouldBe false
    }

    "a key pressed in a field inside a web component is typing, though the window sees only its host" {
        val host = document.createElement("div")
        val field = document.createElement("input")
        val root: dynamic = host.asDynamic().attachShadow(jsObject<dynamic> { mode = "open" })
        root.appendChild(field)

        inPage(host) { typingOn(field) } shouldBe true
    }

    "giving a tree up frees its geometries, its materials, their pictures, a lamp's shadow map, a skeleton and a mesh drawn many times" {
        withThree { three ->
            val freed = mutableListOf<String>()

            fun watch(what: String, thing: dynamic) {
                thing.addEventListener("dispose") { freed.add(what) }
            }

            val root = three.createGroup()
            val geometry = three.createBoxGeometry()
            val picture = three.createCanvasTexture(document.createElement("canvas") as HTMLCanvasElement)
            val pictured = three.createMeshStandardMaterial(jsObject { color = 0xffffff })
            val plain = three.createMeshStandardMaterial(jsObject { color = 0x000000 })

            pictured.asDynamic().map = picture
            watch("geometry", geometry)
            watch("picture", picture)
            watch("pictured", pictured)
            watch("plain", plain)

            // one mesh with one material, one with two; both share the geometry
            root.add(three.createMesh(geometry, pictured))
            root.add(three.createMesh(geometry, arrayOf(pictured, plain)))

            // a lamp whose shadow map and a figure whose skeleton say when they are freed
            val lamp = three.createSpotLight()
            val shadowFreed = mutableListOf<Boolean>()
            lamp.asDynamic().shadow.dispose = { shadowFreed.add(true) }
            root.add(lamp)

            val figure = three.create<dynamic>("SkinnedMesh", geometry, plain)
            val skeletonFreed = mutableListOf<Boolean>()
            figure.skeleton = jsObject<dynamic> { dispose = { skeletonFreed.add(true) } }
            root.add(figure.unsafeCast<Object3D>())

            // a mesh drawn many times over, which says when its places are freed
            val many = three.create<dynamic>("InstancedMesh", geometry, plain, 3)
            val manyFreed = mutableListOf<Boolean>()
            many.addEventListener("dispose") { manyFreed.add(true) }
            root.add(many.unsafeCast<Object3D>())

            root.disposeTree()

            manyFreed.size shouldBe 1
            freed.contains("geometry") shouldBe true
            freed.contains("picture") shouldBe true
            freed.contains("pictured") shouldBe true
            freed.contains("plain") shouldBe true
            shadowFreed.size shouldBe 1
            skeletonFreed.size shouldBe 1
            // the nodes stay where they are
            root.children.size shouldBe 5
        }
    }
})

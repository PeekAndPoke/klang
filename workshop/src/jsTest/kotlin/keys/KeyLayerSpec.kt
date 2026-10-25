package io.peekandpoke.klang.workshop.keys

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.peekandpoke.kraft.components.NoProps
import io.peekandpoke.kraft.components.PureComponent
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.testing.TestBed
import io.peekandpoke.kraft.vdom.VDom
import kotlinx.browser.document
import kotlinx.coroutines.delay
import kotlinx.html.Tag
import kotlinx.html.div

private val save = KeyAction(id = "save", title = "Save", keys = listOf("Mod-S"))
private val log = mutableListOf<String>()

/** A dialog that opens a layer of keys for itself and everything below it. */
private class Dialog(ctx: NoProps) : PureComponent(ctx) {
    init {
        openKeyLayer()
    }

    override fun VDom.render() {
        div {
            OnKey(save) { log.add("dialog") }
        }
    }
}

@Suppress("FunctionName")
private fun Tag.Dialog() = comp { Dialog(it) }

/** The page: its own Save, and the dialog while it is shown. */
private class LayeredPage(ctx: NoProps) : PureComponent(ctx) {
    companion object {
        var current: LayeredPage? = null
    }

    var dialogShown by value(false)

    init {
        current = this
    }

    override fun VDom.render() {
        div {
            OnKey(save) { log.add("page") }

            if (dialogShown) {
                Dialog()
            }
        }
    }
}

@Suppress("FunctionName")
private fun Tag.LayeredPage() = comp { LayeredPage(it) }

/**
 * A component that opens a layer of keys in a mounted Kraft app: while it is there its keys answer and the page's
 * wait; when it goes the page's answer again.
 */
class KeyLayerSpec : StringSpec({

    "a component's layer: its keys answer while it is mounted, the page's wait, and come back when it unmounts" {
        log.clear()
        val manager = KeysManager(platform = KeyPlatform.Pc, hintDelayMs = 10).apply {
            define(object : KeyBindings {
                override val actions = listOf(save)
            })
        }

        fun press() {
            document.body!!.dispatchEvent(keyEvent(key = "s", code = "KeyS", ctrl = true))
        }

        try {
            TestBed.preact(appSetup = { keys(manager) }, view = { LayeredPage() }) {
                val page = LayeredPage.current!!

                press()

                page.dialogShown = true
                delay(30)

                withClue("the dialog's layer is on top once it mounted") {
                    manager.isOnTop(manager.rootLayer) shouldBe false
                }

                press()

                page.dialogShown = false
                delay(30)

                withClue("unmounted: the root on top again") {
                    manager.isOnTop(manager.rootLayer) shouldBe true
                }

                press()

                log shouldContainExactly listOf("page", "dialog", "page")
            }
        } finally {
            manager.dispose()
        }
    }
})

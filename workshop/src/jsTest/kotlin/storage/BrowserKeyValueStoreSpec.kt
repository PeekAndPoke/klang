package io.peekandpoke.klang.workshop.storage

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.browser.localStorage
import kotlinx.browser.sessionStorage
import kotlinx.browser.window
import org.w3c.dom.Storage
import org.w3c.dom.StorageEvent
import org.w3c.dom.StorageEventInit

/**
 * The browser's storage as a store (KLANG-00422): texts kept and forgotten, a change in another tab told, and a
 * browser that gives no storage, or one that fails, never stops the app.
 */
class BrowserKeyValueStoreSpec : StringSpec({

    val key = "browser-key-value-store-spec"

    /** A storage whose every call throws, as a browser's with the site's data blocked. */
    fun blocked(): Storage = js(
        """({
            getItem: function () { throw new Error("blocked") },
            setItem: function () { throw new Error("blocked") },
            removeItem: function () { throw new Error("blocked") }
        })"""
    ).unsafeCast<Storage>()

    fun changeElsewhere(area: Storage, changed: String?, text: String?) {
        window.dispatchEvent(StorageEvent("storage", StorageEventInit(key = changed, newValue = text, storageArea = area)))
    }

    "a text kept is read back; forgotten, there is none" {
        val store = BrowserKeyValueStore(storage = sessionStorage)

        try {
            store.read(key) shouldBe null
            store.write(key = key, text = "kept") shouldBe true
            store.read(key) shouldBe "kept"
            withClue("in the browser's storage") { sessionStorage.getItem(key) shouldBe "kept" }

            store.remove(key)
            store.read(key) shouldBe null
        } finally {
            sessionStorage.removeItem(key)
        }
    }

    "a change in another tab is told: this storage's and this key's only, a removal as none, until stopped" {
        val told = mutableListOf<String?>()
        val stop = BrowserKeyValueStore(storage = localStorage).watch(key) { told.add(it) }

        try {
            changeElsewhere(area = localStorage, changed = key, text = "theirs")
            changeElsewhere(area = sessionStorage, changed = key, text = "the tab's own")
            changeElsewhere(area = localStorage, changed = "another-key", text = "another")
            changeElsewhere(area = localStorage, changed = key, text = null)

            told shouldBe listOf("theirs", null)
        } finally {
            stop()
        }

        changeElsewhere(area = localStorage, changed = key, text = "after")
        withClue("stopped") { told.size shouldBe 2 }
    }

    "a storage that fails: nothing read, nothing kept, each told, nothing thrown" {
        val problems = mutableListOf<String>()
        val store = BrowserKeyValueStore(storage = blocked(), onProblem = { message, _ -> problems.add(message) })

        store.read(key) shouldBe null
        store.write(key = key, text = "kept") shouldBe false
        store.remove(key)

        problems shouldBe listOf("Could not read $key", "Could not keep $key", "Could not forget $key")
    }

    "a browser that gives no storage: memory in its place, and told; one that gives it: the browser's" {
        val problems = mutableListOf<String>()
        val store = BrowserKeyValueStore.orMemory(onProblem = { message, _ -> problems.add(message) }) { error("The operation is insecure") }

        store.shouldBeInstanceOf<MemoryKeyValueStore>()
        problems shouldBe listOf("The browser gives no storage: nothing is kept beyond this visit")

        withClue("it keeps for this visit") {
            store.write(key = key, text = "kept") shouldBe true
            store.read(key) shouldBe "kept"
        }

        try {
            val real = BrowserKeyValueStore.orMemory(onProblem = { message, _ -> problems.add(message) }) { sessionStorage }

            real.write(key = key, text = "kept")
            withClue("the browser's own") { sessionStorage.getItem(key) shouldBe "kept" }
            problems.size shouldBe 1
        } finally {
            sessionStorage.removeItem(key)
        }
    }
})

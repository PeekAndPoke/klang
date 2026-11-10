package io.peekandpoke.klang.workshop.storage

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.ultra.streams.StreamSource
import io.peekandpoke.ultra.streams.StreamStorage
import io.peekandpoke.ultra.streams.Unsubscribe

/** A storage that remembers what it was asked, and can change from elsewhere. */
private class Recording(var kept: Int? = null) : WatchedStreamStorage<Int> {
    val written = mutableListOf<Int>()
    var watcher: ((Int) -> Unit)? = null

    override fun read(): Int? = kept

    override fun write(value: Int) {
        written.add(value)
        kept = value
    }

    override fun watch(onChange: (Int) -> Unit): Unsubscribe {
        watcher = onChange

        return { watcher = null }
    }
}

/** A stream kept in a storage (KLANG-00422): shown from it, written to it, and told of its changes from elsewhere. */
class PersistSpec : StringSpec({

    "what the storage holds is shown at once; with nothing held, the stream's own value stays" {
        StreamSource(1).persist(Recording(kept = 7))() shouldBe 7
        StreamSource(1).persist(Recording())() shouldBe 1
    }

    "a value published is written, then shown: a listener that publishes again writes after it" {
        val storage = Recording()
        val stream = StreamSource(1).persist(storage)
        val seen = mutableListOf<Int>()

        stream.subscribeToStream { now ->
            seen.add(now)

            if (now == 2) {
                stream(3)
            }
        }

        stream(2)

        storage.written shouldBe listOf(2, 3)
        withClue("the storage ends with what the stream shows") {
            stream() shouldBe 3
            storage.kept shouldBe 3
        }
    }

    "a change from elsewhere is shown and not written back; closed, it is no longer followed" {
        val storage = Recording(kept = 1)
        val stream = StreamSource(0).persist(storage)

        storage.watcher!!(5)

        stream() shouldBe 5
        storage.written shouldBe emptyList()

        stream.close()
        withClue("closed") { storage.watcher shouldBe null }
    }

    "a storage that cannot be read leaves the stream its value; a plain storage is not watched" {
        val broken = object : StreamStorage<Int> {
            override fun read(): Int? = error("unreadable")

            override fun write(value: Int) {}
        }

        StreamSource(4).persist(broken)() shouldBe 4
    }
})

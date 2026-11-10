package io.peekandpoke.klang.workshop.storage

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** A document for the spec. */
@Serializable
private data class Sample(val name: String = "none", val count: Int = 1, val inner: Inner = Inner()) {
    @Serializable
    data class Inner(val depth: Int = 0)
}

/** The same document as a newer build knows it: one field more. */
@Serializable
private data class Newer(val name: String = "none", val count: Int = 1, val inner: Sample.Inner = Sample.Inner(), val size: Int = 12)

/** A document with a field that takes only some values. */
@Serializable
private data class Moded(val mode: Mode = Mode.Quiet, val count: Int = 1) {
    enum class Mode { Quiet, Loud }
}

/** A document with a number that is no whole one, and a list of objects. */
@Serializable
private data class Listed(val ratio: Double = 1.0, val tags: List<Sample.Inner> = emptyList())

/** A document with a field that has no default: an empty document cannot be read as it. */
@Serializable
private data class Required(val id: Int, val count: Int = 1)

/** A store that refuses some keys, as a browser's storage that is full. */
private class RefusingStore(private val kept: MutableMap<String, String>, private val refuses: (String) -> Boolean) : KeyValueStore {
    override fun read(key: String): String? = kept[key]

    override fun write(key: String, text: String): Boolean {
        if (refuses(key)) {
            return false
        }

        kept[key] = text

        return true
    }

    override fun remove(key: String) {
        kept.remove(key)
    }

    override fun watch(key: String, onChange: (text: String?) -> Unit) = {}
}

/**
 * A document kept under a key (KLANG-00422): a write merges what changed into what is kept; what cannot be read is
 * put aside, never written over; what was kept before is read once; a change from elsewhere is told.
 */
class DocumentStorageSpec : StringSpec({

    val problems = mutableListOf<String>()

    fun sample(store: KeyValueStore, legacy: (KeyValueStore) -> DocumentStorage.Legacy<Sample>? = { null }, follows: Boolean = true) =
        DocumentStorage(
            key = "doc",
            version = 2,
            serializer = Sample.serializer(),
            store = store,
            clean = { it.copy(count = it.count.coerceAtMost(10)) },
            legacy = legacy,
            follows = follows,
            onProblem = { message, _ -> problems.add(message) },
        )

    fun newer(store: KeyValueStore) = DocumentStorage(key = "doc", version = 2, serializer = Newer.serializer(), store = store)

    "the two merge functions: what changed between two documents, and a patch put over a kept one" {
        val before = JsonObject(mapOf("a" to JsonPrimitive(1), "b" to JsonPrimitive("x")))
        val after = JsonObject(mapOf("a" to JsonPrimitive(1), "b" to JsonPrimitive("y"), "c" to JsonPrimitive(true)))

        changedFields(before = before, after = after) shouldBe JsonObject(mapOf("b" to JsonPrimitive("y"), "c" to JsonPrimitive(true)))
        changedFields(before = after, after = after) shouldBe JsonObject(emptyMap())

        val kept = JsonObject(mapOf("a" to JsonPrimitive(9), "z" to JsonPrimitive("kept")))

        mergeFields(kept = kept, patch = JsonObject(mapOf("a" to JsonPrimitive(1)))) shouldBe
            JsonObject(mapOf("a" to JsonPrimitive(1), "z" to JsonPrimitive("kept")))
    }

    "nothing kept: no value; the first write keeps a document, with its version" {
        val store = MemoryKeyValueStore()
        val storage = sample(store)

        storage.read() shouldBe null

        storage.write(Sample(name = "first"))
        store.read("doc") shouldBe """{"v":2,"data":{"name":"first"}}"""
    }

    "a new document holds only what was chosen: a default that changes later reaches the fields nobody chose" {
        val store = MemoryKeyValueStore()
        val storage = sample(store)

        storage.write(Sample(count = 4))
        store.read("doc") shouldBe """{"v":2,"data":{"count":4}}"""

        storage.write(Sample(name = "none", count = 5))
        withClue("the name is still nobody's choice") { store.read("doc") shouldBe """{"v":2,"data":{"count":5}}""" }

        storage.write(Sample(name = "mine", count = 1))
        withClue("a field set back to its default was chosen: it stays") {
            store.read("doc") shouldBe """{"v":2,"data":{"count":1,"name":"mine"}}"""
        }

        withClue("a field without a default: every field is kept, and the document reads back") {
            val required = MemoryKeyValueStore()

            fun storage() = DocumentStorage(key = "r", version = 1, serializer = Required.serializer(), store = required)

            storage().write(Required(id = 7, count = 3))
            required.read("r") shouldBe """{"v":1,"data":{"id":7,"count":3}}"""
            storage().read() shouldBe Required(id = 7, count = 3)
            required.all.keys shouldBe setOf("r")

            val found = DocumentStorage(key = "r", version = 1, serializer = Required.serializer(), store = required, follows = false)

            required.remove("r")
            found.read() shouldBe null
            required.write(key = "r", text = """{"v":1,"data":{"id":7,"count":1,"size":18}}""")
            found.write(Required(id = 7, count = 3))

            withClue("a document found there keeps what this writer does not know") {
                required.read("r") shouldBe """{"v":1,"data":{"id":7,"count":3,"size":18}}"""
            }
        }
    }

    "a change from elsewhere that cannot be read: the next write starts a new document, as after one found at the start" {
        val store = MemoryKeyValueStore(initial = mapOf("doc" to """{"v":2,"data":{"name":"a"}}"""))
        val storage = sample(store).also { it.read() }

        storage.watch { }
        store.changeFromElsewhere(key = "doc", text = """{"v":2,"data":{"name":"a","count":"many"}}""")
        storage.write(Sample(name = "mine"))

        withClue("readable again") { sample(store).read() shouldBe Sample(name = "mine") }

        sample(store).also { it.read() }.write(Sample(name = "mine", count = 7))
        storage.write(Sample(name = "mine", inner = Sample.Inner(depth = 2)))

        withClue("and from then on a write merges again") {
            sample(store).read() shouldBe Sample(name = "mine", count = 7, inner = Sample.Inner(depth = 2))
        }
    }

    "a document is read as far as it goes: a field it lacks takes the default, and a value is cleaned" {
        val store = MemoryKeyValueStore(initial = mapOf("doc" to """{"v":2,"data":{"count":99}}"""))

        sample(store).read() shouldBe Sample(name = "none", count = 10)
        withClue("read whole: nothing put aside") { store.all.keys shouldBe setOf("doc") }
    }

    "an older build beside a newer one: the field it does not know stays through its writes" {
        val store = MemoryKeyValueStore()

        // The newer build sets its new field
        newer(store).also { it.read() }.write(Newer(name = "n", size = 18))

        // The older build reads the document and changes a field of its own
        val older = sample(store)

        older.read() shouldBe Sample(name = "n")
        older.write(Sample(name = "n", count = 5))

        withClue("the newer build finds its field, and the older build's change") {
            newer(store).read() shouldBe Newer(name = "n", count = 5, size = 18)
        }
        withClue("a field of the top level the older build does not know is no reason to put the document aside") {
            store.all.keys shouldBe setOf("doc")
        }
    }

    "two writers of different fields both keep theirs; of the same field, the later one" {
        val store = MemoryKeyValueStore(initial = mapOf("doc" to """{"v":2,"data":{"name":"start","count":1,"inner":{"depth":0}}}"""))
        val one = sample(store).also { it.read() }
        val other = sample(store).also { it.read() }

        one.write(Sample(name = "one", count = 1))
        other.write(Sample(name = "start", count = 7))

        withClue("different fields") { sample(store).read() shouldBe Sample(name = "one", count = 7) }

        one.write(Sample(name = "one", count = 3))
        other.write(Sample(name = "start", count = 4))

        withClue("the same field: the later write") { sample(store).read() shouldBe Sample(name = "one", count = 4) }
    }

    "a write that changes nothing since the last one leaves another writer's fields alone" {
        val store = MemoryKeyValueStore(initial = mapOf("doc" to """{"v":2,"data":{"name":"start","count":1,"inner":{"depth":0}}}"""))
        val one = sample(store).also { it.read() }
        val other = sample(store).also { it.read() }

        other.write(Sample(name = "theirs", count = 1))
        one.write(Sample(name = "start", count = 1))

        sample(store).read() shouldBe Sample(name = "theirs", count = 1)
    }

    "a document that cannot be read gives no value and is put aside as it was, before a write goes over it" {
        for (unreadable in listOf("not json at all", """{"data":{"name":"a"}}""", """{"v":2,"data":{"name":"a","count":"many"}}""")) {
            val store = MemoryKeyValueStore(initial = mapOf("doc" to unreadable))
            val storage = sample(store)

            withClue(unreadable) {
                storage.read() shouldBe null
                store.read("doc" + DocumentStorage.UNREADABLE) shouldBe unreadable

                storage.write(Sample(name = "new"))

                store.read("doc") shouldBe """{"v":2,"data":{"name":"new"}}"""
                store.read("doc" + DocumentStorage.UNREADABLE) shouldBe unreadable
            }
        }
    }

    "a document read only in part is put aside as it was: a higher version, a value a field cannot take, a field inside it does not know" {
        val higher = """{"v":7,"data":{"name":"later","count":3,"inner":{"depth":0}}}"""
        val store = MemoryKeyValueStore(initial = mapOf("doc" to higher))
        val storage = sample(store)

        storage.read() shouldBe Sample(name = "later", count = 3)
        store.read("doc" + DocumentStorage.NEWER) shouldBe higher

        storage.write(Sample(name = "later", count = 4))
        withClue("merged in, and the higher version stays the document's") {
            store.read("doc") shouldBe """{"v":7,"data":{"name":"later","count":4,"inner":{"depth":0}}}"""
        }

        val inside = """{"v":2,"data":{"name":"a","count":1,"inner":{"depth":2,"more":true}}}"""
        val nested = MemoryKeyValueStore(initial = mapOf("doc" to inside))

        sample(nested).read() shouldBe Sample(name = "a", inner = Sample.Inner(depth = 2))
        withClue("a field inside a nested object") { nested.read("doc" + DocumentStorage.NEWER) shouldBe inside }

        val odd = """{"v":1,"data":{"mode":"Deafening","count":5}}"""
        val moded = MemoryKeyValueStore(initial = mapOf("m" to odd))

        DocumentStorage(key = "m", version = 1, serializer = Moded.serializer(), store = moded).read() shouldBe
            Moded(mode = Moded.Mode.Quiet, count = 5)
        withClue("a value the field cannot take: the rest is read, the text put aside") {
            moded.read("m" + DocumentStorage.NEWER) shouldBe odd
        }
    }

    "no reason to put a document aside: a field it lacks, also inside an object or a list; a number written another way" {
        val lacking = MemoryKeyValueStore(initial = mapOf("doc" to """{"v":2,"data":{"name":"a","inner":{}}}"""))

        sample(lacking).read() shouldBe Sample(name = "a")
        withClue("a field inside that the document lacks") { lacking.all.keys shouldBe setOf("doc") }

        fun listed(text: String) = MemoryKeyValueStore(initial = mapOf("l" to text)).also {
            DocumentStorage(key = "l", version = 1, serializer = Listed.serializer(), store = it).read()
        }

        withClue("2 for 2.0, and an entry of a list that lacks a field") {
            listed("""{"v":1,"data":{"ratio":2,"tags":[{}]}}""").all.keys shouldBe setOf("l")
        }
        withClue("but an entry of a list with a field this build does not know") {
            listed("""{"v":1,"data":{"ratio":2.0,"tags":[{"depth":1,"more":true}]}}""").all.keys shouldBe setOf("l", "l" + DocumentStorage.NEWER)
        }
    }

    "a value cleaned on read is not this writer's change: its next write leaves the kept one" {
        val store = MemoryKeyValueStore(initial = mapOf("doc" to """{"v":2,"data":{"name":"a","count":99,"inner":{"depth":0}}}"""))
        val storage = sample(store)

        storage.read() shouldBe Sample(name = "a", count = 10)
        storage.write(Sample(name = "b", count = 10))

        store.read("doc") shouldBe """{"v":2,"data":{"name":"b","count":99,"inner":{"depth":0}}}"""
    }

    "a write is remembered: the next one brings only its own change; one the store refused is not, and comes again" {
        val store = MemoryKeyValueStore(initial = mapOf("doc" to """{"v":2,"data":{"name":"start","count":1,"inner":{"depth":0}}}"""))
        val storage = sample(store).also { it.read() }

        storage.write(Sample(name = "mine"))
        sample(store).also { it.read() }.write(Sample(name = "theirs"))
        storage.write(Sample(name = "mine", count = 5))

        withClue("the name was written before: not again") { sample(store).read() shouldBe Sample(name = "theirs", count = 5) }

        var full = true
        val kept = mutableMapOf("doc" to """{"v":2,"data":{"name":"start","count":1,"inner":{"depth":0}}}""")
        val refused = sample(RefusingStore(kept = kept, refuses = { full })).also { it.read() }

        refused.write(Sample(name = "mine"))
        full = false
        refused.write(Sample(name = "mine", count = 5))

        withClue("the name was never kept: it comes with the next write") {
            kept["doc"] shouldBe """{"v":2,"data":{"name":"mine","count":5,"inner":{"depth":0}}}"""
        }
    }

    "a document of a lower version is written with this build's" {
        val store = MemoryKeyValueStore(initial = mapOf("doc" to """{"v":1,"data":{"name":"old","count":1,"inner":{"depth":0}}}"""))
        val storage = sample(store)

        storage.read() shouldBe Sample(name = "old")
        withClue("no reason to put it aside") { store.all.keys shouldBe setOf("doc") }

        storage.write(Sample(name = "old", count = 2))
        store.read("doc") shouldBe """{"v":2,"data":{"name":"old","count":2,"inner":{"depth":0}}}"""
    }

    "a value that cannot be encoded is not kept, and nothing is thrown" {
        val store = MemoryKeyValueStore()
        val told = mutableListOf<String>()
        val storage = DocumentStorage(
            key = "l",
            version = 1,
            serializer = Listed.serializer(),
            store = store,
            onProblem = { message, _ -> told.add(message) },
        )

        storage.write(Listed(ratio = Double.NaN))

        store.all shouldBe emptyMap()
        told shouldBe listOf("Could not keep l")
    }

    "where a document cannot be put aside, nothing is written over it in this run" {
        val kept = mutableMapOf("doc" to "not json at all")
        val storage = sample(RefusingStore(kept = kept, refuses = { it.endsWith(DocumentStorage.UNREADABLE) }))

        storage.read() shouldBe null
        storage.write(Sample(name = "new"))

        kept shouldBe mapOf("doc" to "not json at all")
    }

    "the same for one read only in part" {
        val higher = """{"v":7,"data":{"name":"later","count":3,"inner":{"depth":0}}}"""
        val kept = mutableMapOf("doc" to higher)
        val storage = sample(RefusingStore(kept = kept, refuses = { it.endsWith(DocumentStorage.NEWER) }))

        storage.read() shouldBe Sample(name = "later", count = 3)
        storage.write(Sample(name = "mine", count = 3))

        kept shouldBe mapOf("doc" to higher)
    }

    "what was kept before and says what the default says is still a choice: it goes into the document" {
        val store = MemoryKeyValueStore(initial = mapOf("old-count" to "1"))

        sample(
            store = store,
            legacy = { DocumentStorage.Legacy(value = Sample(count = 1), keys = listOf("old-count"), chosen = setOf("count")) },
        ).read() shouldBe Sample()

        store.all shouldBe mapOf("doc" to """{"v":2,"data":{"count":1}}""")
    }

    "forgotten after it could not be put aside: nothing is left to protect, the next write is kept" {
        val kept = mutableMapOf("doc" to "not json at all")
        val storage = sample(RefusingStore(kept = kept, refuses = { it.endsWith(DocumentStorage.UNREADABLE) }))

        storage.read() shouldBe null
        storage.forget()
        storage.write(Sample(name = "new"))

        kept shouldBe mapOf("doc" to """{"v":2,"data":{"name":"new"}}""")
    }

    "what was kept before is asked only when there is no document, becomes the document, and its old keys go" {
        val store = MemoryKeyValueStore(initial = mapOf("old-name" to "before", "other" to "stays"))
        var asked = 0
        val legacy = { from: KeyValueStore ->
            asked++
            from.read("old-name")?.let { DocumentStorage.Legacy(value = Sample(name = it, count = 50), keys = listOf("old-name")) }
        }

        sample(store = store, legacy = legacy).read() shouldBe Sample(name = "before", count = 10)

        withClue("the document kept, the old key gone, another key left alone") {
            store.all shouldBe mapOf("doc" to """{"v":2,"data":{"name":"before","count":10}}""", "other" to "stays")
        }

        sample(store = store, legacy = legacy).read()
        withClue("a document is there: what was before is not asked again") { asked shouldBe 1 }
    }

    "a store that cannot keep the document keeps the old keys; reading what was before fails: no value, nothing removed" {
        val kept = mutableMapOf("old-name" to "before")
        val full = RefusingStore(kept = kept, refuses = { true })

        sample(store = full, legacy = { DocumentStorage.Legacy(value = Sample(name = "before"), keys = listOf("old-name")) })
            .read() shouldBe Sample(name = "before")
        withClue("the old key still there") { kept.keys shouldBe setOf("old-name") }

        val store = MemoryKeyValueStore(initial = mapOf("old-name" to "before"))

        sample(store = store, legacy = { error("unreadable") }).read() shouldBe null
        store.all.keys shouldBe setOf("old-name")
    }

    "a change from elsewhere is told, cleaned; not to one that does not follow; a removal elsewhere is not told" {
        val store = MemoryKeyValueStore()
        val told = mutableListOf<Sample>()
        val stop = sample(store).watch { told.add(it) }
        val deaf = mutableListOf<Sample>()

        sample(store = store, follows = false).watch { deaf.add(it) }

        store.changeFromElsewhere(key = "doc", text = """{"v":2,"data":{"name":"theirs","count":99}}""")
        store.changeFromElsewhere(key = "other", text = "x")
        store.changeFromElsewhere(key = "doc", text = null)

        told shouldBe listOf(Sample(name = "theirs", count = 10))
        deaf shouldBe emptyList()

        stop()
        store.changeFromElsewhere(key = "doc", text = """{"v":2,"data":{"name":"again"}}""")
        withClue("stopped") { told.size shouldBe 1 }

        withClue("nothing put aside so far") { store.all.keys shouldBe setOf("doc", "other") }
    }

    "a change from elsewhere this build reads only in part, or not at all, is put aside as one found at the start" {
        val store = MemoryKeyValueStore()
        val told = mutableListOf<Sample>()
        val higher = """{"v":7,"data":{"name":"later"}}"""

        sample(store).watch { told.add(it) }

        store.changeFromElsewhere(key = "doc", text = higher)
        told shouldBe listOf(Sample(name = "later"))
        store.read("doc" + DocumentStorage.NEWER) shouldBe higher

        store.changeFromElsewhere(key = "doc", text = "not json at all")
        withClue("not told") { told.size shouldBe 1 }
        store.read("doc" + DocumentStorage.UNREADABLE) shouldBe "not json at all"
    }

    "after a change from elsewhere, a write merges into it and does not bring the old values back" {
        val store = MemoryKeyValueStore(initial = mapOf("doc" to """{"v":2,"data":{"name":"start","count":1,"inner":{"depth":0}}}"""))
        val storage = sample(store).also { it.read() }
        var shown = Sample(name = "start")

        storage.watch { shown = it }
        store.changeFromElsewhere(key = "doc", text = """{"v":2,"data":{"name":"theirs","count":1,"inner":{"depth":0}}}""")

        // A third writer changes the name once more, and this storage is not told (its tab was asleep)
        store.write(key = "doc", text = """{"v":2,"data":{"name":"third","count":1,"inner":{"depth":0}}}""")

        storage.write(shown.copy(count = 6))

        withClue("only the count is this storage's change: the name it was told of is not written again") {
            sample(store).read() shouldBe Sample(name = "third", count = 6)
        }
    }

    "forget: nothing is kept under the key, and the next write starts a new document" {
        val store = MemoryKeyValueStore()
        val storage = sample(store)

        storage.write(Sample(name = "mine"))
        storage.forget()
        store.all shouldBe emptyMap()

        storage.write(Sample(count = 2))
        store.read("doc") shouldBe """{"v":2,"data":{"count":2}}"""

        storage.write(Sample(name = "mine", count = 2))
        withClue("what was kept before the forgetting counts no more: the same name is a choice again") {
            store.read("doc") shouldBe """{"v":2,"data":{"count":2,"name":"mine"}}"""
        }
    }
    "holds: whether anything is kept under the key, readable or not" {
        val store = MemoryKeyValueStore()
        val storage = sample(store)

        storage.holds shouldBe false

        storage.write(Sample())
        withClue("a document with nothing chosen is one") { storage.holds shouldBe true }

        storage.forget()
        storage.holds shouldBe false

        store.write(key = "another", text = "x")
        withClue("another key") { storage.holds shouldBe false }

        store.write(key = "doc", text = "not json at all")
        withClue("an unreadable one") { storage.holds shouldBe true }
    }
})

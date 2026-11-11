/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.spine

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.spine.storage.SectionStores
import io.peekandpoke.klang.storage.StorageKeys
import io.peekandpoke.klang.ui.feel.KlangLoadout
import io.peekandpoke.klang.ui.feel.KlangLookPreset
import io.peekandpoke.klang.ui.feel.KlangLookShelf
import io.peekandpoke.klang.ui.feel.KlangLooks
import io.peekandpoke.klang.ui.feel.KlangRole
import io.peekandpoke.klang.ui.feel.KlangTheme
import io.peekandpoke.klang.workshop.storage.KeyValueStore
import io.peekandpoke.klang.workshop.storage.MemoryKeyValueStore

/** A store that cannot keep anything under [failing], as a full browser storage cannot. */
private class FailingOn(private val inner: MemoryKeyValueStore, private val failing: String) : KeyValueStore by inner {
    override fun write(key: String, text: String): Boolean = key != failing && inner.write(key = key, text = text)
}

/**
 * The user's looks as stored sections (KLANG-00422): the widgets' shelf and theme tied to what is kept, the own
 * looks a field each, and what a browser kept before read once.
 */
class LooksSpec : StringSpec({

    fun stores(user: KeyValueStore) =
        SectionStores(user = user, machine = MemoryKeyValueStore(), tab = MemoryKeyValueStore())

    val rust = KlangLookPreset(
        id = "own-1",
        name = "Rust",
        values = mapOf(KlangRole.Accent to "#b04fd6"),
        author = "Someone",
        own = true,
    )
    val moss = KlangLookPreset(id = "own-2", name = "Moss", values = mapOf(KlangRole.Accent to "#2f8f4f"), own = true)

    val rustKept = """{"name":"Rust","author":"Someone","values":{"--kl-accent":"#b04fd6"}}"""
    val mossKept = """{"name":"Moss","author":"","values":{"--kl-accent":"#2f8f4f"}}"""

    /** The widgets' shelf and theme as a page finds them, before and after [block]: they are the page's, not a spec's. */
    suspend fun withWidgets(block: suspend () -> Unit) {
        KlangLookShelf.restore(own = emptyList(), slots = null)
        KlangTheme.choose(KlangLooks.Classic)

        try {
            block()
        } finally {
            KlangLookShelf.restore(own = emptyList(), slots = null)
            KlangTheme.choose(KlangLooks.Classic)
        }
    }

    "a first visit wears the first-visit look and finds the default slots, and no document is made up" {
        withWidgets {
            val user = MemoryKeyValueStore()
            val detach = Looks(stores = stores(user)).attach(firstVisit = KlangLooks.Sun)

            try {
                KlangTheme.worn shouldBe KlangLooks.Sun
                KlangLookShelf() shouldBe KlangLookShelf.State()
                user.all shouldBe emptyMap()
            } finally {
                detach()
            }
        }
    }

    "what the shelf becomes is kept, a look a field, a deleted one null; the look worn is kept when told" {
        withWidgets {
            val user = MemoryKeyValueStore()
            val looks = Looks(stores = stores(user))
            val detach = looks.attach(firstVisit = KlangLooks.Sun)

            try {
                KlangLookShelf.keep(rust)
                KlangLookShelf.keep(moss)

                user.read(StorageKeys.OWN_LOOKS) shouldBe """{"v":1,"data":{"own-1":$rustKept,"own-2":$mossKept}}"""
                withClue("the slots never arranged: not kept") { user.read(StorageKeys.LOOK_CHOICE) shouldBe null }

                KlangLookShelf.place(id = "own-2", slot = 0)
                looks.wear(moss)
                looks.worn shouldBe "own-2"

                val slots = KlangLoadout.default.place(id = "own-2", slot = 0).slots
                val slotsKept = slots.joinToString(",") { if (it == null) "null" else "\"$it\"" }
                user.read(StorageKeys.LOOK_CHOICE) shouldBe """{"v":1,"data":{"slots":[$slotsKept],"worn":"own-2"}}"""

                KlangLookShelf.delete("own-1")
                withClue("a look deleted stays as a field that says so") {
                    user.read(StorageKeys.OWN_LOOKS) shouldBe """{"v":1,"data":{"own-1":null,"own-2":$mossKept}}"""
                }

                withClue("the slots put back as a new user finds them: null, the default's from then on") {
                    KlangLookShelf.unequip(slot = 0)
                    KlangLookShelf.place(id = KlangLoadout.default.slots[0]!!, slot = 0)

                    user.read(StorageKeys.LOOK_CHOICE) shouldBe """{"v":1,"data":{"slots":null,"worn":"own-2"}}"""
                }

                KlangLookShelf.place(id = "own-2", slot = 0)
            } finally {
                detach()
            }

            withClue("untied: the shelf's changes are no longer kept") {
                val before = user.all
                KlangLookShelf.keep(rust)
                user.all shouldBe before
            }

            withClue("a page that starts on the same storage: the shelf and the theme as they were left") {
                KlangLookShelf.restore(own = emptyList(), slots = null)
                KlangTheme.choose(KlangLooks.Classic)

                val again = Looks(stores = stores(user)).attach(firstVisit = KlangLooks.Sun)

                try {
                    KlangLookShelf().own shouldBe listOf(moss)
                    KlangLookShelf().loadout shouldBe KlangLoadout.default.place(id = "own-2", slot = 0)
                    KlangTheme.worn shouldBe moss
                } finally {
                    again()
                }
            }
        }
    }

    "a tab keeps its look until it is loaded again: another tab's changes do not show, and are not undone" {
        withWidgets {
            val user = MemoryKeyValueStore()
            val looks = Looks(stores = stores(user))
            val detach = looks.attach(firstVisit = KlangLooks.Sun)

            try {
                KlangLookShelf.keep(rust)
                looks.wear(rust)
                KlangTheme.choose(rust)

                user.changeFromElsewhere(key = StorageKeys.OWN_LOOKS, text = """{"v":1,"data":{"own-1":null,"own-2":$mossKept}}""")
                user.changeFromElsewhere(key = StorageKeys.LOOK_CHOICE, text = """{"v":1,"data":{"worn":"own-2","slots":null}}""")

                withClue("another tab deleted the look worn here, made one and wears it: this tab stays as it is") {
                    KlangLookShelf().own shouldBe listOf(rust)
                    KlangTheme.worn shouldBe rust
                    looks.worn shouldBe "own-1"
                }

                withClue("what this tab keeps next leaves the other tab's fields alone") {
                    KlangLookShelf.place(id = "own-1", slot = 0)

                    user.read(StorageKeys.OWN_LOOKS) shouldBe """{"v":1,"data":{"own-1":null,"own-2":$mossKept}}"""
                    user.read(StorageKeys.LOOK_CHOICE).orEmpty().contains(""""worn":"own-2"""") shouldBe true
                }
            } finally {
                detach()
            }

            withClue("loaded again: what the other tab kept") {
                KlangLookShelf.restore(own = emptyList(), slots = null)
                KlangTheme.choose(KlangLooks.Classic)

                val again = Looks(stores = stores(user)).attach(firstVisit = KlangLooks.Sun)

                try {
                    KlangLookShelf().own shouldBe listOf(moss)
                    KlangTheme.worn shouldBe moss
                } finally {
                    again()
                }
            }
        }
    }

    "the storage page's reset shows at once: an empty shelf, the first-visit look" {
        withWidgets {
            val user = MemoryKeyValueStore()
            val looks = Looks(stores = stores(user))
            val detach = looks.attach(firstVisit = KlangLooks.Sun)

            try {
                KlangLookShelf.keep(moss)
                KlangLookShelf.place(id = "own-2", slot = 0)
                looks.wear(moss)
                KlangTheme.choose(moss)

                withClue("the look worn alone: the first-visit look, the shelf as it is") {
                    looks.sections.first { it.key == StorageKeys.LOOK_CHOICE }.reset()

                    KlangTheme.worn shouldBe KlangLooks.Sun
                    KlangLookShelf().own shouldBe listOf(moss)
                }

                looks.wear(moss)
                KlangTheme.choose(moss)

                withClue("the own looks alone: gone from the shelf, and the one worn with them") {
                    looks.sections.first { it.key == StorageKeys.OWN_LOOKS }.reset()

                    KlangLookShelf().own shouldBe emptyList()
                    KlangTheme.worn shouldBe KlangLooks.Sun
                }

                looks.sections.forEach { it.reset() }
                KlangLookShelf() shouldBe KlangLookShelf.State()
                user.all shouldBe emptyMap()
            } finally {
                detach()
            }
        }
    }

    "the look worn changed or deleted on the shelf: kept, and the theme left to the page that did it" {
        withWidgets {
            val user = MemoryKeyValueStore()
            val looks = Looks(stores = stores(user))
            val detach = looks.attach(firstVisit = KlangLooks.Sun)

            try {
                KlangLookShelf.keep(rust)
                looks.wear(rust)
                KlangTheme.choose(rust)

                withClue("changed, as the looks page applies a draft: the page puts it on, not the Spine") {
                    KlangLookShelf.keep(rust.copy(name = "Rusty"))

                    KlangTheme.worn shouldBe rust
                }

                withClue("deleted, as the looks page does it: the theme is still the page's to change") {
                    KlangLookShelf.delete("own-1")

                    KlangTheme.worn shouldBe rust
                    user.read(StorageKeys.OWN_LOOKS) shouldBe """{"v":1,"data":{"own-1":null}}"""
                }

                // The looks page, having found the deleted look worn
                KlangTheme.choose(KlangLooks.Classic)
                looks.wear(KlangLooks.Classic)

                withClue("a new look never gets the deleted one's id, and is not worn") {
                    val id = KlangLookShelf.newId()
                    (id == "own-1") shouldBe false

                    KlangLookShelf.keep(moss.copy(id = id))

                    KlangTheme.worn shouldBe KlangLooks.Classic
                    looks.worn shouldBe "classic"
                }
            } finally {
                detach()
            }
        }
    }

    "a look kept as worn that this page does not have stays kept, and the page wears the first-visit look" {
        withWidgets {
            // A built-in look of a newer build, or an own look whose section was reset
            val kept = """{"v":1,"data":{"worn":"neon-2030"}}"""
            val user = MemoryKeyValueStore(initial = mapOf(StorageKeys.LOOK_CHOICE to kept))
            val looks = Looks(stores = stores(user))
            val detach = looks.attach(firstVisit = KlangLooks.Sun)

            try {
                KlangTheme.worn shouldBe KlangLooks.Sun
                looks.worn shouldBe "neon-2030"
                user.read(StorageKeys.LOOK_CHOICE) shouldBe kept
            } finally {
                detach()
            }
        }
    }

    "a value no role accepts in what is kept is left out, and the look is still shown" {
        withWidgets {
            val user = MemoryKeyValueStore(
                initial = mapOf(
                    StorageKeys.OWN_LOOKS to
                        """{"v":1,"data":{"own-1":{"name":"Rust","author":"Someone","values":{"--kl-accent":"url(x)","--kl-no":"x"}}}}"""
                )
            )
            val detach = Looks(stores = stores(user)).attach(firstVisit = KlangLooks.Sun)

            try {
                KlangLookShelf().own shouldBe listOf(rust.copy(values = emptyMap()))

                withClue("what is kept stays as it is: a value of a newer build's role is not dropped by a page that starts") {
                    user.read(StorageKeys.OWN_LOOKS).orEmpty().contains(""""--kl-no":"x"""") shouldBe true
                }
                withClue("the look changed here is kept as this page holds it") {
                    KlangLookShelf.keep(rust.copy(values = mapOf(KlangRole.Accent to "#b04fd6")))

                    user.read(StorageKeys.OWN_LOOKS) shouldBe """{"v":1,"data":{"own-1":$rustKept}}"""
                }
            } finally {
                detach()
            }
        }
    }

    "what a browser kept before, the look's id and the looks as one text, is read once, and the old keys go" {
        withWidgets {
            val old = """{"own":[{"klang-look":1,"id":"own-1","name":"Rust","author":"Someone","values":{"--kl-accent":"#b04fd6"}}],""" +
                """"slots":["own-1",null,"classic"]}"""
            val user = MemoryKeyValueStore(initial = mapOf(StorageKeys.LOOK to "own-1", StorageKeys.LOOKS to old))
            val detach = Looks(stores = stores(user)).attach(firstVisit = KlangLooks.Sun)

            try {
                KlangLookShelf().own shouldBe listOf(rust)
                KlangLookShelf().loadout.slots.take(3) shouldBe listOf("own-1", null, "classic")
                KlangTheme.worn shouldBe rust

                user.read(StorageKeys.LOOK) shouldBe null
                user.read(StorageKeys.LOOKS) shouldBe null
                user.read(StorageKeys.OWN_LOOKS) shouldBe """{"v":1,"data":{"own-1":$rustKept}}"""
            } finally {
                detach()
            }
        }

        withWidgets {
            withClue("the old text unreadable: it stays, the look's id is still read") {
                val user = MemoryKeyValueStore(initial = mapOf(StorageKeys.LOOK to "classic", StorageKeys.LOOKS to "not json"))
                val looks = Looks(stores = stores(user))

                looks.worn shouldBe "classic"
                user.all shouldBe mapOf(
                    StorageKeys.LOOKS to "not json",
                    StorageKeys.LOOK_CHOICE to """{"v":1,"data":{"worn":"classic"}}""",
                )
            }
            withClue("only the slots kept before, no look of the user's own") {
                val user = MemoryKeyValueStore(initial = mapOf(StorageKeys.LOOKS to """{"own":[],"slots":["classic"]}"""))
                Looks(stores = stores(user))

                user.all shouldBe mapOf(StorageKeys.LOOK_CHOICE to """{"v":1,"data":{"slots":["classic"]}}""")
            }
            withClue("nothing kept before: no document is made up") {
                MemoryKeyValueStore().also { Looks(stores = stores(it)) }.all shouldBe emptyMap()
            }
        }
    }

    "the old text of the looks goes only once both documents are kept: a write that fails loses nothing" {
        val old = """{"own":[{"klang-look":1,"id":"own-1","name":"Rust","author":"Someone","values":{"--kl-accent":"#b04fd6"}}],""" +
            """"slots":["own-1"]}"""

        withClue("the own looks could not be kept") {
            val user = MemoryKeyValueStore(initial = mapOf(StorageKeys.LOOK to "own-1", StorageKeys.LOOKS to old))

            Looks(stores = stores(FailingOn(inner = user, failing = StorageKeys.OWN_LOOKS)))
            withClue("the old text stays, the choice is kept") {
                user.all.keys shouldBe setOf(StorageKeys.LOOKS, StorageKeys.LOOK_CHOICE)
            }

            Looks(stores = stores(user))
            withClue("the next start, the storage working: the looks kept, the old text gone") {
                user.all.keys shouldBe setOf(StorageKeys.LOOK_CHOICE, StorageKeys.OWN_LOOKS)
                user.read(StorageKeys.OWN_LOOKS) shouldBe """{"v":1,"data":{"own-1":$rustKept}}"""
            }
        }

        withClue("the choice could not be kept") {
            val user = MemoryKeyValueStore(initial = mapOf(StorageKeys.LOOK to "own-1", StorageKeys.LOOKS to old))

            Looks(stores = stores(FailingOn(inner = user, failing = StorageKeys.LOOK_CHOICE)))
            withClue("the old keys stay, the looks are kept") {
                user.all.keys shouldBe setOf(StorageKeys.LOOK, StorageKeys.LOOKS, StorageKeys.OWN_LOOKS)
            }

            Looks(stores = stores(user))
            withClue("the next start: the choice kept with the old slots, the old keys gone") {
                user.all.keys shouldBe setOf(StorageKeys.OWN_LOOKS, StorageKeys.LOOK_CHOICE)
                user.read(StorageKeys.LOOK_CHOICE) shouldBe """{"v":1,"data":{"worn":"own-1","slots":["own-1"]}}"""
            }
        }
    }

    "old keys left beside both documents (a write that failed once, made good later) go at the next start" {
        val old = """{"own":[{"klang-look":1,"id":"own-1","name":"Rust","author":"Someone","values":{"--kl-accent":"#b04fd6"}}],""" +
            """"slots":["own-1"]}"""
        val documents = mapOf(
            StorageKeys.OWN_LOOKS to """{"v":1,"data":{"own-2":$mossKept}}""",
            StorageKeys.LOOK_CHOICE to """{"v":1,"data":{"worn":"own-2"}}""",
        )
        val user = MemoryKeyValueStore(initial = documents + mapOf(StorageKeys.LOOK to "own-1", StorageKeys.LOOKS to old))

        Looks(stores = stores(user))
        user.all shouldBe documents

        withClue("an old text that cannot be read stays") {
            val odd = MemoryKeyValueStore(initial = documents + mapOf(StorageKeys.LOOKS to "not json"))

            Looks(stores = stores(odd))
            odd.all shouldBe documents + mapOf(StorageKeys.LOOKS to "not json")
        }
        withClue("one document only: the old text is the other's still") {
            val half = MemoryKeyValueStore(
                initial = mapOf(StorageKeys.OWN_LOOKS to """{"v":1,"data":{"own-2":$mossKept}}""", StorageKeys.LOOKS to "not json")
            )

            Looks(stores = stores(half))
            half.read(StorageKeys.LOOKS) shouldBe "not json"
        }
    }

    "the Spine's reset of the looks' keys forgets them" {
        val user = MemoryKeyValueStore()
        val spine = Spine(stores = stores(user))

        spine.looks.wear(KlangLooks.Classic)
        spine.resetStored(StorageKeys.LOOK_CHOICE)

        spine.looks.worn shouldBe null
        user.read(StorageKeys.LOOK_CHOICE) shouldBe null
    }
})

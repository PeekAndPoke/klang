/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang

import io.peekandpoke.klang.layouts.FullscreenLayout
import io.peekandpoke.klang.layouts.MotorBackgroundLayout
import io.peekandpoke.klang.layouts.WorkbenchLayout
import io.peekandpoke.klang.pages.CodeSongPage
import io.peekandpoke.klang.pages.CreditsPage
import io.peekandpoke.klang.pages.DashboardPage
import io.peekandpoke.klang.pages.DevStatusPage
import io.peekandpoke.klang.pages.DevStoragePage
import io.peekandpoke.klang.pages.LooksPage
import io.peekandpoke.klang.pages.MidiPlaygroundPage
import io.peekandpoke.klang.pages.NotFoundPage
import io.peekandpoke.klang.pages.ResourcesPage
import io.peekandpoke.klang.pages.SamplesLibraryPage
import io.peekandpoke.klang.pages.SongsPage
import io.peekandpoke.klang.pages.StartPage
import io.peekandpoke.klang.pages.TourPage
import io.peekandpoke.klang.pages.UiKitchenSinkPage
import io.peekandpoke.klang.pages.docs.DocsPage
import io.peekandpoke.klang.pages.docs.KlangScriptDocsPage
import io.peekandpoke.klang.pages.docs.KlangScriptLibraryDocsPage
import io.peekandpoke.klang.pages.docs.lexikon.LexikonEntryPage
import io.peekandpoke.klang.pages.docs.lexikon.LexikonPage
import io.peekandpoke.klang.pages.docs.tutorials.TutorialPage
import io.peekandpoke.klang.pages.docs.tutorials.TutorialTrackPage
import io.peekandpoke.klang.pages.docs.tutorials.TutorialsListPage
import io.peekandpoke.kraft.routing.RootRouterBuilder
import io.peekandpoke.kraft.routing.Route1
import io.peekandpoke.kraft.routing.Static

object Nav {
    val start = Static("")
    val startSlash = Static("/")

    val dashboard = Static("/dashboard")

    /** The songs, to open one or start a new one (KLANG-00349). */
    val songs = Static("/songs")

    val newSongCode = Static("/song/code")
    val editSongCode = Route1("/song/code/{id}")

    val samplesLibrary = Static("/samples/library")

    const val manualsBase = "/manuals"
    val manuals = Static(manualsBase)
    val manualsKlangScript = Static("$manualsBase/klang-script")

    val manualsLexikon = Static("$manualsBase/lexikon")

    /** One term on its own page. The permalink DSL docs link to, see LexikonEntryPage. */
    val manualsLexikonEntry = Route1("$manualsBase/lexikon/{slug}")

    val manualsLibrary = Route1("$manualsBase/library/{library}")
    fun manualsLibrarySearch(library: String, search: String) =
        manualsLibrary(library).withQueryParams(KlangScriptLibraryDocsPage.PARAM_SEARCH to search)

    const val tutorialsBase = "$manualsBase/tutorials"
    val tutorials = Static(tutorialsBase)
    val tutorial = Route1("$tutorialsBase/{slug}")

    /** Overview page of one tutorial track. */
    val tutorialTrack = Route1("$tutorialsBase/track/{slug}")

    /** A tutorial opened WITH track context — Prev/Next then walk that track. */
    fun tutorialInTrack(slug: String, trackSlug: String?) =
        if (trackSlug == null) tutorial(slug)
        else tutorial(slug).withQueryParams(TutorialPage.PARAM_TRACK to trackSlug)

    val midiPlayground = Static("/midi-playground")

    val resources = Static("/resources")

    val credits = Static("/credits")

    /** The Dev Tools, all under one base (the maintainer, 2026-10-28: "/dev-tools" and "/dev-tools/<SLUG>"). */
    const val devToolsBase = "/dev-tools"

    /** The Dev Tools' landing: where Klang stands. */
    val devStatus = Static(devToolsBase)

    /** What this browser keeps for Klang, to reset all of it or a part. */
    val devStorage = Static("$devToolsBase/storage")

    val tour = Static("/tour")

    /** The look editor: the equip grid, the bench, a look of one's own (KLANG-00382). */
    val looks = Static("/looks")

    /** The look editor with an empty slot of the drawer ready to be filled. */
    val looksSlot = Route1("/looks/slot/{slot}")

    /** Every UI element in every state (KLANG-00337). */
    val uiKitchenSink = Static("$devToolsBase/ui-kitchen-sink")
}

fun RootRouterBuilder.mountNav() {

    layout({ MotorBackgroundLayout { it() } }) {
        mount(Nav.start) { StartPage() }
        mount(Nav.startSlash) { StartPage() }
    }

    layout({ FullscreenLayout { it() } }) {
        mount(Nav.tour) { TourPage() }
    }

    layout({ WorkbenchLayout { it() } }) {

        mount(Nav.dashboard) { DashboardPage() }

        mount(Nav.songs) { SongsPage() }

        mount(Nav.newSongCode) { CodeSongPage(id = null) }
        mount(Nav.editSongCode) { CodeSongPage(id = it["id"]) }

        mount(Nav.samplesLibrary) { SamplesLibraryPage() }

        mount(Nav.manuals) { DocsPage() }
        mount(Nav.manualsKlangScript) { KlangScriptDocsPage() }
        mount(Nav.manualsLexikon) { LexikonPage() }
        mount(Nav.manualsLexikonEntry) { LexikonEntryPage(it["slug"]) }
        mount(Nav.manualsLibrary) { KlangScriptLibraryDocsPage(it["library"]) }

        mount(Nav.tutorials) { TutorialsListPage() }
        mount(Nav.tutorialTrack) { TutorialTrackPage() }
        mount(Nav.tutorial) { TutorialPage() }

        mount(Nav.midiPlayground) { MidiPlaygroundPage() }

        mount(Nav.resources) { ResourcesPage() }

        mount(Nav.credits) { CreditsPage() }

        mount(Nav.looks) { LooksPage(slot = null) }
        mount(Nav.looksSlot) { LooksPage(slot = it["slot"].toIntOrNull()) }

        mount(Nav.devStatus) { DevStatusPage() }
        mount(Nav.devStorage) { DevStoragePage() }
        mount(Nav.uiKitchenSink) { UiKitchenSinkPage() }
    }

    layout({ FullscreenLayout { it() } }) {
        catchAll {
            NotFoundPage()
        }
    }
}

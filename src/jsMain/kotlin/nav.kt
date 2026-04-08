package io.peekandpoke.klang

import io.peekandpoke.klang.layouts.FullscreenLayout
import io.peekandpoke.klang.layouts.MenuLayout
import io.peekandpoke.klang.layouts.MotorBackgroundLayout
import io.peekandpoke.klang.pages.CodeSongPage
import io.peekandpoke.klang.pages.CreditsPage
import io.peekandpoke.klang.pages.DashboardPage
import io.peekandpoke.klang.pages.NotFoundPage
import io.peekandpoke.klang.pages.SamplesLibraryPage
import io.peekandpoke.klang.pages.StartPage
import io.peekandpoke.klang.pages.TourPage
import io.peekandpoke.klang.pages.docs.DocsPage
import io.peekandpoke.klang.pages.docs.KlangScriptDocsPage
import io.peekandpoke.klang.pages.docs.KlangScriptLibraryDocsPage
import io.peekandpoke.klang.pages.docs.lexikon.LexikonPage
import io.peekandpoke.klang.pages.docs.tutorials.TutorialPage
import io.peekandpoke.klang.pages.docs.tutorials.TutorialsListPage
import io.peekandpoke.kraft.routing.RootRouterBuilder
import io.peekandpoke.kraft.routing.Route1
import io.peekandpoke.kraft.routing.Router
import io.peekandpoke.kraft.routing.Static

object Nav {
    val start = Static("")
    val startSlash = Static("/")

    val dashboard = Static("/dashboard")

    val newSongCode = Static("/song/code")
    val editSongCode = Route1("/song/code/{id}")

    val samplesLibrary = Static("/samples/library")

    const val manualsBase = "/manuals"
    val manuals = Static(manualsBase)
    val manualsKlangScript = Static("$manualsBase/klang-script")

    val manualsLexikon = Static("$manualsBase/lexikon")

    val manualsLibrary = Route1("$manualsBase/library/{library}")
    fun manualsLibrarySearch(library: String, search: String) =
        manualsLibrary(library).withQueryParams(KlangScriptLibraryDocsPage.PARAM_SEARCH to search)

    const val tutorialsBase = "$manualsBase/tutorials"
    val tutorials = Static(tutorialsBase)
    fun tutorialsWithUpdatedParams(router: Router, vararg params: Pair<String, String>) =
        tutorials().withQueryParams(
            router.current().takeIf { it.route == tutorials }?.matchedRoute?.queryParams ?: emptyMap()
        ).plusQueryParams(params.toMap())

    val tutorial = Route1("$tutorialsBase/{slug}")

    val credits = Static("/credits")

    val tour = Static("/tour")
}

fun RootRouterBuilder.mountNav() {

    layout({ MotorBackgroundLayout { it() } }) {
        mount(Nav.start) { StartPage() }
        mount(Nav.startSlash) { StartPage() }
    }

    layout({ FullscreenLayout { it() } }) {
        mount(Nav.tour) { TourPage() }
    }

    layout({ MenuLayout { it() } }) {

        mount(Nav.dashboard) { DashboardPage() }

        mount(Nav.newSongCode) { CodeSongPage(id = null) }
        mount(Nav.editSongCode) { CodeSongPage(id = it["id"]) }

        mount(Nav.samplesLibrary) { SamplesLibraryPage() }

        mount(Nav.manuals) { DocsPage() }
        mount(Nav.manualsKlangScript) { KlangScriptDocsPage() }
        mount(Nav.manualsLexikon) { LexikonPage() }
        mount(Nav.manualsLibrary) { KlangScriptLibraryDocsPage(it["library"]) }

        mount(Nav.tutorials) { TutorialsListPage() }
        mount(Nav.tutorial) { TutorialPage() }

        mount(Nav.credits) { CreditsPage() }
    }

    layout({ FullscreenLayout { it() } }) {
        catchAll {
            NotFoundPage()
        }
    }
}

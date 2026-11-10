#!/usr/bin/env python3
"""Tests of ui_wiring.py, on small trees made here. Run: python3 -I test_ui_wiring.py"""

import os
import sys
import tempfile
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import ui_wiring as uw  # noqa: E402

SPINE = """
package x

/** Not a holder: localStorage is only named in this comment. */
object Player {
    val status = StreamSource<String>("off")
    val loud: Stream<Boolean> = status.map { it == "on" }
    fun play() {}
}

object Words {
    const val TITLE = "a localStorage in a string"
}

object Commands {
    fun run() {
        var count = 0
        val seen = mutableListOf<String>()
    }
}

private object Drafts {
    private val pending = mutableMapOf<String, String>()
}
"""

STORAGE = """
package x

object EditorSettings {
    private val source = StreamSource(66)
    fun strength() = source()
    fun load() = localStorage.getItem("k")
}
"""

INDEX = """
package x

val version = VersionController()
private val log = Logger()
val Component<*>.reach: Int get() = 1
val Tag.other: Int get() = 2
"""

PAGE = """
package x

import x.Player

class SongPage(ctx: Ctx<Props>) : Component<SongPage.Props>(ctx) {
    companion object {
        val open = StreamSource(false)
        const val KEY = "k"
    }

    private val status by subscribingTo(Player.status)
    private val info by subscribingTo(version)
    private val wide by subscribingTo(
        Player.loud
    )
    private val mine by subscribingTo(props.stream)
    private var hover: Boolean by value(false)

    fun go() {
        Player.play()
        EditorSettings.strength()
        // Player.play() in a comment is no cable
        val text = "Player.play()"
        localStorage.setItem("a", "b")
        JSON.parse("{}")
    }
}
"""

OTHER = """
package x

class Menu(ctx: NoProps) : PureComponent(ctx) {
    private val open by subscribingTo(SongPage.open)
    private val key = SongPage.KEY
    private val text = "playing: ${Player.status()} now"

    private val label = "${open.let { "N: $it" } ?: ""} ${Player.loud}"

    companion object {
        val cache = mutableMapOf<String, String>()
        val kept = persistInLocalStorage("k")

        fun rows(): Int {
            val seen = mutableMapOf<String, Int>()
            var count = 0

            return count + seen.size
        }
    }
}

class After {
    companion object {
        fun only(): Int {
            val local = mutableListOf<Int>()
            var n = 0

            return n + local.size
        }
    }
}
"""

LIBRARY = """
package lib

object KlangTheme {
    var look = 1
}

class Part {
    val version = 3
    fun show() = version.toString() + Player.status
}
"""


SPRUDEL = """
package sprudel

class GainTool(ctx: Ctx<Props>) : Component<GainTool.Props>(ctx) {
    private val look by subscribingTo(KlangTheme)
    private val map by subscribingTo(keys.keymap)
    private val words = listOf("a").associateWith { it }.keys
}
"""

OWN_NAME = """
package sprudel

class Chords(ctx: Ctx<Props>) : Component<Chords.Props>(ctx) {
    fun count(keys: List<String>) = keys.size
}
"""

NO_COMPONENT = """
package sprudel

fun label() = keys.joinToString()
"""

KRAFT = """
package workshop

fun KraftApp.Builder.keys(manager: KeysManager) = apply {
    setAttribute(KeysManager.key, manager)
}

val Component<*>.keys: KeysManager get() = getAttributeRecursive(KeysManager.key)

fun Component<*>.openKeyLayer(): Int {
    return keys.newLayer()
}
"""

MANAGER = """
package workshop

class KeysManager {
    private val _keymap = StreamSource(1)
    val keymap: Stream<Int> = _keymap
}
"""

MODAL = """
package lib

class Modal(ctx: Ctx<Props>) : FadingModal<Modal.Props, Boolean>(ctx) {
    init {
        openKeyLayer()
    }

    private val direct = getAttributeRecursiveOrNull(KeysManager.key)?.keymap
}
"""


def tree(files):
    root = tempfile.mkdtemp(prefix="ui-wiring-test-")

    for rel, text in files.items():
        path = os.path.join(root, rel)
        os.makedirs(os.path.dirname(path), exist_ok=True)

        with open(path, "w", encoding="utf-8") as fh:
            fh.write(text)

    return root


class UiWiringTest(unittest.TestCase):

    @classmethod
    def setUpClass(cls):
        cls.root = tree({
            "src/jsMain/kotlin/spine/Player.kt": SPINE,
            "src/jsMain/kotlin/storage/EditorSettings.kt": STORAGE,
            "src/jsMain/kotlin/index.kt": INDEX,
            "src/jsMain/kotlin/pages/SongPage.kt": PAGE,
            "src/jsMain/kotlin/layouts/Menu.kt": OTHER,
            "klangui/src/jsMain/kotlin/Part.kt": LIBRARY,
            "klangui/src/jsMain/kotlin/Modal.kt": MODAL,
            "sprudel/src/jsMain/kotlin/ui/GainTool.kt": SPRUDEL,
            "sprudel/src/jsMain/kotlin/ui/Chords.kt": OWN_NAME,
            "sprudel/src/jsMain/kotlin/ui/label.kt": NO_COMPONENT,
            "workshop/src/jsMain/kotlin/keys/_kraft.kt": KRAFT,
            "workshop/src/jsMain/kotlin/keys/KeysManager.kt": MANAGER,
        })
        cls.files, cls.holders, cls.facts, cls.edges = uw.analyse(cls.root)

    def edges_of(self, rel, to=None):
        return sorted((e["to"], e["member"], e["kind"]) for e in self.edges
                      if e["from"].endswith(rel) and (to is None or e["to"] == to))

    def test_comments_go_and_strings_are_blanked(self):
        code = uw.blank_strings(uw.strip_comments('a // x.y\nb /* c\nd */ e "f.g" """h\ni"""'))

        self.assertNotIn("x.y", code)
        self.assertNotIn("f.g", code)
        self.assertEqual(code.count("\n"), 3)

    def test_holders(self):
        self.assertEqual(self.holders["Player"]["kind"], "object")
        self.assertEqual(self.holders["Player"]["rail"], "spine")
        self.assertEqual(self.holders["Player"]["keeps"], ["stream source", "derived stream"])
        self.assertEqual(self.holders["EditorSettings"]["keeps"], ["stream source", "browser storage"])
        self.assertEqual(self.holders["SongPage"]["kind"], "companion")
        self.assertEqual(self.holders["SongPage"]["members"], ["open"])
        self.assertEqual(self.holders["version"]["kind"], "val")
        self.assertEqual(self.holders["KlangTheme"]["rail"], "library")

    def test_an_object_without_state_and_a_private_val_are_no_holders(self):
        self.assertNotIn("Words", self.holders)
        self.assertNotIn("log", self.holders)

    def test_an_extension_value_is_no_holder(self):
        self.assertNotIn("Component", self.holders)
        self.assertNotIn("Tag", self.holders)

    def test_a_functions_locals_are_not_the_objects_state(self):
        self.assertNotIn("Commands", self.holders)

    def test_a_private_object_and_a_companions_cache_are_holders(self):
        self.assertEqual(self.holders["Drafts"]["keeps"], ["mutable collection"])
        self.assertEqual(self.holders["Menu"]["members"], ["cache"])
        self.assertEqual(self.holders["Menu"]["keeps"], ["mutable collection", "browser storage"])
        self.assertEqual(self.facts["src/jsMain/kotlin/layouts/Menu.kt"]["browser_storage"], 1)

    def test_stream_and_direct_cables(self):
        self.assertEqual(self.edges_of("pages/SongPage.kt", "Player"), [
            ("Player", "loud", "stream"), ("Player", "play", "direct"), ("Player", "status", "stream"),
        ])
        self.assertEqual(self.edges_of("pages/SongPage.kt", "version"), [("version", "", "stream")])
        self.assertEqual(self.edges_of("pages/SongPage.kt", "EditorSettings"), [("EditorSettings", "strength", "direct")])

    def test_a_companion_is_reached_only_by_its_state(self):
        self.assertEqual(self.edges_of("layouts/Menu.kt", "SongPage"), [("SongPage", "open", "stream")])

    def test_a_string_templates_expression_is_code(self):
        self.assertEqual(self.edges_of("layouts/Menu.kt", "Player"),
                         [("Player", "loud", "direct"), ("Player", "status", "direct")])

    def test_a_template_with_braces_and_a_string_of_its_own_keeps_the_braces_matched(self):
        code = uw.blank_strings(uw.strip_comments('val a = "${x.let { "N: $it" } ?: ""} y" // z\nobject B { }'))

        self.assertEqual(code.count("{"), code.count("}"))
        self.assertIn("x.let", code)
        self.assertNotIn("N:", code)
        self.assertIn("object B", code)

    def test_a_quote_or_a_brace_as_a_char_is_no_string_and_no_block(self):
        code = uw.blank_strings(uw.strip_comments("val a = x.trim('\"') + '{' // c\nval b = \"s\""))

        self.assertEqual(code.count("{"), 0)
        self.assertEqual(code.count('"'), 2)
        self.assertIn("val b", code)

    def test_sprudels_ui_is_read_as_a_library(self):
        self.assertEqual(self.facts["sprudel/src/jsMain/kotlin/ui/GainTool.kt"]["rail"], "library")
        self.assertEqual(self.facts["workshop/src/jsMain/kotlin/keys/KeysManager.kt"]["rail"], "library")
        self.assertEqual(self.edges_of("sprudel/src/jsMain/kotlin/ui/GainTool.kt", "KlangTheme"),
                         [("KlangTheme", "", "stream")])

    def test_a_service_on_the_app_is_a_holder_reached_by_its_accessors(self):
        manager = self.holders["KeysManager"]

        self.assertEqual(manager["kind"], "attribute")
        self.assertEqual(manager["file"], "workshop/src/jsMain/kotlin/keys/KeysManager.kt")
        self.assertEqual(manager["accessors"], ["keys", "openKeyLayer"])
        self.assertEqual(manager["keeps"], ["stream source", "derived stream"])
        # a component reads it by the accessor; a map's .keys is no cable
        self.assertEqual(self.edges_of("sprudel/src/jsMain/kotlin/ui/GainTool.kt", "KeysManager"),
                         [("KeysManager", "keymap", "stream")])
        # a file with a parameter of that name, and a file without a component, are no readers
        self.assertEqual(self.edges_of("sprudel/src/jsMain/kotlin/ui/Chords.kt"), [])
        self.assertEqual(self.edges_of("sprudel/src/jsMain/kotlin/ui/label.kt"), [])
        # a component of another base class, by the function and by a direct look-up
        self.assertEqual(self.edges_of("klangui/src/jsMain/kotlin/Modal.kt", "KeysManager"),
                         [("KeysManager", "", "direct"), ("KeysManager", "keymap", "direct")])
        # the file that registers it is no reader
        self.assertEqual(self.edges_of("workshop/src/jsMain/kotlin/keys/_kraft.kt", "KeysManager"), [])

    def test_strings_of_every_kind_end_where_kotlin_ends_them(self):
        def clean(text):
            return uw.blank_strings(uw.strip_comments(text))

        raw = clean('val a = """say "hi" { and } // no comment"""\nPlayer.play()')
        self.assertNotIn("hi", raw)
        self.assertNotIn("{", raw)
        self.assertIn("Player.play()", raw)

        four = clean('val a = """import "x""""\nPlayer.play() // c')
        self.assertIn("Player.play()", four)
        self.assertNotIn(" c", four)

        nested = clean('val a = "${b.let { "}" }} z"\nobject C { }')
        self.assertEqual(nested.count("{"), nested.count("}"))
        self.assertIn("object C", nested)

        url = clean('val a = "http://x" + Player.status // real comment')
        self.assertIn("Player.status", url)
        self.assertNotIn("real", url)

        escaped = clean('val a = "a \\" b" + Player.status')
        self.assertIn("Player.status", escaped)
        self.assertNotIn(" b", escaped)

        chars = clean("val q = '\\'' + '\\u0041' + '\"' + Player.status")
        self.assertIn("Player.status", chars)
        self.assertEqual(chars.count('"'), 0)

        self.assertNotIn("u0041", chars)

        escaped_quote = clean("val q = '\\\"' + Player.status")
        self.assertIn("Player.status", escaped_quote)

        same_line = clean('val a = """x"""" + Player.status')
        self.assertIn("Player.status", same_line)

        char_in_template = clean('val a = "${if (c == \'}\') one else two} z" + Player.status')
        self.assertIn("else two", char_in_template)
        self.assertIn("Player.status", char_in_template)

        open_line = clean('val a = "never closed\nPlayer.play()')
        self.assertIn("Player.play()", open_line)

    def test_block_comments_nest(self):
        code = uw.strip_comments("a /* one /* two */ still */ b")

        self.assertNotIn("still", code)
        self.assertIn("b", code)

    def test_a_companions_function_locals_are_not_its_state(self):
        self.assertEqual(self.holders["Menu"]["members"], ["cache"])
        self.assertNotIn("After", self.holders)

    def test_an_import_is_not_a_use(self):
        facts = uw.file_facts("src/jsMain/kotlin/pages/X.kt", "import a.ComponentRef\nimport b.localStorage\nval x = 1", {})

        self.assertEqual(facts["component_ref"], 0)
        self.assertEqual(facts["browser_storage"], 0)

    def test_the_library_cannot_see_the_app_and_a_name_of_its_own_is_no_cable(self):
        self.assertEqual(self.edges_of("klangui/src/jsMain/kotlin/Part.kt"), [])

    def test_file_facts(self):
        page = self.facts["src/jsMain/kotlin/pages/SongPage.kt"]

        self.assertTrue(page["component"])
        self.assertEqual(page["subscribing"], {"holder": 3, "props": 1})
        self.assertEqual(page["by_value"], 1)
        self.assertEqual(page["browser_storage"], 1)
        self.assertEqual(page["manual_json"], 1)
        self.assertEqual(page["stream_sources"], ["open"])
        self.assertTrue(self.facts["src/jsMain/kotlin/layouts/Menu.kt"]["component"])

    def test_findings(self):
        found = uw.findings(self.holders, self.facts, self.edges)

        def paths(title):
            return [rel for rel, _ in found[title]]

        self.assertEqual(paths("browser storage touched outside the storage rail"),
                         ["src/jsMain/kotlin/layouts/Menu.kt", "src/jsMain/kotlin/pages/SongPage.kt"])
        self.assertEqual(paths("hand-written JSON"), ["src/jsMain/kotlin/pages/SongPage.kt"])
        self.assertEqual(paths("storage reached from the UI"), ["src/jsMain/kotlin/pages/SongPage.kt"])
        self.assertEqual(sorted(what.split(" ")[0] for _, what in found["state held outside the Spine"]),
                         ["KeysManager", "KlangTheme", "Menu", "SongPage", "version"])

    def test_the_report_names_every_holder(self):
        report = uw.render(self.holders, self.facts, self.edges)

        for name in self.holders:
            self.assertIn(f"`{name}`", report)


if __name__ == "__main__":
    unittest.main()

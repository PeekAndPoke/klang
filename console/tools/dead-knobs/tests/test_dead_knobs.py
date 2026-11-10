#!/usr/bin/env python3
"""Tests for dead_knobs.py, on the fixture in fixtures/panspread and on small trees built in a temp dir.
Run: python3 -I console/tools/dead-knobs/tests/test_dead_knobs.py"""
import io
import os
import shutil
import sys
import tempfile
import unittest
from contextlib import redirect_stderr, redirect_stdout

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.dirname(HERE))
import dead_knobs  # noqa: E402

FIXTURE = os.path.join(HERE, "fixtures", "panspread")


def run(root, *argv, waivers=os.devnull):
    out, err = io.StringIO(), io.StringIO()
    with redirect_stdout(out), redirect_stderr(err):
        rc = dead_knobs.main(["--root", root, "--waivers", waivers, *argv])
    return rc, out.getvalue() + err.getvalue()


def section(text, title):
    """The lines of one report section, from its title to the blank line after it."""
    lines = text.splitlines()
    start = next(i for i, line in enumerate(lines) if line.startswith(title))
    body = []
    for line in lines[start + 1:]:
        if not line.strip():
            break
        body.append(line)
    return "\n".join(body)


class Tree:
    """A copy of the fixture in a temp dir (outside git, so the os.walk path is used), plus extra files."""

    def __enter__(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = os.path.join(self.tmp.name, "repo")
        shutil.copytree(FIXTURE, self.root)
        return self

    def __exit__(self, *exc):
        self.tmp.cleanup()

    def write(self, rel, text):
        path = os.path.join(self.root, rel)
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, "w", encoding="utf-8") as f:
            f.write(text)
        return path

    def remove(self, rel):
        os.remove(os.path.join(self.root, rel))


DOOR = "sprudel/src/commonMain/kotlin/lang/lang_extra.kt"


class PanSpread(unittest.TestCase):
    def test_the_old_unison_door_is_a_dead_knob(self):
        rc, out = run(FIXTURE)
        self.assertEqual(rc, 1, out)
        dead = section(out, "(a) written, never read")
        self.assertIn("ignitor panSpread", dead)
        self.assertIn("lang_dynamics_unison.kt:11", dead)
        # the keys written through the shared table are read, so they are not in (a)
        self.assertNotIn("spread\n", dead.replace("panSpread", ""))
        self.assertNotIn("voices", dead)

    def test_a_frontend_read_back_is_not_an_engine_reader(self):
        rc, out = run(FIXTURE, "--list")
        table = out.split("(a) written")[0]
        block = table.split("ignitor   panSpread")[1].split("\nignitor ")[0]
        self.assertIn("W1 R0", block)

    def test_the_fix_makes_it_clean(self):
        with Tree() as t:
            path = os.path.join(t.root, "sprudel/src/commonMain/kotlin/lang/lang_dynamics_unison.kt")
            with open(path, encoding="utf-8") as f:
                text = f.read()
            t.write("sprudel/src/commonMain/kotlin/lang/lang_dynamics_unison.kt",
                    text.replace('putIgnitorParam("panSpread"', 'putIgnitorParam(IgnitorSlotKeys.spread'))
            rc, out = run(t.root)
            self.assertEqual(rc, 0, out)
            self.assertIn("(a) written, never read (0)", out)

    def test_a_waiver_silences_it_and_needs_a_reason(self):
        with Tree() as t:
            ok = t.write("waivers.txt", "# comment\nignitor panSpread reserved for the stereo width, KLANG-00419\n")
            rc, out = run(t.root, waivers=ok)
            self.assertEqual(rc, 0, out)
            self.assertIn("(a) written, never read (0)", out)
            bad = t.write("bad.txt", "ignitor panSpread\n")
            rc, out = run(t.root, waivers=bad)
            self.assertEqual(rc, 2, out)
            self.assertIn("expected", out)

    def test_an_unused_waiver_is_reported_and_fails_only_strict(self):
        with Tree() as t:
            w = t.write("waivers.txt", "ignitor panSpread reserved\nkatalyst delay.wet nothing to waive here\n")
            rc, out = run(t.root, waivers=w)
            self.assertEqual(rc, 0, out)
            self.assertIn("katalyst delay.wet", section(out, "unused waivers"))
            rc, out = run(t.root, "--strict", waivers=w)
            self.assertEqual(rc, 1, out)


class Resolution(unittest.TestCase):
    def test_near_miss_in_case_and_on_the_other_map(self):
        with Tree() as t:
            t.write(DOOR, """
private val a = voiceSetter { putIgnitorParam("Spread", it) }
private val b = voiceSetter { putKatalystParam("voices", it) }
""")
            rc, out = run(t.root)
            self.assertEqual(rc, 1, out)
            near = section(out, "(c) near misses")
            self.assertIn('ignitor written "Spread" ~ read "spread": differs in case', near)
            self.assertIn('katalyst written "voices" ~ read "voices": read on the other map (ignitor)', near)

    def test_edit_distance_and_dot(self):
        self.assertEqual(dead_knobs.near("delay.feedbak", "delay.feedback"), "edit distance 1")
        self.assertEqual(dead_knobs.near("delaywet", "delay.wet"), "differs in a dot")
        self.assertEqual(dead_knobs.near("reverb.sise", "reverb.size"), "edit distance 1")
        self.assertIsNone(dead_knobs.near("rate", "gain"))  # short keys: distance 2 is noise
        self.assertIsNone(dead_knobs.near("panSpread", "spread"))

    def test_open_writers_are_listed_not_counted(self):
        with Tree() as t:
            t.write(DOOR, """
fun SprudelVoiceData.putIgnitorParam(name: String, value: Double?) {
    ignitorParamsOrNew().set(name, value)
}
fun SprudelPattern.ignp(slot: IgnitorSlotLike?, value: PatternLike): SprudelPattern = applyIgnitorParam(this, slot)
private fun applyIgnitorParam(source: SprudelPattern, slot: String) = voiceSetter { putIgnitorParam(slot, it) }
""")
            rc, out = run(t.root)
            opened = section(out, "open writers")
            self.assertIn("lang_extra.kt:3", opened)
            self.assertIn("lang_extra.kt:6", opened)
            self.assertNotIn("lang_extra.kt:5", opened)  # a definition, not a call
            self.assertIn("unresolved writer arguments: the tool cannot name these keys (0)", out)

    def test_comments_and_script_text_are_not_code(self):
        with Tree() as t:
            t.write(DOOR, '''
// putIgnitorParam("ghostA", 1.0)
/* katalystParamsOrNew().set("ghostB", 1.0) */
val song = """note("c").ignp("ghostC", 1).katp("ghostD", 2)"""
val label = "putIgnitorParam(\\"ghostE\\", 1.0)"
''')
            rc, out = run(t.root)
            self.assertEqual(rc, 1, out)  # panSpread only
            self.assertNotIn("ghost", out)

    def test_a_declared_slot_no_engine_code_places_is_dead_when_a_door_writes_it(self):
        with Tree() as t:
            path = os.path.join(t.root, "audio_bridge/src/commonMain/kotlin/IgnitorDsl.kt")
            with open(path, encoding="utf-8") as f:
                text = f.read()
            t.write("audio_bridge/src/commonMain/kotlin/IgnitorDsl.kt", text.replace(
                'val voices: IgnitorDsl = Param(name = "voices", default = 8.0)',
                'val voices: IgnitorDsl = Param(name = "voices", default = 8.0)\n'
                '        val width: IgnitorDsl = Param(name = "width", default = 0.0)'))
            rc, out = run(t.root)
            self.assertNotIn("ignitor width", section(out, "(a) written, never read"))
            t.write(DOOR, 'private val a = voiceSetter { putIgnitorParam("width", it) }\n')
            rc, out = run(t.root)
            self.assertEqual(rc, 1, out)
            self.assertIn("ignitor width", section(out, "(a) written, never read"))

    def test_constants_and_unresolved(self):
        with Tree() as t:
            t.write(DOOR, """
const val SPREAD_KEY = "spread"
private val a = voiceSetter { putIgnitorParam(SPREAD_KEY, it) }
private val b = voiceSetter { putIgnitorParam(Other.thing, it) }
""")
            rc, out = run(t.root, "--list")
            self.assertIn("lang_extra.kt:3", out.split("ignitor   spread")[1].split("\nignitor ")[0])
            self.assertIn("Other.thing", section(out, "unresolved writer arguments"))
            w = t.write("waivers.txt", "ignitor panSpread the fixture's own dead knob\n")
            t.write(DOOR, 'const val SPREAD_KEY = "spread"\nprivate val a = voiceSetter { putIgnitorParam(SPREAD_KEY, it) }\n')
            rc, out = run(t.root, waivers=w)
            self.assertEqual(rc, 0, out)
            t.write(DOOR, "private val b = voiceSetter { putIgnitorParam(Other.thing, it) }\n")
            rc, out = run(t.root, waivers=w)
            self.assertEqual(rc, 1, "a writer the tool cannot name is not a clean run")

    def test_the_classic_table_with_groups_composites_and_fill_defaults(self):
        with Tree() as t:
            t.write("audio_bridge/src/commonMain/kotlin/IgnitorDsl.kt", """
sealed interface IgnitorDsl {
    data class Param(val name: String, val default: Double) : IgnitorDsl
    object Slots {
        val spread: IgnitorDsl = Param(name = "spread", default = 0.2)
        val voices: IgnitorDsl = Param(name = "voices", default = 8.0)
        val lpf: PassFilterSlots = PassFilterSlots("lpf")
        val lpfCurves: CurvesSlots = CurvesSlots("lpfCurves")
        val sample: SampleSlots = SampleSlots()
    }
}
""")
            t.write("audio_bridge/src/commonMain/kotlin/IgnitorDslClassic.kt", """
private fun slot(door: String, param: String, default: Double, description: String? = null): IgnitorDsl.Param =
    IgnitorDsl.Param(name = "$door.$param", default = default)

class PassFilterSlots internal constructor(door: String) {
    val freq: IgnitorDsl = slot(door = door, param = "freq", default = SLOT_UNSET)
    val q: IgnitorDsl = slot(
        door = door, param = "q", default = 0.707,
        description = "Mirrors (sprudel's) reader",
    )
}

class CurvesSlots internal constructor(door: String) {
    val attack: IgnitorDsl = slot(door = door, param = "attack", default = 1.0)
}

class SampleSlots internal constructor() {
    val loop: IgnitorDsl = IgnitorDsl.Param(
        name = "loop",
        default = 0.0,
    )
}

fun IgnitorDsl.classic(): IgnitorDsl {
    val s = IgnitorDsl.Slots
    return IgnitorDsl.Lowpass(inner = this, freq = s.lpf.freq, q = s.lpf.q, attackCurve = s.lpfCurves.attack)
}
""")
            t.write("audio_be/src/commonMain/kotlin/voices/VoiceFactory.kt", """
private fun loop(bag: KlangMap<Double>?) = bag.finiteSlot(IgnitorDsl.Slots.sample.loop)
private fun drive(data: VoiceData) = data.ignitorParams?.get("drive")
""")
            t.write("sprudel/src/commonMain/kotlin/_classic_slot_params.kt", """
internal fun SprudelVoiceData.classicSlotParams(): KlangMap<Double>? {
    val k = ClassicSlotKeys
    val bag = ClassicSlotParams(ignitorParams)
    lpf?.let { bag.putFilter(it, k.lpf) }
    sample?.let { p -> bag.put(k.loop, p.loop) }
    return bag.result()
}

private class FilterSlotKeys(val freq: String, val q: String, val curveAttack: String)

private object ClassicSlotKeys {
    private val s = IgnitorDsl.Slots

    private fun name(slot: IgnitorDsl): String = (slot as IgnitorDsl.Param).name

    private fun pass(f: PassFilterSlots, c: CurvesSlots) = FilterSlotKeys(
        name(f.freq), name(f.q), name(c.attack),
    )

    val lpf = pass(s.lpf, s.lpfCurves)
    val loop = name(s.sample.loop)
}
""")
            t.write(DOOR, """
private fun fillA(d: SprudelVoiceData) {
    val slots = d.ignitorParamsOrNew()
    slots.setOrDefault("spread", value = null, default = SPREAD_A)
}
private fun fillB(d: SprudelVoiceData) {
    d.ignitorParamsOrNew().setOrDefault("spread", null, SPREAD_B)
}
""")
            rc, out = run(t.root, "--list")
            for key, line in (("lpf.freq", 5), ("lpf.q", 5), ("lpfCurves.attack", 5), ("loop", 6)):
                block = out.split(f"ignitor   {key} ")[1].split("\nignitor ")[0]
                self.assertIn(f"_classic_slot_params.kt:{line}", block, key)
                self.assertIn("R decl", block, key)
                self.assertIn("R use", block, key)
            self.assertIn("ignitor drive", section(out, "(b) read or declared, written by no door"))
            unplaced = section(out, "(e) declared in Slots")
            self.assertIn("ignitor voices", unplaced)  # this IgnitorDsl.kt has no SuperSaw that places it
            self.assertNotIn("lpf.", unplaced)
            defaults = section(out, "(d) one key, different fill defaults")
            self.assertIn("default SPREAD_A", defaults)
            self.assertIn("default SPREAD_B", defaults)

    def test_a_tree_without_the_slots_objects_is_an_error_not_clean(self):
        with Tree() as t:
            t.remove("audio_bridge/src/commonMain/kotlin/KatalystDsl.kt")
            rc, out = run(t.root)
            self.assertEqual(rc, 2, out)
            self.assertIn("KatalystDsl.Slots", out)

    def test_a_changed_slot_helper_is_an_error(self):
        with Tree() as t:
            t.write("audio_bridge/src/commonMain/kotlin/KatalystDslSlots.kt", """
private fun knob(stage: String, knob: String, default: Double): KatalystKnobDsl.Param =
    KatalystKnobDsl.Param(name = "$stage/$knob", default = default)

class KatalystDelaySlots internal constructor() {
    val wet: KatalystKnobDsl.Param = knob(stage = "delay", knob = "wet", default = 0.0)
}
""")
            rc, out = run(t.root)
            self.assertEqual(rc, 2, out)
            self.assertIn("knob(...)", out)


if __name__ == "__main__":
    unittest.main()

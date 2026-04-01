package io.peekandpoke.klang.audio_fe.samples

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_bridge.SampleRequest
import io.peekandpoke.klang.audio_fe.utils.AssetLoader
import kotlinx.serialization.json.Json

/**
 * Tests for GenericBundleLoader — parsed through the public SampleIndexLoader API.
 * Uses a mock AssetLoader to provide known JSON content without HTTP requests.
 */
class GenericBundleLoaderTest : StringSpec({

    val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun mockLoader(responses: Map<String, String>) = object : AssetLoader {
        override suspend fun download(uri: String): ByteArray? {
            return responses[uri]?.encodeToByteArray()
        }
    }

    // -- Percussive bank (array of URLs) --

    val percussiveJson = """
    {
        "_base": "https://example.com/drums/",
        "kit_bd": ["kick1.wav", "kick2.wav"],
        "kit_hh": ["hat.wav"]
    }
    """.trimIndent()

    "percussive bank loads sounds with correct bank/sound split" {
        val loader = mockLoader(mapOf("drums.json" to percussiveJson))
        val indexLoader = SampleIndexLoader(loader, json)

        val index = indexLoader.load(
            SampleCatalogue.of(SampleCatalogue.Bundle(name = "test", soundsUri = "drums.json"))
        )

        val bank = index.banksByKey["kit"]
        bank.shouldNotBeNull()
        bank.soundsByKey["bd"].shouldNotBeNull()
        bank.soundsByKey["hh"].shouldNotBeNull()
    }

    "percussive sample resolved by index" {
        val loader = mockLoader(mapOf("drums.json" to percussiveJson))
        val indexLoader = SampleIndexLoader(loader, json)

        val index = indexLoader.load(
            SampleCatalogue.of(SampleCatalogue.Bundle(name = "test", soundsUri = "drums.json"))
        )

        val request = SampleRequest(bank = "kit", sound = "bd", index = 0, note = null)
        val resolved = index.resolve(request)
        resolved.shouldNotBeNull()
        (resolved.sample as Samples.Sample.FromUrl).url shouldBe "https://example.com/drums/kick1.wav"
    }

    "percussive sample index wraps around" {
        val loader = mockLoader(mapOf("drums.json" to percussiveJson))
        val indexLoader = SampleIndexLoader(loader, json)

        val index = indexLoader.load(
            SampleCatalogue.of(SampleCatalogue.Bundle(name = "test", soundsUri = "drums.json"))
        )

        // index=5 with 2 samples -> 5 % 2 = 1
        val request = SampleRequest(bank = "kit", sound = "bd", index = 5, note = null)
        val resolved = index.resolve(request)
        resolved.shouldNotBeNull()
        (resolved.sample as Samples.Sample.FromUrl).url shouldBe "https://example.com/drums/kick2.wav"
    }

    "percussive sample type is VARIANTS when multiple URLs" {
        val loader = mockLoader(mapOf("drums.json" to percussiveJson))
        val indexLoader = SampleIndexLoader(loader, json)

        val index = indexLoader.load(
            SampleCatalogue.of(SampleCatalogue.Bundle(name = "test", soundsUri = "drums.json"))
        )

        val provider = index.banksByKey["kit"]!!.soundsByKey["bd"]!!
        provider.sampleType shouldBe Samples.SampleType.VARIANTS
        provider.variantCount shouldBe 2
    }

    "single percussive sample type is SINGLE" {
        val loader = mockLoader(mapOf("drums.json" to percussiveJson))
        val indexLoader = SampleIndexLoader(loader, json)

        val index = indexLoader.load(
            SampleCatalogue.of(SampleCatalogue.Bundle(name = "test", soundsUri = "drums.json"))
        )

        val provider = index.banksByKey["kit"]!!.soundsByKey["hh"]!!
        provider.sampleType shouldBe Samples.SampleType.SINGLE
        provider.variantCount shouldBe 1
    }

    // -- Pitched bank (object with note keys) --

    val pitchedJson = """
    {
        "_base": "https://example.com/piano/",
        "piano": {
            "C3": "C3.mp3",
            "C4": "C4.mp3",
            "C5": "C5.mp3"
        }
    }
    """.trimIndent()

    "pitched bank classified as PITCHED" {
        val loader = mockLoader(mapOf("piano.json" to pitchedJson))
        val indexLoader = SampleIndexLoader(loader, json)

        val index = indexLoader.load(
            SampleCatalogue.of(SampleCatalogue.Bundle(name = "test", soundsUri = "piano.json"))
        )

        // "piano" has no underscore so bank="" (default), sound="piano"
        val provider = index.banksByKey[""]!!.soundsByKey["piano"]!!
        provider.sampleType shouldBe Samples.SampleType.PITCHED
        provider.variantCount shouldBe 3
    }

    "pitched sample selects nearest sample at or above requested pitch" {
        val loader = mockLoader(mapOf("piano.json" to pitchedJson))
        val indexLoader = SampleIndexLoader(loader, json)

        val index = indexLoader.load(
            SampleCatalogue.of(SampleCatalogue.Bundle(name = "test", soundsUri = "piano.json"))
        )

        // D4 (~293 Hz) is between C4 (~261 Hz) and C5 (~523 Hz), should pick C5
        // Actually: "first where pitch <= samplePitch" — C4=261.63, D4=293.66 → C4 < D4, C5=523.25 > D4 → picks C5
        val request = SampleRequest(bank = "", sound = "piano", index = null, note = "D4")
        val resolved = index.resolve(request)
        resolved.shouldNotBeNull()
        val sample = resolved.sample as Samples.Sample.FromUrl
        sample.url shouldBe "https://example.com/piano/C5.mp3"
    }

    "pitched sample at exact note picks that sample" {
        val loader = mockLoader(mapOf("piano.json" to pitchedJson))
        val indexLoader = SampleIndexLoader(loader, json)

        val index = indexLoader.load(
            SampleCatalogue.of(SampleCatalogue.Bundle(name = "test", soundsUri = "piano.json"))
        )

        val request = SampleRequest(bank = "", sound = "piano", index = null, note = "C4")
        val resolved = index.resolve(request)
        resolved.shouldNotBeNull()
        val sample = resolved.sample as Samples.Sample.FromUrl
        sample.url shouldBe "https://example.com/piano/C4.mp3"
    }

    "pitched sample above all picks highest" {
        val loader = mockLoader(mapOf("piano.json" to pitchedJson))
        val indexLoader = SampleIndexLoader(loader, json)

        val index = indexLoader.load(
            SampleCatalogue.of(SampleCatalogue.Bundle(name = "test", soundsUri = "piano.json"))
        )

        val request = SampleRequest(bank = "", sound = "piano", index = null, note = "C8")
        val resolved = index.resolve(request)
        resolved.shouldNotBeNull()
        val sample = resolved.sample as Samples.Sample.FromUrl
        sample.url shouldBe "https://example.com/piano/C5.mp3"
    }

    "pitched sample below all picks lowest" {
        val loader = mockLoader(mapOf("piano.json" to pitchedJson))
        val indexLoader = SampleIndexLoader(loader, json)

        val index = indexLoader.load(
            SampleCatalogue.of(SampleCatalogue.Bundle(name = "test", soundsUri = "piano.json"))
        )

        val request = SampleRequest(bank = "", sound = "piano", index = null, note = "C1")
        val resolved = index.resolve(request)
        resolved.shouldNotBeNull()
        val sample = resolved.sample as Samples.Sample.FromUrl
        sample.url shouldBe "https://example.com/piano/C3.mp3"
    }

    // -- URL handling --

    "spaces in URLs are encoded as %20" {
        val jsonWithSpaces = """
        {
            "_base": "https://example.com/sounds/",
            "kit_bd": ["My Kick Drum.wav"]
        }
        """.trimIndent()

        val loader = mockLoader(mapOf("test.json" to jsonWithSpaces))
        val indexLoader = SampleIndexLoader(loader, json)

        val index = indexLoader.load(
            SampleCatalogue.of(SampleCatalogue.Bundle(name = "test", soundsUri = "test.json"))
        )

        val request = SampleRequest(bank = "kit", sound = "bd", index = 0, note = null)
        val resolved = index.resolve(request)
        resolved.shouldNotBeNull()
        (resolved.sample as Samples.Sample.FromUrl).url shouldBe "https://example.com/sounds/My%20Kick%20Drum.wav"
    }

    "absolute URLs are not prefixed with base" {
        val jsonWithAbsolute = """
        {
            "_base": "https://example.com/sounds/",
            "kit_bd": ["https://other.com/kick.wav"]
        }
        """.trimIndent()

        val loader = mockLoader(mapOf("test.json" to jsonWithAbsolute))
        val indexLoader = SampleIndexLoader(loader, json)

        val index = indexLoader.load(
            SampleCatalogue.of(SampleCatalogue.Bundle(name = "test", soundsUri = "test.json"))
        )

        val request = SampleRequest(bank = "kit", sound = "bd", index = 0, note = null)
        val resolved = index.resolve(request)
        resolved.shouldNotBeNull()
        (resolved.sample as Samples.Sample.FromUrl).url shouldBe "https://other.com/kick.wav"
    }

    // -- Edge cases --

    "unknown sound returns null" {
        val loader = mockLoader(mapOf("drums.json" to percussiveJson))
        val indexLoader = SampleIndexLoader(loader, json)

        val index = indexLoader.load(
            SampleCatalogue.of(SampleCatalogue.Bundle(name = "test", soundsUri = "drums.json"))
        )

        val request = SampleRequest(bank = "kit", sound = "nonexistent", index = 0, note = null)
        index.resolve(request).shouldBeNull()
    }

    "unknown bank returns null" {
        val loader = mockLoader(mapOf("drums.json" to percussiveJson))
        val indexLoader = SampleIndexLoader(loader, json)

        val index = indexLoader.load(
            SampleCatalogue.of(SampleCatalogue.Bundle(name = "test", soundsUri = "drums.json"))
        )

        val request = SampleRequest(bank = "nonexistent", sound = "bd", index = 0, note = null)
        index.resolve(request).shouldBeNull()
    }
})

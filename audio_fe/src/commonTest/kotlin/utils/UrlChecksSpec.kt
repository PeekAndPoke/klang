/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_fe.utils

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/**
 * [isUrlWithProtocol] decides whether a sample index entry is an absolute url or a path to resolve against the
 * index's base (`SampleIndexLoader`, `SampleMirrorMain`).
 */
class UrlChecksSpec : StringSpec({

    "http and https urls with a host are absolute" {
        "https://example.com/samples/bd.wav".isUrlWithProtocol() shouldBe true
        "http://cdn.example.org/a.json".isUrlWithProtocol() shouldBe true
        "https://www.example.com".isUrlWithProtocol() shouldBe true
    }

    "the protocol and host are matched case-insensitively" {
        "HTTPS://Example.COM/Bd.wav".isUrlWithProtocol() shouldBe true
    }

    "a path, a bare host or another protocol is not" {
        "samples/bd.wav".isUrlWithProtocol() shouldBe false
        "/samples/bd.wav".isUrlWithProtocol() shouldBe false
        "example.com/bd.wav".isUrlWithProtocol() shouldBe false
        "ftp://example.com/bd.wav".isUrlWithProtocol() shouldBe false
        "https://".isUrlWithProtocol() shouldBe false
        "".isUrlWithProtocol() shouldBe false
    }
})

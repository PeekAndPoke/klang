package io.peekandpoke.klang.strudel.math

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.math.Rational.Companion.toRational
import kotlinx.serialization.json.Json

class RationalStringSerializerSpec : StringSpec({

    "serialize positive fraction 2/3" {
        val rational = (2.0 / 3.0).toRational()
        val json = Json.encodeToString(RationalStringSerializer, rational)
        json shouldBe "\"2/3\""
    }

    "serialize positive fraction 3/4" {
        val rational = (3.0 / 4.0).toRational()
        val json = Json.encodeToString(RationalStringSerializer, rational)
        json shouldBe "\"3/4\""
    }

    "serialize positive fraction 1/2" {
        val rational = (1.0 / 2.0).toRational()
        val json = Json.encodeToString(RationalStringSerializer, rational)
        json shouldBe "\"1/2\""
    }

    "serialize negative fraction -5/8" {
        val rational = (-5.0 / 8.0).toRational()
        val json = Json.encodeToString(RationalStringSerializer, rational)
        json shouldBe "\"-5/8\""
    }

    "serialize negative fraction -2/3" {
        val rational = (-2.0 / 3.0).toRational()
        val json = Json.encodeToString(RationalStringSerializer, rational)
        json shouldBe "\"-2/3\""
    }

    "serialize negative fraction -7/10" {
        val rational = (-7.0 / 10.0).toRational()
        val json = Json.encodeToString(RationalStringSerializer, rational)
        json shouldBe "\"-7/10\""
    }

    "serialize positive whole number 5" {
        val rational = 5.0.toRational()
        val json = Json.encodeToString(RationalStringSerializer, rational)
        json shouldBe "\"5/1\""
    }

    "serialize positive whole number 42" {
        val rational = 42.0.toRational()
        val json = Json.encodeToString(RationalStringSerializer, rational)
        json shouldBe "\"42/1\""
    }

    "serialize negative whole number -10" {
        val rational = (-10.0).toRational()
        val json = Json.encodeToString(RationalStringSerializer, rational)
        json shouldBe "\"-10/1\""
    }

    "serialize zero" {
        val rational = 0.0.toRational()
        val json = Json.encodeToString(RationalStringSerializer, rational)
        json shouldBe "\"0/1\""
    }

    "serialize one" {
        val rational = 1.0.toRational()
        val json = Json.encodeToString(RationalStringSerializer, rational)
        json shouldBe "\"1/1\""
    }

    "serialize negative one" {
        val rational = (-1.0).toRational()
        val json = Json.encodeToString(RationalStringSerializer, rational)
        json shouldBe "\"-1/1\""
    }

    "serialize decimal 42.5 as 85/2" {
        val rational = 42.5.toRational()
        val json = Json.encodeToString(RationalStringSerializer, rational)
        json shouldBe "\"85/2\""
    }

    "serialize decimal 1.1 as 11/10" {
        val rational = 1.1.toRational()
        val json = Json.encodeToString(RationalStringSerializer, rational)
        json shouldBe "\"11/10\""
    }

    "serialize decimal -3.25 as -13/4" {
        val rational = (-3.25).toRational()
        val json = Json.encodeToString(RationalStringSerializer, rational)
        json shouldBe "\"-13/4\""
    }

    "serialize NaN" {
        val rational = Rational.NaN
        val json = Json.encodeToString(RationalStringSerializer, rational)
        json shouldBe "\"NaN\""
    }

    "deserialize positive fraction 2/3" {
        val json = "\"2/3\""
        val rational = Json.decodeFromString(RationalStringSerializer, json)
        rational.toDouble() shouldBe (2.0 / 3.0 plusOrMinus 1e-6)
    }

    "deserialize positive fraction 3/4" {
        val json = "\"3/4\""
        val rational = Json.decodeFromString(RationalStringSerializer, json)
        rational.toDouble() shouldBe (3.0 / 4.0 plusOrMinus 1e-6)
    }

    "deserialize negative fraction -5/8" {
        val json = "\"-5/8\""
        val rational = Json.decodeFromString(RationalStringSerializer, json)
        rational.toDouble() shouldBe (-5.0 / 8.0 plusOrMinus 1e-6)
    }

    "deserialize negative fraction -7/10" {
        val json = "\"-7/10\""
        val rational = Json.decodeFromString(RationalStringSerializer, json)
        rational.toDouble() shouldBe (-7.0 / 10.0 plusOrMinus 1e-6)
    }

    "deserialize positive whole number 5/1" {
        val json = "\"5/1\""
        val rational = Json.decodeFromString(RationalStringSerializer, json)
        rational.toDouble() shouldBe 5.0
    }

    "deserialize negative whole number -10/1" {
        val json = "\"-10/1\""
        val rational = Json.decodeFromString(RationalStringSerializer, json)
        rational.toDouble() shouldBe -10.0
    }

    "deserialize zero 0/1" {
        val json = "\"0/1\""
        val rational = Json.decodeFromString(RationalStringSerializer, json)
        rational.toDouble() shouldBe 0.0
    }

    "deserialize decimal 42.5 from 85/2" {
        val json = "\"85/2\""
        val rational = Json.decodeFromString(RationalStringSerializer, json)
        rational.toDouble() shouldBe 42.5
    }

    "deserialize NaN" {
        val json = "\"NaN\""
        val rational = Json.decodeFromString(RationalStringSerializer, json)
        rational.isNaN shouldBe true
    }

    "round-trip positive fraction 2/3" {
        val original = (2.0 / 3.0).toRational()
        val json = Json.encodeToString(RationalStringSerializer, original)
        val deserialized = Json.decodeFromString(RationalStringSerializer, json)
        deserialized.toDouble() shouldBe (original.toDouble() plusOrMinus 1e-6)
    }

    "round-trip negative fraction -5/8" {
        val original = (-5.0 / 8.0).toRational()
        val json = Json.encodeToString(RationalStringSerializer, original)
        val deserialized = Json.decodeFromString(RationalStringSerializer, json)
        deserialized.toDouble() shouldBe (original.toDouble() plusOrMinus 1e-6)
    }

    "round-trip positive whole number 42" {
        val original = 42.0.toRational()
        val json = Json.encodeToString(RationalStringSerializer, original)
        val deserialized = Json.decodeFromString(RationalStringSerializer, json)
        deserialized.toDouble() shouldBe original.toDouble()
    }

    "round-trip negative whole number -100" {
        val original = (-100.0).toRational()
        val json = Json.encodeToString(RationalStringSerializer, original)
        val deserialized = Json.decodeFromString(RationalStringSerializer, json)
        deserialized.toDouble() shouldBe original.toDouble()
    }

    "round-trip zero" {
        val original = 0.0.toRational()
        val json = Json.encodeToString(RationalStringSerializer, original)
        val deserialized = Json.decodeFromString(RationalStringSerializer, json)
        deserialized.toDouble() shouldBe 0.0
    }

    "round-trip decimal 42.5" {
        val original = 42.5.toRational()
        val json = Json.encodeToString(RationalStringSerializer, original)
        val deserialized = Json.decodeFromString(RationalStringSerializer, json)
        deserialized.toDouble() shouldBe (42.5 plusOrMinus 1e-6)
    }

    "round-trip NaN" {
        val original = Rational.NaN
        val json = Json.encodeToString(RationalStringSerializer, original)
        val deserialized = Json.decodeFromString(RationalStringSerializer, json)
        deserialized.isNaN shouldBe true
    }

    "deserialize plain number string as double" {
        val json = "\"3.14159\""
        val rational = Json.decodeFromString(RationalStringSerializer, json)
        rational.toDouble() shouldBe (3.14159 plusOrMinus 1e-6)
    }

    "deserialize plain integer string" {
        val json = "\"123\""
        val rational = Json.decodeFromString(RationalStringSerializer, json)
        rational.toDouble() shouldBe 123.0
    }

    "serialize large positive fraction" {
        val rational = (999.0 / 1000.0).toRational()
        val json = Json.encodeToString(RationalStringSerializer, rational)
        json shouldBe "\"999/1000\""
    }

    "serialize large negative fraction" {
        val rational = (-999.0 / 1000.0).toRational()
        val json = Json.encodeToString(RationalStringSerializer, rational)
        json shouldBe "\"-999/1000\""
    }

    "serialize very small positive fraction" {
        val rational = (1.0 / 100.0).toRational()
        val json = Json.encodeToString(RationalStringSerializer, rational)
        json shouldBe "\"1/100\""
    }

    "serialize very small negative fraction" {
        val rational = (-1.0 / 100.0).toRational()
        val json = Json.encodeToString(RationalStringSerializer, rational)
        json shouldBe "\"-1/100\""
    }

    // Edge cases: division by zero
    "deserialize 1/0 results in NaN" {
        val json = "\"1/0\""
        val rational = Json.decodeFromString(RationalStringSerializer, json)
        rational.isNaN shouldBe true
    }

    "deserialize -1/0 results in NaN" {
        val json = "\"-1/0\""
        val rational = Json.decodeFromString(RationalStringSerializer, json)
        rational.isNaN shouldBe true
    }

    "deserialize 0/0 results in NaN" {
        val json = "\"0/0\""
        val rational = Json.decodeFromString(RationalStringSerializer, json)
        rational.isNaN shouldBe true
    }

    "deserialize 5/0 results in NaN" {
        val json = "\"5/0\""
        val rational = Json.decodeFromString(RationalStringSerializer, json)
        rational.isNaN shouldBe true
    }

    "deserialize -10/0 results in NaN" {
        val json = "\"-10/0\""
        val rational = Json.decodeFromString(RationalStringSerializer, json)
        rational.isNaN shouldBe true
    }

    // Edge cases: malformed fractions
    "deserialize malformed fraction with multiple slashes returns NaN" {
        val json = "\"1/2/3\""
        val rational = Json.decodeFromString(RationalStringSerializer, json)
        // Should fall back to trying to parse as double, which will fail and create NaN
        rational.isNaN shouldBe true
    }

    "deserialize fraction with non-numeric parts returns NaN" {
        val json = "\"abc/def\""
        val rational = Json.decodeFromString(RationalStringSerializer, json)
        rational.isNaN shouldBe true
    }

    "deserialize empty numerator returns NaN" {
        val json = "\"/5\""
        val rational = Json.decodeFromString(RationalStringSerializer, json)
        rational.isNaN shouldBe true
    }

    "deserialize empty denominator returns NaN" {
        val json = "\"5/\""
        val rational = Json.decodeFromString(RationalStringSerializer, json)
        rational.isNaN shouldBe true
    }
})

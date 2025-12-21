package io.peekandpoke

typealias FilterFn = (sample: Double) -> Double

object SimpleFilters {
    private class SvfState {
        var ic1eq = 0.0
        var ic2eq = 0.0
    }

    fun createLPF(cutoffHz: Double, q: Double?, sampleRate: Double): FilterFn =
        when (q) {
            null -> createOnePoleLPF(cutoffHz, sampleRate)
            else -> createSvfLPF(cutoffHz, q, sampleRate)
        }

    fun createOnePoleLPF(cutoffHz: Double, sampleRate: Double): FilterFn {
        var y = 0.0

        val nyquist = 0.5 * sampleRate
        val cutoff = cutoffHz.coerceIn(40.0, nyquist - 1.0)
        // one-pole LPF coefficient in exponential form
        val lowPass = 1.0 - kotlin.math.exp(-2.0 * Math.PI * cutoff / sampleRate)

        return { sample ->
            y += lowPass * (sample - y)
            // return
            y
        }
    }

    fun createBiquadLPF(cutoffHz: Double, q: Double, sampleRate: Double): FilterFn {
        val fc = clampCutoff(cutoffHz, sampleRate)
        val Q = clampQ(q)

        val w0 = 2.0 * Math.PI * fc / sampleRate
        val cosw0 = kotlin.math.cos(w0)
        val sinw0 = kotlin.math.sin(w0)
        val alpha = sinw0 / (2.0 * Q)

        // RBJ low-pass
        val b0 = (1.0 - cosw0) / 2.0
        val b1 = (1.0 - cosw0)
        val b2 = (1.0 - cosw0) / 2.0
        val a0 = 1.0 + alpha
        val a1 = -2.0 * cosw0
        val a2 = 1.0 - alpha

        return createBiquad(b0, b1, b2, a0, a1, a2)
    }

    fun createSvfLPF(cutoffHz: Double, q: Double, sampleRate: Double): FilterFn {
        val (low, _, _) = createSvfCore(cutoffHz, q, sampleRate)
        return low
    }

    fun createHPF(cutoffHz: Double, q: Double?, sampleRate: Double): FilterFn {
        return when (q) {
            null -> createOnePoleHPF(cutoffHz, sampleRate)
            else -> createSvfHPF(cutoffHz, q, sampleRate)
        }
    }

    fun createOnePoleHPF(cutoffHz: Double, sampleRate: Double): FilterFn {
        var y = 0.0          // filter state
        var xPrev = 0.0      // previous input sample

        val nyquist = 0.5 * sampleRate
        val cutoff = cutoffHz.coerceIn(40.0, nyquist - 1.0)

        // one-pole coefficient (same exponential form as LPF)
        val a = kotlin.math.exp(-2.0 * Math.PI * cutoff / sampleRate)

        // Suppressing False positive IDE warning
        @Suppress("AssignedValueIsNeverRead")
        return { x ->
            // one-pole HPF difference equation
            y = a * (y + x - xPrev)
            xPrev = x
            y
        }
    }

    fun createBiquadHPF(cutoffHz: Double, q: Double, sampleRate: Double): FilterFn {
        val fc = clampCutoff(cutoffHz, sampleRate)
        val Q = clampQ(q)

        val w0 = 2.0 * Math.PI * fc / sampleRate
        val cosw0 = kotlin.math.cos(w0)
        val sinw0 = kotlin.math.sin(w0)
        val alpha = sinw0 / (2.0 * Q)

        // RBJ high-pass
        val b0 = (1.0 + cosw0) / 2.0
        val b1 = -(1.0 + cosw0)
        val b2 = (1.0 + cosw0) / 2.0
        val a0 = 1.0 + alpha
        val a1 = -2.0 * cosw0
        val a2 = 1.0 - alpha

        return createBiquad(b0, b1, b2, a0, a1, a2)
    }

    fun createSvfHPF(cutoffHz: Double, q: Double, sampleRate: Double): FilterFn {
        val (_, _, high) = createSvfCore(cutoffHz, q, sampleRate)
        return high
    }

    private fun createBiquad(
        b0: Double, b1: Double, b2: Double,
        a0: Double, a1: Double, a2: Double,
    ): FilterFn {
        // Normalize coefficients by a0
        val nb0 = b0 / a0
        val nb1 = b1 / a0
        val nb2 = b2 / a0
        val na1 = a1 / a0
        val na2 = a2 / a0

        // DF2T state
        var z1 = 0.0
        var z2 = 0.0

        @Suppress("AssignedValueIsNeverRead")
        return { x ->
            val y = nb0 * x + z1
            z1 = nb1 * x - na1 * y + z2
            z2 = nb2 * x - na2 * y
            y
        }
    }

    private fun createSvfCore(cutoffHz: Double, q: Double, sampleRate: Double): Triple<FilterFn, FilterFn, FilterFn> {
        val fc = clampCutoff(cutoffHz, sampleRate)
        val Q = clampQ(q)

        // TPT coefficients
        val g = kotlin.math.tan(Math.PI * fc / sampleRate)
        val k = 1.0 / Q
        val a1 = 1.0 / (1.0 + g * (g + k))
        val a2 = g * a1
        val a3 = g * a2

        val st = SvfState()

        // Returns: low, band, high
        val low: FilterFn = { x ->
            // We compute full SVF step but return low
            val v0 = x
            val v3 = v0 - st.ic2eq
            val v1 = a1 * st.ic1eq + a2 * v3
            val v2 = st.ic2eq + a2 * st.ic1eq + a3 * v3

            st.ic1eq = 2.0 * v1 - st.ic1eq
            st.ic2eq = 2.0 * v2 - st.ic2eq

            v2
        }

        val band: FilterFn = { x ->
            val v0 = x
            val v3 = v0 - st.ic2eq
            val v1 = a1 * st.ic1eq + a2 * v3
            val v2 = st.ic2eq + a2 * st.ic1eq + a3 * v3

            st.ic1eq = 2.0 * v1 - st.ic1eq
            st.ic2eq = 2.0 * v2 - st.ic2eq

            v1
        }

        val high: FilterFn = { x ->
            val v0 = x
            val v3 = v0 - st.ic2eq
            val v1 = a1 * st.ic1eq + a2 * v3
            val v2 = st.ic2eq + a2 * st.ic1eq + a3 * v3

            st.ic1eq = 2.0 * v1 - st.ic1eq
            st.ic2eq = 2.0 * v2 - st.ic2eq

            // high output of SVF:
            // v0 - k*v1 - v2
            v0 - k * v1 - v2
        }

        return Triple(low, band, high)
    }

    private fun clampCutoff(cutoffHz: Double, sampleRate: Double): Double {
        val nyquist = 0.5 * sampleRate
        return cutoffHz.coerceIn(5.0, nyquist - 1.0)
    }

    private fun clampQ(q: Double): Double =
        q.coerceIn(0.1, 50.0) // safe range; adjust if you want
}

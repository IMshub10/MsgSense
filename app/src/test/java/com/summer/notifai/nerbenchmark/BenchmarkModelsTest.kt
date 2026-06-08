package com.summer.notifai.nerbenchmark

import org.junit.Assert.assertEquals
import org.junit.Test

class BenchmarkModelsTest {
    @Test
    fun percentile_usesStableNearestRankFloor() {
        val values = listOf(5.0, 1.0, 4.0, 2.0, 3.0)
        assertEquals(3.0, percentile(values, 0.50), 0.0)
        assertEquals(4.0, percentile(values, 0.95), 0.0)
    }

    @Test
    fun percentile_emptyIsZero() {
        assertEquals(0.0, percentile(emptyList(), 0.95), 0.0)
    }
}

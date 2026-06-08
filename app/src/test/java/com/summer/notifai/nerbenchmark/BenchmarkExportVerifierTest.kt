package com.summer.notifai.nerbenchmark

import org.junit.Assert.assertEquals
import org.junit.Test

class BenchmarkExportVerifierTest {
    @Test
    fun protocolAndFixtureCountArePinned() {
        assertEquals("ner-v50-bg500-v1", BACKGROUND_PERFORMANCE_PROTOCOL)
        assertEquals("ner-v50-fg-accuracy-v1", FOREGROUND_ACCURACY_PROTOCOL)
        assertEquals(6445, BENCHMARK_FULL_CASES)
        assertEquals(500, BENCHMARK_PERFORMANCE_CASES)
        assertEquals(25, BENCHMARK_WARMUP_CASES)
        assertEquals(100, BENCHMARK_CHECKPOINT_CASES)
    }
}

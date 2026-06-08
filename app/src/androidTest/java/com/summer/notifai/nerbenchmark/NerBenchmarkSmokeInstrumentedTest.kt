package com.summer.notifai.nerbenchmark

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.summer.notifai.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NerBenchmarkSmokeInstrumentedTest {
    @Test
    fun flavorAssets_verifyHashesAndExpectedFiles() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val assets = BenchmarkAssets(context).verifyAndMaterialize()
        assertTrue(BuildConfig.NER_BENCHMARK_MODEL_ID.endsWith("-v50"))
        assertTrue(assets.model.length() > 0)
        assertTrue(assets.tokenizer.length() > 0)
        assertEquals(BENCHMARK_FULL_CASES, assets.fixture.useLines { it.count() })
        assertEquals(BENCHMARK_FULL_CASES, assets.golden.useLines { it.count() })
        assertTrue(assets.performanceSelection.length() > 0)
    }
}

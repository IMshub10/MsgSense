package com.summer.notifai.nerbenchmark

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.summer.notifai.nerbenchmark.db.BenchmarkDatabase
import com.summer.notifai.nerbenchmark.db.BenchmarkRunEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BenchmarkExporterInstrumentedTest {
    @Test
    fun liveExportAtomicallyReplacesNonAuthoritativeSummary() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, BenchmarkDatabase::class.java).build()
        val dao = database.benchmarkDao()
        val initial = run()
        dao.insertRun(initial)
        val exporter = BenchmarkExporter(context, dao)

        val first = exporter.export(initial, live = true)
        val updated = initial.copy(measured_cases = 100, last_checkpoint_at_ms = 99)
        dao.updateRun(updated)
        val second = exporter.export(updated, live = true)

        assertTrue(second.summary.name.endsWith(".live.json"))
        assertTrue(second.cases.name.endsWith(".live-cases.csv"))
        assertTrue(second.checksum.name.endsWith(".live.sha256"))
        assertTrue(second.summary.readText().contains("\"measured_cases\": 100"))
        assertTrue(second.summary.readText().contains("\"status\": \"RUNNING\""))
        assertFalse(first.summary.resolveSibling("${first.summary.name}.tmp").exists())
        database.close()
    }

    private fun run() = BenchmarkRunEntity(
        run_id = "export-test",
        protocol_version = BACKGROUND_PERFORMANCE_PROTOCOL,
        benchmark_mode = BenchmarkMode.BACKGROUND_PERFORMANCE.value,
        expected_cases = BENCHMARK_PERFORMANCE_CASES,
        selection_sha256 = "s",
        model_id = "albert-v50",
        status = "RUNNING",
        started_at_ms = 1,
        background_verified = true,
        model_sha256 = "m",
        tokenizer_sha256 = "t",
        fixture_sha256 = "f",
        golden_sha256 = "g",
        apk_sha256 = "a",
        device_id = "device-id",
        device_fingerprint = "device",
        device_model = "device",
        sdk_int = 35,
        abi = "arm64-v8a",
        start_pss_kb = 1,
        start_heap_kb = 1,
        start_battery_pct = 100,
        start_charge_uah = 1,
        start_thermal_status = 0,
        max_thermal_status = 0,
    )
}

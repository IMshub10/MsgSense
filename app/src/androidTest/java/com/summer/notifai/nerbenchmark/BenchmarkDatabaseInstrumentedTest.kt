package com.summer.notifai.nerbenchmark

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.summer.notifai.nerbenchmark.db.BenchmarkCaseResultEntity
import com.summer.notifai.nerbenchmark.db.BenchmarkDatabase
import com.summer.notifai.nerbenchmark.db.BenchmarkNamedEntityEntity
import com.summer.notifai.nerbenchmark.db.BenchmarkRunEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BenchmarkDatabaseInstrumentedTest {
    @Test
    fun checkpointPersistsBatchAndRunMetadataInOneTransaction() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, BenchmarkDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val dao = database.benchmarkDao()
        val run = run()
        dao.insertRun(run)
        val case = case(run.run_id)
        val entity = BenchmarkNamedEntityEntity(
            run_id = run.run_id,
            fixture_id = case.fixture_id,
            expected = true,
            entity_type = "BANK",
            text = "HDFC",
            start_token = 1,
            end_token = 1,
        )

        dao.checkpoint(
            run.copy(measured_cases = 1, last_checkpoint_at_ms = 50, compute_elapsed_ms = 3.0),
            listOf(case),
            listOf(entity),
        )

        assertEquals(1, dao.getRun(run.run_id)?.measured_cases)
        assertEquals(50, dao.getRun(run.run_id)?.last_checkpoint_at_ms)
        assertEquals(listOf(case), dao.getCases(run.run_id))
        assertEquals(listOf(entity), dao.getEntities(run.run_id))
        database.close()
    }

    @Test
    fun deletingRun_cascadesCaseRows() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, BenchmarkDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val dao = database.benchmarkDao()
        val run = run()
        dao.insertRun(run)
        dao.insertCases(listOf(case(run.run_id)))
        dao.deleteRun(run.run_id)
        assertEquals(emptyList<BenchmarkCaseResultEntity>(), dao.getCases(run.run_id))
        database.close()
    }

    private fun run() = BenchmarkRunEntity(
            run_id = "run",
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

    private fun case(runId: String) = BenchmarkCaseResultEntity(
        run_id = runId,
        fixture_id = 1,
        tokenizer_ms = 1.0,
        inference_ms = 1.0,
        decode_ms = 1.0,
        total_ms = 3.0,
        tokenizer_parity = true,
        argmax_parity = true,
        entity_parity = true,
        true_positive = 1,
        false_positive = 0,
        false_negative = 0,
        error = null,
    )
}

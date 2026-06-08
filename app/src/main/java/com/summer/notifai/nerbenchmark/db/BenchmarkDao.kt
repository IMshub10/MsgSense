package com.summer.notifai.nerbenchmark.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update

@Dao
abstract class BenchmarkDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertRun(run: BenchmarkRunEntity)

    @Update
    abstract suspend fun updateRun(run: BenchmarkRunEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertCases(cases: List<BenchmarkCaseResultEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertEntities(entities: List<BenchmarkNamedEntityEntity>)

    @Query("SELECT * FROM ner_benchmark_runs WHERE run_id = :runId")
    abstract suspend fun getRun(runId: String): BenchmarkRunEntity?

    @Query("SELECT * FROM ner_benchmark_case_results WHERE run_id = :runId ORDER BY fixture_id")
    abstract suspend fun getCases(runId: String): List<BenchmarkCaseResultEntity>

    @Query("SELECT * FROM ner_benchmark_entities WHERE run_id = :runId ORDER BY fixture_id, expected DESC, start_token")
    abstract suspend fun getEntities(runId: String): List<BenchmarkNamedEntityEntity>

    @Query("DELETE FROM ner_benchmark_runs WHERE run_id = :runId")
    abstract suspend fun deleteRun(runId: String)

    @Transaction
    open suspend fun checkpoint(
        run: BenchmarkRunEntity,
        cases: List<BenchmarkCaseResultEntity>,
        entities: List<BenchmarkNamedEntityEntity>,
    ) {
        insertCases(cases)
        insertEntities(entities)
        updateRun(run)
    }
}

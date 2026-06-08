package com.summer.notifai.nerbenchmark.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        BenchmarkRunEntity::class,
        BenchmarkCaseResultEntity::class,
        BenchmarkNamedEntityEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class BenchmarkDatabase : RoomDatabase() {
    abstract fun benchmarkDao(): BenchmarkDao
}

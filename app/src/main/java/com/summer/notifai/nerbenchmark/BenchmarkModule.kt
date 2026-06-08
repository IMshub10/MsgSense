package com.summer.notifai.nerbenchmark

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.summer.notifai.nerbenchmark.db.BenchmarkDao
import com.summer.notifai.nerbenchmark.db.BenchmarkDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object BenchmarkModule {
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE ner_benchmark_runs ADD COLUMN last_checkpoint_at_ms INTEGER")
            database.execSQL("ALTER TABLE ner_benchmark_runs ADD COLUMN compute_elapsed_ms REAL NOT NULL DEFAULT 0")
            database.execSQL("ALTER TABLE ner_benchmark_runs ADD COLUMN wall_elapsed_ms REAL NOT NULL DEFAULT 0")
            database.execSQL("ALTER TABLE ner_benchmark_runs ADD COLUMN compute_throughput_msgs_sec REAL")
            database.execSQL("ALTER TABLE ner_benchmark_runs ADD COLUMN wall_throughput_msgs_sec REAL")
            database.execSQL("ALTER TABLE ner_benchmark_runs ADD COLUMN benchmark_mode TEXT NOT NULL DEFAULT 'background-performance'")
            database.execSQL("ALTER TABLE ner_benchmark_runs ADD COLUMN expected_cases INTEGER NOT NULL DEFAULT 500")
            database.execSQL("ALTER TABLE ner_benchmark_runs ADD COLUMN selection_sha256 TEXT")
        }
    }

    @Provides
    @Singleton
    fun provideBenchmarkDatabase(@ApplicationContext context: Context): BenchmarkDatabase =
        Room.databaseBuilder(context, BenchmarkDatabase::class.java, "ner_benchmark_temp.db")
            .addMigrations(MIGRATION_1_2)
            .build()

    @Provides
    fun provideBenchmarkDao(database: BenchmarkDatabase): BenchmarkDao = database.benchmarkDao()
}

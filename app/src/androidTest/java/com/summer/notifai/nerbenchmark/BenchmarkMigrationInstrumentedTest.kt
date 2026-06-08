package com.summer.notifai.nerbenchmark

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.summer.notifai.nerbenchmark.db.BenchmarkDatabase
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BenchmarkMigrationInstrumentedTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        requireNotNull(BenchmarkDatabase::class.java.canonicalName),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrate1To2AddsCheckpointColumns() {
        helper.createDatabase(TEST_DATABASE, 1).close()
        helper.runMigrationsAndValidate(TEST_DATABASE, 2, true, BenchmarkModule.MIGRATION_1_2).close()
    }

    companion object {
        private const val TEST_DATABASE = "ner-benchmark-migration-test"
    }
}

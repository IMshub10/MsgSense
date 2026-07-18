package com.summer.core.data.local.db

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SmsDatabaseMigrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "sms-migration-test.db"

    @After
    fun cleanUp() {
        context.deleteDatabase(name)
    }

    @Test
    fun migrationPreservesSmsAndCreatesNerTables() = runBlocking {
        createVersionOneDatabase()

        val database = Room.databaseBuilder(context, SmsDatabase::class.java, name)
            .addMigrations(
                SmsDatabase.MIGRATION_1_2,
                SmsDatabase.MIGRATION_2_3,
                SmsDatabase.MIGRATION_3_4,
                SmsDatabase.MIGRATION_4_5,
            )
            .build()
        val sqlite = database.openHelper.writableDatabase

        assertEquals("existing body", database.smsDao().getSmsEntityById(1)?.body)
        assertTrue(sqlite.hasTable("sms_ner_extractions"))
        assertTrue(sqlite.hasTable("sms_ner_entities"))
        assertTrue(sqlite.hasColumn("sms_ner_extractions", "notification_state"))
        assertTrue(sqlite.hasTable("sms_transaction_overrides"))
        assertTrue(sqlite.hasTable("bank_accounts"))
        assertTrue(sqlite.hasTable("sms_transaction_account_links"))
        assertTrue(sqlite.hasTable("bank_account_balance_observations"))
        database.close()
    }

    private fun createVersionOneDatabase() {
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(name)
            .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL("CREATE TABLE sender_addresses (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, sender_address TEXT NOT NULL, original_sender_address TEXT NOT NULL, sender_type TEXT NOT NULL, is_blocked INTEGER NOT NULL)")
                    db.execSQL("CREATE UNIQUE INDEX index_sender_addresses_sender_address ON sender_addresses(sender_address)")
                    db.execSQL("CREATE TABLE sms_classification_types (id INTEGER NOT NULL, multi_label_sms_type TEXT, sms_type TEXT, aggregate_sms_type TEXT, is_important INTEGER NOT NULL, compact_sms_type TEXT, description TEXT NOT NULL DEFAULT '', PRIMARY KEY(id))")
                    db.execSQL("CREATE TABLE contacts (id INTEGER NOT NULL, name TEXT NOT NULL, phone_number TEXT NOT NULL, original_phone_number TEXT NOT NULL, updated_at_app INTEGER NOT NULL, PRIMARY KEY(id))")
                    db.execSQL("CREATE UNIQUE INDEX index_contacts_phone_number ON contacts(phone_number)")
                    db.execSQL(
                        """
                        CREATE TABLE sms_messages (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            android_sms_id INTEGER,
                            sender_address_id INTEGER NOT NULL,
                            raw_address TEXT NOT NULL,
                            body TEXT NOT NULL,
                            date INTEGER NOT NULL,
                            date_sent INTEGER,
                            type INTEGER NOT NULL,
                            thread_id INTEGER,
                            read INTEGER NOT NULL,
                            status INTEGER,
                            service_center TEXT,
                            subscription_id INTEGER,
                            sms_classification_type_id INTEGER,
                            importance_score INTEGER,
                            confidence_score REAL,
                            created_at_app INTEGER NOT NULL,
                            updated_at_app INTEGER NOT NULL,
                            FOREIGN KEY(sender_address_id) REFERENCES sender_addresses(id) ON UPDATE NO ACTION ON DELETE SET NULL
                        )
                        """.trimIndent()
                    )
                    db.execSQL("CREATE INDEX index_sms_messages_sender_address_id ON sms_messages(sender_address_id)")
                    db.execSQL("CREATE UNIQUE INDEX index_sms_messages_android_sms_id ON sms_messages(android_sms_id)")
                    db.execSQL("INSERT INTO sender_addresses VALUES (1, 'BANK', 'BANK', 'BUSINESS', 0)")
                    db.execSQL("INSERT INTO sms_messages VALUES (1, 10, 1, 'BANK', 'existing body', 1, NULL, 1, NULL, 0, NULL, NULL, NULL, 2, 3, 0.9, 1, 1)")
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()
        FrameworkSQLiteOpenHelperFactory().create(configuration).use {
            it.writableDatabase
        }
    }

    private fun SupportSQLiteDatabase.hasTable(name: String): Boolean =
        query("SELECT name FROM sqlite_master WHERE type='table' AND name=?", arrayOf(name)).use {
            it.moveToFirst()
        }

    private fun SupportSQLiteDatabase.hasColumn(table: String, column: String): Boolean =
        query("PRAGMA table_info(`$table`)").use {
            val nameIndex = it.getColumnIndex("name")
            while (it.moveToNext()) {
                if (it.getString(nameIndex) == column) return@use true
            }
            false
        }
}

package com.summer.core.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.summer.core.data.local.dao.ContactDao
import com.summer.core.data.local.dao.NerDao
import com.summer.core.data.local.dao.SmsDao
import com.summer.core.android.phone.data.entity.ContactEntity
import com.summer.core.data.local.entities.SenderAddressEntity
import com.summer.core.data.local.entities.SmsEntity
import com.summer.core.data.local.entities.SmsClassificationTypeEntity
import com.summer.core.data.local.entities.SmsNerEntity
import com.summer.core.data.local.entities.SmsNerExtractionEntity
import com.summer.core.data.local.entities.SmsTransactionOverrideEntity
import com.summer.core.data.local.entities.BankAccountEntity
import com.summer.core.data.local.entities.BankAccountBalanceObservationEntity
import com.summer.core.data.local.entities.SmsTransactionAccountLinkEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        SmsEntity::class, SmsClassificationTypeEntity::class, SenderAddressEntity::class,
        ContactEntity::class, SmsNerExtractionEntity::class, SmsNerEntity::class,
        SmsTransactionOverrideEntity::class, BankAccountEntity::class,
        SmsTransactionAccountLinkEntity::class, BankAccountBalanceObservationEntity::class,
    ],
    version = 5,
    exportSchema = true,
)
abstract class SmsDatabase : RoomDatabase() {

    abstract fun smsDao(): SmsDao
    abstract fun contactDao(): ContactDao
    abstract fun nerDao(): NerDao

    companion object {
        private const val DB_NAME = "sms_database"

        @Volatile
        private var INSTANCE: SmsDatabase? = null

        fun getDatabase(context: Context): SmsDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SmsDatabase::class.java,
                    DB_NAME
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .addCallback(
                        object : Callback() {
                            override fun onCreate(db: SupportSQLiteDatabase) {
                                super.onCreate(db)
                                initData()
                            }
                        }
                    ).build()
                INSTANCE = instance
                instance
            }
        }

        private fun initData() {
            CoroutineScope(Dispatchers.IO).launch {
                INSTANCE?.smsDao()
                    ?.insertAllSmsClassificationTypes(DataSet.smsClassificationTypes)
            }
        }

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `sms_ner_extractions` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `sms_id` INTEGER NOT NULL,
                        `status` TEXT NOT NULL,
                        `priority` TEXT NOT NULL,
                        `attempts` INTEGER NOT NULL,
                        `model_id` TEXT,
                        `model_sha256` TEXT,
                        `tokenizer_sha256` TEXT,
                        `preprocessing_version` TEXT,
                        `token_count` INTEGER,
                        `truncated` INTEGER,
                        `inference_ms` REAL,
                        `entity_count` INTEGER,
                        `notification_ready` INTEGER NOT NULL,
                        `failure_code` TEXT,
                        `failure_message` TEXT,
                        `created_at` INTEGER NOT NULL,
                        `started_at` INTEGER,
                        `completed_at` INTEGER,
                        `updated_at` INTEGER NOT NULL,
                        FOREIGN KEY(`sms_id`) REFERENCES `sms_messages`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_sms_ner_extractions_sms_id` ON `sms_ner_extractions` (`sms_id`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_sms_ner_extractions_status_priority_created_at` ON `sms_ner_extractions` (`status`, `priority`, `created_at`)")
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `sms_ner_entities` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `extraction_id` INTEGER NOT NULL,
                        `entity_order` INTEGER NOT NULL,
                        `entity_type` TEXT NOT NULL,
                        `raw_text` TEXT NOT NULL,
                        `normalized_value` TEXT,
                        `start_offset` INTEGER NOT NULL,
                        `end_offset` INTEGER NOT NULL,
                        FOREIGN KEY(`extraction_id`) REFERENCES `sms_ner_extractions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_sms_ner_entities_extraction_id` ON `sms_ner_entities` (`extraction_id`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_sms_ner_entities_entity_type` ON `sms_ner_entities` (`entity_type`)")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE `sms_ner_extractions` ADD COLUMN `notification_id` INTEGER")
                database.execSQL("ALTER TABLE `sms_ner_extractions` ADD COLUMN `notification_state` TEXT NOT NULL DEFAULT 'NONE'")
                database.execSQL("ALTER TABLE `sms_ner_extractions` ADD COLUMN `notification_suppression_reason` TEXT")
                database.execSQL("ALTER TABLE `sms_ner_extractions` ADD COLUMN `result_notification_posted_at` INTEGER")
                database.execSQL(
                    """
                    UPDATE `sms_ner_extractions`
                    SET `notification_state` = CASE
                        WHEN `notification_ready` = 1 THEN 'READY'
                        ELSE 'NONE'
                    END
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `sms_transaction_overrides` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `extraction_id` INTEGER NOT NULL,
                        `merchant` TEXT NOT NULL,
                        `amount` TEXT NOT NULL,
                        `currency` TEXT NOT NULL,
                        `direction` TEXT NOT NULL,
                        `category` TEXT NOT NULL,
                        `payment_method` TEXT NOT NULL,
                        `review_state` TEXT NOT NULL,
                        `created_at` INTEGER NOT NULL,
                        `updated_at` INTEGER NOT NULL,
                        FOREIGN KEY(`extraction_id`) REFERENCES `sms_ner_extractions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_sms_transaction_overrides_extraction_id` ON `sms_transaction_overrides` (`extraction_id`)")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `bank_accounts` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `canonical_bank` TEXT NOT NULL,
                        `instrument_type` TEXT NOT NULL,
                        `generated_name` TEXT NOT NULL,
                        `custom_name` TEXT,
                        `masked_identifier` TEXT,
                        `identifier_fingerprint` TEXT NOT NULL,
                        `logo_key` TEXT,
                        `is_hidden` INTEGER NOT NULL,
                        `merge_target_id` INTEGER,
                        `created_at` INTEGER NOT NULL,
                        `updated_at` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_bank_accounts_identifier_fingerprint` ON `bank_accounts` (`identifier_fingerprint`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_bank_accounts_canonical_bank_instrument_type` ON `bank_accounts` (`canonical_bank`, `instrument_type`)")
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `sms_transaction_account_links` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `extraction_id` INTEGER NOT NULL,
                        `account_id` INTEGER NOT NULL,
                        `source` TEXT NOT NULL,
                        `confidence` REAL NOT NULL,
                        `reason` TEXT NOT NULL,
                        `created_at` INTEGER NOT NULL,
                        `updated_at` INTEGER NOT NULL,
                        FOREIGN KEY(`extraction_id`) REFERENCES `sms_ner_extractions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`account_id`) REFERENCES `bank_accounts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_sms_transaction_account_links_extraction_id` ON `sms_transaction_account_links` (`extraction_id`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_sms_transaction_account_links_account_id` ON `sms_transaction_account_links` (`account_id`)")
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `bank_account_balance_observations` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `account_id` INTEGER NOT NULL,
                        `source_extraction_id` INTEGER NOT NULL,
                        `balance` TEXT NOT NULL,
                        `currency` TEXT NOT NULL,
                        `observed_at` INTEGER NOT NULL,
                        `created_at` INTEGER NOT NULL,
                        FOREIGN KEY(`account_id`) REFERENCES `bank_accounts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`source_extraction_id`) REFERENCES `sms_ner_extractions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_bank_account_balance_observations_account_id_observed_at` ON `bank_account_balance_observations` (`account_id`, `observed_at`)")
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_bank_account_balance_observations_source_extraction_id` ON `bank_account_balance_observations` (`source_extraction_id`)")
            }
        }
    }
}

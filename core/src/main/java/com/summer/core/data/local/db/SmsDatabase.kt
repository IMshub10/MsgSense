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
import com.summer.core.data.local.entities.ContactEntity
import com.summer.core.data.local.entities.SenderAddressEntity
import com.summer.core.data.local.entities.SmsEntity
import com.summer.core.data.local.entities.SmsClassificationTypeEntity
import com.summer.core.data.local.entities.NerMentionEntity
import com.summer.core.data.local.entities.NerRunEntity
import com.summer.core.data.local.entities.NerRunMetadataEntity
import com.summer.core.data.local.entities.BankingNerNotificationEntity
import com.summer.core.data.local.entities.BankingTransactionOverrideEntity
import com.summer.core.data.local.entities.BankAccountEntity
import com.summer.core.data.local.entities.BankAccountBalanceObservationEntity
import com.summer.core.data.local.entities.BankingTransactionFactEntity
import com.summer.core.data.local.entities.SmsTransactionAccountLinkEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        SmsEntity::class, SmsClassificationTypeEntity::class, SenderAddressEntity::class,
        ContactEntity::class, NerRunEntity::class, NerRunMetadataEntity::class,
        NerMentionEntity::class, BankingNerNotificationEntity::class,
        BankingTransactionOverrideEntity::class, BankAccountEntity::class,
        SmsTransactionAccountLinkEntity::class, BankAccountBalanceObservationEntity::class,
        BankingTransactionFactEntity::class,
    ],
    version = 6,
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
                ).addMigrations(
                    MIGRATION_1_2,
                    MIGRATION_2_3,
                    MIGRATION_3_4,
                    MIGRATION_4_5,
                    MIGRATION_5_6,
                )
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

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("PRAGMA foreign_keys=OFF")
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `ner_runs` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `sms_id` INTEGER NOT NULL,
                        `status` TEXT NOT NULL,
                        `priority` TEXT NOT NULL,
                        `attempts` INTEGER NOT NULL,
                        `pipeline_fingerprint` TEXT NOT NULL,
                        `active` INTEGER NOT NULL,
                        `stale_reason` TEXT,
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
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_ner_runs_sms_id_pipeline_fingerprint` ON `ner_runs` (`sms_id`, `pipeline_fingerprint`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_ner_runs_status_priority_created_at` ON `ner_runs` (`status`, `priority`, `created_at`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_ner_runs_active_status_completed_at` ON `ner_runs` (`active`, `status`, `completed_at`)")
                database.execSQL(
                    """
                    INSERT INTO `ner_runs` (
                        `id`, `sms_id`, `status`, `priority`, `attempts`, `pipeline_fingerprint`,
                        `active`, `stale_reason`, `failure_code`, `failure_message`, `created_at`,
                        `started_at`, `completed_at`, `updated_at`
                    )
                    SELECT `id`, `sms_id`, `status`, `priority`, `attempts`, 'legacy',
                           1, NULL, `failure_code`, `failure_message`, `created_at`,
                           `started_at`, `completed_at`, `updated_at`
                    FROM `sms_ner_extractions`
                    """.trimIndent()
                )
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `ner_run_metadata` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `run_id` INTEGER NOT NULL,
                        `model_id` TEXT NOT NULL,
                        `model_sha256` TEXT NOT NULL,
                        `tokenizer_sha256` TEXT NOT NULL,
                        `preprocessing_version` TEXT NOT NULL,
                        `label_schema_sha256` TEXT,
                        `decoder_version` TEXT,
                        `normalizer_version` TEXT,
                        `token_count` INTEGER NOT NULL,
                        `truncated` INTEGER NOT NULL,
                        `inference_ms` REAL NOT NULL,
                        `mention_count` INTEGER NOT NULL,
                        `created_at` INTEGER NOT NULL,
                        `updated_at` INTEGER NOT NULL,
                        FOREIGN KEY(`run_id`) REFERENCES `ner_runs`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_ner_run_metadata_run_id` ON `ner_run_metadata` (`run_id`)")
                database.execSQL(
                    """
                    INSERT OR IGNORE INTO `ner_run_metadata` (
                        `run_id`, `model_id`, `model_sha256`, `tokenizer_sha256`,
                        `preprocessing_version`, `label_schema_sha256`, `decoder_version`,
                        `normalizer_version`, `token_count`, `truncated`, `inference_ms`,
                        `mention_count`, `created_at`, `updated_at`
                    )
                    SELECT `id`, COALESCE(`model_id`, 'legacy'), COALESCE(`model_sha256`, 'legacy'),
                           COALESCE(`tokenizer_sha256`, 'legacy'), COALESCE(`preprocessing_version`, 'legacy'),
                           NULL, NULL, NULL, COALESCE(`token_count`, 0), COALESCE(`truncated`, 0),
                           COALESCE(`inference_ms`, 0.0), COALESCE(`entity_count`, 0),
                           COALESCE(`completed_at`, `updated_at`), `updated_at`
                    FROM `sms_ner_extractions`
                    WHERE `status` = 'COMPLETED'
                    """.trimIndent()
                )
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `ner_mentions` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `run_id` INTEGER NOT NULL,
                        `entity_order` INTEGER NOT NULL,
                        `entity_type` TEXT NOT NULL,
                        `raw_text` TEXT NOT NULL,
                        `normalized_value` TEXT,
                        `start_offset` INTEGER NOT NULL,
                        `end_offset` INTEGER NOT NULL,
                        FOREIGN KEY(`run_id`) REFERENCES `ner_runs`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_ner_mentions_run_id` ON `ner_mentions` (`run_id`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_ner_mentions_run_id_entity_type_entity_order` ON `ner_mentions` (`run_id`, `entity_type`, `entity_order`)")
                database.execSQL(
                    """
                    INSERT INTO `ner_mentions` (
                        `id`, `run_id`, `entity_order`, `entity_type`, `raw_text`,
                        `normalized_value`, `start_offset`, `end_offset`
                    )
                    SELECT `id`, `extraction_id`, `entity_order`, `entity_type`, `raw_text`,
                           `normalized_value`, `start_offset`, `end_offset`
                    FROM `sms_ner_entities`
                    """.trimIndent()
                )
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `banking_ner_notifications` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `run_id` INTEGER NOT NULL,
                        `notification_id` INTEGER NOT NULL,
                        `state` TEXT NOT NULL,
                        `suppression_reason` TEXT,
                        `posted_at` INTEGER,
                        `created_at` INTEGER NOT NULL,
                        `updated_at` INTEGER NOT NULL,
                        FOREIGN KEY(`run_id`) REFERENCES `ner_runs`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_banking_ner_notifications_run_id` ON `banking_ner_notifications` (`run_id`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_banking_ner_notifications_state` ON `banking_ner_notifications` (`state`)")
                database.execSQL(
                    """
                    INSERT OR IGNORE INTO `banking_ner_notifications` (
                        `run_id`, `notification_id`, `state`, `suppression_reason`,
                        `posted_at`, `created_at`, `updated_at`
                    )
                    SELECT `id`, COALESCE(`notification_id`, -2000000 - (`id` % 1000000000)),
                           `notification_state`, `notification_suppression_reason`,
                           `result_notification_posted_at`, `created_at`, `updated_at`
                    FROM `sms_ner_extractions`
                    WHERE `notification_state` IS NOT NULL AND `notification_state` != 'NONE'
                    """.trimIndent()
                )
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `banking_transaction_facts` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `run_id` INTEGER NOT NULL,
                        `merchant` TEXT,
                        `amount` TEXT,
                        `amount_currency` TEXT,
                        `direction` TEXT,
                        `bank` TEXT,
                        `account` TEXT,
                        `card_type` TEXT,
                        `txn_type` TEXT,
                        `balance` TEXT,
                        `balance_currency` TEXT,
                        `ref_id` TEXT,
                        `review_state` TEXT NOT NULL,
                        `ambiguity_flags` TEXT,
                        `merchant_entity_id` INTEGER,
                        `amount_entity_id` INTEGER,
                        `direction_entity_id` INTEGER,
                        `bank_entity_id` INTEGER,
                        `account_entity_id` INTEGER,
                        `card_type_entity_id` INTEGER,
                        `txn_type_entity_id` INTEGER,
                        `balance_entity_id` INTEGER,
                        `ref_id_entity_id` INTEGER,
                        `created_at` INTEGER NOT NULL,
                        `updated_at` INTEGER NOT NULL,
                        FOREIGN KEY(`run_id`) REFERENCES `ner_runs`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_banking_transaction_facts_run_id` ON `banking_transaction_facts` (`run_id`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_banking_transaction_facts_review_state` ON `banking_transaction_facts` (`review_state`)")
                database.execSQL(
                    """
                    INSERT OR IGNORE INTO `banking_transaction_facts` (
                        `run_id`, `merchant`, `amount`, `amount_currency`, `direction`,
                        `bank`, `account`, `card_type`, `txn_type`, `balance`, `balance_currency`,
                        `ref_id`, `review_state`, `ambiguity_flags`, `merchant_entity_id`,
                        `amount_entity_id`, `direction_entity_id`, `bank_entity_id`,
                        `account_entity_id`, `card_type_entity_id`, `txn_type_entity_id`,
                        `balance_entity_id`, `ref_id_entity_id`, `created_at`, `updated_at`
                    )
                    SELECT
                        extraction.`id`,
                        merchant.`raw_text`,
                        amount.`normalized_value`,
                        CASE
                            WHEN amount.`raw_text` LIKE '%USD%' OR instr(amount.`raw_text`, '$') > 0 THEN 'USD'
                            WHEN amount.`raw_text` LIKE '%EUR%' OR instr(amount.`raw_text`, '€') > 0 THEN 'EUR'
                            WHEN amount.`raw_text` LIKE '%GBP%' OR instr(amount.`raw_text`, '£') > 0 THEN 'GBP'
                            WHEN amount.`raw_text` LIKE '%INR%' OR upper(amount.`raw_text`) LIKE '%RS%' OR instr(amount.`raw_text`, '₹') > 0 THEN 'INR'
                            ELSE NULL
                        END,
                        upper(direction.`normalized_value`),
                        bank.`raw_text`,
                        account.`normalized_value`,
                        card_type.`normalized_value`,
                        txn_type.`normalized_value`,
                        balance.`normalized_value`,
                        CASE
                            WHEN balance.`raw_text` LIKE '%USD%' OR instr(balance.`raw_text`, '$') > 0 THEN 'USD'
                            WHEN balance.`raw_text` LIKE '%EUR%' OR instr(balance.`raw_text`, '€') > 0 THEN 'EUR'
                            WHEN balance.`raw_text` LIKE '%GBP%' OR instr(balance.`raw_text`, '£') > 0 THEN 'GBP'
                            WHEN balance.`raw_text` LIKE '%INR%' OR upper(balance.`raw_text`) LIKE '%RS%' OR instr(balance.`raw_text`, '₹') > 0 THEN 'INR'
                            ELSE NULL
                        END,
                        ref_id.`normalized_value`,
                        CASE
                            WHEN extraction.`truncated` = 1 OR amount.`normalized_value` IS NULL
                                OR upper(direction.`normalized_value`) NOT IN ('DEBIT', 'CREDIT', 'REVERSAL')
                            THEN 'NEEDS_REVIEW'
                            ELSE 'AI_EXTRACTED'
                        END,
                        CASE
                            WHEN extraction.`truncated` = 1 THEN 'TRUNCATED'
                            WHEN amount.`normalized_value` IS NULL THEN 'MISSING_AMOUNT'
                            WHEN upper(direction.`normalized_value`) NOT IN ('DEBIT', 'CREDIT', 'REVERSAL') THEN 'UNSUPPORTED_DIRECTION'
                            ELSE NULL
                        END,
                        merchant.`id`,
                        amount.`id`,
                        direction.`id`,
                        bank.`id`,
                        account.`id`,
                        card_type.`id`,
                        txn_type.`id`,
                        balance.`id`,
                        ref_id.`id`,
                        COALESCE(extraction.`completed_at`, extraction.`updated_at`),
                        extraction.`updated_at`
                    FROM `sms_ner_extractions` extraction
                    LEFT JOIN `ner_mentions` merchant ON merchant.`id` = (
                        SELECT `id` FROM `ner_mentions` WHERE `run_id` = extraction.`id` AND `entity_type` = 'MERCHANT' ORDER BY `entity_order` LIMIT 1
                    )
                    LEFT JOIN `ner_mentions` amount ON amount.`id` = (
                        SELECT `id` FROM `ner_mentions` WHERE `run_id` = extraction.`id` AND `entity_type` = 'AMOUNT' ORDER BY `entity_order` LIMIT 1
                    )
                    LEFT JOIN `ner_mentions` direction ON direction.`id` = (
                        SELECT `id` FROM `ner_mentions` WHERE `run_id` = extraction.`id` AND `entity_type` = 'DIRECTION' ORDER BY `entity_order` LIMIT 1
                    )
                    LEFT JOIN `ner_mentions` bank ON bank.`id` = (
                        SELECT `id` FROM `ner_mentions` WHERE `run_id` = extraction.`id` AND `entity_type` = 'BANK' ORDER BY `entity_order` LIMIT 1
                    )
                    LEFT JOIN `ner_mentions` account ON account.`id` = (
                        SELECT `id` FROM `ner_mentions` WHERE `run_id` = extraction.`id` AND `entity_type` = 'ACCOUNT' ORDER BY `entity_order` LIMIT 1
                    )
                    LEFT JOIN `ner_mentions` card_type ON card_type.`id` = (
                        SELECT `id` FROM `ner_mentions` WHERE `run_id` = extraction.`id` AND `entity_type` = 'CARD_TYPE' ORDER BY `entity_order` LIMIT 1
                    )
                    LEFT JOIN `ner_mentions` txn_type ON txn_type.`id` = (
                        SELECT `id` FROM `ner_mentions` WHERE `run_id` = extraction.`id` AND `entity_type` = 'TXN_TYPE' ORDER BY `entity_order` LIMIT 1
                    )
                    LEFT JOIN `ner_mentions` balance ON balance.`id` = (
                        SELECT `id` FROM `ner_mentions` WHERE `run_id` = extraction.`id` AND `entity_type` = 'BALANCE' ORDER BY `entity_order` LIMIT 1
                    )
                    LEFT JOIN `ner_mentions` ref_id ON ref_id.`id` = (
                        SELECT `id` FROM `ner_mentions` WHERE `run_id` = extraction.`id` AND `entity_type` = 'REF_ID' ORDER BY `entity_order` LIMIT 1
                    )
                    WHERE extraction.`status` = 'COMPLETED'
                    """.trimIndent()
                )
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `banking_transaction_overrides` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `run_id` INTEGER NOT NULL,
                        `merchant` TEXT NOT NULL,
                        `amount` TEXT NOT NULL,
                        `currency` TEXT NOT NULL,
                        `direction` TEXT NOT NULL,
                        `category` TEXT NOT NULL,
                        `payment_method` TEXT NOT NULL,
                        `review_state` TEXT NOT NULL,
                        `created_at` INTEGER NOT NULL,
                        `updated_at` INTEGER NOT NULL,
                        FOREIGN KEY(`run_id`) REFERENCES `ner_runs`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_banking_transaction_overrides_run_id` ON `banking_transaction_overrides` (`run_id`)")
                database.execSQL(
                    """
                    INSERT INTO `banking_transaction_overrides` (
                        `id`, `run_id`, `merchant`, `amount`, `currency`, `direction`,
                        `category`, `payment_method`, `review_state`, `created_at`, `updated_at`
                    )
                    SELECT `id`, `extraction_id`, `merchant`, `amount`, `currency`, `direction`,
                           `category`, `payment_method`, `review_state`, `created_at`, `updated_at`
                    FROM `sms_transaction_overrides`
                    """.trimIndent()
                )
                database.execSQL("ALTER TABLE `sms_transaction_account_links` RENAME TO `sms_transaction_account_links_legacy`")
                database.execSQL("DROP INDEX IF EXISTS `index_sms_transaction_account_links_extraction_id`")
                database.execSQL("DROP INDEX IF EXISTS `index_sms_transaction_account_links_account_id`")
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
                        FOREIGN KEY(`extraction_id`) REFERENCES `ner_runs`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`account_id`) REFERENCES `bank_accounts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_sms_transaction_account_links_extraction_id` ON `sms_transaction_account_links` (`extraction_id`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_sms_transaction_account_links_account_id` ON `sms_transaction_account_links` (`account_id`)")
                database.execSQL("INSERT INTO `sms_transaction_account_links` SELECT * FROM `sms_transaction_account_links_legacy`")
                database.execSQL("ALTER TABLE `bank_account_balance_observations` RENAME TO `bank_account_balance_observations_legacy`")
                database.execSQL("DROP INDEX IF EXISTS `index_bank_account_balance_observations_account_id_observed_at`")
                database.execSQL("DROP INDEX IF EXISTS `index_bank_account_balance_observations_source_extraction_id`")
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
                        FOREIGN KEY(`source_extraction_id`) REFERENCES `ner_runs`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_bank_account_balance_observations_account_id_observed_at` ON `bank_account_balance_observations` (`account_id`, `observed_at`)")
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_bank_account_balance_observations_source_extraction_id` ON `bank_account_balance_observations` (`source_extraction_id`)")
                database.execSQL("INSERT INTO `bank_account_balance_observations` SELECT * FROM `bank_account_balance_observations_legacy`")
                database.execSQL("DROP TABLE `sms_transaction_account_links_legacy`")
                database.execSQL("DROP TABLE `bank_account_balance_observations_legacy`")
                database.execSQL("DROP TABLE IF EXISTS `sms_transaction_overrides`")
                database.execSQL("DROP TABLE IF EXISTS `sms_ner_entities`")
                database.execSQL("DROP TABLE IF EXISTS `sms_ner_extractions`")
                database.execSQL("PRAGMA foreign_keys=ON")
            }
        }
    }
}

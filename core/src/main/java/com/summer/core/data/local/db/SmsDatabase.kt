package com.summer.core.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.summer.core.data.local.dao.ContactDao
import com.summer.core.data.local.dao.SmsDao
import com.summer.core.android.phone.data.entity.ContactEntity
import com.summer.core.data.local.entities.SenderAddressEntity
import com.summer.core.data.local.entities.SmsEntity
import com.summer.core.data.local.entities.SmsClassificationTypeEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [SmsEntity::class, SmsClassificationTypeEntity::class, SenderAddressEntity::class, ContactEntity::class],
    version = 1
)
abstract class SmsDatabase : RoomDatabase() {

    abstract fun smsDao(): SmsDao
    abstract fun contactDao(): ContactDao

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
                ).fallbackToDestructiveMigration()
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
    }
}
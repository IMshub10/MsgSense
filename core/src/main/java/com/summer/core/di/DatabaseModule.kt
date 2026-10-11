package com.summer.core.di

import android.content.Context
import com.summer.core.data.local.dao.ContactDao
import com.summer.core.data.local.dao.SmsDao
import com.summer.core.data.local.dao.NerDao
import com.summer.core.data.local.db.SmsDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): SmsDatabase {
        return SmsDatabase.getDatabase(context)
    }

    @Provides
    fun provideSmsDao(database: SmsDatabase): SmsDao {
        return database.smsDao()
    }

    @Provides
    fun provideContactDao(database: SmsDatabase): ContactDao {
        return database.contactDao()
    }

    @Provides
    fun provideNerDao(database: SmsDatabase): NerDao = database.nerDao()
}

package com.summer.core.di

import android.content.ContentResolver
import android.content.Context
import com.summer.core.android.sms.data.source.ISmsContentProvider
import com.summer.core.android.sms.data.source.SmsContentProvider
import com.summer.core.android.sms.processor.SmsBatchProcessor
import com.summer.core.classifier.SmsClassifier
import com.summer.core.data.local.dao.SmsDao
import com.summer.core.util.CountryCodeProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SmsModule {

    @Provides
    @Singleton
    fun provideContentResolver(@ApplicationContext context: Context): ContentResolver {
        return context.contentResolver
    }

    @Provides
    @Singleton
    fun provideSmsContentProvider(contentResolver: ContentResolver): ISmsContentProvider {
        return SmsContentProvider(contentResolver)
    }

    @Provides
    @Singleton
    fun provideSmsBatchProcessor(
        smsContentProvider: ISmsContentProvider,
        smsDao: SmsDao,
        smsClassifier: SmsClassifier,
        countryCodeProvider: CountryCodeProvider
    ): SmsBatchProcessor {
        return SmsBatchProcessor(
            smsContentProvider,
            smsDao,
            smsClassifier,
            countryCodeProvider
        )
    }
}
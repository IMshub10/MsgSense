package com.summer.core.di

import android.content.ContentResolver
import android.content.Context
import com.summer.core.android.sms.data.source.ISmsContentProvider
import com.summer.core.android.sms.data.source.SmsContentProvider
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
}
package com.summer.ner

import android.content.Context
import com.summer.core.ner.NerScheduler
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class NerModule {
    @Binds
    abstract fun bindNerScheduler(implementation: NerWorkScheduler): NerScheduler

    companion object {
        @Provides
        fun provideNerClientFactory(@ApplicationContext context: Context): NerClientFactory =
            NerClientFactory { NerServiceClient(context) }
    }
}

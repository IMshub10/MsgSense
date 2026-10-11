package com.summer.ner

import com.summer.core.ner.NerScheduler
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class NerModule {
    @Binds
    abstract fun bindNerScheduler(implementation: NerWorkScheduler): NerScheduler
}

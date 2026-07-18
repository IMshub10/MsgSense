package com.summer.notifai.ner

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface NerStartupEntryPoint {
    fun scheduler(): NerWorkScheduler
}

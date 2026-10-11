package com.summer.notifai.di

import com.summer.notifai.domain.usecase.MarkSmsAsDeliveredUseCase
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface DeliveredSmsReceiverEntryPoint {
    fun markSmsAsDelivered(): MarkSmsAsDeliveredUseCase
}

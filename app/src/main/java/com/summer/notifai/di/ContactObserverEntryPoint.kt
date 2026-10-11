package com.summer.notifai.di

import com.summer.notifai.domain.repository.IContactRepository
import com.summer.notifai.domain.usecase.SyncContactsUseCase
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ContactObserverEntryPoint {
    fun contactRepository(): IContactRepository
    fun syncContactsUseCase(): SyncContactsUseCase
}
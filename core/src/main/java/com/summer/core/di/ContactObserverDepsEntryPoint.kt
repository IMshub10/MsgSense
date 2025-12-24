package com.summer.core.di

import com.summer.core.android.phone.service.ContactObserver
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ContactObserverDepsEntryPoint {
    fun contactObserver(): ContactObserver
}
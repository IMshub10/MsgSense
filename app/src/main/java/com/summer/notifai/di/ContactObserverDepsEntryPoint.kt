package com.summer.notifai.di

import com.summer.notifai.android.observer.ContactObserver
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ContactObserverDepsEntryPoint {
    fun contactObserver(): ContactObserver
}
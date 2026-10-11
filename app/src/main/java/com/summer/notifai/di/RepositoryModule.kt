package com.summer.notifai.di

import com.summer.notifai.data.repository.ContactRepository
import com.summer.notifai.domain.repository.IContactRepository
import com.summer.notifai.domain.repository.IOnboardingRepository
import com.summer.notifai.domain.repository.ISmsRepository
import com.summer.notifai.data.repository.OnboardingRepository
import com.summer.notifai.data.repository.SmsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindOnboardingRepository(
        onboardingRepository: OnboardingRepository
    ): IOnboardingRepository

    @Binds
    @Singleton
    abstract fun bindSmsRepository(
        smsRepository: SmsRepository
    ): ISmsRepository

    @Binds
    @Singleton
    abstract fun bindContactRepository(
        contactRepository: ContactRepository
    ): IContactRepository

}
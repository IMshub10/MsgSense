package com.summer.core.di

import com.summer.core.data.local.dao.SmsDao
import com.summer.core.domain.repository.ISmsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages prewarming of dependencies and Room queries to eliminate cold-start lag.
 * 
 * When navigating from Splash → Home → SmsInbox, the ISmsRepository singleton
 * and Room DAO queries are "cold" (not yet constructed/compiled). This causes
 * a noticeable freeze on first inbox open.
 * 
 * By injecting this manager in the Home screen and calling [prewarm], we:
 * 1. Force Hilt to construct [ISmsRepository] singleton and all its dependencies
 * 2. Execute a lightweight DAO query to trigger Room's query compilation
 * 
 * This ensures smooth navigation to SmsInboxFrag without cold-start delays.
 */
@Singleton
class PrewarmManager @Inject constructor(
    @Suppress("unused")
    private val smsRepository: ISmsRepository,
    private val smsDao: SmsDao
) {
    private val isWarmed = AtomicBoolean(false)

    /**
     * Prewarms Room DAO queries used by SmsMessageLoader.
     * 
     * This triggers Room's query compilation for [SmsDao.getMessagesBefore],
     * which is the main query used when loading SMS inbox messages.
     * 
     * Safe to call multiple times - only runs once.
     * Should be called from a coroutine scope on the IO dispatcher.
     */
    suspend fun prewarm() {
        if (!isWarmed.compareAndSet(false, true)) return
        
        withContext(Dispatchers.IO) {
            smsDao.getMessagesBefore(
                senderAddressId = 0,
                date = Long.MAX_VALUE,
                important = 0,
                id = Long.MAX_VALUE,
                limit = 1
            )
        }
    }
}


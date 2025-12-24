package com.summer.core.data.repository

import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.summer.core.android.sms.data.source.ISmsContentProvider
import com.summer.core.data.local.dao.SmsDao
import com.summer.core.data.local.preference.PreferenceKey
import com.summer.core.data.local.preference.SharedPreferencesManager
import com.summer.core.domain.repository.IOnboardingRepository
import javax.inject.Inject


class OnboardingRepository @Inject constructor(
    private val sharedPreferencesManager: SharedPreferencesManager,
    private val smsDao: SmsDao,
    private val smsContentProvider: ISmsContentProvider
) : IOnboardingRepository {
    override fun hasAgreedToUserAgreement(): Boolean {
        return sharedPreferencesManager.getDataBoolean(PreferenceKey.USER_AGREEMENT)
    }

    override fun setUserAgreement(agreed: Boolean) {
        sharedPreferencesManager.saveData(PreferenceKey.USER_AGREEMENT, agreed)
    }

    override fun setDataSharing(enabled: Boolean) {
        sharedPreferencesManager.saveData(PreferenceKey.DATA_SHARING, enabled)
    }

    override suspend fun isSmsProcessingCompleted(): Boolean {
        return try {
            val lastSmsInDb = smsDao.getLastInsertedSmsMessageByAndroidSmsId()
            val lastProcessedId = lastSmsInDb?.androidSmsId ?: -1

            val firstSmsInDb = smsDao.getFirstInsertedSmsMessageByAndroidSmsId()
            val firstProcessedId = firstSmsInDb?.androidSmsId ?: Int.MAX_VALUE

            val lastDeviceId = smsContentProvider.getLastAndroidSmsId() ?: -1
            val firstDeviceId = smsContentProvider.getFirstAndroidSmsId() ?: Int.MAX_VALUE

            val hasProcessedAllNew = lastProcessedId >= lastDeviceId
            val hasProcessedAllOld = firstProcessedId <= firstDeviceId

            hasProcessedAllNew && hasProcessedAllOld
        } catch (e: Exception) {
            Log.e("SmsBatchProcessor", "Error checking processing status", e)
            FirebaseCrashlytics.getInstance().recordException(e)
            false
        }
    }

    override fun setPhoneTableLastUpdated(timeInMillis: Long) {
        sharedPreferencesManager.saveData(PreferenceKey.PHONE_TABLE_LAST_UPDATED, timeInMillis)
    }

    override fun areContactsSynced(): Boolean {
        return sharedPreferencesManager.getDataLong(PreferenceKey.PHONE_TABLE_LAST_UPDATED) != 0L
    }
}
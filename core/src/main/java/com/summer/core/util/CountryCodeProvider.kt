package com.summer.core.util

import android.content.Context
import android.telephony.TelephonyManager
import com.google.i18n.phonenumbers.PhoneNumberUtil
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import java.util.Locale

@Singleton
class CountryCodeProvider @Inject constructor(@ApplicationContext context: Context) {

    private val countryCode: Int = getUserDialingCode(context)

    val defaultIso: String = getDeviceIso(context)

    private fun getUserDialingCode(context: Context): Int {
        val telephonyManager =
            context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        val countryIso =
            telephonyManager.simCountryIso?.uppercase(Locale.ROOT) ?: "IN" // Default to "IN"
        return try {
            PhoneNumberUtil.getInstance().getCountryCodeForRegion(countryIso.uppercase())
        } catch (e: Exception) {
            91 // Default fallback to India
        }
    }

    fun getMyCountryCode(): Int {
        return countryCode
    }

    private fun getDeviceIso(context: Context): String {
        val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

        return telephonyManager.simCountryIso?.uppercase(Locale.ROOT)
            ?: telephonyManager.networkCountryIso?.uppercase(Locale.ROOT)
            ?: "IN"
    }
}
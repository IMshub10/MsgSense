package com.summer.core.android.phone.data.mapper

import android.database.Cursor
import android.provider.ContactsContract
import com.google.i18n.phonenumbers.NumberParseException
import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.summer.core.android.phone.data.entity.ContactEntity
import com.summer.core.android.sms.constants.Constants
import com.summer.core.util.stripNonDigits

object ContactMapper {

    // We create a static instance of the Util to avoid re-fetching it for every row
    private val phoneUtil = PhoneNumberUtil.getInstance()

    /**
     * @param defaultRegion The ISO country code (e.g., "IN", "US") to assume if the number has no country code.
     */
    fun mapCursorToContact(cursor: Cursor, defaultRegion: String = Constants.DEFAULT_REGION): ContactEntity? {
        val idIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone._ID)
        val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
        val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
        val lastUpdatedAtIndex =
            cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_LAST_UPDATED_TIMESTAMP)

        if (idIndex == -1 || numberIndex == -1 || nameIndex == -1 || lastUpdatedAtIndex == -1) return null

        val id = cursor.getLong(idIndex)
        val name = cursor.getString(nameIndex) ?: return null
        val rawPhoneNumber = cursor.getString(numberIndex) ?: return null
        val lastUpdatedAt = cursor.getLong(lastUpdatedAtIndex)

        val normalizedPhoneNumber = try {
            val numberProto = phoneUtil.parse(rawPhoneNumber, defaultRegion)

            if (phoneUtil.isValidNumber(numberProto)) {
                phoneUtil.format(numberProto, PhoneNumberUtil.PhoneNumberFormat.E164)
            } else {
                rawPhoneNumber.stripNonDigits()
            }
        } catch (e: NumberParseException) {
            e.printStackTrace()
            rawPhoneNumber.stripNonDigits()
        }

        return ContactEntity(
            id = id,
            name = name.trim(),
            phoneNumber = normalizedPhoneNumber,
            originalPhoneNumber = rawPhoneNumber,
            updatedAtApp = lastUpdatedAt
        )
    }
}
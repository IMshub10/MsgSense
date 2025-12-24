package com.summer.core.android.phone.data.mapper

import android.database.Cursor
import android.provider.ContactsContract
import com.summer.core.android.phone.data.entity.ContactEntity
import com.summer.core.util.stripNonDigits

object ContactMapper {
    fun mapCursorToContact(cursor: Cursor): ContactEntity? {
        val idIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone._ID)
        val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
        val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
        val lastUpdatedAtIndex =
            cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_LAST_UPDATED_TIMESTAMP)

        if (idIndex == -1 || numberIndex == -1 || nameIndex == -1 || lastUpdatedAtIndex == -1) return null // Prevent crashes if index not found

        val id = cursor.getLong(idIndex)
        val name = cursor.getString(nameIndex) ?: return null
        val phoneNumber = cursor.getString(numberIndex) ?: return null
        val lastUpdatedAt = cursor.getLong(lastUpdatedAtIndex)
        
        val normalizedPhoneNumber = phoneNumber.stripNonDigits().uppercase()

        return ContactEntity(
            id = id,
            name = name.trim(),
            phoneNumber = normalizedPhoneNumber, // Normalized for matching
            originalPhoneNumber = phoneNumber,
            updatedAtApp = lastUpdatedAt
        )
    }
}
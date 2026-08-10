package com.summer.notifai.ui.datamodel.mapper

import com.summer.core.data.local.entities.ContactEntity
import com.summer.notifai.ui.datamodel.NewContactDataModel

object NewContactMapper {
    fun ContactEntity.toNewContactDataModel(): NewContactDataModel {
        return NewContactDataModel(
            id = id,
            icon = com.summer.core.R.drawable.ic_contact_24x24,
            contactName = name,
            phoneNumber = phoneNumber
        )
    }
}
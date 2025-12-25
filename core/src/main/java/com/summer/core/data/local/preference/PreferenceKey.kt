package com.summer.core.data.local.preference

enum class PreferenceKey(val key: String) {
    USER_AGREEMENT("user_agreement"),
    DATA_SHARING("data_sharing"),
    SMS_PROCESSING_STATUS("sms_processing_status"), //TODO(Check if required as comparing android sms id to )
    PHONE_TABLE_LAST_UPDATED("phone_table_last_updated"),
    SAVE_DATA_IN_PUBLIC_DB("save_data_in_public_cb"),
    DEFAULT_SMS_PROMPT_LAST_SHOWN("default_sms_prompt_last_shown");

    companion object {
        const val DEFAULT_PREF = "notifai_prefs"
    }
}
package com.summer.core.android.sms.constants

/**
 * Global constants used throughout the app for paging, batching, UI timeouts, and SMS logic.
 */
object Constants {

    const val DEFAULT_REGION = "IN"

    /**
     * Batch size for processing SMS messages from the device in chunks.
     */
    const val BATCH_SIZE = 25

    /**
     * Number of contacts to load per page in the contact list screen.
     */
    const val CONTACT_LIST_PAGE_SIZE = 20

    /**
     * Number of SMS messages to load per page in the SMS inbox screen.
     */
    const val SMS_LIST_PAGE_SIZE = 40

    /**
     * Duration (in milliseconds) to show the floating date header while scrolling messages.
     */
    const val DATE_FLOATER_SHOW_TIME = 800L

    /**
     * Maximum allowed SMS parts before suggesting conversion to MMS or disabling send.
     * Used for soft-limiting long messages (as OEMs like Samsung often do).
     */
    const val SMS_PART_LIMIT = 6

    /**
     * Static ID for searched phone number
     */
    const val SEARCH_NEW_CONTACT_ID = -1000

    const val SEARCH_SECTION_MAX_COUNT = 3

    /**
     * WorkManager unique name for SMS processing tasks.
     * Used to manage the lifecycle of SMS processing jobs.
     */
    const val SMS_PROCESSING_WORK_NAME = "sms_processing_work"

    const val SMS_HIGHLIGHT_ANIMATION = 1500L
}
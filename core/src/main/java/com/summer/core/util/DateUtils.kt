package com.summer.core.util

import android.text.format.DateUtils as AndroidDateUtils
import java.text.SimpleDateFormat
import java.util.*

object DateUtils {

    private const val FORMAT_TIME_12_HOUR = "hh:mm a"
    private const val FORMAT_DAY_MONTH = "dd MMM"
    private const val FORMAT_FULL_DATE = "dd MMM, yy"
    private const val FORMAT_DAY_HEADER_SAME_YEAR = "EEEE, d MMMM"
    private const val FORMAT_DAY_HEADER_DIFF_YEAR = "EEEE, d MMMM yyyy"

    /**
     * Formats an epoch timestamp into a user-friendly string based on its relation to the current date:
     *
     * - If the timestamp is from today, returns the time in 12-hour format → "hh:mm a" (e.g., "08:30 AM")
     * - If it's from the same year but not today, returns → "dd MMMM" (e.g., "01 April")
     * - If it's from a different year, returns → "dd MMMM, yy" (e.g., "01 April, 22")
     *
     * @param timeInMillis Epoch timestamp in milliseconds.
     * @return A formatted string representing the date/time appropriately for display.
     */
    fun formatDisplayDateTime(timeInMillis: Long): String {
        val inputDate = Calendar.getInstance().apply { this.timeInMillis = timeInMillis }
        val currentDate = Calendar.getInstance()

        return if (inputDate.get(Calendar.YEAR) == currentDate.get(Calendar.YEAR) &&
            inputDate.get(Calendar.DAY_OF_YEAR) == currentDate.get(Calendar.DAY_OF_YEAR)
        ) {
            SimpleDateFormat(FORMAT_TIME_12_HOUR, Locale.getDefault()).format(Date(timeInMillis))
        } else if (inputDate.get(Calendar.YEAR) == currentDate.get(Calendar.YEAR)) {
            SimpleDateFormat(FORMAT_DAY_MONTH, Locale.getDefault()).format(Date(timeInMillis))
        } else {
            SimpleDateFormat(FORMAT_FULL_DATE, Locale.getDefault()).format(Date(timeInMillis))
        }
    }

    /**
     * Converts epoch time to "hh:mm a" (e.g., "08:30 AM") in device locale.
     *
     * @param timeInMillis Epoch time
     * @return Time string
     */
    fun formatTimeOnly(timeInMillis: Long): String {
        return SimpleDateFormat(FORMAT_TIME_12_HOUR, Locale.getDefault()).format(Date(timeInMillis))
    }

    /**
     * Returns a localized day header string for grouping:
     * - "Today", "Yesterday" if applicable
     * - Else → "Tuesday, 2 April" or "Tuesday, 2 April 2023"
     *
     * @param dateInEpoch Epoch millis
     * @return Formatted label string
     */
    fun formatDayHeader(dateInEpoch: Long): String {
        val now = System.currentTimeMillis()

        val isToday = AndroidDateUtils.isToday(dateInEpoch)
        val isYesterday = AndroidDateUtils.isToday(dateInEpoch + AndroidDateUtils.DAY_IN_MILLIS)

        return when {
            isToday || isYesterday -> AndroidDateUtils.getRelativeTimeSpanString(
                dateInEpoch,
                now,
                AndroidDateUtils.DAY_IN_MILLIS,
                AndroidDateUtils.FORMAT_ABBREV_RELATIVE
            ).toString()

            else -> {
                val smsCal = Calendar.getInstance().apply { timeInMillis = dateInEpoch }
                val thisYear =
                    Calendar.getInstance().get(Calendar.YEAR) == smsCal.get(Calendar.YEAR)
                val pattern =
                    if (thisYear) FORMAT_DAY_HEADER_SAME_YEAR else FORMAT_DAY_HEADER_DIFF_YEAR
                SimpleDateFormat(pattern, Locale.getDefault()).format(Date(dateInEpoch))
            }
        }
    }
}
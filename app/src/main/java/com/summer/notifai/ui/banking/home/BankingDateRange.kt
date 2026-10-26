package com.summer.notifai.ui.banking.home

import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

internal object BankingDateRange {
    /** MaterialDatePicker encodes calendar dates at UTC midnight, regardless of device zone. */
    fun fromPicker(start: Long, endInclusive: Long, zone: ZoneId = ZoneId.systemDefault()): Pair<Long, Long> {
        val firstDay = Instant.ofEpochMilli(start).atZone(ZoneOffset.UTC).toLocalDate()
        val lastDay = Instant.ofEpochMilli(endInclusive).atZone(ZoneOffset.UTC).toLocalDate()
        require(!lastDay.isBefore(firstDay)) { "End date must not precede start date" }
        return firstDay.atStartOfDay(zone).toInstant().toEpochMilli() to
            lastDay.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    }
}

package com.summer.notifai.ui.banking.home

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.*
import org.junit.Test

class BankingDateRangeTest {
    @Test
    fun includesWholeSelectedDayInUtcIndiaAndAmerica() {
        val date = LocalDate.of(2026, 10, 26)
        listOf("UTC", "Asia/Kolkata", "America/Los_Angeles").forEach { name ->
            val zone = ZoneId.of(name)
            val (start, end) = BankingDateRange.fromPicker(picker(date), picker(date), zone)
            assertEquals(date.atStartOfDay(zone).toInstant().toEpochMilli(), start)
            assertTrue(date.atTime(23, 59, 59).atZone(zone).toInstant().toEpochMilli() in start until end)
            assertEquals(date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(), end)
            assertFalse(end in start until end)
        }
    }

    @Test
    fun usesCalendarDaysAcrossSpringAndAutumnDstTransitions() {
        val zone = ZoneId.of("America/New_York")
        listOf(LocalDate.of(2026, 3, 8) to 23L, LocalDate.of(2026, 11, 1) to 25L).forEach { (day, hours) ->
            val (start, end) = BankingDateRange.fromPicker(picker(day), picker(day), zone)
            assertEquals(hours * 60 * 60 * 1000, end - start)
        }
    }

    @Test
    fun includesFinalDayOfMultiDayRange() {
        val zone = ZoneId.of("Asia/Kolkata")
        val first = LocalDate.of(2026, 10, 1)
        val last = LocalDate.of(2026, 10, 26)
        val (start, end) = BankingDateRange.fromPicker(picker(first), picker(last), zone)
        assertTrue(last.atTime(23, 59).atZone(zone).toInstant().toEpochMilli() in start until end)
        assertFalse(first.minusDays(1).atTime(23, 59).atZone(zone).toInstant().toEpochMilli() in start until end)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsReversedRange() {
        BankingDateRange.fromPicker(picker(LocalDate.of(2026, 10, 27)), picker(LocalDate.of(2026, 10, 26)))
    }

    private fun picker(date: LocalDate) = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
}

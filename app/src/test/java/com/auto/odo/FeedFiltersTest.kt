package com.auto.odo

import com.auto.odo.domain.usecase.LogItem
import com.auto.odo.presentation.viewmodel.FeedFilters
import com.auto.odo.presentation.viewmodel.FeedSort
import com.auto.odo.presentation.viewmodel.FeedType
import com.auto.odo.presentation.viewmodel.applyFeedFilters
import com.auto.odo.presentation.viewmodel.monthKey
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class FeedFiltersTest {
    private fun day(year: Int, month: Int, d: Int) =
        Calendar.getInstance().apply { clear(); set(year, month - 1, d, 12, 0) }.timeInMillis

    private val fuel = LogItem.Fuel(1, 1, day(2026, 9, 5), 1000.0, 10.0, 100.0, 1000.0, false, "HP Andheri", null, null)
    private val service = LogItem.Service(2, 1, day(2026, 9, 20), 1200.0, "Oil change", 2500.0, null)
    private val expense = LogItem.Expense(3, 1, day(2026, 8, 1), "Toll", 150.0, "Expressway")
    private val trip = LogItem.Trip(4, 1, day(2026, 9, 25), 1200.0, 1350.0, "Personal", null, startPlace = "Andheri", endPlace = "Pune")
    private val all = listOf(fuel, service, expense, trip)

    @Test
    fun monthFilterKeepsOnlyThatMonthNewestFirst() {
        val result = applyFeedFilters(all, FeedFilters(month = monthKey(day(2026, 9, 1))))
        assertEquals(listOf(trip, service, fuel), result)
    }

    @Test
    fun costRangeAndSort() {
        val result = applyFeedFilters(all, FeedFilters(minCost = 200.0, maxCost = 2000.0))
        assertEquals(listOf(fuel), result)
        assertEquals(listOf(service, fuel, expense, trip), applyFeedFilters(all, FeedFilters(sort = FeedSort.COST_HIGH)))
    }

    @Test
    fun searchMatchesStationsPlacesAndNotesIgnoringCase() {
        assertEquals(listOf(trip, fuel), applyFeedFilters(all, FeedFilters(query = "andheri")))
        assertEquals(listOf(expense), applyFeedFilters(all, FeedFilters(query = "EXPRESS")))
    }

    @Test
    fun typePagesSplitTheFilteredList() {
        assertEquals(listOf(trip), all.filter(FeedType.TRIP::matches))
        assertEquals(4, all.count(FeedType.ALL::matches))
    }
}

package com.jcheol.commutestop.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class BusArrivalSortingTest {
    @Test
    fun `sorts by seconds then numeric route name`() {
        val arrivals = listOf(
            arrival(routeName = "6003", seconds = 300),
            arrival(routeName = "310", seconds = 90),
            arrival(routeName = "55", seconds = 90),
        )

        assertEquals(
            listOf("55", "310", "6003"),
            arrivals.sortedBySoonest().map(BusArrival::routeName),
        )
    }

    @Test
    fun `keeps selected metropolitan routes and excludes unselected routes`() {
        val arrivals = listOf(
            arrival(routeName = "55", seconds = 90, routeTypeCode = 13),
            arrival(routeName = "310", seconds = 30, routeTypeCode = 13),
            arrival(routeName = "100-1", seconds = 60, routeTypeCode = 13),
            arrival(routeName = "6003", seconds = 10, routeTypeCode = 51),
            arrival(routeName = "4000", seconds = 20),
            arrival(routeName = "6011", seconds = 40),
            arrival(routeName = "M4102", seconds = 50),
            arrival(routeName = "990", seconds = 70, routeTypeCode = 11),
        )

        assertEquals(
            listOf("6003", "6011", "M4102", "55"),
            arrivals.forSelectedRoutes(setOf("55", "6003", "6011", "M4102"))
                .map(BusArrival::routeName),
        )
    }

    private fun arrival(
        routeName: String,
        seconds: Int,
        routeTypeCode: Int? = null,
    ) = BusArrival(
        routeId = routeName,
        routeName = routeName,
        destination = "",
        routeTypeCode = routeTypeCode,
        status = "PASS",
        arrivalSeconds = seconds,
        stopsAway = null,
        currentStationName = null,
        nextArrivalSeconds = null,
        nextStopsAway = null,
        vehicleTypeCode = null,
        remainingSeats = null,
        crowdednessCode = null,
    )
}

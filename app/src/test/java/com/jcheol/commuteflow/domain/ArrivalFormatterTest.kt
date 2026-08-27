package com.jcheol.commuteflow.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ArrivalFormatterTest {
    @Test
    fun `formats imminent minute and hour arrivals`() {
        assertEquals("곧 도착", formatArrivalTime(45))
        assertEquals("2분 후 도착", formatArrivalTime(125))
        assertEquals("1시간 후 도착", formatArrivalTime(3_600))
        assertEquals("1시간 5분 후 도착", formatArrivalTime(3_900))
    }

    @Test
    fun `formats stop distance`() {
        assertEquals("정류소 진입", formatStopsAway(0))
        assertEquals("1정거장 전", formatStopsAway(1))
        assertEquals("4정거장 전", formatStopsAway(4))
        assertEquals(null, formatStopsAway(null))
    }

}

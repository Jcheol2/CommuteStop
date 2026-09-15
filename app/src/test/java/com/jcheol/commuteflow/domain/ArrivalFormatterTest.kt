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

    @Test
    fun `uses subway position when arrival seconds are unavailable`() {
        assertEquals("2번째 전역", formatSubwayArrivalStatus(0, "[2]번째 전역 (강남)"))
        assertEquals("6번째 전역", formatSubwayArrivalStatus(0, "[6]번째 전역 (판교)"))
        assertEquals("1번째 전역", formatSubwayArrivalStatus(0, "전역 진입"))
        assertEquals("1번째 전역", formatSubwayArrivalStatus(0, "전역 도착"))
        assertEquals("1번째 전역", formatSubwayArrivalStatus(0, "전역 출발"))
        assertEquals("2번째 전역", formatSubwayArrivalStatus(60, "[2]번째 전역 (강남)"))
        assertEquals("4분 후 도착", formatSubwayArrivalStatus(240, "4분 후 (고속터미널)"))
    }

    @Test
    fun `uses countdown with scheduled time message`() {
        assertEquals(
            "5분 후 도착",
            formatSubwayArrivalStatus(341, "오후 3:39 예정"),
        )
    }

}

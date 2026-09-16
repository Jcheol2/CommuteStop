package com.jcheol.commutestop.data

import com.jcheol.commutestop.domain.SubwayTransitTarget
import java.util.Calendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.test.runTest

class ShinbundangTimetableTest {
    private val timetable = ShinbundangTimetable()

    @Test
    fun `returns official weekday Gwanggyo schedule at Nonhyeon`() {
        val arrivals = timetable.nextArrivals(
            target = target(directionId = "DOWN", directionLabel = "하행"),
            nowMillis = kstMillis(2026, 9, 15, 15, 33, 19),
        )

        assertEquals(listOf(341, 881), arrivals.map { it.arrivalSeconds })
        assertEquals(listOf("광교", "광교"), arrivals.map { it.destination })
        assertEquals("오후 3:39 예정", arrivals.first().message)
    }

    @Test
    fun `returns official weekday Sinsa schedule at Nonhyeon`() {
        val arrivals = timetable.nextArrivals(
            target = target(directionId = "UP", directionLabel = "상행"),
            nowMillis = kstMillis(2026, 9, 15, 15, 33, 19),
        )

        assertEquals(401, arrivals.first().arrivalSeconds)
        assertEquals("신사", arrivals.first().destination)
        assertEquals("오후 3:40 예정", arrivals.first().message)
    }

    @Test
    fun `uses weekend schedule on Saturday and weekday public holiday`() {
        val saturday = timetable.nextArrivals(
            target = target(directionId = "DOWN", directionLabel = "하행"),
            nowMillis = kstMillis(2026, 9, 19, 15, 33, 19),
        )
        val childrensDay = timetable.nextArrivals(
            target = target(directionId = "DOWN", directionLabel = "하행"),
            nowMillis = kstMillis(2026, 5, 5, 15, 33, 19),
        )

        assertEquals(101, saturday.first().arrivalSeconds)
        assertEquals(101, childrensDay.first().arrivalSeconds)
        assertEquals("오후 3:35 예정", saturday.first().message)
    }

    @Test
    fun `keeps after-midnight service on previous weekday and preserves short turn`() {
        val arrivals = timetable.nextArrivals(
            target = target(directionId = "DOWN", directionLabel = "하행"),
            nowMillis = kstMillis(2026, 9, 19, 0, 10, 0),
        )

        assertEquals(60, arrivals.first().arrivalSeconds)
        assertEquals("정자", arrivals.first().destination)
        assertEquals("오전 12:11 예정", arrivals.first().message)
    }

    @Test
    fun `returns no schedule for an unsupported station`() {
        val arrivals = timetable.nextArrivals(
            target = target(stationName = "없는역", directionId = "DOWN", directionLabel = "하행"),
            nowMillis = kstMillis(2026, 9, 15, 15, 33, 19),
        )

        assertTrue(arrivals.isEmpty())
    }

    @Test
    fun `repository uses bundled schedule without calling the zero ETA API`() = runTest {
        val nowMillis = kstMillis(2026, 9, 15, 15, 33, 19)
        val repository = DefaultTransitRepository(
            gbisClient = GbisApiClient(""),
            seoulBusClient = SeoulBusApiClient(""),
            subwayClient = SeoulSubwayApiClient(""),
            currentTimeMillis = { nowMillis },
        )

        val snapshot = repository.arrivalsFor(
            target(directionId = "DOWN", directionLabel = "하행"),
        ) as TransitArrivalSnapshot.Subway

        assertEquals(341, snapshot.arrivals.first().arrivalSeconds)
        assertEquals("광교", snapshot.arrivals.first().destination)
    }

    private fun target(
        stationName: String = "논현역",
        directionId: String,
        directionLabel: String,
    ) = SubwayTransitTarget(
        id = "test-target",
        stationId = "4305",
        stationName = stationName,
        lineId = "신분당선",
        lineName = "신분당선",
        directionId = directionId,
        directionLabel = directionLabel,
    )

    private fun kstMillis(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
        second: Int,
    ): Long = Calendar.getInstance(SEOUL_TIME_ZONE).run {
        clear()
        set(year, month - 1, day, hour, minute, second)
        timeInMillis
    }

    private companion object {
        val SEOUL_TIME_ZONE: TimeZone = TimeZone.getTimeZone("Asia/Seoul")
    }
}
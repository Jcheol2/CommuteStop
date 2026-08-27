package com.jcheol.commuteflow.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CommuteRulesTest {
    @Test
    fun `defaults use noon cutoff and profile-specific themes`() {
        val settings = CommuteSettings()

        assertEquals(720, settings.cutoffMinute)
        assertEquals(ThemePreference.DARK, settings.morning.theme)
        assertEquals(ThemePreference.LIGHT, settings.evening.theme)
        assertEquals(emptyList<TransitTarget>(), settings.morning.targets)
        assertEquals(emptyList<TransitTarget>(), settings.evening.targets)
    }

    @Test
    fun `cutoff selects morning only before the boundary`() {
        val settings = CommuteSettings(cutoffMinute = 720)

        assertEquals(CommuteProfile.MORNING, settings.activeProfileAt(719))
        assertEquals(CommuteProfile.EVENING, settings.activeProfileAt(720))
        assertEquals(CommuteProfile.EVENING, settings.activeProfileAt(1_439))
    }

    @Test
    fun `save replaces in place and rejects a fourth target`() {
        val first = busTarget("first")
        val second = busTarget("second")
        val third = subwayTarget("third")
        val full = CommuteSettings()
            .withSavedTarget(CommuteProfile.MORNING, first)
            .withSavedTarget(CommuteProfile.MORNING, second)
            .withSavedTarget(CommuteProfile.MORNING, third)

        val replaced = full.withSavedTarget(
            CommuteProfile.MORNING,
            first.copy(stationName = "수정된 정류소"),
        )

        assertEquals(listOf("first", "second", "third"), replaced.morning.targets.map { it.id })
        assertEquals("수정된 정류소", replaced.morning.targets.first().stationName)
        assertThrows(IllegalArgumentException::class.java) {
            replaced.withSavedTarget(CommuteProfile.MORNING, busTarget("fourth"))
        }
        assertEquals(emptyList<TransitTarget>(), replaced.evening.targets)
    }

    @Test
    fun `remove and reorder preserve target identity`() {
        val settings = CommuteSettings()
            .withSavedTarget(CommuteProfile.EVENING, busTarget("a"))
            .withSavedTarget(CommuteProfile.EVENING, busTarget("b"))
            .withSavedTarget(CommuteProfile.EVENING, subwayTarget("c"))
            .withReorderedTarget(CommuteProfile.EVENING, fromIndex = 0, toIndex = 2)
            .withoutTarget(CommuteProfile.EVENING, targetId = "c")

        assertEquals(listOf("b", "a"), settings.evening.targets.map { it.id })
    }

    @Test
    fun `target constructors enforce route and direction invariants`() {
        assertThrows(IllegalArgumentException::class.java) {
            BusTransitTarget(
                id = "bus",
                provider = TransitProvider.GYEONGGI_BUS,
                stationId = "station",
                stationName = "정류소",
                routes = emptyList(),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            BusTransitTarget(
                id = "bus",
                provider = TransitProvider.GYEONGGI_BUS,
                stationId = "station",
                stationName = "정류소",
                routes = listOf(
                    BusRouteRef(routeId = "route", routeName = "1"),
                    BusRouteRef(routeId = "route", routeName = "1 duplicate"),
                ),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            subwayTarget("subway").copy(directionId = " ")
        }
        assertEquals(TransitProvider.SEOUL_SUBWAY, subwayTarget("subway").provider)
    }

    private fun busTarget(id: String) = BusTransitTarget(
        id = id,
        provider = TransitProvider.GYEONGGI_BUS,
        stationId = "station-$id",
        stationName = "정류소 $id",
        stationNumber = "07521",
        regionName = "성남시",
        routes = listOf(BusRouteRef(routeId = "route-$id", routeName = "55")),
    )

    private fun subwayTarget(id: String) = SubwayTransitTarget(
        id = id,
        stationId = "station-$id",
        stationName = "역 $id",
        lineId = "line-2",
        lineName = "2호선",
        directionId = "inner",
        directionLabel = "내선순환",
    )
}
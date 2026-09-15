package com.jcheol.commuteflow.data

import com.jcheol.commuteflow.domain.BusArrival
import com.jcheol.commuteflow.domain.BusRouteOption
import com.jcheol.commuteflow.domain.BusTransitTarget
import com.jcheol.commuteflow.domain.SubwayArrival
import com.jcheol.commuteflow.domain.SubwayTransitTarget
import com.jcheol.commuteflow.domain.TransitProvider
import com.jcheol.commuteflow.domain.TransitSearchResult
import com.jcheol.commuteflow.domain.TransitTarget
import com.jcheol.commuteflow.domain.forSelectedRoutes
import com.jcheol.commuteflow.domain.sortedBySoonest

sealed interface TransitArrivalSnapshot {
    data class Bus(val arrivals: List<BusArrival>) : TransitArrivalSnapshot
    data class Subway(val arrivals: List<SubwayArrival>) : TransitArrivalSnapshot
}

interface TransitRepository {
    suspend fun search(provider: TransitProvider, query: String): List<TransitSearchResult>
    suspend fun routesAt(provider: TransitProvider, stationId: String): List<BusRouteOption>
    suspend fun arrivalsFor(target: TransitTarget): TransitArrivalSnapshot
}

class DefaultTransitRepository(
    private val gbisClient: GbisApiClient,
    private val seoulBusClient: SeoulBusApiClient,
    private val subwayClient: SeoulSubwayApiClient,
    private val shinbundangTimetable: ShinbundangTimetable = ShinbundangTimetable(),
    private val currentTimeMillis: () -> Long = System::currentTimeMillis,
) : TransitRepository {
    override suspend fun search(
        provider: TransitProvider,
        query: String,
    ): List<TransitSearchResult> = when (provider) {
        TransitProvider.GYEONGGI_BUS -> gbisClient.searchStations(query)
        TransitProvider.SEOUL_BUS -> seoulBusClient.searchStations(query)
        TransitProvider.SEOUL_SUBWAY -> subwayClient.searchStations(query)
    }

    override suspend fun routesAt(
        provider: TransitProvider,
        stationId: String,
    ): List<BusRouteOption> = when (provider) {
        TransitProvider.GYEONGGI_BUS -> gbisClient.getRoutes(stationId)
        TransitProvider.SEOUL_BUS -> seoulBusClient.getRoutes(stationId)
        TransitProvider.SEOUL_SUBWAY -> emptyList()
    }.sortedWith(compareBy<BusRouteOption> { it.routeName.routeNumber() }.thenBy { it.routeName })

    override suspend fun arrivalsFor(target: TransitTarget): TransitArrivalSnapshot = when (target) {
        is BusTransitTarget -> {
            val selectedIds = target.routes.mapTo(hashSetOf()) { it.routeId }
            val arrivals = when (target.provider) {
                TransitProvider.GYEONGGI_BUS -> gbisClient.getArrivals(target.stationId)
                TransitProvider.SEOUL_BUS -> seoulBusClient.getArrivals(target.stationId)
                TransitProvider.SEOUL_SUBWAY -> emptyList()
            }.forSelectedRoutes(selectedIds)
            TransitArrivalSnapshot.Bus(arrivals)
        }

        is SubwayTransitTarget -> {
            val scheduledArrivals = shinbundangTimetable.nextArrivals(
                target = target,
                nowMillis = currentTimeMillis(),
            )
            val arrivals = if (scheduledArrivals.isNotEmpty()) {
                scheduledArrivals
            } else {
                subwayClient.getArrivals(
                    stationName = target.stationName,
                    lineId = target.lineId,
                    lineName = target.lineName,
                    directionId = target.directionId,
                )
            }
            TransitArrivalSnapshot.Subway(arrivals)
        }
    }

    private fun String.routeNumber(): Int = filter(Char::isDigit).toIntOrNull() ?: Int.MAX_VALUE
}

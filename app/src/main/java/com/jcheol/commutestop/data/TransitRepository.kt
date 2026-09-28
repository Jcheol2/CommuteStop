package com.jcheol.commutestop.data

import com.jcheol.commutestop.domain.BusArrival
import com.jcheol.commutestop.domain.BusRouteOption
import com.jcheol.commutestop.domain.BusTransitTarget
import com.jcheol.commutestop.domain.SubwayArrival
import com.jcheol.commutestop.domain.SubwayTransitTarget
import com.jcheol.commutestop.domain.TransitProvider
import com.jcheol.commutestop.domain.TransitSearchResult
import com.jcheol.commutestop.domain.TransitTarget
import com.jcheol.commutestop.domain.forSelectedRoutes
import com.jcheol.commutestop.domain.sortedBySoonest
import kotlinx.coroutines.CancellationException

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
                TransitProvider.GYEONGGI_BUS ->
                    gbisClient.getArrivals(target.stationId).forSelectedRoutes(selectedIds)

                TransitProvider.SEOUL_BUS ->
                    getSeoulBusArrivalsWithGbisSeats(target.stationId, selectedIds)

                TransitProvider.SEOUL_SUBWAY -> emptyList()
            }
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

    private fun getSeoulBusArrivalsWithGbisSeats(
        arsId: String,
        selectedRouteIds: Set<String>,
    ): List<BusArrival> {
        val arrivals = seoulBusClient.getArrivals(arsId).forSelectedRoutes(selectedRouteIds)
        val gbisStationId = arrivals.firstNotNullOfOrNull { arrival ->
            if (
                arrival.routeTypeCode == SEOUL_GYEONGGI_ROUTE_TYPE &&
                    arrival.remainingSeats == null
            ) {
                arrival.stationId
            } else {
                null
            }
        } ?: return arrivals
        val gbisArrivals = try {
            gbisClient.getArrivals(
                stationId = gbisStationId,
                connectTimeoutMillis = SEAT_ENRICHMENT_CONNECT_TIMEOUT_MILLIS,
                readTimeoutMillis = SEAT_ENRICHMENT_READ_TIMEOUT_MILLIS,
            )
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            return arrivals
        }
        return arrivals.mergeRemainingSeatsFrom(gbisArrivals)
    }

    private fun String.routeNumber(): Int = filter(Char::isDigit).toIntOrNull() ?: Int.MAX_VALUE

    private companion object {
        const val SEOUL_GYEONGGI_ROUTE_TYPE = 8
        const val SEAT_ENRICHMENT_CONNECT_TIMEOUT_MILLIS = 2_000
        const val SEAT_ENRICHMENT_READ_TIMEOUT_MILLIS = 3_000
    }
}

internal fun List<BusArrival>.mergeRemainingSeatsFrom(
    seatArrivals: List<BusArrival>,
): List<BusArrival> {
    val seatsByVehicle = seatArrivals
        .filter { it.vehicleId != null && it.remainingSeats != null }
        .associateBy { it.routeId to it.vehicleId }
    return map { arrival ->
        if (arrival.remainingSeats != null || arrival.vehicleId == null) {
            arrival
        } else {
            val remainingSeats = seatsByVehicle[arrival.routeId to arrival.vehicleId]
                ?.remainingSeats
            if (remainingSeats == null) arrival else arrival.copy(remainingSeats = remainingSeats)
        }
    }
}

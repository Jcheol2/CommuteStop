package com.jcheol.commuteflow.domain

data class BusArrival(
    val routeId: String,
    val routeName: String,
    val destination: String,
    val routeTypeCode: Int?,
    val status: String,
    val arrivalSeconds: Int,
    val stopsAway: Int?,
    val currentStationName: String?,
    val nextArrivalSeconds: Int?,
    val nextStopsAway: Int?,
    val vehicleTypeCode: Int?,
    val remainingSeats: Int?,
    val crowdednessCode: Int?,
)

fun List<BusArrival>.sortedBySoonest(): List<BusArrival> = sortedWith(
    compareBy<BusArrival> { it.arrivalSeconds }
        .thenBy { arrival -> arrival.routeName.toRouteSortKey() }
        .thenBy { it.routeName },
)

fun List<BusArrival>.forSelectedRoutes(routeIds: Set<String>): List<BusArrival> =
    filter { arrival -> arrival.routeId in routeIds }.sortedBySoonest()

private fun String.toRouteSortKey(): Int = filter(Char::isDigit).toIntOrNull() ?: Int.MAX_VALUE

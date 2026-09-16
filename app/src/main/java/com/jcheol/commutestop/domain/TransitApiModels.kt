package com.jcheol.commutestop.domain

data class TransitSearchResult(
    val provider: TransitProvider,
    val stationId: String,
    val stationName: String,
    val stationNumber: String? = null,
    val detail: String? = null,
    val lineId: String? = null,
    val lineName: String? = null,
)

data class BusRouteOption(
    val routeId: String,
    val routeName: String,
    val destinationName: String? = null,
    val routeTypeCode: Int? = null,
)

data class SubwayArrival(
    val lineId: String,
    val lineName: String,
    val direction: String,
    val destination: String,
    val arrivalSeconds: Int,
    val message: String,
)
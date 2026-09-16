package com.jcheol.commutestop.data

import com.jcheol.commutestop.domain.BusArrival
import com.jcheol.commutestop.domain.BusRouteOption
import com.jcheol.commutestop.domain.TransitSearchResult
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class MissingApiKeyException : IllegalStateException()

class GbisApiException(
    val userMessage: String,
) : IOException(userMessage)

class GbisApiClient(
    private val serviceKey: String,
) {
    fun getArrivals(stationId: String): List<BusArrival> {
        val response = request(
            endpoint = ARRIVAL_ENDPOINT,
            query = "stationId=${stationId.encode()}",
            parser = GbisXmlParser::parse,
        )
        return when (response.resultCode) {
            RESULT_SUCCESS -> response.arrivals
            RESULT_NOT_FOUND -> emptyList()
            else -> throw GbisApiException(response.toUserMessage())
        }
    }

    fun searchStations(keyword: String): List<TransitSearchResult> {
        val response = request(
            endpoint = STATION_SEARCH_ENDPOINT,
            query = "keyword=${keyword.encode()}",
            parser = GbisXmlParser::parseStations,
        )
        return when (response.resultCode) {
            RESULT_SUCCESS -> response.stations
            RESULT_NOT_FOUND -> emptyList()
            else -> throw GbisApiException(response.toUserMessage())
        }
    }

    fun getRoutes(stationId: String): List<BusRouteOption> {
        val response = request(
            endpoint = STATION_ROUTE_ENDPOINT,
            query = "stationId=${stationId.encode()}",
            parser = GbisXmlParser::parseRoutes,
        )
        return when (response.resultCode) {
            RESULT_SUCCESS -> response.routes
            RESULT_NOT_FOUND -> emptyList()
            else -> throw GbisApiException(response.toUserMessage())
        }
    }

    private fun <T> request(
        endpoint: String,
        query: String,
        parser: (java.io.InputStream) -> T,
    ): T {
        if (serviceKey.isBlank()) throw MissingApiKeyException()

        val encodedKey = serviceKey.encode()
        val requestUrl = URL(
            "$endpoint?serviceKey=$encodedKey&$query&format=xml",
        )
        val connection = requestUrl.openConnection() as HttpURLConnection

        return try {
            connection.requestMethod = "GET"
            connection.connectTimeout = CONNECT_TIMEOUT_MILLIS
            connection.readTimeout = READ_TIMEOUT_MILLIS
            connection.useCaches = false
            connection.setRequestProperty("Accept", "application/xml")

            val statusCode = connection.responseCode
            if (statusCode !in 200..299) {
                throw GbisApiException(
                    when (statusCode) {
                        HttpURLConnection.HTTP_UNAUTHORIZED,
                        HttpURLConnection.HTTP_FORBIDDEN,
                        -> "API 인증키를 확인해 주세요."

                        in 500..599 -> "버스 정보 서버가 잠시 응답하지 않습니다."
                        else -> "버스 정보를 불러오지 못했습니다. (HTTP $statusCode)"
                    },
                )
            }

            connection.inputStream.buffered().use(parser)
        } finally {
            connection.disconnect()
        }
    }

    private fun String.encode(): String = URLEncoder.encode(this, "UTF-8")

    private fun ApiResult.toUserMessage(): String = when (resultCode) {
        20 -> "버스도착정보 API 활용 신청 상태를 확인해 주세요."
        21 -> "API 인증키가 일시 정지된 상태입니다."
        22 -> "오늘의 API 요청 한도를 초과했습니다."
        30 -> "등록되지 않은 API 인증키입니다."
        31 -> "API 인증키 사용 기한이 만료되었습니다."
        32 -> "등록되지 않은 IP에서 요청했습니다."
        1 -> "버스 정보 서버에서 오류가 발생했습니다."
        2 -> "버스 정보 요청값을 확인해 주세요."
        else -> resultMessage.ifBlank { "버스 정보를 불러오지 못했습니다." }
    }

    private companion object {
        const val ARRIVAL_ENDPOINT =
            "https://apis.data.go.kr/6410000/busarrivalservice/v2/getBusArrivalListv2"
        const val STATION_SEARCH_ENDPOINT =
            "https://apis.data.go.kr/6410000/busstationservice/v2/getBusStationListv2"
        const val STATION_ROUTE_ENDPOINT =
            "https://apis.data.go.kr/6410000/busstationservice/v2/getBusStationViaRouteListv2"
        const val CONNECT_TIMEOUT_MILLIS = 8_000
        const val READ_TIMEOUT_MILLIS = 10_000
        const val RESULT_SUCCESS = 0
        const val RESULT_NOT_FOUND = 4
    }
}

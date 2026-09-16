package com.jcheol.commutestop.data

import com.jcheol.commutestop.domain.BusArrival
import com.jcheol.commutestop.domain.BusRouteOption
import com.jcheol.commutestop.domain.TransitProvider
import com.jcheol.commutestop.domain.TransitSearchResult
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Document
import org.w3c.dom.Element

class SeoulBusApiClient(
    proxyBaseUrl: String,
) {
    private val baseUrl = proxyBaseUrl.trim().trimEnd('/')

    fun searchStations(keyword: String): List<TransitSearchResult> {
        val requestUrl =
            "$baseUrl/seoul-bus/station-search?query=${keyword.trim().encodeQuery()}"
        return request(requestUrl, SeoulBusXmlParser::parseStations)
    }

    fun getRoutes(arsId: String): List<BusRouteOption> {
        val requestUrl = "$baseUrl/seoul-bus/routes?arsId=${arsId.encodeQuery()}"
        return request(requestUrl, SeoulBusXmlParser::parseRoutes)
    }

    fun getArrivals(arsId: String): List<BusArrival> {
        val requestUrl = "$baseUrl/seoul-bus/station-arrivals?arsId=${arsId.encodeQuery()}"
        return request(requestUrl, SeoulBusXmlParser::parseArrivals)
    }

    private fun <T> request(
        requestUrl: String,
        parse: (InputStream) -> T,
    ): T {
        ensureSecureProxy()
        val connection = URL(requestUrl).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "GET"
            connection.connectTimeout = CONNECT_TIMEOUT_MILLIS
            connection.readTimeout = READ_TIMEOUT_MILLIS
            connection.useCaches = false
            connection.setRequestProperty("Accept", "application/xml")
            if (connection.responseCode !in 200..299) {
                throw TransitApiException(
                    "서울 버스 서버가 응답하지 않습니다. (HTTP ${connection.responseCode})",
                )
            }
            connection.inputStream.buffered().use(parse)
        } finally {
            connection.disconnect()
        }
    }

    private fun ensureSecureProxy() {
        if (baseUrl.isBlank()) {
            throw MissingProviderKeyException("서울 버스 HTTPS 중계", "SEOUL_TRANSIT_PROXY_URL")
        }
        if (!baseUrl.startsWith("https://")) {
            throw TransitApiException("SEOUL_TRANSIT_PROXY_URL은 HTTPS 주소여야 합니다.")
        }
    }

    private fun String.encodeQuery(): String = URLEncoder.encode(this, "UTF-8")

    private companion object {
        const val CONNECT_TIMEOUT_MILLIS = 8_000
        const val READ_TIMEOUT_MILLIS = 12_000
    }
}

internal object SeoulBusXmlParser {
    fun parseStations(input: InputStream): List<TransitSearchResult> {
        val document = input.toDocument()
        document.throwIfApiError()
        return document.items().mapNotNull { item ->
            val arsId = item.text("arsId")?.takeIf { value -> value.all(Char::isDigit) }
                ?: return@mapNotNull null
            val stationName = item.text("stNm") ?: return@mapNotNull null
            TransitSearchResult(
                provider = TransitProvider.SEOUL_BUS,
                stationId = arsId,
                stationName = stationName,
                stationNumber = arsId,
                detail = "서울",
            )
        }.distinctBy { it.stationId }
    }

    fun parseRoutes(input: InputStream): List<BusRouteOption> {
        val document = input.toDocument()
        document.throwIfApiError()
        return document.items().mapNotNull { item ->
            val routeId = item.text("busRouteId") ?: return@mapNotNull null
            val routeName = item.text("busRouteNm")
                ?: item.text("rtNm")
                ?: item.text("busRouteAbrv")
                ?: return@mapNotNull null
            BusRouteOption(
                routeId = routeId,
                routeName = routeName,
                destinationName = item.text("adirection"),
                routeTypeCode = item.int("routeType"),
            )
        }.distinctBy { it.routeId }
    }

    fun parseArrivals(input: InputStream): List<BusArrival> {
        val document = input.toDocument()
        document.throwIfApiError()
        return document.items().mapNotNull { item ->
            val routeId = item.text("busRouteId") ?: return@mapNotNull null
            val routeName = item.text("rtNm")
                ?: item.text("busRouteAbrv")
                ?: item.text("busRouteNm")
                ?: return@mapNotNull null
            val vehicleId = item.text("vehId1") ?: return@mapNotNull null
            if (vehicleId == "0") return@mapNotNull null
            val firstSeconds = item.int("traTime1")?.takeIf { it >= 0 }
                ?: item.int("arrmsgSec1")?.takeIf { it >= 0 }
                ?: return@mapNotNull null
            val stationOrder = item.int("staOrd")
            val firstSectionOrder = item.int("sectOrd1")
            val secondSectionOrder = item.int("sectOrd2")
            val secondVehicleId = item.text("vehId2")
            val secondSeconds = if (!secondVehicleId.isNullOrBlank() && secondVehicleId != "0") {
                item.int("traTime2")?.takeIf { it >= 0 }
                    ?: item.int("arrmsgSec2")?.takeIf { it >= 0 }
            } else {
                null
            }
            BusArrival(
                routeId = routeId,
                routeName = routeName,
                destination = item.text("adirection").orEmpty(),
                routeTypeCode = item.int("routeType"),
                status = item.text("arrmsg1") ?: "운행 중",
                arrivalSeconds = firstSeconds,
                stopsAway = stopsAway(stationOrder, firstSectionOrder),
                currentStationName = item.text("stationNm1"),
                nextArrivalSeconds = secondSeconds,
                nextStopsAway = stopsAway(stationOrder, secondSectionOrder),
                vehicleTypeCode = item.int("busType1"),
                remainingSeats = null,
                crowdednessCode = item.int("congetion1"),
            )
        }
    }

    private fun stopsAway(stationOrder: Int?, sectionOrder: Int?): Int? =
        if (stationOrder != null && sectionOrder != null) {
            (stationOrder - sectionOrder).coerceAtLeast(0)
        } else {
            null
        }

    private fun InputStream.toDocument(): Document = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = false
        isValidating = false
        runCatching { isXIncludeAware = false }
        runCatching { setExpandEntityReferences(false) }
        runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
        runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
    }.newDocumentBuilder().parse(this).also { document -> document.documentElement.normalize() }

    private fun Document.throwIfApiError() {
        val code = firstText("headerCd")
            ?: firstText("resultCode")
            ?: firstText("returnReasonCode")
            ?: "0"
        if (code == "0" || code == "00") return
        val message = firstText("headerMsg")
            ?: firstText("resultMsg")
            ?: firstText("returnAuthMsg")
            ?: firstText("errMsg")
            ?: "서울 버스 정보를 불러오지 못했습니다."
        throw TransitApiException(message)
    }

    private fun Document.items(): List<Element> {
        val nodes = getElementsByTagName("itemList")
        return buildList {
            for (index in 0 until nodes.length) {
                (nodes.item(index) as? Element)?.let(::add)
            }
        }
    }

    private fun Element.text(tagName: String): String? =
        getElementsByTagName(tagName).item(0)?.textContent?.trim()?.takeIf(String::isNotEmpty)

    private fun Element.int(tagName: String): Int? = text(tagName)?.toIntOrNull()

    private fun Document.firstText(tagName: String): String? =
        getElementsByTagName(tagName).item(0)?.textContent?.trim()?.takeIf(String::isNotEmpty)
}
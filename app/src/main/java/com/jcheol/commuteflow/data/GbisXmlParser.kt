package com.jcheol.commuteflow.data

import com.jcheol.commuteflow.domain.BusArrival
import com.jcheol.commuteflow.domain.BusRouteOption
import com.jcheol.commuteflow.domain.TransitProvider
import com.jcheol.commuteflow.domain.TransitSearchResult
import java.io.ByteArrayInputStream
import java.io.InputStream
import kotlin.text.Charsets.UTF_8
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Document
import org.w3c.dom.Element

internal interface ApiResult {
    val resultCode: Int
    val resultMessage: String
}

internal data class GbisResponse(
    override val resultCode: Int,
    override val resultMessage: String,
    val arrivals: List<BusArrival>,
) : ApiResult

internal data class GbisStationResponse(
    override val resultCode: Int,
    override val resultMessage: String,
    val stations: List<TransitSearchResult>,
) : ApiResult

internal data class GbisRouteResponse(
    override val resultCode: Int,
    override val resultMessage: String,
    val routes: List<BusRouteOption>,
) : ApiResult

internal object GbisXmlParser {
    fun parse(inputStream: InputStream): GbisResponse {
        val payload = inputStream.readBytes()
        payload.jsonResultHeaderOrNull()?.let { header ->
            return GbisResponse(header.first, header.second, emptyList())
        }
        val document = payload.toXmlDocument()

        val resultCode = document.firstText("resultCode")?.toIntOrNull()
            ?: document.firstText("returnReasonCode")?.toIntOrNull()
            ?: -1
        val resultMessage = document.firstText("resultMessage")
            ?: document.firstText("returnAuthMsg")
            ?: document.firstText("errMsg")
            ?: "알 수 없는 API 응답입니다."

        val nodes = document.getElementsByTagName("busArrivalList")
        val arrivals = buildList {
            for (index in 0 until nodes.length) {
                val element = nodes.item(index) as? Element ?: continue
                element.toBusArrival()?.let(::add)
            }
        }

        return GbisResponse(
            resultCode = resultCode,
            resultMessage = resultMessage,
            arrivals = arrivals,
        )
    }

    fun parseStations(inputStream: InputStream): GbisStationResponse {
        val payload = inputStream.readBytes()
        payload.jsonResultHeaderOrNull()?.let { header ->
            return GbisStationResponse(header.first, header.second, emptyList())
        }
        val document = payload.toXmlDocument()
        val header = document.resultHeader()
        val nodes = document.getElementsByTagName("busStationList")
        val stations = buildList {
            for (index in 0 until nodes.length) {
                val element = nodes.item(index) as? Element ?: continue
                val stationId = element.text("stationId").orEmpty()
                val stationName = element.text("stationName").orEmpty()
                if (stationId.isBlank() || stationName.isBlank()) continue
                add(
                    TransitSearchResult(
                        provider = TransitProvider.GYEONGGI_BUS,
                        stationId = stationId,
                        stationName = stationName,
                        stationNumber = element.text("mobileNo"),
                        detail = element.text("regionName"),
                    ),
                )
            }
        }
        return GbisStationResponse(header.first, header.second, stations)
    }

    fun parseRoutes(inputStream: InputStream): GbisRouteResponse {
        val payload = inputStream.readBytes()
        payload.jsonResultHeaderOrNull()?.let { header ->
            return GbisRouteResponse(header.first, header.second, emptyList())
        }
        val document = payload.toXmlDocument()
        val header = document.resultHeader()
        val nodes = document.getElementsByTagName("busRouteList")
        val routes = buildList {
            for (index in 0 until nodes.length) {
                val element = nodes.item(index) as? Element ?: continue
                val routeId = element.text("routeId").orEmpty()
                val routeName = element.text("routeName").orEmpty()
                if (routeId.isBlank() || routeName.isBlank()) continue
                add(
                    BusRouteOption(
                        routeId = routeId,
                        routeName = routeName,
                        destinationName = element.text("routeDestName"),
                        routeTypeCode = element.int("routeTypeCd"),
                    ),
                )
            }
        }
        return GbisRouteResponse(header.first, header.second, routes)
    }

    private fun Element.toBusArrival(): BusArrival? {
        val status = text("flag").orEmpty()
        if (status == "STOP") return null

        val routeName = text("routeName").orEmpty()
        val firstSeconds = int("predictTimeSec1")?.takeIf { it >= 0 }
            ?: int("predictTime1")?.takeIf { it >= 0 }?.times(60)
        if (routeName.isBlank() || firstSeconds == null) return null

        val secondSeconds = int("predictTimeSec2")?.takeIf { it >= 0 }
            ?: int("predictTime2")?.takeIf { it >= 0 }?.times(60)
        return BusArrival(
            routeId = text("routeId").orEmpty(),
            routeName = routeName,
            destination = text("routeDestName").orEmpty(),
            routeTypeCode = int("routeTypeCd"),
            status = status,
            arrivalSeconds = firstSeconds,
            stopsAway = int("locationNo1")?.takeIf { it >= 0 },
            currentStationName = text("stationNm1"),
            nextArrivalSeconds = secondSeconds,
            nextStopsAway = int("locationNo2")?.takeIf { it >= 0 },
            vehicleTypeCode = int("lowPlate1"),
            remainingSeats = int("remainSeatCnt1")?.takeIf { it >= 0 },
            crowdednessCode = int("crowded1"),
        )
    }

    private fun Element.text(tagName: String): String? =
        getElementsByTagName(tagName).item(0)?.textContent?.trim()?.takeIf(String::isNotEmpty)

    private fun Element.int(tagName: String): Int? = text(tagName)?.toIntOrNull()

    private fun Document.firstText(tagName: String): String? =
        getElementsByTagName(tagName).item(0)?.textContent?.trim()?.takeIf(String::isNotEmpty)

    private fun Document.resultHeader(): Pair<Int, String> = Pair(
        firstText("resultCode")?.toIntOrNull()
            ?: firstText("returnReasonCode")?.toIntOrNull()
            ?: -1,
        firstText("resultMessage")
            ?: firstText("returnAuthMsg")
            ?: firstText("errMsg")
            ?: "알 수 없는 API 응답입니다.",
    )

    private fun ByteArray.jsonResultHeaderOrNull(): Pair<Int, String>? {
        val body = toString(UTF_8).trimStart()
        if (!body.startsWith('{')) return null
        val resultCode = JSON_RESULT_CODE.find(body)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
            ?: -1
        val resultMessage = JSON_RESULT_MESSAGE.find(body)
            ?.groupValues
            ?.getOrNull(1)
            ?.takeIf(String::isNotBlank)
            ?: "알 수 없는 API 응답입니다."
        return resultCode to resultMessage
    }

    private fun ByteArray.toXmlDocument(): Document = secureDocumentBuilderFactory()
        .newDocumentBuilder()
        .parse(ByteArrayInputStream(this))
        .also { document -> document.documentElement.normalize() }

    private fun secureDocumentBuilderFactory(): DocumentBuilderFactory =
        DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            isValidating = false
            runCatching { isXIncludeAware = false }
            runCatching { setExpandEntityReferences(false) }
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        }

    private val JSON_RESULT_CODE = Regex("\"resultCode\"\\s*:\\s*\"?(-?\\d+)\"?")
    private val JSON_RESULT_MESSAGE = Regex("\"resultMessage\"\\s*:\\s*\"([^\"]*)\"")
}

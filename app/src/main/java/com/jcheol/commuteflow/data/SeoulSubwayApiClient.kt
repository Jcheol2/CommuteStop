package com.jcheol.commuteflow.data

import com.jcheol.commuteflow.domain.SubwayArrival
import com.jcheol.commuteflow.domain.TransitProvider
import com.jcheol.commuteflow.domain.TransitSearchResult
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Document
import org.w3c.dom.Element

class SeoulSubwayApiClient(
    proxyBaseUrl: String,
) {
    private val baseUrl = proxyBaseUrl.trim().trimEnd('/')

    fun searchStations(keyword: String): List<TransitSearchResult> {
        ensureSecureProxy()
        val requestUrl = "$baseUrl/station-search?query=${keyword.removeSuffix("역").encodeQuery()}"
        return request(requestUrl, ::parseStationRows)
    }

    fun getArrivals(
        stationName: String,
        lineId: String,
        lineName: String,
        directionId: String,
    ): List<SubwayArrival> {
        ensureSecureProxy()
        val requestUrl = "$baseUrl/arrivals?station=${stationName.removeSuffix("역").encodeQuery()}"
        return request(requestUrl) { document ->
            document.realtimeItems().mapNotNull { item ->
                val apiLineId = item.text("subwayId").orEmpty()
                val apiLineDescription = item.text("trainLineNm").orEmpty()
                val apiDirection = item.text("updnLine").orEmpty()
                if (!lineMatches(lineId, lineName, apiLineId, apiLineDescription)) {
                    return@mapNotNull null
                }
                if (!directionMatches(directionId, apiDirection)) return@mapNotNull null

                val seconds = item.int("barvlDt")?.coerceAtLeast(0) ?: return@mapNotNull null
                val destination = item.text("bstatnNm")
                    ?: apiLineDescription.substringBefore("행").takeIf(String::isNotBlank)
                    ?: "열차"
                SubwayArrival(
                    lineId = apiLineId,
                    lineName = lineName,
                    direction = apiDirection,
                    destination = destination,
                    arrivalSeconds = seconds,
                    message = item.text("arvlMsg2") ?: item.text("arvlMsg3").orEmpty(),
                )
            }.sortedBy { it.arrivalSeconds }
        }
    }

    private fun parseStationRows(document: Document): List<TransitSearchResult> =
        document.rows().mapNotNull { row ->
            val stationId = row.text("STATION_CD") ?: return@mapNotNull null
            val stationName = row.text("STATION_NM") ?: return@mapNotNull null
            val rawLine = row.text("LINE_NUM") ?: return@mapNotNull null
            TransitSearchResult(
                provider = TransitProvider.SEOUL_SUBWAY,
                stationId = stationId,
                stationName = stationName,
                detail = formatLineName(rawLine),
                lineId = rawLine,
                lineName = formatLineName(rawLine),
            )
        }.distinctBy { "${it.stationId}:${it.lineId}" }

    private fun <T> request(url: String, parse: (Document) -> T): T {
        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "GET"
            connection.connectTimeout = CONNECT_TIMEOUT_MILLIS
            connection.readTimeout = READ_TIMEOUT_MILLIS
            connection.useCaches = false
            connection.setRequestProperty("Accept", "application/xml")
            if (connection.responseCode !in 200..299) {
                throw TransitApiException("서울 지하철 서버가 응답하지 않습니다. (HTTP ${connection.responseCode})")
            }
            connection.inputStream.buffered().use { input ->
                val document = input.toSecureDocument()
                document.throwIfApiError()
                parse(document)
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun ensureSecureProxy() {
        if (baseUrl.isBlank()) {
            throw MissingProviderKeyException("서울 지하철 HTTPS 중계", "SEOUL_TRANSIT_PROXY_URL")
        }
        if (!baseUrl.startsWith("https://")) {
            throw TransitApiException("SEOUL_TRANSIT_PROXY_URL은 HTTPS 주소여야 합니다.")
        }
    }

    private fun lineMatches(
        savedLineId: String,
        savedLineName: String,
        apiLineId: String,
        apiDescription: String,
    ): Boolean {
        val expectedRealtimeId = savedLineId.toRealtimeLineId(savedLineName)
        return apiLineId == expectedRealtimeId ||
            normalizeLine(savedLineName) in normalizeLine(apiDescription)
    }

    private fun directionMatches(directionId: String, apiDirection: String): Boolean =
        when (directionId) {
            DIRECTION_UP -> apiDirection.contains("상행") || apiDirection.contains("내선")
            DIRECTION_DOWN -> apiDirection.contains("하행") || apiDirection.contains("외선")
            else -> apiDirection == directionId
        }

    private fun String.toRealtimeLineId(lineName: String): String {
        val normalized = normalizeLine(ifBlank { lineName })
        val number = Regex("^(\\d+)").find(normalized)?.groupValues?.getOrNull(1)?.toIntOrNull()
        if (number != null && number in 1..9) return (1000 + number).toString()
        return when {
            "경의중앙" in normalized -> "1063"
            "공항" in normalized -> "1065"
            "경춘" in normalized -> "1067"
            "수인분당" in normalized || normalized == "분당" -> "1075"
            "신분당" in normalized -> "1077"
            "경강" in normalized -> "1081"
            "김포골드" in normalized || "김포도시" in normalized -> "1091"
            "우이신설" in normalized -> "1092"
            "서해" in normalized -> "1093"
            else -> this
        }
    }

    private fun normalizeLine(value: String): String = value
        .replace("0", "")
        .replace("호선", "")
        .replace(" ", "")
        .lowercase()

    private fun formatLineName(raw: String): String {
        val trimmed = raw.trim()
        val number = Regex("^0?(\\d+)호선$").matchEntire(trimmed)?.groupValues?.getOrNull(1)
        return number?.let { "${it.toInt()}호선" } ?: trimmed
    }

    private fun Document.rows(): List<Element> = elements("row")

    private fun Document.realtimeItems(): List<Element> {
        val named = elements("realtimeArrivalList")
        return named.ifEmpty { rows() }
    }

    private fun Document.elements(tagName: String): List<Element> {
        val nodes = getElementsByTagName(tagName)
        return buildList {
            for (index in 0 until nodes.length) {
                (nodes.item(index) as? Element)?.let(::add)
            }
        }
    }

    private fun Document.throwIfApiError() {
        val code = firstText("CODE") ?: firstText("code") ?: "INFO-000"
        if (code == "INFO-000" || code == "INFO-200") return
        val message = firstText("MESSAGE")
            ?: firstText("message")
            ?: "서울 지하철 정보를 불러오지 못했습니다."
        throw TransitApiException(message)
    }

    private fun InputStream.toSecureDocument(): Document =
        DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            isValidating = false
            runCatching { isXIncludeAware = false }
            runCatching { setExpandEntityReferences(false) }
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        }.newDocumentBuilder().parse(this).also { it.documentElement.normalize() }

    private fun Element.text(tagName: String): String? =
        getElementsByTagName(tagName).item(0)?.textContent?.trim()?.takeIf(String::isNotEmpty)

    private fun Element.int(tagName: String): Int? = text(tagName)?.toIntOrNull()

    private fun Document.firstText(tagName: String): String? =
        getElementsByTagName(tagName).item(0)?.textContent?.trim()?.takeIf(String::isNotEmpty)

    private fun String.encodeQuery(): String = URLEncoder.encode(this, "UTF-8")

    companion object {
        const val DIRECTION_UP = "UP"
        const val DIRECTION_DOWN = "DOWN"
        const val DIRECTION_UP_LABEL = "상행 · 내선"
        const val DIRECTION_DOWN_LABEL = "하행 · 외선"

        private const val CONNECT_TIMEOUT_MILLIS = 8_000
        private const val READ_TIMEOUT_MILLIS = 10_000
    }
}
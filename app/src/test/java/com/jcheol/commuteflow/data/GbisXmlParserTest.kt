package com.jcheol.commuteflow.data

import com.jcheol.commuteflow.domain.TransitProvider
import java.io.ByteArrayInputStream
import kotlin.text.Charsets.UTF_8
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GbisXmlParserTest {
    @Test
    fun `parses flexible arrival fields and falls back to minutes`() {
        val response = GbisXmlParser.parse(
            ByteArrayInputStream(SUCCESS_XML.toByteArray(UTF_8)),
        )

        assertEquals(0, response.resultCode)
        assertEquals(2, response.arrivals.size)

        val express = response.arrivals[0]
        assertEquals("6003", express.routeName)
        assertEquals(11, express.routeTypeCode)
        assertEquals(78, express.arrivalSeconds)
        assertEquals(1, express.stopsAway)
        assertEquals(1_060, express.nextArrivalSeconds)
        assertEquals(63, express.remainingSeats)

        val local = response.arrivals[1]
        assertEquals("55", local.routeName)
        assertEquals(13, local.routeTypeCode)
        assertEquals(180, local.arrivalSeconds)
        assertNull(local.stopsAway)
        assertNull(local.nextArrivalSeconds)
        assertNull(local.remainingSeats)
    }

    @Test
    fun `parses public data portal authentication error`() {
        val response = GbisXmlParser.parse(
            ByteArrayInputStream(AUTH_ERROR_XML.toByteArray(UTF_8)),
        )

        assertEquals(30, response.resultCode)
        assertEquals("SERVICE KEY IS NOT REGISTERED ERROR.", response.resultMessage)
        assertEquals(emptyList<Any>(), response.arrivals)
    }

    @Test
    fun `parses stations and skips incomplete station rows`() {
        val response = GbisXmlParser.parseStations(
            ByteArrayInputStream(STATIONS_XML.toByteArray(UTF_8)),
        )

        assertEquals(0, response.resultCode)
        assertEquals("정상적으로 처리되었습니다.", response.resultMessage)
        assertEquals(2, response.stations.size)

        val butdeul = response.stations[0]
        assertEquals(TransitProvider.GYEONGGI_BUS, butdeul.provider)
        assertEquals("206000578", butdeul.stationId)
        assertEquals("봇들마을5단지", butdeul.stationName)
        assertEquals("07521", butdeul.stationNumber)
        assertEquals("성남", butdeul.detail)

        val pangyo = response.stations[1]
        assertEquals("206000123", pangyo.stationId)
        assertEquals("판교역", pangyo.stationName)
        assertNull(pangyo.stationNumber)
    }

    @Test
    fun `parses JSON no-result header returned for an XML station request`() {
        val response = GbisXmlParser.parseStations(
            ByteArrayInputStream(NO_RESULT_JSON.toByteArray(UTF_8)),
        )

        assertEquals(4, response.resultCode)
        assertEquals("결과가 존재하지 않습니다.", response.resultMessage)
        assertTrue(response.stations.isEmpty())
    }

    @Test
    fun `parses routes and skips incomplete route rows`() {
        val response = GbisXmlParser.parseRoutes(
            ByteArrayInputStream(ROUTES_XML.toByteArray(UTF_8)),
        )

        assertEquals(0, response.resultCode)
        assertEquals(2, response.routes.size)

        val local = response.routes[0]
        assertEquals("204000026", local.routeId)
        assertEquals("55", local.routeName)
        assertEquals("남한산성입구", local.destinationName)
        assertEquals(13, local.routeTypeCode)

        val metropolitan = response.routes[1]
        assertEquals("233000266", metropolitan.routeId)
        assertEquals("6003", metropolitan.routeName)
        assertEquals(11, metropolitan.routeTypeCode)
    }

    private companion object {
        val NO_RESULT_JSON = """
            {"response":{"msgHeader":{"resultCode":4,"resultMessage":"결과가 존재하지 않습니다."}}}
        """.trimIndent()

        val SUCCESS_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <response>
                <msgHeader>
                    <resultCode>0</resultCode>
                    <resultMessage>정상적으로 처리되었습니다.</resultMessage>
                </msgHeader>
                <msgBody>
                    <busArrivalList>
                        <flag>PASS</flag>
                        <routeId>233000266</routeId>
                        <routeName>6003</routeName>
                        <routeTypeCd>11</routeTypeCd>
                        <routeDestName>판교역.낙생육교.현대백화점</routeDestName>
                        <predictTime1>1</predictTime1>
                        <predictTimeSec1>78</predictTimeSec1>
                        <locationNo1>1</locationNo1>
                        <stationNm1>한국무역정보통신</stationNm1>
                        <lowPlate1>2</lowPlate1>
                        <remainSeatCnt1>63</remainSeatCnt1>
                        <predictTime2>17</predictTime2>
                        <predictTimeSec2>1060</predictTimeSec2>
                        <locationNo2>9</locationNo2>
                    </busArrivalList>
                    <busArrivalList>
                        <flag>RUN</flag>
                        <routeId>204000026</routeId>
                        <routeName>55</routeName>
                        <routeTypeCd>13</routeTypeCd>
                        <routeDestName>남한산성입구</routeDestName>
                        <predictTime1>3</predictTime1>
                        <predictTimeSec1>-1</predictTimeSec1>
                        <locationNo1>-1</locationNo1>
                        <predictTimeSec2></predictTimeSec2>
                        <predictTime2></predictTime2>
                        <remainSeatCnt1>-1</remainSeatCnt1>
                    </busArrivalList>
                    <busArrivalList>
                        <flag>STOP</flag>
                        <routeId>stopped-route</routeId>
                        <routeName>종료</routeName>
                        <predictTimeSec1>30</predictTimeSec1>
                    </busArrivalList>
                </msgBody>
            </response>
        """.trimIndent()

        val AUTH_ERROR_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <OpenAPI_ServiceResponse>
                <cmmMsgHeader>
                    <errMsg>SERVICE ERROR</errMsg>
                    <returnAuthMsg>SERVICE KEY IS NOT REGISTERED ERROR.</returnAuthMsg>
                    <returnReasonCode>30</returnReasonCode>
                </cmmMsgHeader>
            </OpenAPI_ServiceResponse>
        """.trimIndent()

        val STATIONS_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <response>
                <msgHeader>
                    <resultCode>0</resultCode>
                    <resultMessage>정상적으로 처리되었습니다.</resultMessage>
                </msgHeader>
                <msgBody>
                    <busStationList>
                        <stationId>206000578</stationId>
                        <stationName>봇들마을5단지</stationName>
                        <mobileNo> 07521 </mobileNo>
                        <regionName>성남</regionName>
                    </busStationList>
                    <busStationList>
                        <stationId>206000123</stationId>
                        <stationName>판교역</stationName>
                    </busStationList>
                    <busStationList>
                        <stationId></stationId>
                        <stationName>식별자 없는 정류소</stationName>
                    </busStationList>
                    <busStationList>
                        <stationId>206000999</stationId>
                        <stationName></stationName>
                    </busStationList>
                </msgBody>
            </response>
        """.trimIndent()

        val ROUTES_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <response>
                <msgHeader>
                    <resultCode>0</resultCode>
                    <resultMessage>정상적으로 처리되었습니다.</resultMessage>
                </msgHeader>
                <msgBody>
                    <busRouteList>
                        <routeId>204000026</routeId>
                        <routeName>55</routeName>
                        <routeDestName>남한산성입구</routeDestName>
                        <routeTypeCd>13</routeTypeCd>
                    </busRouteList>
                    <busRouteList>
                        <routeId>233000266</routeId>
                        <routeName>6003</routeName>
                        <routeDestName>판교역</routeDestName>
                        <routeTypeCd>11</routeTypeCd>
                    </busRouteList>
                    <busRouteList>
                        <routeName>누락 노선</routeName>
                    </busRouteList>
                </msgBody>
            </response>
        """.trimIndent()
    }
}

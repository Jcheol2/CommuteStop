package com.jcheol.commutestop.data

import com.jcheol.commutestop.domain.TransitProvider
import java.io.ByteArrayInputStream
import kotlin.text.Charsets.UTF_8
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SeoulBusXmlParserTest {
    @Test
    fun `parses station search response using ARS id`() {
        val stations = SeoulBusXmlParser.parseStations(STATION_XML.asInput())

        assertEquals(1, stations.size)
        assertEquals(TransitProvider.SEOUL_BUS, stations.single().provider)
        assertEquals("02173", stations.single().stationId)
        assertEquals("서울역버스환승센터", stations.single().stationName)
        assertEquals("02173", stations.single().stationNumber)
    }

    @Test
    fun `parses routes and arrivals from Seoul station responses`() {
        val routes = SeoulBusXmlParser.parseRoutes(ROUTE_XML.asInput())
        val arrivals = SeoulBusXmlParser.parseArrivals(ARRIVAL_XML.asInput())

        assertEquals(1, routes.size)
        assertEquals("100100118", routes.single().routeId)
        assertEquals("701", routes.single().routeName)
        assertEquals(3, routes.single().routeTypeCode)

        assertEquals(1, arrivals.size)
        val arrival = arrivals.single()
        assertEquals("100100118", arrival.routeId)
        assertEquals("701", arrival.routeName)
        assertEquals(125, arrival.arrivalSeconds)
        assertEquals(2, arrival.stopsAway)
        assertEquals("2분5초후[2번째 전]", arrival.status)
        assertEquals(420, arrival.nextArrivalSeconds)
        assertEquals(6, arrival.nextStopsAway)
        assertNull(arrival.remainingSeats)
    }

    private fun String.asInput() = ByteArrayInputStream(toByteArray(UTF_8))

    private companion object {
        val STATION_XML = """
            <ServiceResult>
                <msgHeader><headerCd>0</headerCd><headerMsg>정상 처리</headerMsg></msgHeader>
                <msgBody>
                    <itemList><stId>101000008</stId><stNm>서울역버스환승센터</stNm><arsId>02173</arsId></itemList>
                    <itemList><stId>0</stId><stNm>미정차</stNm><arsId>미정차</arsId></itemList>
                </msgBody>
            </ServiceResult>
        """.trimIndent()

        val ROUTE_XML = """
            <ServiceResult>
                <msgHeader><headerCd>0</headerCd></msgHeader>
                <msgBody>
                    <itemList>
                        <busRouteId>100100118</busRouteId><busRouteNm>701</busRouteNm>
                        <routeType>3</routeType><adirection>진관공영차고지</adirection>
                    </itemList>
                </msgBody>
            </ServiceResult>
        """.trimIndent()

        val ARRIVAL_XML = """
            <ServiceResult>
                <msgHeader><headerCd>0</headerCd></msgHeader>
                <msgBody>
                    <itemList>
                        <busRouteId>100100118</busRouteId><rtNm>701</rtNm><routeType>3</routeType>
                        <adirection>진관공영차고지</adirection><vehId1>1001</vehId1>
                        <traTime1>125</traTime1><arrmsg1>2분5초후[2번째 전]</arrmsg1>
                        <staOrd>20</staOrd><sectOrd1>18</sectOrd1><stationNm1>시청앞</stationNm1>
                        <vehId2>1002</vehId2><traTime2>420</traTime2><sectOrd2>14</sectOrd2>
                        <busType1>1</busType1><congetion1>3</congetion1>
                    </itemList>
                </msgBody>
            </ServiceResult>
        """.trimIndent()
    }
}
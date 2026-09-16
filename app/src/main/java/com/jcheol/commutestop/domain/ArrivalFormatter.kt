package com.jcheol.commutestop.domain

import kotlin.math.max

fun formatArrivalTime(seconds: Int): String = when {
    seconds <= 60 -> "곧 도착"
    seconds < 3_600 -> "${max(1, seconds / 60)}분 후 도착"
    else -> {
        val hours = seconds / 3_600
        val minutes = (seconds % 3_600) / 60
        if (minutes == 0) "${hours}시간 후 도착" else "${hours}시간 ${minutes}분 후 도착"
    }
}

fun formatSubwayArrivalStatus(seconds: Int, message: String): String {
    val normalizedMessage = message.trim()
    val stopsBefore = SUBWAY_STOPS_BEFORE_PATTERN
        .find(normalizedMessage)
        ?.groupValues
        ?.getOrNull(1)
        ?.toIntOrNull()
    if (stopsBefore != null) return "${stopsBefore}번째 전역"
    if (SUBWAY_PREVIOUS_STATION_PATTERN.matches(normalizedMessage)) return "1번째 전역"
    if (seconds > 0) return formatArrivalTime(seconds)
    return normalizedMessage.ifBlank { "도착 정보 확인 중" }
}

fun formatStopsAway(stopsAway: Int?): String? = when (stopsAway) {
    null -> null
    0 -> "정류소 진입"
    1 -> "1정거장 전"
    else -> "${stopsAway}정거장 전"
}

fun routeTypeName(code: Int?): String = when (code) {
    11, 17, 21 -> "직행좌석"
    12, 22 -> "좌석"
    13, 23 -> "일반"
    14 -> "광역급행"
    15 -> "따복"
    16 -> "경기순환"
    30 -> "마을"
    41, 42, 43 -> "시외"
    51, 52, 53 -> "공항"
    else -> "버스"
}

fun vehicleTypeName(code: Int?): String? = when (code) {
    1 -> "저상"
    2 -> "2층"
    5 -> "전세"
    6 -> "예약"
    7 -> "트롤리"
    else -> null
}

fun crowdednessName(code: Int?): String? = when (code) {
    1 -> "여유"
    2 -> "보통"
    3 -> "혼잡"
    4 -> "매우 혼잡"
    else -> null
}

private val SUBWAY_STOPS_BEFORE_PATTERN = Regex("""\[?(\d+)]?\s*번째\s*전역""")
private val SUBWAY_PREVIOUS_STATION_PATTERN = Regex("""전역\s+(진입|도착|출발)""")

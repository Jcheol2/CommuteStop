package com.jcheol.commuteflow.data

import com.jcheol.commuteflow.domain.SubwayArrival
import com.jcheol.commuteflow.domain.SubwayTransitTarget
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class ShinbundangTimetable(
    private val timeZone: TimeZone = TimeZone.getTimeZone(SEOUL_TIME_ZONE_ID),
) {
    fun supports(target: SubwayTransitTarget): Boolean =
        target.lineId == SHINBUNDANG_API_LINE_ID ||
            target.lineId.contains(SHINBUNDANG_NAME) ||
            target.lineName.contains(SHINBUNDANG_NAME)

    fun nextArrivals(
        target: SubwayTransitTarget,
        nowMillis: Long = System.currentTimeMillis(),
        limit: Int = DEFAULT_RESULT_LIMIT,
    ): List<SubwayArrival> {
        if (!supports(target) || limit <= 0) return emptyList()

        val stationSchedule = SHINBUNDANG_TIMETABLE_DATA[normalizeStationName(target.stationName)]
            ?: return emptyList()
        val direction = target.directionId.uppercase(Locale.ROOT)
        if (direction != DIRECTION_UP && direction != DIRECTION_DOWN) return emptyList()

        val serviceDay = startOfDay(nowMillis)
        val candidates = buildList {
            for (dayOffset in PREVIOUS_DAY..NEXT_DAY) {
                val serviceDate = (serviceDay.clone() as Calendar).apply {
                    add(Calendar.DATE, dayOffset)
                }
                val rawSchedule = stationSchedule.forServiceDate(serviceDate, direction)
                val defaultDestination = if (direction == DIRECTION_UP) {
                    DESTINATION_SINSA
                } else {
                    DESTINATION_GWANGGYO
                }
                parseDepartures(rawSchedule, defaultDestination).forEach { departure ->
                    val departureTime = (serviceDate.clone() as Calendar).apply {
                        add(Calendar.MINUTE, departure.serviceMinute)
                    }.timeInMillis
                    if (departureTime >= nowMillis) {
                        add(ScheduledCandidate(departureTime, departure.destination))
                    }
                }
            }
        }

        return candidates
            .sortedBy(ScheduledCandidate::departureTimeMillis)
            .take(limit)
            .map { candidate ->
                val remainingSeconds = (
                    (candidate.departureTimeMillis - nowMillis + MILLIS_PER_SECOND - 1) /
                        MILLIS_PER_SECOND
                    ).coerceAtLeast(1L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                SubwayArrival(
                    lineId = target.lineId,
                    lineName = target.lineName,
                    direction = target.directionLabel,
                    destination = candidate.destination,
                    arrivalSeconds = remainingSeconds,
                    message = "${formatDepartureTime(candidate.departureTimeMillis)} 예정",
                )
            }
    }

    private fun ShinbundangStationSchedule.forServiceDate(
        serviceDate: Calendar,
        direction: String,
    ): String {
        val weekendOrHoliday = serviceDate.isWeekendOrHoliday()
        return when {
            weekendOrHoliday && direction == DIRECTION_UP -> weekendUp
            weekendOrHoliday -> weekendDown
            direction == DIRECTION_UP -> weekdayUp
            else -> weekdayDown
        }
    }

    private fun Calendar.isWeekendOrHoliday(): Boolean {
        val weekend = get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY ||
            get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY
        return weekend || dateKey() in SOUTH_KOREA_PUBLIC_HOLIDAYS_2026
    }

    private fun Calendar.dateKey(): Int =
        get(Calendar.YEAR) * 10_000 + (get(Calendar.MONTH) + 1) * 100 + get(Calendar.DAY_OF_MONTH)

    private fun startOfDay(timeMillis: Long): Calendar = Calendar.getInstance(timeZone).apply {
        this.timeInMillis = timeMillis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    private fun parseDepartures(
        rawSchedule: String,
        defaultDestination: String,
    ): List<ScheduledDeparture> = rawSchedule
        .splitToSequence(',')
        .filter(String::isNotBlank)
        .mapNotNull { token ->
            val serviceMinute = token.substringBefore(DESTINATION_SEPARATOR).toIntOrNull()
                ?: return@mapNotNull null
            ScheduledDeparture(
                serviceMinute = serviceMinute,
                destination = token.substringAfter(
                    delimiter = DESTINATION_SEPARATOR,
                    missingDelimiterValue = defaultDestination,
                ),
            )
        }
        .toList()

    private fun formatDepartureTime(timeMillis: Long): String =
        SimpleDateFormat("a h:mm", Locale.KOREA).apply {
            timeZone = this@ShinbundangTimetable.timeZone
        }.format(Date(timeMillis))

    private fun normalizeStationName(stationName: String): String = stationName
        .substringBefore('(')
        .trim()
        .removeSuffix("역")
        .filterNot(Char::isWhitespace)

    private data class ScheduledDeparture(
        val serviceMinute: Int,
        val destination: String,
    )

    private data class ScheduledCandidate(
        val departureTimeMillis: Long,
        val destination: String,
    )

    private companion object {
        const val SEOUL_TIME_ZONE_ID = "Asia/Seoul"
        const val SHINBUNDANG_API_LINE_ID = "1077"
        const val SHINBUNDANG_NAME = "신분당"
        const val DIRECTION_UP = "UP"
        const val DIRECTION_DOWN = "DOWN"
        const val DESTINATION_SINSA = "신사"
        const val DESTINATION_GWANGGYO = "광교"
        const val DESTINATION_SEPARATOR = '@'
        const val DEFAULT_RESULT_LIMIT = 2
        const val PREVIOUS_DAY = -1
        const val NEXT_DAY = 1
        const val MILLIS_PER_SECOND = 1_000L

        val SOUTH_KOREA_PUBLIC_HOLIDAYS_2026 = setOf(
            20260101,
            20260216,
            20260217,
            20260218,
            20260302,
            20260505,
            20260525,
            20260603,
            20260817,
            20260924,
            20260925,
            20261005,
            20261009,
            20261225,
        )
    }
}
package com.jcheol.commutestop.domain

enum class CommuteProfile {
    MORNING,
    EVENING,
}

enum class ThemePreference {
    DARK,
    LIGHT,
}

enum class TransitProvider {
    SEOUL_BUS,
    GYEONGGI_BUS,
    SEOUL_SUBWAY,
}

sealed interface TransitTarget {
    val id: String
    val provider: TransitProvider
    val stationId: String
    val stationName: String
}

data class BusRouteRef(
    val routeId: String,
    val routeName: String,
    val destinationName: String? = null,
) {
    init {
        require(routeId.isNotBlank()) { "routeId must not be blank" }
        require(routeName.isNotBlank()) { "routeName must not be blank" }
        require(destinationName == null || destinationName.isNotBlank()) {
            "destinationName must be null or non-blank"
        }
    }
}

data class BusTransitTarget(
    override val id: String,
    override val provider: TransitProvider,
    override val stationId: String,
    override val stationName: String,
    val routes: List<BusRouteRef>,
    val stationNumber: String? = null,
    val regionName: String? = null,
) : TransitTarget {
    init {
        requireBaseFields()
        require(provider == TransitProvider.SEOUL_BUS || provider == TransitProvider.GYEONGGI_BUS) {
            "A bus target requires a bus provider"
        }
        require(stationNumber == null || stationNumber.isNotBlank()) {
            "stationNumber must be null or non-blank"
        }
        require(regionName == null || regionName.isNotBlank()) {
            "regionName must be null or non-blank"
        }
        require(routes.isNotEmpty()) { "A bus target must contain at least one route" }
        require(routes.distinctBy(BusRouteRef::routeId).size == routes.size) {
            "Bus route IDs must be unique"
        }
    }
}

data class SubwayTransitTarget(
    override val id: String,
    override val provider: TransitProvider = TransitProvider.SEOUL_SUBWAY,
    override val stationId: String,
    override val stationName: String,
    val lineId: String,
    val lineName: String,
    val directionId: String,
    val directionLabel: String,
) : TransitTarget {
    init {
        requireBaseFields()
        require(provider == TransitProvider.SEOUL_SUBWAY) {
            "A subway target requires the SEOUL_SUBWAY provider"
        }
        require(lineId.isNotBlank()) { "lineId must not be blank" }
        require(lineName.isNotBlank()) { "lineName must not be blank" }
        require(directionId.isNotBlank()) { "directionId must not be blank" }
        require(directionLabel.isNotBlank()) { "directionLabel must not be blank" }
    }
}

data class ProfileSettings(
    val theme: ThemePreference,
    val targets: List<TransitTarget> = emptyList(),
) {
    init {
        require(targets.size <= MAX_TARGETS_PER_PROFILE) {
            "A profile can contain at most $MAX_TARGETS_PER_PROFILE targets"
        }
        require(targets.distinctBy(TransitTarget::id).size == targets.size) {
            "Target IDs must be unique within a profile"
        }
    }
}

data class CommuteSettings(
    val cutoffMinute: Int = DEFAULT_CUTOFF_MINUTE,
    val morning: ProfileSettings = ProfileSettings(theme = ThemePreference.DARK),
    val evening: ProfileSettings = ProfileSettings(theme = ThemePreference.LIGHT),
) {
    init {
        require(cutoffMinute in MINUTES_IN_DAY_RANGE) {
            "cutoffMinute must be between 0 and 1439"
        }
    }
}

private fun TransitTarget.requireBaseFields() {
    require(id.isNotBlank()) { "id must not be blank" }
    require(stationId.isNotBlank()) { "stationId must not be blank" }
    require(stationName.isNotBlank()) { "stationName must not be blank" }
}
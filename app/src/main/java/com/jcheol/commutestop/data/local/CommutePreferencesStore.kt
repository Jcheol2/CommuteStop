package com.jcheol.commutestop.data.local

import android.content.Context
import android.content.SharedPreferences
import com.jcheol.commutestop.domain.BusRouteRef
import com.jcheol.commutestop.domain.BusTransitTarget
import com.jcheol.commutestop.domain.CommuteProfile
import com.jcheol.commutestop.domain.CommuteSettings
import com.jcheol.commutestop.domain.DEFAULT_CUTOFF_MINUTE
import com.jcheol.commutestop.domain.MAX_TARGETS_PER_PROFILE
import com.jcheol.commutestop.domain.ProfileSettings
import com.jcheol.commutestop.domain.SubwayTransitTarget
import com.jcheol.commutestop.domain.ThemePreference
import com.jcheol.commutestop.domain.TransitProvider
import com.jcheol.commutestop.domain.TransitTarget
import com.jcheol.commutestop.domain.withCutoffMinute
import com.jcheol.commutestop.domain.withReorderedTarget
import com.jcheol.commutestop.domain.withSavedTarget
import com.jcheol.commutestop.domain.withTheme
import com.jcheol.commutestop.domain.withoutTarget
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

class CommutePreferencesStore internal constructor(
    private val sharedPreferences: SharedPreferences,
    private val codec: CommuteSettingsCodec,
) {
    constructor(sharedPreferences: SharedPreferences) : this(
        sharedPreferences = sharedPreferences,
        codec = JsonCommuteSettingsCodec,
    )

    constructor(context: Context) : this(
        sharedPreferences = (context.applicationContext ?: context).getSharedPreferences(
            PREFERENCES_FILE_NAME,
            Context.MODE_PRIVATE,
        ),
    )

    private val updateLock = Any()
    private val mutableSettings = MutableStateFlow(readSettings())
    val settings: StateFlow<CommuteSettings> = mutableSettings.asStateFlow()

    fun setCutoffMinute(cutoffMinute: Int): CommuteSettings = update { current ->
        current.withCutoffMinute(cutoffMinute)
    }

    fun setTheme(
        profile: CommuteProfile,
        theme: ThemePreference,
    ): CommuteSettings = update { current ->
        current.withTheme(profile, theme)
    }

    fun saveTarget(
        profile: CommuteProfile,
        target: TransitTarget,
    ): CommuteSettings = update { current ->
        current.withSavedTarget(profile, target)
    }

    fun removeTarget(
        profile: CommuteProfile,
        targetId: String,
    ): CommuteSettings = update { current ->
        current.withoutTarget(profile, targetId)
    }

    fun reorderTarget(
        profile: CommuteProfile,
        fromIndex: Int,
        toIndex: Int,
    ): CommuteSettings = update { current ->
        current.withReorderedTarget(profile, fromIndex, toIndex)
    }

    private fun readSettings(): CommuteSettings {
        val encoded = runCatching { sharedPreferences.getString(SETTINGS_KEY, null) }.getOrNull()
            ?: return CommuteSettings()
        return runCatching { codec.decode(encoded) }.getOrElse { CommuteSettings() }
    }

    private fun update(transform: (CommuteSettings) -> CommuteSettings): CommuteSettings =
        synchronized(updateLock) {
            val current = mutableSettings.value
            val updated = transform(current)
            if (updated == current) return@synchronized current

            val encoded = codec.encode(updated)
            sharedPreferences.edit().putString(SETTINGS_KEY, encoded).apply()
            mutableSettings.value = updated
            updated
        }

    companion object {
        const val PREFERENCES_FILE_NAME = "commute_preferences"
        private const val SETTINGS_KEY = "commute_settings_json"
    }
}

internal interface CommuteSettingsCodec {
    fun encode(settings: CommuteSettings): String
    fun decode(encoded: String): CommuteSettings
}

private object JsonCommuteSettingsCodec : CommuteSettingsCodec {
    override fun encode(settings: CommuteSettings): String = JSONObject()
        .put(KEY_SCHEMA_VERSION, SCHEMA_VERSION)
        .put(KEY_CUTOFF_MINUTE, settings.cutoffMinute)
        .put(KEY_MORNING, settings.morning.toJson())
        .put(KEY_EVENING, settings.evening.toJson())
        .toString()

    override fun decode(encoded: String): CommuteSettings {
        val defaults = CommuteSettings()
        val root = JSONObject(encoded)
        val cutoffMinute = root.optInt(KEY_CUTOFF_MINUTE, DEFAULT_CUTOFF_MINUTE)
            .takeIf { it in 0 until 24 * 60 }
            ?: DEFAULT_CUTOFF_MINUTE
        return CommuteSettings(
            cutoffMinute = cutoffMinute,
            morning = root.optJSONObject(KEY_MORNING)?.toProfileSettings(ThemePreference.DARK)
                ?: defaults.morning,
            evening = root.optJSONObject(KEY_EVENING)?.toProfileSettings(ThemePreference.LIGHT)
                ?: defaults.evening,
        )
    }

    private fun ProfileSettings.toJson(): JSONObject = JSONObject()
        .put(KEY_THEME, theme.name)
        .put(
            KEY_TARGETS,
            JSONArray().apply {
                targets.forEach { target -> put(target.toJson()) }
            },
        )

    private fun TransitTarget.toJson(): JSONObject = when (this) {
        is BusTransitTarget -> JSONObject()
            .put(KEY_TYPE, TYPE_BUS)
            .putBaseFields(this)
            .putOptional(KEY_STATION_NUMBER, stationNumber)
            .putOptional(KEY_REGION_NAME, regionName)
            .put(
                KEY_ROUTES,
                JSONArray().apply {
                    routes.forEach { route ->
                        put(
                            JSONObject()
                                .put(KEY_ROUTE_ID, route.routeId)
                                .put(KEY_ROUTE_NAME, route.routeName)
                                .putOptional(KEY_DESTINATION_NAME, route.destinationName),
                        )
                    }
                },
            )

        is SubwayTransitTarget -> JSONObject()
            .put(KEY_TYPE, TYPE_SUBWAY)
            .putBaseFields(this)
            .put(KEY_LINE_ID, lineId)
            .put(KEY_LINE_NAME, lineName)
            .put(KEY_DIRECTION_ID, directionId)
            .put(KEY_DIRECTION_LABEL, directionLabel)
    }

    private fun JSONObject.toProfileSettings(defaultTheme: ThemePreference): ProfileSettings {
        val theme = runCatching {
            ThemePreference.valueOf(optString(KEY_THEME, defaultTheme.name))
        }.getOrDefault(defaultTheme)
        val targetArray = optJSONArray(KEY_TARGETS)
        val targets = buildList<TransitTarget> {
            if (targetArray != null) {
                for (index in 0 until targetArray.length()) {
                    if (size == MAX_TARGETS_PER_PROFILE) break
                    val targetJson = targetArray.optJSONObject(index) ?: continue
                    val target = runCatching { targetJson.toTransitTarget() }.getOrNull() ?: continue
                    if (none { existing -> existing.id == target.id }) add(target)
                }
            }
        }
        return ProfileSettings(theme = theme, targets = targets)
    }

    private fun JSONObject.toTransitTarget(): TransitTarget = when (requiredString(KEY_TYPE)) {
        TYPE_BUS -> BusTransitTarget(
            id = requiredString(KEY_ID),
            provider = providerOrDefault(TransitProvider.GYEONGGI_BUS),
            stationId = requiredString(KEY_STATION_ID),
            stationName = requiredString(KEY_STATION_NAME),
            routes = requiredArray(KEY_ROUTES).toBusRoutes(),
            stationNumber = optionalString(KEY_STATION_NUMBER),
            regionName = optionalString(KEY_REGION_NAME),
        )

        TYPE_SUBWAY -> SubwayTransitTarget(
            id = requiredString(KEY_ID),
            stationId = requiredString(KEY_STATION_ID),
            stationName = requiredString(KEY_STATION_NAME),
            lineId = requiredString(KEY_LINE_ID),
            lineName = requiredString(KEY_LINE_NAME),
            directionId = requiredString(KEY_DIRECTION_ID),
            directionLabel = requiredString(KEY_DIRECTION_LABEL),
            provider = providerOrDefault(TransitProvider.SEOUL_SUBWAY),
        )

        else -> error("Unknown transit target type")
    }

    private fun JSONArray.toBusRoutes(): List<BusRouteRef> = buildList {
        for (index in 0 until length()) {
            val routeJson = optJSONObject(index) ?: continue
            val route = runCatching {
                BusRouteRef(
                    routeId = routeJson.requiredString(KEY_ROUTE_ID),
                    routeName = routeJson.requiredString(KEY_ROUTE_NAME),
                    destinationName = routeJson.optionalString(KEY_DESTINATION_NAME),
                )
            }.getOrNull() ?: continue
            if (none { existing -> existing.routeId == route.routeId }) add(route)
        }
    }

    private fun JSONObject.putBaseFields(target: TransitTarget): JSONObject =
        put(KEY_ID, target.id)
            .put(KEY_PROVIDER, target.provider.name)
            .put(KEY_STATION_ID, target.stationId)
            .put(KEY_STATION_NAME, target.stationName)

    private fun JSONObject.putOptional(key: String, value: String?): JSONObject = apply {
        if (value != null) put(key, value)
    }

    private fun JSONObject.requiredString(key: String): String {
        if (!has(key) || isNull(key)) error("Missing or blank $key")
        return optString(key).trim().takeIf(String::isNotEmpty)
            ?: error("Missing or blank $key")
    }

    private fun JSONObject.optionalString(key: String): String? =
        if (!has(key) || isNull(key)) null else optString(key).trim().takeIf(String::isNotEmpty)

    private fun JSONObject.providerOrDefault(default: TransitProvider): TransitProvider =
        when (val value = optionalString(KEY_PROVIDER)) {
            null -> default
            "GBIS" -> TransitProvider.GYEONGGI_BUS
            else -> TransitProvider.valueOf(value)
        }

    private fun JSONObject.requiredArray(key: String): JSONArray =
        optJSONArray(key) ?: error("Missing $key")

    private const val SCHEMA_VERSION = 1
    private const val KEY_SCHEMA_VERSION = "schemaVersion"
    private const val KEY_CUTOFF_MINUTE = "cutoffMinute"
    private const val KEY_MORNING = "morning"
    private const val KEY_EVENING = "evening"
    private const val KEY_THEME = "theme"
    private const val KEY_TARGETS = "targets"
    private const val KEY_TYPE = "type"
    private const val KEY_ID = "id"
    private const val KEY_PROVIDER = "provider"
    private const val KEY_STATION_ID = "stationId"
    private const val KEY_STATION_NAME = "stationName"
    private const val KEY_STATION_NUMBER = "stationNumber"
    private const val KEY_REGION_NAME = "regionName"
    private const val KEY_ROUTES = "routes"
    private const val KEY_ROUTE_ID = "routeId"
    private const val KEY_ROUTE_NAME = "routeName"
    private const val KEY_DESTINATION_NAME = "destinationName"
    private const val KEY_LINE_ID = "lineId"
    private const val KEY_LINE_NAME = "lineName"
    private const val KEY_DIRECTION_ID = "directionId"
    private const val KEY_DIRECTION_LABEL = "directionLabel"
    private const val TYPE_BUS = "BUS"
    private const val TYPE_SUBWAY = "SUBWAY"
}
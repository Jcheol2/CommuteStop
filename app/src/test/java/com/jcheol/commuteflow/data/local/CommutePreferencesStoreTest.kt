package com.jcheol.commuteflow.data.local

import android.content.SharedPreferences
import com.jcheol.commuteflow.domain.BusRouteRef
import com.jcheol.commuteflow.domain.BusTransitTarget
import com.jcheol.commuteflow.domain.CommuteProfile
import com.jcheol.commuteflow.domain.CommuteSettings
import com.jcheol.commuteflow.domain.SubwayTransitTarget
import com.jcheol.commuteflow.domain.ThemePreference
import com.jcheol.commuteflow.domain.TransitProvider
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CommutePreferencesStoreTest {
    @Test
    fun `updates StateFlow and reloads the persisted settings`() {
        val preferences = FakeSharedPreferences()
        val codec = RecordingCodec()
        val store = CommutePreferencesStore(preferences.instance, codec)

        store.setCutoffMinute(510)
        store.setTheme(CommuteProfile.MORNING, ThemePreference.LIGHT)
        store.saveTarget(CommuteProfile.MORNING, busTarget("bus"))
        store.saveTarget(CommuteProfile.MORNING, subwayTarget("subway"))
        store.reorderTarget(CommuteProfile.MORNING, fromIndex = 1, toIndex = 0)

        val updated = store.settings.value
        assertEquals(510, updated.cutoffMinute)
        assertEquals(ThemePreference.LIGHT, updated.morning.theme)
        assertEquals(listOf("subway", "bus"), updated.morning.targets.map { it.id })

        val reloaded = CommutePreferencesStore(preferences.instance, codec)
        assertEquals(updated, reloaded.settings.value)

        reloaded.removeTarget(CommuteProfile.MORNING, "subway")
        assertEquals(listOf("bus"), reloaded.settings.value.morning.targets.map { it.id })
    }

    @Test
    fun `failed fourth save leaves memory and persistence unchanged`() {
        val preferences = FakeSharedPreferences()
        val codec = RecordingCodec()
        val store = CommutePreferencesStore(preferences.instance, codec)
        store.saveTarget(CommuteProfile.EVENING, busTarget("one"))
        store.saveTarget(CommuteProfile.EVENING, busTarget("two"))
        store.saveTarget(CommuteProfile.EVENING, subwayTarget("three"))
        val before = store.settings.value
        val writesBefore = preferences.writeCount

        assertThrows(IllegalArgumentException::class.java) {
            store.saveTarget(CommuteProfile.EVENING, busTarget("four"))
        }

        assertEquals(before, store.settings.value)
        assertEquals(writesBefore, preferences.writeCount)
        assertEquals(before, CommutePreferencesStore(preferences.instance, codec).settings.value)
    }

    @Test
    fun `unreadable persisted data falls back to defaults`() {
        val preferences = FakeSharedPreferences(initialValue = "broken")
        val codec = RecordingCodec()

        assertEquals(CommuteSettings(), CommutePreferencesStore(preferences.instance, codec).settings.value)
    }

    private fun busTarget(id: String) = BusTransitTarget(
        id = id,
        provider = TransitProvider.GYEONGGI_BUS,
        stationId = "station-$id",
        stationName = "정류소 $id",
        routes = listOf(BusRouteRef(routeId = "route-$id", routeName = "55")),
    )

    private fun subwayTarget(id: String) = SubwayTransitTarget(
        id = id,
        stationId = "subway-$id",
        stationName = "역 $id",
        lineId = "2",
        lineName = "2호선",
        directionId = "inner",
        directionLabel = "내선순환",
    )

    private class RecordingCodec : CommuteSettingsCodec {
        private val values = mutableMapOf<String, CommuteSettings>()
        private var nextId = 0

        override fun encode(settings: CommuteSettings): String = "encoded-${nextId++}".also { encoded ->
            values[encoded] = settings
        }

        override fun decode(encoded: String): CommuteSettings =
            values[encoded] ?: error("Unreadable value")
    }

    private class FakeSharedPreferences(initialValue: String? = null) {
        private var storedValue: String? = initialValue
        private var pendingValue: String? = null
        private var hasPendingValue = false
        var writeCount: Int = 0
            private set

        private val editor: SharedPreferences.Editor = Proxy.newProxyInstance(
            SharedPreferences.Editor::class.java.classLoader,
            arrayOf(SharedPreferences.Editor::class.java),
        ) { proxy, method, arguments ->
            when (method.name) {
                "putString" -> {
                    pendingValue = arguments?.get(1) as String?
                    hasPendingValue = true
                    proxy
                }

                "apply" -> {
                    applyPendingValue()
                    null
                }

                "commit" -> {
                    applyPendingValue()
                    true
                }

                "toString" -> "FakeSharedPreferences.Editor"
                else -> error("Unexpected editor call: ${method.name}")
            }
        } as SharedPreferences.Editor

        val instance: SharedPreferences = Proxy.newProxyInstance(
            SharedPreferences::class.java.classLoader,
            arrayOf(SharedPreferences::class.java),
        ) { _, method, arguments ->
            when (method.name) {
                "getString" -> storedValue ?: arguments?.get(1) as String?
                "edit" -> editor
                "contains" -> storedValue != null
                "toString" -> "FakeSharedPreferences"
                else -> error("Unexpected preferences call: ${method.name}")
            }
        } as SharedPreferences

        private fun applyPendingValue() {
            if (hasPendingValue) {
                storedValue = pendingValue
                hasPendingValue = false
                writeCount += 1
            }
        }
    }
}
package com.jcheol.commuteflow.ui

import android.content.SharedPreferences
import com.jcheol.commuteflow.data.TransitArrivalSnapshot
import com.jcheol.commuteflow.data.TransitRepository
import com.jcheol.commuteflow.data.local.CommutePreferencesStore
import com.jcheol.commuteflow.data.local.CommuteSettingsCodec
import com.jcheol.commuteflow.domain.BusArrival
import com.jcheol.commuteflow.domain.BusRouteOption
import com.jcheol.commuteflow.domain.BusRouteRef
import com.jcheol.commuteflow.domain.BusTransitTarget
import com.jcheol.commuteflow.domain.CommuteProfile
import com.jcheol.commuteflow.domain.CommuteSettings
import com.jcheol.commuteflow.domain.SubwayTransitTarget
import com.jcheol.commuteflow.domain.TransitProvider
import com.jcheol.commuteflow.domain.TransitSearchResult
import com.jcheol.commuteflow.domain.TransitTarget
import java.lang.reflect.Proxy
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CommuteFlowViewModelTest {
    @Test
    fun `switching to evening cancels morning refresh and ignores its late result`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val morningTarget = busTarget(id = "morning", stationName = "아침 정류소")
            val eveningTarget = busTarget(id = "evening", stationName = "저녁 정류소")
            val store = preferencesStore().apply {
                saveTarget(CommuteProfile.MORNING, morningTarget)
                saveTarget(CommuteProfile.EVENING, eveningTarget)
            }
            val repository = ControlledArrivalRepository()
            val viewModel = CommuteFlowViewModel(
                preferencesStore = store,
                transitRepository = repository,
                workerDispatcher = dispatcher,
                currentMinuteOfDay = { 8 * 60 },
                currentTimeMillis = { 1_000L },
            )

            viewModel.refresh()
            runCurrent()
            assertEquals(listOf(morningTarget.id), repository.requestedTargetIds)

            viewModel.selectProfile(CommuteProfile.EVENING)
            runCurrent()

            assertEquals(
                listOf(morningTarget.id, eveningTarget.id),
                repository.requestedTargetIds,
            )
            assertTrue(repository.requestJobs.getValue(morningTarget.id).isCancelled)

            repository.complete(eveningTarget.id, busSnapshot("EVENING_NEW"))
            runCurrent()
            repository.complete(morningTarget.id, busSnapshot("MORNING_OLD"))
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(CommuteProfile.EVENING, state.selectedProfile)
            assertEquals(setOf(eveningTarget.id), state.targetStatuses.keys)
            assertEquals(
                listOf("EVENING_NEW"),
                state.targetStatuses.getValue(eveningTarget.id).arrivals.map { it.primaryLabel },
            )
            assertTrue(state.targetStatuses.getValue(eveningTarget.id).hasLoaded)
            assertFalse(state.targetStatuses.getValue(eveningTarget.id).isLoading)
            assertFalse(state.isRefreshing)
            assertTrue(
                state.targetStatuses.values
                    .flatMap { it.arrivals }
                    .none { it.primaryLabel == "MORNING_OLD" },
            )
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `saving line B at an interchange persists line B station id`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val store = preferencesStore()
            val repository = SearchTransitRepository(
                results = listOf(
                    subwaySearchResult(stationId = "station-A", lineId = "A", lineName = "A호선"),
                    subwaySearchResult(stationId = "station-B", lineId = "B", lineName = "B호선"),
                ),
            )
            val viewModel = CommuteFlowViewModel(
                preferencesStore = store,
                transitRepository = repository,
                workerDispatcher = dispatcher,
                currentMinuteOfDay = { 8 * 60 },
            )

            viewModel.openSearch(CommuteProfile.MORNING)
            viewModel.selectSearchProvider(TransitProvider.SEOUL_SUBWAY)
            viewModel.changeSearchQuery("환승역")
            advanceUntilIdle()

            val lineAResult = viewModel.uiState.value.searchEditor.results
                .first { it.lineId == "A" }
            viewModel.selectSearchResult(lineAResult)
            val lineB = viewModel.uiState.value.searchEditor.subwayLines
                .first { it.lineId == "B" }
            viewModel.selectSubwayLine(lineB)
            val direction = viewModel.uiState.value.searchEditor.subwayDirections.first()
            viewModel.selectSubwayDirection(direction)
            viewModel.saveSearchSelection()
            advanceUntilIdle()

            val saved = store.settings.value.morning.targets.single() as SubwayTransitTarget
            assertEquals("station-B", saved.stationId)
            assertEquals("B", saved.lineId)
            assertEquals("B호선", saved.lineName)
            assertEquals("환승역", saved.stationName)
        } finally {
            Dispatchers.resetMain()
        }
    }

    private fun preferencesStore(): CommutePreferencesStore = CommutePreferencesStore(
        sharedPreferences = FakeSharedPreferences().instance,
        codec = RecordingCodec(),
    )

    private fun busTarget(id: String, stationName: String) = BusTransitTarget(
        id = id,
        provider = TransitProvider.GYEONGGI_BUS,
        stationId = "station-$id",
        stationName = stationName,
        routes = listOf(BusRouteRef(routeId = "route-$id", routeName = "55")),
    )

    private fun busSnapshot(routeName: String) = TransitArrivalSnapshot.Bus(
        arrivals = listOf(
            BusArrival(
                routeId = "route-$routeName",
                routeName = routeName,
                destination = "종점",
                routeTypeCode = null,
                status = "운행중",
                arrivalSeconds = 120,
                stopsAway = 2,
                currentStationName = null,
                nextArrivalSeconds = null,
                nextStopsAway = null,
                vehicleTypeCode = null,
                remainingSeats = null,
                crowdednessCode = null,
            ),
        ),
    )

    private fun subwaySearchResult(
        stationId: String,
        lineId: String,
        lineName: String,
    ) = TransitSearchResult(
        provider = TransitProvider.SEOUL_SUBWAY,
        stationId = stationId,
        stationName = "환승역",
        lineId = lineId,
        lineName = lineName,
    )

    private class ControlledArrivalRepository : TransitRepository {
        val requestedTargetIds = mutableListOf<String>()
        val requestJobs = mutableMapOf<String, Job>()
        private val continuations = mutableMapOf<String, Continuation<TransitArrivalSnapshot>>()

        override suspend fun search(
            provider: TransitProvider,
            query: String,
        ): List<TransitSearchResult> = emptyList()

        override suspend fun routesAt(
            provider: TransitProvider,
            stationId: String,
        ): List<BusRouteOption> = emptyList()

        override suspend fun arrivalsFor(target: TransitTarget): TransitArrivalSnapshot {
            requestedTargetIds += target.id
            requestJobs[target.id] = currentCoroutineContext()[Job]
                ?: error("Arrival request has no Job")
            return suspendCoroutine { continuation ->
                continuations[target.id] = continuation
            }
        }

        fun complete(targetId: String, snapshot: TransitArrivalSnapshot) {
            continuations.remove(targetId)?.resume(snapshot)
                ?: error("No pending request for $targetId")
        }
    }

    private class SearchTransitRepository(
        private val results: List<TransitSearchResult>,
    ) : TransitRepository {
        override suspend fun search(
            provider: TransitProvider,
            query: String,
        ): List<TransitSearchResult> = results.filter { it.provider == provider }

        override suspend fun routesAt(
            provider: TransitProvider,
            stationId: String,
        ): List<BusRouteOption> = emptyList()

        override suspend fun arrivalsFor(target: TransitTarget): TransitArrivalSnapshot =
            TransitArrivalSnapshot.Subway(emptyList())
    }

    private class RecordingCodec : CommuteSettingsCodec {
        private val values = mutableMapOf<String, CommuteSettings>()
        private var nextId = 0

        override fun encode(settings: CommuteSettings): String = "encoded-${nextId++}".also { encoded ->
            values[encoded] = settings
        }

        override fun decode(encoded: String): CommuteSettings =
            values[encoded] ?: error("Unreadable value")
    }

    private class FakeSharedPreferences {
        private var storedValue: String? = null
        private var pendingValue: String? = null
        private var hasPendingValue = false

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
            }
        }
    }
}
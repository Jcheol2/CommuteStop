package com.jcheol.commuteflow.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.jcheol.commuteflow.data.GbisApiException
import com.jcheol.commuteflow.data.MissingApiKeyException
import com.jcheol.commuteflow.data.MissingProviderKeyException
import com.jcheol.commuteflow.data.TransitApiException
import com.jcheol.commuteflow.data.TransitArrivalSnapshot
import com.jcheol.commuteflow.data.TransitRepository
import com.jcheol.commuteflow.data.local.CommutePreferencesStore
import com.jcheol.commuteflow.domain.BusRouteRef
import com.jcheol.commuteflow.domain.BusTransitTarget
import com.jcheol.commuteflow.domain.CommuteProfile
import com.jcheol.commuteflow.domain.SubwayTransitTarget
import com.jcheol.commuteflow.domain.ThemePreference
import com.jcheol.commuteflow.domain.TransitProvider
import com.jcheol.commuteflow.domain.TransitSearchResult
import com.jcheol.commuteflow.domain.activeProfileAt
import com.jcheol.commuteflow.domain.formatArrivalTime
import com.jcheol.commuteflow.domain.formatStopsAway
import com.jcheol.commuteflow.domain.profileSettings
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext

class CommuteFlowViewModel(
    private val preferencesStore: CommutePreferencesStore,
    private val transitRepository: TransitRepository,
    private val workerDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val currentMinuteOfDay: () -> Int = ::deviceMinuteOfDay,
    private val currentTimeMillis: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    private val initialSettings = preferencesStore.settings.value
    private val mutableUiState = MutableStateFlow(
        CommuteFlowUiState(
            selectedProfile = initialSettings.activeProfileAt(currentMinuteOfDay()),
            settings = initialSettings,
        ),
    )
    val uiState: StateFlow<CommuteFlowUiState> = mutableUiState.asStateFlow()

    private var refreshJob: Job? = null
    private var refreshGeneration = 0L
    private var searchJob: Job? = null
    private var selectionJob: Job? = null
    private var searchResults: List<TransitSearchResult> = emptyList()
    private var editorReturnPage = CommuteFlowPage.SETTINGS

    init {
        viewModelScope.launch {
            preferencesStore.settings.collect { settings ->
                mutableUiState.update { it.copy(settings = settings) }
            }
        }
    }

    fun selectProfile(profile: CommuteProfile) {
        if (profile == mutableUiState.value.selectedProfile) return
        cancelRefresh()
        mutableUiState.update {
            it.copy(
                page = CommuteFlowPage.HOME,
                selectedProfile = profile,
                lastUpdatedLabel = null,
                bannerMessage = null,
            )
        }
        refresh()
    }

    fun openSettings() {
        cancelRefresh()
        mutableUiState.update { it.copy(page = CommuteFlowPage.SETTINGS, bannerMessage = null) }
    }

    fun navigateBack() {
        val currentPage = mutableUiState.value.page
        val destination = when (currentPage) {
            CommuteFlowPage.SEARCH_EDITOR -> editorReturnPage
            CommuteFlowPage.SETTINGS,
            CommuteFlowPage.HOME,
            -> CommuteFlowPage.HOME
        }
        mutableUiState.update { state ->
            state.copy(
                page = destination,
                bannerMessage = null,
            )
        }
        if (destination == CommuteFlowPage.HOME) {
            cancelRefresh()
            refresh()
        }
    }

    fun openSearch(profile: CommuteProfile) {
        if (mutableUiState.value.settings.profileSettings(profile).targets.size >= MAX_TARGETS) return
        editorReturnPage = mutableUiState.value.page
        cancelRefresh()
        cancelEditorJobs()
        searchResults = emptyList()
        mutableUiState.update {
            it.copy(
                page = CommuteFlowPage.SEARCH_EDITOR,
                searchEditor = SearchEditorUiState(
                    profile = profile,
                    provider = TransitProvider.GYEONGGI_BUS,
                ),
                bannerMessage = null,
            )
        }
    }

    fun setTheme(profile: CommuteProfile, theme: ThemePreference) {
        runStoreUpdate { preferencesStore.setTheme(profile, theme) }
    }

    fun adjustCutoffMinute(delta: Int) {
        val current = mutableUiState.value.settings.cutoffMinute
        val adjusted = ((current + delta) % MINUTES_PER_DAY + MINUTES_PER_DAY) % MINUTES_PER_DAY
        runStoreUpdate { preferencesStore.setCutoffMinute(adjusted) }
    }

    fun moveTarget(profile: CommuteProfile, targetId: String, delta: Int) {
        val targets = mutableUiState.value.settings.profileSettings(profile).targets
        val from = targets.indexOfFirst { it.id == targetId }
        val to = from + delta
        if (from !in targets.indices || to !in targets.indices) return
        runStoreUpdate { preferencesStore.reorderTarget(profile, from, to) }
    }

    fun deleteTarget(profile: CommuteProfile, targetId: String) {
        runStoreUpdate { preferencesStore.removeTarget(profile, targetId) }
        mutableUiState.update { state ->
            state.copy(targetStatuses = state.targetStatuses - targetId)
        }
    }

    fun selectSearchProvider(provider: TransitProvider) {
        cancelEditorJobs()
        searchResults = emptyList()
        mutableUiState.update { state ->
            state.copy(searchEditor = SearchEditorUiState(
                profile = state.searchEditor.profile,
                provider = provider,
            ))
        }
    }

    fun changeSearchQuery(query: String) {
        selectionJob?.cancel()
        searchJob?.cancel()
        searchResults = emptyList()
        mutableUiState.update { state ->
            state.copy(
                searchEditor = state.searchEditor.copy(
                    query = query,
                    isSearching = false,
                    errorMessage = null,
                    results = emptyList(),
                    selectedResult = null,
                    busRoutes = emptyList(),
                    selectedBusRouteIds = emptySet(),
                    subwayLines = emptyList(),
                    selectedSubwayLineId = null,
                    selectedSubwayDirectionId = null,
                ),
            )
        }
        if (query.trim().length < MIN_SEARCH_LENGTH) return

        val provider = mutableUiState.value.searchEditor.provider
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MILLIS)
            mutableUiState.update { state ->
                state.copy(searchEditor = state.searchEditor.copy(isSearching = true))
            }
            runCatching {
                withContext(workerDispatcher) { transitRepository.search(provider, query.trim()) }
            }.onSuccess { results ->
                searchResults = results
                mutableUiState.update { state ->
                    state.copy(
                        searchEditor = state.searchEditor.copy(
                            isSearching = false,
                            results = results.map { result -> result.toUi() },
                        ),
                    )
                }
            }.onFailure { error ->
                if (error is CancellationException) return@onFailure
                mutableUiState.update { state ->
                    state.copy(
                        searchEditor = state.searchEditor.copy(
                            isSearching = false,
                            errorMessage = error.toUserMessage(),
                        ),
                    )
                }
            }
        }
    }

    fun selectSearchResult(result: TransitSearchResultUi) {
        selectionJob?.cancel()
        val matched = searchResults.firstOrNull {
            it.provider == result.provider &&
                it.stationId == result.stationId &&
                it.lineId == result.lineId
        } ?: return
        mutableUiState.update { state ->
            state.copy(
                searchEditor = state.searchEditor.copy(
                    selectedResult = result,
                    isSearching = matched.provider != TransitProvider.SEOUL_SUBWAY,
                    errorMessage = null,
                    busRoutes = emptyList(),
                    selectedBusRouteIds = emptySet(),
                    subwayLines = if (matched.provider == TransitProvider.SEOUL_SUBWAY) {
                        searchResults.asSequence()
                            .filter { it.provider == matched.provider && it.stationName == matched.stationName }
                            .mapNotNull { search ->
                                val lineId = search.lineId ?: return@mapNotNull null
                                SubwayLineOptionUi(
                                    stationId = search.stationId,
                                    lineId = lineId,
                                    lineName = search.lineName ?: lineId,
                                )
                            }
                            .distinctBy { it.lineId }
                            .toList()
                    } else {
                        emptyList()
                    },
                    selectedSubwayLineId = matched.lineId,
                    subwayDirections = defaultDirections(matched.lineName),
                    selectedSubwayDirectionId = null,
                ),
            )
        }

        if (matched.provider == TransitProvider.SEOUL_SUBWAY) return
        selectionJob = viewModelScope.launch {
            runCatching {
                withContext(workerDispatcher) {
                    transitRepository.routesAt(matched.provider, matched.stationId)
                }
            }.onSuccess { routes ->
                mutableUiState.update { state ->
                    state.copy(
                        searchEditor = state.searchEditor.copy(
                            isSearching = false,
                            busRoutes = routes.map { route ->
                                BusRouteRef(
                                    routeId = route.routeId,
                                    routeName = route.routeName,
                                    destinationName = route.destinationName,
                                )
                            },
                        ),
                    )
                }
            }.onFailure { error ->
                if (error is CancellationException) return@onFailure
                mutableUiState.update { state ->
                    state.copy(
                        searchEditor = state.searchEditor.copy(
                            isSearching = false,
                            errorMessage = error.toUserMessage(),
                        ),
                    )
                }
            }
        }
    }

    fun toggleBusRoute(route: BusRouteRef) {
        mutableUiState.update { state ->
            val selected = state.searchEditor.selectedBusRouteIds
            state.copy(
                searchEditor = state.searchEditor.copy(
                    selectedBusRouteIds = if (route.routeId in selected) {
                        selected - route.routeId
                    } else {
                        selected + route.routeId
                    },
                ),
            )
        }
    }

    fun selectSubwayLine(line: SubwayLineOptionUi) {
        mutableUiState.update { state ->
            state.copy(
                searchEditor = state.searchEditor.copy(
                    selectedSubwayLineId = line.lineId,
                    subwayDirections = defaultDirections(line.lineName),
                    selectedSubwayDirectionId = null,
                ),
            )
        }
    }

    fun selectSubwayDirection(direction: SubwayDirectionOptionUi) {
        mutableUiState.update { state ->
            state.copy(
                searchEditor = state.searchEditor.copy(
                    selectedSubwayDirectionId = direction.directionId,
                ),
            )
        }
    }

    fun saveSearchSelection() {
        if (mutableUiState.value.page != CommuteFlowPage.SEARCH_EDITOR) return
        val editor = mutableUiState.value.searchEditor
        val result = editor.selectedResult ?: return
        if (editor.isSaving || result.provider != editor.provider) return
        val target = when (editor.provider) {
            TransitProvider.SEOUL_BUS,
            TransitProvider.GYEONGGI_BUS,
            -> {
                val routes = editor.busRoutes.filter { it.routeId in editor.selectedBusRouteIds }
                if (routes.isEmpty()) return
                BusTransitTarget(
                    id = UUID.randomUUID().toString(),
                    provider = editor.provider,
                    stationId = result.stationId,
                    stationName = result.stationName,
                    routes = routes,
                    stationNumber = result.stationNumber,
                    regionName = result.regionName,
                )
            }

            TransitProvider.SEOUL_SUBWAY -> {
                val line = editor.subwayLines.firstOrNull {
                    it.lineId == editor.selectedSubwayLineId
                } ?: return
                val direction = editor.subwayDirections.firstOrNull {
                    it.directionId == editor.selectedSubwayDirectionId
                } ?: return
                SubwayTransitTarget(
                    id = UUID.randomUUID().toString(),
                    stationId = line.stationId,
                    stationName = result.stationName,
                    lineId = line.lineId,
                    lineName = line.lineName,
                    directionId = direction.directionId,
                    directionLabel = direction.directionLabel,
                )
            }
        }
        mutableUiState.update { state ->
            state.copy(searchEditor = state.searchEditor.copy(isSaving = true, errorMessage = null))
        }
        runCatching { preferencesStore.saveTarget(editor.profile, target) }
            .onSuccess {
                mutableUiState.update { state ->
                    state.copy(
                        page = editorReturnPage,
                        settings = preferencesStore.settings.value,
                        searchEditor = state.searchEditor.copy(isSaving = false),
                    )
                }
                if (editorReturnPage == CommuteFlowPage.HOME) refresh()
            }
            .onFailure { error ->
                mutableUiState.update { state ->
                    state.copy(
                        searchEditor = state.searchEditor.copy(
                            isSaving = false,
                            errorMessage = error.toUserMessage(),
                        ),
                    )
                }
            }
    }

    fun refresh() {
        if (mutableUiState.value.page != CommuteFlowPage.HOME) return
        if (refreshJob?.isActive == true) return
        val state = mutableUiState.value
        val profile = state.selectedProfile
        val targets = state.settings.profileSettings(profile).targets
        val targetIds = targets.map { it.id }
        val generation = ++refreshGeneration
        if (targets.isEmpty()) {
            mutableUiState.update { it.copy(isRefreshing = false, targetStatuses = emptyMap()) }
            return
        }
        refreshJob = viewModelScope.launch {
            mutableUiState.update { current ->
                current.copy(
                    isRefreshing = true,
                    bannerMessage = null,
                    targetStatuses = targets.associate { target ->
                        val previous = current.targetStatuses[target.id]
                        target.id to (previous?.copy(isLoading = true, errorMessage = null)
                            ?: TransitTargetStatusUi(isLoading = true))
                    },
                )
            }
            val hadSuccessfulRequest = supervisorScope {
                val requests = targets.map { target ->
                    async(workerDispatcher) {
                        val result = try {
                            Result.success(transitRepository.arrivalsFor(target))
                        } catch (error: CancellationException) {
                            throw error
                        } catch (error: Throwable) {
                            Result.failure(error)
                        }
                        if (!isCurrentRefresh(generation, profile, targetIds)) return@async false
                        mutableUiState.update { current ->
                            if (!isCurrentRefresh(generation, profile, targetIds)) {
                                return@update current
                            }
                            val previous = current.targetStatuses[target.id]
                                ?: TransitTargetStatusUi()
                            val updated = result.fold(
                                onSuccess = { snapshot ->
                                    TransitTargetStatusUi(
                                        arrivals = snapshot.toUiLines(),
                                        isLoading = false,
                                        hasLoaded = true,
                                    )
                                },
                                onFailure = { error ->
                                    previous.copy(
                                        isLoading = false,
                                        errorMessage = error.toUserMessage(),
                                    )
                                },
                            )
                            current.copy(targetStatuses = current.targetStatuses + (target.id to updated))
                        }
                        result.isSuccess
                    }
                }
                requests.map { it.await() }.any { it }
            }
            if (!isCurrentRefresh(generation, profile, targetIds)) return@launch
            mutableUiState.update { current ->
                current.copy(
                    isRefreshing = false,
                    lastUpdatedLabel = if (hadSuccessfulRequest) {
                        SimpleDateFormat("a h:mm:ss", Locale.KOREA)
                            .format(Date(currentTimeMillis()))
                    } else {
                        current.lastUpdatedLabel
                    },
                )
            }
        }
    }

    fun cancelRefresh() {
        refreshGeneration += 1
        refreshJob?.cancel()
        refreshJob = null
        mutableUiState.update { it.copy(isRefreshing = false) }
    }

    private fun isCurrentRefresh(
        generation: Long,
        profile: CommuteProfile,
        targetIds: List<String>,
    ): Boolean {
        val current = mutableUiState.value
        return generation == refreshGeneration &&
            current.page == CommuteFlowPage.HOME &&
            current.selectedProfile == profile &&
            current.settings.profileSettings(profile).targets.map { it.id } == targetIds
    }

    private fun runStoreUpdate(update: () -> Unit) {
        runCatching(update).onFailure { error ->
            mutableUiState.update { it.copy(bannerMessage = error.toUserMessage()) }
        }
    }

    private fun cancelEditorJobs() {
        searchJob?.cancel()
        selectionJob?.cancel()
    }

    private fun TransitSearchResult.toUi(): TransitSearchResultUi = TransitSearchResultUi(
        provider = provider,
        stationId = stationId,
        stationName = stationName,
        stationNumber = stationNumber,
        regionName = detail?.takeUnless { provider == TransitProvider.SEOUL_SUBWAY },
        supportingText = lineName,
        lineId = lineId,
    )

    private fun TransitArrivalSnapshot.toUiLines(): List<ArrivalLineUi> = when (this) {
        is TransitArrivalSnapshot.Bus -> arrivals.map { arrival ->
            ArrivalLineUi(
                primaryLabel = arrival.routeName,
                stopsLabel = formatStopsAway(arrival.stopsAway),
                arrivalLabel = formatArrivalTime(arrival.arrivalSeconds),
            )
        }

        is TransitArrivalSnapshot.Subway -> arrivals.take(MAX_SUBWAY_ARRIVALS).map { arrival ->
            ArrivalLineUi(
                primaryLabel = "${arrival.destination}행",
                stopsLabel = arrival.message.ifBlank { arrival.direction },
                arrivalLabel = formatArrivalTime(arrival.arrivalSeconds),
            )
        }
    }

    private fun defaultDirections(lineName: String?): List<SubwayDirectionOptionUi> =
        if (lineName?.contains("2호선") == true) {
            listOf(
                SubwayDirectionOptionUi("UP", "내선순환"),
                SubwayDirectionOptionUi("DOWN", "외선순환"),
            )
        } else {
            listOf(
                SubwayDirectionOptionUi("UP", "상행"),
                SubwayDirectionOptionUi("DOWN", "하행"),
            )
        }

    private fun Throwable.toUserMessage(): String = when (this) {
        is MissingApiKeyException -> "경기버스 API 키 설정이 필요합니다."
        is MissingProviderKeyException -> "$providerName 설정이 필요합니다. ($propertyName)"
        is GbisApiException -> userMessage
        is TransitApiException -> userMessage
        is SocketTimeoutException -> "교통 정보 서버 응답이 늦어지고 있습니다."
        is UnknownHostException -> "인터넷 연결을 확인해 주세요."
        is IOException -> "교통 정보를 불러오지 못했습니다."
        is IllegalArgumentException -> message ?: "설정값을 확인해 주세요."
        else -> "잠시 후 다시 시도해 주세요."
    }

    class Factory(
        private val preferencesStore: CommutePreferencesStore,
        private val transitRepository: TransitRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(CommuteFlowViewModel::class.java))
            return CommuteFlowViewModel(preferencesStore, transitRepository) as T
        }
    }

    private companion object {
        const val MINUTES_PER_DAY = 24 * 60
        const val MIN_SEARCH_LENGTH = 2
        const val MAX_TARGETS = 3
        const val MAX_SUBWAY_ARRIVALS = 1
        const val SEARCH_DEBOUNCE_MILLIS = 350L

        fun deviceMinuteOfDay(): Int = Calendar.getInstance().run {
            get(Calendar.HOUR_OF_DAY) * 60 + get(Calendar.MINUTE)
        }
    }
}

package com.jcheol.commutestop

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.jcheol.commutestop.data.DefaultTransitRepository
import com.jcheol.commutestop.data.GbisApiClient
import com.jcheol.commutestop.data.SeoulBusApiClient
import com.jcheol.commutestop.data.SeoulSubwayApiClient
import com.jcheol.commutestop.data.local.CommutePreferencesStore
import com.jcheol.commutestop.domain.ThemePreference
import com.jcheol.commutestop.domain.profileSettings
import com.jcheol.commutestop.ui.CommuteStopCallbacks
import com.jcheol.commutestop.ui.CommuteStopPage
import com.jcheol.commutestop.ui.CommuteStopScreen
import com.jcheol.commutestop.ui.CommuteStopViewModel
import com.jcheol.commutestop.ui.theme.CommuteStopTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private val commuteStopViewModel: CommuteStopViewModel by viewModels {
        CommuteStopViewModel.Factory(
            preferencesStore = CommutePreferencesStore(applicationContext),
            transitRepository = DefaultTransitRepository(
                gbisClient = GbisApiClient(BuildConfig.GBIS_SERVICE_KEY),
                seoulBusClient = SeoulBusApiClient(BuildConfig.SEOUL_TRANSIT_PROXY_URL),
                subwayClient = SeoulSubwayApiClient(BuildConfig.SEOUL_TRANSIT_PROXY_URL),
            ),
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val uiState by commuteStopViewModel.uiState.collectAsStateWithLifecycle()
            val themedProfile = if (uiState.page == CommuteStopPage.SEARCH_EDITOR) {
                uiState.searchEditor.profile
            } else {
                uiState.selectedProfile
            }
            val darkTheme = uiState.settings
                .profileSettings(themedProfile)
                .theme == ThemePreference.DARK
            val lifecycleOwner = LocalLifecycleOwner.current

            SideEffect {
                enableEdgeToEdge(
                    statusBarStyle = if (darkTheme) {
                        SystemBarStyle.dark(Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                    },
                    navigationBarStyle = if (darkTheme) {
                        SystemBarStyle.dark(Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                    },
                )
            }

            LaunchedEffect(lifecycleOwner, commuteStopViewModel) {
                lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    try {
                        commuteStopViewModel.refresh()
                        while (true) {
                            delay(REFRESH_INTERVAL_MILLIS)
                            commuteStopViewModel.refresh()
                        }
                    } finally {
                        commuteStopViewModel.cancelRefresh()
                    }
                }
            }

            BackHandler(enabled = uiState.page != CommuteStopPage.HOME) {
                commuteStopViewModel.navigateBack()
            }

            CommuteStopTheme(darkTheme = darkTheme) {
                CommuteStopScreen(
                    uiState = uiState,
                    callbacks = CommuteStopCallbacks(
                        onProfileSelected = commuteStopViewModel::selectProfile,
                        onRefresh = commuteStopViewModel::refresh,
                        onOpenSettings = commuteStopViewModel::openSettings,
                        onBack = commuteStopViewModel::navigateBack,
                        onOpenSearch = commuteStopViewModel::openSearch,
                        onThemeSelected = commuteStopViewModel::setTheme,
                        onAdjustCutoffMinute = commuteStopViewModel::adjustCutoffMinute,
                        onMoveTarget = commuteStopViewModel::moveTarget,
                        onDeleteTarget = commuteStopViewModel::deleteTarget,
                        onSearchProviderSelected = commuteStopViewModel::selectSearchProvider,
                        onSearchQueryChanged = commuteStopViewModel::changeSearchQuery,
                        onSearchResultSelected = commuteStopViewModel::selectSearchResult,
                        onBusRouteToggled = commuteStopViewModel::toggleBusRoute,
                        onSubwayLineSelected = commuteStopViewModel::selectSubwayLine,
                        onSubwayDirectionSelected = commuteStopViewModel::selectSubwayDirection,
                        onSaveSearchSelection = commuteStopViewModel::saveSearchSelection,
                    ),
                )
            }
        }
    }

    private companion object {
        const val REFRESH_INTERVAL_MILLIS = 30_000L
    }
}

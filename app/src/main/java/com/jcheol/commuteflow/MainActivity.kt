package com.jcheol.commuteflow

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
import com.jcheol.commuteflow.data.DefaultTransitRepository
import com.jcheol.commuteflow.data.GbisApiClient
import com.jcheol.commuteflow.data.SeoulBusApiClient
import com.jcheol.commuteflow.data.SeoulSubwayApiClient
import com.jcheol.commuteflow.data.local.CommutePreferencesStore
import com.jcheol.commuteflow.domain.ThemePreference
import com.jcheol.commuteflow.domain.profileSettings
import com.jcheol.commuteflow.ui.CommuteFlowCallbacks
import com.jcheol.commuteflow.ui.CommuteFlowPage
import com.jcheol.commuteflow.ui.CommuteFlowScreen
import com.jcheol.commuteflow.ui.CommuteFlowViewModel
import com.jcheol.commuteflow.ui.theme.CommuteFlowTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private val commuteFlowViewModel: CommuteFlowViewModel by viewModels {
        CommuteFlowViewModel.Factory(
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
            val uiState by commuteFlowViewModel.uiState.collectAsStateWithLifecycle()
            val themedProfile = if (uiState.page == CommuteFlowPage.SEARCH_EDITOR) {
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

            LaunchedEffect(lifecycleOwner, commuteFlowViewModel) {
                lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    try {
                        commuteFlowViewModel.refresh()
                        while (true) {
                            delay(REFRESH_INTERVAL_MILLIS)
                            commuteFlowViewModel.refresh()
                        }
                    } finally {
                        commuteFlowViewModel.cancelRefresh()
                    }
                }
            }

            BackHandler(enabled = uiState.page != CommuteFlowPage.HOME) {
                commuteFlowViewModel.navigateBack()
            }

            CommuteFlowTheme(darkTheme = darkTheme) {
                CommuteFlowScreen(
                    uiState = uiState,
                    callbacks = CommuteFlowCallbacks(
                        onProfileSelected = commuteFlowViewModel::selectProfile,
                        onRefresh = commuteFlowViewModel::refresh,
                        onOpenSettings = commuteFlowViewModel::openSettings,
                        onBack = commuteFlowViewModel::navigateBack,
                        onOpenSearch = commuteFlowViewModel::openSearch,
                        onThemeSelected = commuteFlowViewModel::setTheme,
                        onAdjustCutoffMinute = commuteFlowViewModel::adjustCutoffMinute,
                        onMoveTarget = commuteFlowViewModel::moveTarget,
                        onDeleteTarget = commuteFlowViewModel::deleteTarget,
                        onSearchProviderSelected = commuteFlowViewModel::selectSearchProvider,
                        onSearchQueryChanged = commuteFlowViewModel::changeSearchQuery,
                        onSearchResultSelected = commuteFlowViewModel::selectSearchResult,
                        onBusRouteToggled = commuteFlowViewModel::toggleBusRoute,
                        onSubwayLineSelected = commuteFlowViewModel::selectSubwayLine,
                        onSubwayDirectionSelected = commuteFlowViewModel::selectSubwayDirection,
                        onSaveSearchSelection = commuteFlowViewModel::saveSearchSelection,
                    ),
                )
            }
        }
    }

    private companion object {
        const val REFRESH_INTERVAL_MILLIS = 30_000L
    }
}

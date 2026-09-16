package com.jcheol.commutestop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jcheol.commutestop.domain.BusRouteRef
import com.jcheol.commutestop.domain.BusTransitTarget
import com.jcheol.commutestop.domain.CommuteProfile
import com.jcheol.commutestop.domain.CommuteSettings
import com.jcheol.commutestop.domain.ProfileSettings
import com.jcheol.commutestop.domain.SubwayTransitTarget
import com.jcheol.commutestop.domain.ThemePreference
import com.jcheol.commutestop.domain.TransitProvider
import com.jcheol.commutestop.domain.TransitTarget

enum class CommuteStopPage {
    HOME,
    SETTINGS,
    SEARCH_EDITOR,
}

data class ArrivalLineUi(
    val primaryLabel: String,
    val stopsLabel: String? = null,
    val arrivalLabel: String,
)

data class TransitTargetStatusUi(
    val arrivals: List<ArrivalLineUi> = emptyList(),
    val isLoading: Boolean = false,
    val hasLoaded: Boolean = false,
    val errorMessage: String? = null,
)

data class TransitSearchResultUi(
    val provider: TransitProvider,
    val stationId: String,
    val stationName: String,
    val stationNumber: String? = null,
    val regionName: String? = null,
    val supportingText: String? = null,
    val lineId: String? = null,
) {
    val stableId: String = "${provider.name}:$stationId:${lineId.orEmpty()}"
}

data class SubwayLineOptionUi(
    val stationId: String,
    val lineId: String,
    val lineName: String,
)

data class SubwayDirectionOptionUi(
    val directionId: String,
    val directionLabel: String,
)

data class SearchEditorUiState(
    val profile: CommuteProfile = CommuteProfile.MORNING,
    val provider: TransitProvider = TransitProvider.SEOUL_BUS,
    val query: String = "",
    val isSearching: Boolean = false,
    val errorMessage: String? = null,
    val results: List<TransitSearchResultUi> = emptyList(),
    val selectedResult: TransitSearchResultUi? = null,
    val busRoutes: List<BusRouteRef> = emptyList(),
    val selectedBusRouteIds: Set<String> = emptySet(),
    val subwayLines: List<SubwayLineOptionUi> = emptyList(),
    val selectedSubwayLineId: String? = null,
    val subwayDirections: List<SubwayDirectionOptionUi> = emptyList(),
    val selectedSubwayDirectionId: String? = null,
    val isSaving: Boolean = false,
) {
    val canSave: Boolean
        get() = selectedResult?.provider == provider && when (provider) {
            TransitProvider.SEOUL_BUS,
            TransitProvider.GYEONGGI_BUS,
            -> selectedBusRouteIds.isNotEmpty()

            TransitProvider.SEOUL_SUBWAY ->
                selectedSubwayLineId != null && selectedSubwayDirectionId != null
        }
}

data class CommuteStopUiState(
    val page: CommuteStopPage = CommuteStopPage.HOME,
    val selectedProfile: CommuteProfile = CommuteProfile.MORNING,
    val settings: CommuteSettings,
    val targetStatuses: Map<String, TransitTargetStatusUi> = emptyMap(),
    val isRefreshing: Boolean = false,
    val lastUpdatedLabel: String? = null,
    val bannerMessage: String? = null,
    val searchEditor: SearchEditorUiState = SearchEditorUiState(),
)

data class CommuteStopCallbacks(
    val onProfileSelected: (CommuteProfile) -> Unit = {},
    val onRefresh: () -> Unit = {},
    val onOpenSettings: () -> Unit = {},
    val onBack: () -> Unit = {},
    val onOpenSearch: (CommuteProfile) -> Unit = {},
    val onThemeSelected: (CommuteProfile, ThemePreference) -> Unit = { _, _ -> },
    val onAdjustCutoffMinute: (Int) -> Unit = {},
    val onMoveTarget: (CommuteProfile, String, Int) -> Unit = { _, _, _ -> },
    val onDeleteTarget: (CommuteProfile, String) -> Unit = { _, _ -> },
    val onSearchProviderSelected: (TransitProvider) -> Unit = {},
    val onSearchQueryChanged: (String) -> Unit = {},
    val onSearchResultSelected: (TransitSearchResultUi) -> Unit = {},
    val onBusRouteToggled: (BusRouteRef) -> Unit = {},
    val onSubwayLineSelected: (SubwayLineOptionUi) -> Unit = {},
    val onSubwayDirectionSelected: (SubwayDirectionOptionUi) -> Unit = {},
    val onSaveSearchSelection: () -> Unit = {},
)

@Composable
fun CommuteStopScreen(
    uiState: CommuteStopUiState,
    callbacks: CommuteStopCallbacks,
    modifier: Modifier = Modifier,
) {
    when (uiState.page) {
        CommuteStopPage.HOME -> HomeScreen(
            uiState = uiState,
            callbacks = callbacks,
            modifier = modifier,
        )

        CommuteStopPage.SETTINGS -> SettingsScreen(
            settings = uiState.settings,
            callbacks = callbacks,
            modifier = modifier,
        )

        CommuteStopPage.SEARCH_EDITOR -> SearchEditorScreen(
            searchState = uiState.searchEditor,
            callbacks = callbacks,
            modifier = modifier,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(
    uiState: CommuteStopUiState,
    callbacks: CommuteStopCallbacks,
    modifier: Modifier,
) {
    val profileSettings = uiState.settings.forProfile(uiState.selectedProfile)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "출퇴근 정류장",
                                modifier = Modifier.semantics { heading() },
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = uiState.lastUpdatedLabel?.let { "업데이트 $it" }
                                    ?: "서울·경기 실시간 교통",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = callbacks.onRefresh,
                            enabled = !uiState.isRefreshing,
                            modifier = Modifier.semantics {
                                contentDescription = "도착 정보 새로고침"
                            },
                        ) {
                            Text(
                                text = "↻",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        IconButton(
                            onClick = callbacks.onOpenSettings,
                            modifier = Modifier.semantics {
                                contentDescription = "설정 열기"
                            },
                        ) {
                            Text(text = "⚙", fontSize = 21.sp)
                        }
                    },
                )
                if (uiState.isRefreshing) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                ProfileTabs(
                    selectedProfile = uiState.selectedProfile,
                    onProfileSelected = callbacks.onProfileSelected,
                )
            }
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (uiState.bannerMessage != null) {
                item(key = "banner") {
                    MessageSurface(
                        message = uiState.bannerMessage,
                        isError = true,
                    )
                }
            }

            item(key = "profile-heading") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${uiState.selectedProfile.label()} 교통편",
                        modifier = Modifier.semantics { heading() },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "${profileSettings.targets.size}/$MAX_TARGETS",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }

            if (profileSettings.targets.isEmpty()) {
                item(key = "empty") {
                    EmptyProfileCard(
                        profile = uiState.selectedProfile,
                        onAdd = { callbacks.onOpenSearch(uiState.selectedProfile) },
                    )
                }
            } else {
                items(
                    items = profileSettings.targets,
                    key = TransitTarget::id,
                ) { target ->
                    TransitTargetCard(
                        target = target,
                        status = uiState.targetStatuses[target.id]
                            ?: TransitTargetStatusUi(isLoading = true),
                    )
                }

                if (profileSettings.targets.size < MAX_TARGETS) {
                    item(key = "add-target") {
                        OutlinedButton(
                            onClick = { callbacks.onOpenSearch(uiState.selectedProfile) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("+ 교통편 추가")
                        }
                    }
                }
            }

            item(key = "refresh-note") {
                Text(
                    text = "도착 정보는 앱을 보고 있을 때 자동으로 갱신됩니다.",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 8.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun ProfileTabs(
    selectedProfile: CommuteProfile,
    onProfileSelected: (CommuteProfile) -> Unit,
) {
    val profiles = listOf(CommuteProfile.MORNING, CommuteProfile.EVENING)
    TabRow(selectedTabIndex = profiles.indexOf(selectedProfile)) {
        profiles.forEach { profile ->
            Tab(
                selected = profile == selectedProfile,
                onClick = { onProfileSelected(profile) },
                text = {
                    Text(
                        text = profile.label(),
                        fontWeight = if (profile == selectedProfile) {
                            FontWeight.Bold
                        } else {
                            FontWeight.Normal
                        },
                    )
                },
            )
        }
    }
}

@Composable
private fun TransitTargetCard(
    target: TransitTarget,
    status: TransitTargetStatusUi,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = target.stationName,
                        modifier = Modifier.semantics { heading() },
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = target.supportingLabel(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                ProviderBadge(label = target.provider.shortLabel())
            }

            when {
                status.isLoading && !status.hasLoaded -> {
                    Row(
                        modifier = Modifier.padding(top = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text(
                            text = "도착 정보를 불러오는 중",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }

                status.errorMessage != null && status.arrivals.isEmpty() -> {
                    Text(
                        text = status.errorMessage,
                        modifier = Modifier.padding(top = 10.dp),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                status.hasLoaded && status.arrivals.isEmpty() -> {
                    Text(
                        text = "현재 도착 예정 정보가 없어요.",
                        modifier = Modifier.padding(top = 10.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                else -> {
                    status.arrivals.forEachIndexed { index, arrival ->
                        HorizontalDivider(
                            modifier = Modifier.padding(top = if (index == 0) 10.dp else 4.dp),
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                        ArrivalLine(arrival = arrival)
                    }
                    if (status.errorMessage != null) {
                        Text(
                            text = "업데이트 지연 · ${status.errorMessage}",
                            modifier = Modifier.padding(top = 6.dp),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ArrivalLine(arrival: ArrivalLineUi) {
    val spokenText = listOfNotNull(
        arrival.primaryLabel,
        arrival.stopsLabel,
        arrival.arrivalLabel,
    ).joinToString(", ")

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 9.dp)
            .clearAndSetSemantics { contentDescription = spokenText },
    ) {
        val useStackedLayout = maxWidth < 300.dp || LocalDensity.current.fontScale >= 1.3f
        if (useStackedLayout) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    ArrivalPrimaryLabel(
                        text = arrival.primaryLabel,
                        modifier = Modifier.weight(1f),
                    )
                    ArrivalTimeLabel(text = arrival.arrivalLabel)
                }
                if (!arrival.stopsLabel.isNullOrBlank()) {
                    Text(
                        text = arrival.stopsLabel,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ArrivalPrimaryLabel(
                    text = arrival.primaryLabel,
                    modifier = Modifier.weight(0.34f),
                )
                Text(
                    text = arrival.stopsLabel.orEmpty(),
                    modifier = Modifier.weight(0.36f),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                ArrivalTimeLabel(
                    text = arrival.arrivalLabel,
                    modifier = Modifier.weight(0.30f),
                )
            }
        }
    }
}

@Composable
private fun ArrivalPrimaryLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier = modifier,
        color = MaterialTheme.colorScheme.onSurface,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.ExtraBold,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun ArrivalTimeLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier = modifier,
        color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.End,
        maxLines = 2,
    )
}

@Composable
private fun ProviderBadge(label: String) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = RoundedCornerShape(50),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun EmptyProfileCard(
    profile: CommuteProfile,
    onAdd: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "${profile.label()} 교통편이 아직 없어요",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "자주 이용하는 버스 정류소나 지하철역을 추가해 주세요.",
                modifier = Modifier.padding(top = 7.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
            Button(
                onClick = onAdd,
                modifier = Modifier.padding(top = 16.dp),
            ) {
                Text("교통편 추가")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(
    settings: CommuteSettings,
    callbacks: CommuteStopCallbacks,
    modifier: Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "설정",
                        modifier = Modifier.semantics { heading() },
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = callbacks.onBack,
                        modifier = Modifier.semantics { contentDescription = "홈으로 돌아가기" },
                    ) {
                        Text(text = "←", fontSize = 24.sp)
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item(key = "cutoff") {
                CutoffTimeCard(
                    cutoffMinute = settings.cutoffMinute,
                    onAdjust = callbacks.onAdjustCutoffMinute,
                )
            }
            item(key = "morning") {
                ProfileSettingsSection(
                    profile = CommuteProfile.MORNING,
                    profileSettings = settings.morning,
                    callbacks = callbacks,
                )
            }
            item(key = "evening") {
                ProfileSettingsSection(
                    profile = CommuteProfile.EVENING,
                    profileSettings = settings.evening,
                    callbacks = callbacks,
                )
            }
        }
    }
}

@Composable
private fun CutoffTimeCard(
    cutoffMinute: Int,
    onAdjust: (Int) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "기본 탭 전환 시각",
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "이 시각 전에는 출근, 이후에는 퇴근 탭으로 시작합니다.",
                modifier = Modifier.padding(top = 4.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = { onAdjust(-5) },
                    modifier = Modifier.semantics {
                        contentDescription = "기준 시각 5분 앞당기기"
                    },
                ) {
                    Text(text = "−", fontSize = 26.sp)
                }
                Text(
                    text = cutoffMinute.toClockLabel(),
                    modifier = Modifier
                        .padding(horizontal = 18.dp)
                        .semantics {
                            contentDescription = "현재 기준 시각 ${cutoffMinute.toClockLabel()}"
                        },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                IconButton(
                    onClick = { onAdjust(5) },
                    modifier = Modifier.semantics {
                        contentDescription = "기준 시각 5분 늦추기"
                    },
                ) {
                    Text(text = "+", fontSize = 24.sp)
                }
            }
        }
    }
}

@Composable
private fun ProfileSettingsSection(
    profile: CommuteProfile,
    profileSettings: ProfileSettings,
    callbacks: CommuteStopCallbacks,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${profile.label()} 설정",
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "${profileSettings.targets.size}/$MAX_TARGETS",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge,
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "화면 테마",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Row(
                    modifier = Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ThemePreference.entries.forEach { theme ->
                        FilterChip(
                            selected = theme == profileSettings.theme,
                            onClick = { callbacks.onThemeSelected(profile, theme) },
                            label = { Text(theme.label()) },
                        )
                    }
                }
            }
        }

        if (profileSettings.targets.isEmpty()) {
            MessageSurface(
                message = "등록된 교통편이 없습니다.",
                isError = false,
            )
        } else {
            profileSettings.targets.forEachIndexed { index, target ->
                TargetOrderRow(
                    target = target,
                    position = index,
                    itemCount = profileSettings.targets.size,
                    onMove = { delta -> callbacks.onMoveTarget(profile, target.id, delta) },
                    onDelete = { callbacks.onDeleteTarget(profile, target.id) },
                )
            }
        }

        OutlinedButton(
            onClick = { callbacks.onOpenSearch(profile) },
            modifier = Modifier.fillMaxWidth(),
            enabled = profileSettings.targets.size < MAX_TARGETS,
        ) {
            Text(
                if (profileSettings.targets.size < MAX_TARGETS) {
                    "+ ${profile.label()} 교통편 추가"
                } else {
                    "최대 3개까지 등록할 수 있어요"
                },
            )
        }
    }
}

@Composable
private fun TargetOrderRow(
    target: TransitTarget,
    position: Int,
    itemCount: Int,
    onMove: (Int) -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, top = 8.dp, end = 4.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = target.stationName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = target.supportingLabel(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(
                onClick = { onMove(-1) },
                enabled = position > 0,
                modifier = Modifier.semantics {
                    contentDescription = "${target.stationName} 위로 이동"
                },
            ) {
                Text(text = "↑", fontSize = 19.sp)
            }
            IconButton(
                onClick = { onMove(1) },
                enabled = position < itemCount - 1,
                modifier = Modifier.semantics {
                    contentDescription = "${target.stationName} 아래로 이동"
                },
            ) {
                Text(text = "↓", fontSize = 19.sp)
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier.semantics {
                    contentDescription = "${target.stationName} 삭제"
                },
            ) {
                Text(
                    text = "×",
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 23.sp,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchEditorScreen(
    searchState: SearchEditorUiState,
    callbacks: CommuteStopCallbacks,
    modifier: Modifier,
) {
    val visibleResults = searchState.selectedResult?.let(::listOf) ?: searchState.results

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "교통편 추가",
                            modifier = Modifier.semantics { heading() },
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "${searchState.profile.label()} · 최대 3개",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = callbacks.onBack,
                        modifier = Modifier.semantics { contentDescription = "이전 화면으로 돌아가기" },
                    ) {
                        Text(text = "←", fontSize = 24.sp)
                    }
                },
            )
        },
        bottomBar = {
            Surface(shadowElevation = 6.dp) {
                Button(
                    onClick = callbacks.onSaveSearchSelection,
                    enabled = searchState.canSave && !searchState.isSaving,
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    if (searchState.isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                    }
                    Text("선택한 교통편 저장")
                }
            }
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "provider") {
                ProviderSelector(
                    selectedProvider = searchState.provider,
                    onProviderSelected = callbacks.onSearchProviderSelected,
                )
            }
            item(key = "query") {
                OutlinedTextField(
                    value = searchState.query,
                    onValueChange = callbacks.onSearchQueryChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(searchState.provider.searchHint()) },
                    placeholder = { Text("이름을 두 글자 이상 입력해 주세요") },
                    singleLine = true,
                )
            }

            if (searchState.isSearching) {
                item(key = "searching") {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }

            if (searchState.errorMessage != null) {
                item(key = "search-error") {
                    MessageSurface(
                        message = searchState.errorMessage,
                        isError = true,
                    )
                }
            }

            if (
                searchState.query.length >= MIN_SEARCH_QUERY_LENGTH &&
                !searchState.isSearching &&
                searchState.errorMessage == null &&
                searchState.results.isEmpty()
            ) {
                item(key = "no-results") {
                    MessageSurface(
                        message = "검색 결과가 없습니다. 정류소나 역 이름을 확인해 주세요.",
                        isError = false,
                    )
                }
            }

            if (visibleResults.isNotEmpty()) {
                item(key = "result-heading") {
                    SectionHeading(
                        text = if (searchState.selectedResult == null) {
                            "검색 결과"
                        } else {
                            "선택한 장소"
                        },
                    )
                }
                items(
                    items = visibleResults,
                    key = TransitSearchResultUi::stableId,
                ) { result ->
                    SearchResultRow(
                        result = result,
                        isSelected = searchState.selectedResult?.stableId == result.stableId,
                        onClick = { callbacks.onSearchResultSelected(result) },
                    )
                }
            }

            if (searchState.selectedResult != null) {
                when (searchState.provider) {
                    TransitProvider.SEOUL_BUS,
                    TransitProvider.GYEONGGI_BUS,
                    -> {
                        item(key = "route-heading") {
                            SectionHeading(
                                text = "표시할 버스 선택",
                                supportingText = "한 대 이상 선택해 주세요.",
                            )
                        }
                        if (
                            !searchState.isSearching &&
                            searchState.errorMessage == null &&
                            searchState.busRoutes.isEmpty()
                        ) {
                            item(key = "no-routes") {
                                MessageSurface(
                                    message = "선택할 수 있는 일반 버스 노선이 없습니다.",
                                    isError = false,
                                )
                            }
                        } else {
                            items(
                                items = searchState.busRoutes,
                                key = BusRouteRef::routeId,
                            ) { route ->
                                BusRouteSelectionRow(
                                    route = route,
                                    selected = route.routeId in searchState.selectedBusRouteIds,
                                    onToggle = { callbacks.onBusRouteToggled(route) },
                                )
                            }
                        }
                    }

                    TransitProvider.SEOUL_SUBWAY -> {
                        item(key = "line-heading") {
                            SectionHeading(
                                text = "지하철 노선 선택",
                                supportingText = "환승역은 이용할 노선을 먼저 골라 주세요.",
                            )
                        }
                        items(
                            items = searchState.subwayLines,
                            key = SubwayLineOptionUi::lineId,
                        ) { line ->
                            RadioSelectionRow(
                                title = line.lineName,
                                selected = line.lineId == searchState.selectedSubwayLineId,
                                onClick = { callbacks.onSubwayLineSelected(line) },
                            )
                        }

                        if (searchState.selectedSubwayLineId != null) {
                            item(key = "direction-heading") {
                                SectionHeading(
                                    text = "방향 선택",
                                    supportingText = "실제로 탑승할 방향 한 곳을 선택해 주세요.",
                                )
                            }
                            items(
                                items = searchState.subwayDirections,
                                key = SubwayDirectionOptionUi::directionId,
                            ) { direction ->
                                RadioSelectionRow(
                                    title = direction.directionLabel,
                                    selected = direction.directionId ==
                                        searchState.selectedSubwayDirectionId,
                                    onClick = {
                                        callbacks.onSubwayDirectionSelected(direction)
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProviderSelector(
    selectedProvider: TransitProvider,
    onProviderSelected: (TransitProvider) -> Unit,
) {
    Column {
        Text(
            text = "교통수단",
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Column(
            modifier = Modifier.padding(top = 7.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            listOf(
                TransitProvider.SEOUL_BUS,
                TransitProvider.GYEONGGI_BUS,
                TransitProvider.SEOUL_SUBWAY,
            ).forEach { provider ->
                FilterChip(
                    selected = provider == selectedProvider,
                    onClick = { onProviderSelected(provider) },
                    modifier = Modifier.fillMaxWidth(),
                    label = {
                        Text(
                            text = provider.filterLabel(),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun SearchResultRow(
    result: TransitSearchResultUi,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { selected = isSelected },
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = result.stationName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = result.detailLabel(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (isSelected) {
                Text(
                    text = "선택됨",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
            } else {
                Text(
                    text = "선택",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
private fun BusRouteSelectionRow(
    route: BusRouteRef,
    selected: Boolean,
    onToggle: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = selected,
                role = Role.Checkbox,
                onValueChange = { onToggle() },
            ),
        shape = RoundedCornerShape(12.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = selected,
                onCheckedChange = null,
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp),
            ) {
                Text(
                    text = "${route.routeName}번",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                if (!route.destinationName.isNullOrBlank()) {
                    Text(
                        text = route.destinationName,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun RadioSelectionRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = selected,
                role = Role.RadioButton,
                onValueChange = { onClick() },
            ),
        shape = RoundedCornerShape(12.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(
                selected = selected,
                onClick = null,
            )
            Text(
                text = title,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            )
        }
    }
}

@Composable
private fun SectionHeading(
    text: String,
    supportingText: String? = null,
) {
    Column(modifier = Modifier.padding(top = 6.dp)) {
        Text(
            text = text,
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        if (supportingText != null) {
            Text(
                text = supportingText,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun MessageSurface(
    message: String,
    isError: Boolean,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = if (isError) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        contentColor = if (isError) {
            MaterialTheme.colorScheme.onErrorContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        shape = RoundedCornerShape(14.dp),
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(14.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

private fun CommuteSettings.forProfile(profile: CommuteProfile): ProfileSettings = when (profile) {
    CommuteProfile.MORNING -> morning
    CommuteProfile.EVENING -> evening
}

private fun CommuteProfile.label(): String = when (this) {
    CommuteProfile.MORNING -> "출근"
    CommuteProfile.EVENING -> "퇴근"
}

private fun ThemePreference.label(): String = when (this) {
    ThemePreference.DARK -> "다크"
    ThemePreference.LIGHT -> "라이트"
}

private fun TransitProvider.shortLabel(): String = when (this) {
    TransitProvider.SEOUL_BUS -> "서울버스"
    TransitProvider.GYEONGGI_BUS -> "경기버스"
    TransitProvider.SEOUL_SUBWAY -> "지하철"
}

private fun TransitProvider.filterLabel(): String = when (this) {
    TransitProvider.SEOUL_BUS -> "서울 버스"
    TransitProvider.GYEONGGI_BUS -> "경기 버스"
    TransitProvider.SEOUL_SUBWAY -> "지하철"
}

private fun TransitProvider.searchHint(): String = when (this) {
    TransitProvider.SEOUL_BUS,
    TransitProvider.GYEONGGI_BUS,
    -> "버스 정류소 검색"

    TransitProvider.SEOUL_SUBWAY -> "지하철역 검색"
}

private fun TransitTarget.supportingLabel(): String = when (this) {
    is BusTransitTarget -> listOfNotNull(
        provider.shortLabel(),
        stationNumber?.takeIf(String::isNotBlank),
        regionName?.takeIf(String::isNotBlank),
    ).joinToString(" · ")

    is SubwayTransitTarget -> "$lineName · $directionLabel"
}

private fun TransitSearchResultUi.detailLabel(): String = listOfNotNull(
    provider.shortLabel(),
    stationNumber?.takeIf(String::isNotBlank),
    regionName?.takeIf(String::isNotBlank),
    supportingText?.takeIf(String::isNotBlank),
).joinToString(" · ")

private fun Int.toClockLabel(): String {
    val normalized = ((this % MINUTES_PER_DAY) + MINUTES_PER_DAY) % MINUTES_PER_DAY
    val hour = normalized / MINUTES_PER_HOUR
    val minute = normalized % MINUTES_PER_HOUR
    return hour.toString().padStart(2, '0') + ":" + minute.toString().padStart(2, '0')
}

private const val MAX_TARGETS = 3
private const val MIN_SEARCH_QUERY_LENGTH = 2
private const val MINUTES_PER_HOUR = 60
private const val MINUTES_PER_DAY = 24 * MINUTES_PER_HOUR

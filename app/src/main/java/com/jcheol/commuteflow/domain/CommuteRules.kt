package com.jcheol.commuteflow.domain

const val DEFAULT_CUTOFF_MINUTE = 12 * 60
const val MAX_TARGETS_PER_PROFILE = 3
internal val MINUTES_IN_DAY_RANGE = 0 until 24 * 60

fun CommuteSettings.activeProfileAt(minuteOfDay: Int): CommuteProfile {
    require(minuteOfDay in MINUTES_IN_DAY_RANGE) {
        "minuteOfDay must be between 0 and 1439"
    }
    return if (minuteOfDay < cutoffMinute) CommuteProfile.MORNING else CommuteProfile.EVENING
}

fun CommuteSettings.profileSettings(profile: CommuteProfile): ProfileSettings = when (profile) {
    CommuteProfile.MORNING -> morning
    CommuteProfile.EVENING -> evening
}

fun CommuteSettings.withCutoffMinute(cutoffMinute: Int): CommuteSettings =
    copy(cutoffMinute = cutoffMinute)

fun CommuteSettings.withTheme(
    profile: CommuteProfile,
    theme: ThemePreference,
): CommuteSettings = updateProfile(profile) { settings ->
    settings.copy(theme = theme)
}

fun CommuteSettings.withSavedTarget(
    profile: CommuteProfile,
    target: TransitTarget,
): CommuteSettings = updateProfile(profile) { settings ->
    val existingIndex = settings.targets.indexOfFirst { it.id == target.id }
    val updatedTargets = if (existingIndex >= 0) {
        settings.targets.toMutableList().apply { this[existingIndex] = target }
    } else {
        require(settings.targets.size < MAX_TARGETS_PER_PROFILE) {
            "A profile can contain at most $MAX_TARGETS_PER_PROFILE targets"
        }
        settings.targets + target
    }
    settings.copy(targets = updatedTargets)
}

fun CommuteSettings.withoutTarget(
    profile: CommuteProfile,
    targetId: String,
): CommuteSettings {
    require(targetId.isNotBlank()) { "targetId must not be blank" }
    return updateProfile(profile) { settings ->
        val updatedTargets = settings.targets.filterNot { it.id == targetId }
        if (updatedTargets.size == settings.targets.size) settings else settings.copy(targets = updatedTargets)
    }
}

fun CommuteSettings.withReorderedTarget(
    profile: CommuteProfile,
    fromIndex: Int,
    toIndex: Int,
): CommuteSettings = updateProfile(profile) { settings ->
    require(fromIndex in settings.targets.indices) { "fromIndex is out of bounds" }
    require(toIndex in settings.targets.indices) { "toIndex is out of bounds" }
    if (fromIndex == toIndex) {
        settings
    } else {
        settings.copy(
            targets = settings.targets.toMutableList().apply {
                add(toIndex, removeAt(fromIndex))
            },
        )
    }
}

private inline fun CommuteSettings.updateProfile(
    profile: CommuteProfile,
    transform: (ProfileSettings) -> ProfileSettings,
): CommuteSettings = when (profile) {
    CommuteProfile.MORNING -> copy(morning = transform(morning))
    CommuteProfile.EVENING -> copy(evening = transform(evening))
}
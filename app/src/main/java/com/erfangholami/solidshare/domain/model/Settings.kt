package com.erfangholami.solidshare.domain.model

enum class ThemeMode {
    SYSTEM, LIGHT, DARK;

    companion object {
        fun fromName(name: String?): ThemeMode =
            entries.firstOrNull { it.name == name } ?: SYSTEM
    }
}

data class Settings(
    val hasCompletedOnboarding: Boolean,
    val themeMode: ThemeMode,
) {
    companion object {
        val Default = Settings(
            hasCompletedOnboarding = false,
            themeMode = ThemeMode.SYSTEM,
        )
    }
}

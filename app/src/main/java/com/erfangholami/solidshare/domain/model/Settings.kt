package com.erfangholami.solidshare.domain.model

data class Settings(
    val hasCompletedOnboarding: Boolean,
) {
    companion object {
        val Default = Settings(
            hasCompletedOnboarding = false,
        )
    }
}

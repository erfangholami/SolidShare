package com.erfangh.solidshare.domain.model

data class Settings(
    val hasCompletedOnboarding: Boolean,
) {
    companion object {
        val Default = Settings(
            hasCompletedOnboarding = false,
        )
    }
}

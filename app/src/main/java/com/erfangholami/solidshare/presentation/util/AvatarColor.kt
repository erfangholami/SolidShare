package com.erfangholami.solidshare.presentation.util

import androidx.compose.ui.graphics.Color
import com.erfangholami.solidshare.domain.model.PublicProfile
import kotlin.math.absoluteValue

private val AvatarPalette = listOf(
    Color(0xFF1A73E8),
    Color(0xFFD93025),
    Color(0xFF188038),
    Color(0xFFB06000),
    Color(0xFF8430CE),
    Color(0xFF00897B),
    Color(0xFFE8710A),
    Color(0xFFC2185B),
    Color(0xFF455A64),
    Color(0xFF5F6368),
    Color(0xFF673AB7),
    Color(0xFF0097A7),
)

fun webIdToAvatarColor(seed: String): Color {
    if (seed.isEmpty()) return AvatarPalette[0]
    val index = seed.hashCode().absoluteValue % AvatarPalette.size
    return AvatarPalette[index]
}

fun displayNameFor(profile: PublicProfile?): String? {
    val name = profile?.name
    if (!name.isNullOrBlank()) return name
    val combined = listOfNotNull(profile?.givenName, profile?.familyName).joinToString(" ").trim()
    return combined.takeIf { it.isNotEmpty() }
}

fun initialFor(displayName: String?, webId: String?): String {
    val source = displayName?.trim().takeUnless { it.isNullOrEmpty() }
        ?: webId?.substringAfter("//")?.trim()
        ?: return "?"
    return source.firstOrNull { it.isLetterOrDigit() }
        ?.uppercaseChar()
        ?.toString()
        ?: "?"
}

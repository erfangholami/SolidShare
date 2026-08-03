package com.erfangholami.solidshare.presentation.sharing

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import javax.inject.Inject
import javax.inject.Singleton

interface SharedEntityUi {
    val typeIri: String
    val icon: ImageVector

    @get:StringRes
    val kindLabelRes: Int

    val homeCard: HomeModuleCard?
        get() = null

    fun receivedShareRoute(resourceUri: String, ownerWebId: String?): Any
    fun manageShareRoute(resourceUri: String): Any

    suspend fun resolveName(webId: String, resourceUri: String): String? = null
}

data class HomeModuleCard(
    val order: Int,
    val icon: ImageVector,
    @param:StringRes val titleRes: Int,
    @param:StringRes val subtitleRes: Int,
    val route: Any,
)

@Singleton
class SharedEntityRegistry @Inject constructor(
    private val handlers: Set<@JvmSuppressWildcards SharedEntityUi>,
) {
    private val byType = handlers.associateBy { it.typeIri }

    fun forType(typeIri: String?): SharedEntityUi? = typeIri?.let { byType[it] }

    fun homeCards(): List<HomeModuleCard> =
        handlers.mapNotNull { it.homeCard }.sortedBy { it.order }
}

package com.erfangholami.solidshare.presentation.sharing

import javax.inject.Inject
import javax.inject.Singleton

interface ScanContributor {

    fun classify(raw: String): Any?

    fun classifyContent(bytes: ByteArray, fileName: String?): Any? = null
}

@Singleton
class ScanRouter @Inject constructor(
    private val contributors: Set<@JvmSuppressWildcards ScanContributor>,
) {
    fun route(raw: String): Any? = contributors.firstNotNullOfOrNull { it.classify(raw) }

    fun routeContent(bytes: ByteArray, fileName: String?): Any? =
        contributors.firstNotNullOfOrNull { it.classifyContent(bytes, fileName) }
}

package com.erfangholami.solidshare.domain.model

data class ContainerStats(
    val totalSize: Long,
    val newestModified: Long?,
) {
    companion object {
        val EMPTY = ContainerStats(totalSize = 0L, newestModified = null)
    }
}

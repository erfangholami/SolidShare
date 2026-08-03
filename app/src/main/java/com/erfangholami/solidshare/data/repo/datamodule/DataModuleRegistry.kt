package com.erfangholami.solidshare.data.repo.datamodule

import com.erfangholami.solidshare.data.repo.outbox.ModuleOutbox
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DataModuleRegistry @Inject constructor(
    private val modules: Set<@JvmSuppressWildcards DataModuleLifecycle>,
    private val outbox: ModuleOutbox,
) {

    fun all(): Set<DataModuleLifecycle> = modules

    suspend fun clearCache(webId: String) {
        modules.forEach { runCatching { it.clearCache(webId) } }
    }

    suspend fun drainPending(): Boolean {
        val work = runCatching { outbox.pendingWork() }.getOrDefault(emptyList())
        if (work.isEmpty()) return true
        val byId = modules.associateBy { it.moduleId }
        var allOk = true
        work.forEach { pending ->
            val module = byId[pending.module] ?: return@forEach
            val ok = runCatching { module.drain(pending.webId) }.getOrDefault(false)
            if (!ok) allOk = false
        }
        return allOk
    }
}

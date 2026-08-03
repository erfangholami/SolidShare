package com.erfangholami.solidshare.data.repo.outbox

import com.erfangholami.solidshare.data.local.cache.ModuleOutboxDao
import com.erfangholami.solidshare.data.local.cache.ModuleOutboxOpEntity
import com.erfangholami.solidshare.data.local.cache.OpStatus
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ModuleOutbox @Inject constructor(
    private val dao: ModuleOutboxDao,
    private val trigger: OutboxTrigger,
) {

    suspend fun enqueue(module: String, webId: String, type: String, payload: String) {
        val now = System.currentTimeMillis()
        dao.insert(
            ModuleOutboxOpEntity(
                module = module,
                webId = webId,
                type = type,
                payload = payload,
                status = OpStatus.PENDING,
                attempts = 0,
                nextRetryAt = 0,
                lastError = null,
                createdAt = now,
                updatedAt = now,
            ),
        )
        trigger.requestDrain(OutboxQueue.DATA_MODULES)
    }

    suspend fun drain(
        module: String,
        webId: String,
        execute: suspend (ModuleOutboxOpEntity) -> Unit,
    ): Boolean {
        val now = System.currentTimeMillis()
        var allOk = true
        for (op in dao.dueOps(module, webId, now)) {
            val result = runCatching { execute(op) }
            if (result.isSuccess) {
                dao.deleteById(op.id)
            } else {
                allOk = false
                val attempts = op.attempts + 1
                dao.update(
                    op.copy(
                        status = OpStatus.FAILED,
                        attempts = attempts,
                        nextRetryAt = now + backoffMillis(attempts),
                        lastError = result.exceptionOrNull()?.message,
                        updatedAt = now,
                    ),
                )
            }
        }
        return allOk
    }

    suspend fun pendingOps(module: String, webId: String): List<ModuleOutboxOpEntity> =
        dao.pendingOps(module, webId)

    suspend fun rewrite(op: ModuleOutboxOpEntity, payload: String) {
        dao.update(op.copy(payload = payload, updatedAt = System.currentTimeMillis()))
    }

    suspend fun drop(op: ModuleOutboxOpEntity) {
        dao.deleteById(op.id)
    }

    suspend fun clear(module: String, webId: String) {
        dao.deleteAllForWebId(module, webId)
    }

    suspend fun pendingWork(): List<PendingOutboxWork> =
        dao.pendingModuleWebIds().mapNotNull { row ->
            val module = row.substringBefore(' ')
            val webId = row.substringAfter(' ', missingDelimiterValue = "")
            if (module.isEmpty() || webId.isEmpty()) null else PendingOutboxWork(module, webId)
        }

    fun observePendingCount(module: String, webId: String): Flow<Int> =
        dao.observePendingCount(module, webId)

    private fun backoffMillis(attempts: Int): Long =
        minOf(30_000L * (1L shl minOf(attempts, 6)), 3_600_000L)
}

data class PendingOutboxWork(val module: String, val webId: String)

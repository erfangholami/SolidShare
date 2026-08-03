package com.erfangholami.solidshare.data.repo.outbox

interface OutboxTrigger {

    fun requestDrain(queue: OutboxQueue)
}

enum class OutboxQueue {
    FILES,
    DATA_MODULES,
}

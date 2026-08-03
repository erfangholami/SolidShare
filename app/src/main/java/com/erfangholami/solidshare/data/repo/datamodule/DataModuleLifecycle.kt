package com.erfangholami.solidshare.data.repo.datamodule

interface DataModuleLifecycle {

    val moduleId: String

    suspend fun drain(webId: String): Boolean

    suspend fun clearCache(webId: String)
}

object DataModuleIds {
    const val CONTACTS = "contacts"
    const val TICKETS = "tickets"
}

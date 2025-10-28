package com.erfangh.solidshare

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform
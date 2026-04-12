package com.erfangh.solidshare.domain.model

data class LoginFilledData(
    val type: LoginFilledMethod = LoginFilledMethod.NONE,
    val podServer: PodServer? = null
)

enum class LoginFilledMethod {
    PREVIOUS_USER,
    OFFICIAL_POD,
    PERSONAL_SERVER,
    NONE
}

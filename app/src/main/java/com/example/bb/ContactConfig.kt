package com.example.bb

object ContactConfig {
    const val CONTACT_API = "contact_info.php"

    val phoneNumber: String
        get() = RemoteConfigManager.current().contact.phone

    val phoneDisplay: String
        get() = RemoteConfigManager.current().contact.phoneDisplay

    val eitaaNumber: String
        get() = RemoteConfigManager.current().contact.eitaaNumber

    val eitaaUrl: String
        get() = RemoteConfigManager.current().contact.eitaaUrl

    val addressUrl: String
        get() = RemoteConfigManager.current().contact.addressUrl
}

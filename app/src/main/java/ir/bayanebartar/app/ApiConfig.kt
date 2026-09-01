package ir.bayanebartar.app

object ApiConfig {
    /**
     * A fresh install needs exactly one stable network anchor to discover all
     * other URLs. Keep this endpoint alive when moving domains long enough for
     * installed apps to learn the new configUrl/apiBaseUrl.
     */
    const val BOOTSTRAP_CONFIG_URL = "https://bayan-e-bartar.ir/api/app_config.php"

    // Offline/fresh-install fallbacks only. Runtime requests use RemoteConfigManager.
    const val DEFAULT_API_BASE_URL = "https://bayan-e-bartar.ir/api/"
    const val DEFAULT_WEBSITE_URL = "https://bayan-e-bartar.ir/"
}

package com.example.bb

import android.content.Context
import android.graphics.BitmapFactory
import android.widget.ImageView
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.util.concurrent.CopyOnWriteArraySet

/**
 * Remote configuration with an offline-first cache.
 *
 * Priority:
 *   server -> last successful device cache -> built-in defaults
 *
 * Only BOOTSTRAP_CONFIG_URL must stay inside the APK so a fresh install knows
 * where to ask for its first configuration. All other mutable school metadata
 * can move to the server.
 */
data class AppRemoteConfig(
    val configVersion: Int = 1,
    val configUrl: String = ApiConfig.BOOTSTRAP_CONFIG_URL,
    val apiBaseUrl: String = ApiConfig.DEFAULT_API_BASE_URL,
    val websiteUrl: String = ApiConfig.DEFAULT_WEBSITE_URL,
    val schoolNameFa: String = "آموزشگاه زبان خارجی بیان برتر",
    val schoolShortNameFa: String = "بیان برتر",
    val logoUrl: String = "https://bayan-e-bartar.ir/final3000px%28web%29.png",
    val systemCreatorName: String = "سامانه بیان برتر",
    val managementFirstName: String = "مدیریت",
    val managementLastName: String = "آموزشگاه",
    val contact: ContactRemoteConfig = ContactRemoteConfig(),
    val reportCard: ReportCardRemoteConfig = ReportCardRemoteConfig(),
    val membership: MembershipRemoteConfig = MembershipRemoteConfig(),
    val classDefaults: ClassDefaultsRemoteConfig = ClassDefaultsRemoteConfig(),
    val classNameFallbacks: List<String> = SchoolClassCatalog.BUILT_IN_FALLBACKS
)

data class ContactRemoteConfig(
    val title: String = "ارتباط با آموزشگاه",
    val phone: String = "03142660690",
    val phoneDisplay: String = "031-42660690",
    val phoneLabel: String = "تماس با آموزشگاه",
    val email: String = "bayanebartar95@gmail.com",
    val eitaaNumber: String = "09014269723",
    val eitaaUrl: String = "https://eitaa.com/09014269723",
    val eitaaLabel: String = "ارتباط در ایتا",
    val addressText: String = "",
    val addressUrl: String = "https://nshn.ir/dc7bZluVIx4B28",
    val addressLabel: String = "نشانی آموزشگاه"
)

data class ReportCardRemoteConfig(
    val schoolName: String = "آموزشگاه زبان‌های خارجی بیان برتر",
    val schoolNameEn: String = "Bayan-E-Bartar Language School",
    val title: String = "OFFICIAL REPORT CARD",
    val contactLine: String = "",
    val subjectLabel: String = "Subject",
    val scoreLabel: String = "Score",
    val outOfLabel: String = "Out of",
    val totalLabel: String = "TOTAL",
    val totalScoreLabel: String = "TOTAL SCORE",
    val passLabel: String = "PASS",
    val conditionalLabel: String = "CONDITIONAL",
    val failLabel: String = "FAIL",
    val incompleteLabel: String = "INCOMPLETE",
    val unknownLabel: String = "UNKNOWN",
    val studentIdLabel: String = "Student ID",
    val studentNameLabel: String = "Student Name",
    val classCodeLabel: String = "Class Code",
    val classLabel: String = "Class",
    val levelLabel: String = "Level",
    val termLabel: String = "Term",
    val textBookLabel: String = "Text Book",
    val managerSignatureLabel: String = "Manager's Signature",
    val teacherSignatureLabel: String = "Teacher's Signature",
    val dateLabel: String = "Date",
    val fallbackMessage: String = "—"
)

data class MembershipRemoteConfig(
    val durationMonths: Int = 12,
    val firstToolbarTitle: String = "عضویت سالانه",
    val firstPageTitle: String = "حق عضویت سالانه دانش‌آموز",
    val firstDescription: String = "برای فعال‌سازی حساب دانش‌آموزی، حق عضویت سالانه را پرداخت کنید.",
    val firstAmountLabel: String = "مبلغ عضویت یک‌ساله",
    val firstFooter: String = "پس از پرداخت موفق، حساب شما برای یک سال فعال می‌شود.",
    val firstButton: String = "پرداخت حق عضویت",
    val renewalToolbarTitle: String = "تمدید عضویت",
    val renewalPageTitle: String = "اعتبار حساب شما به پایان رسیده",
    val renewalDescription: String = "برای ادامه استفاده از سامانه، عضویت سالانه خود را تمدید کنید.",
    val renewalAmountLabel: String = "مبلغ تمدید یک‌ساله",
    val renewalFooter: String = "پس از پرداخت موفق، اعتبار حساب شما برای یک سال دیگر فعال می‌شود.",
    val renewalButton: String = "تمدید عضویت",
    val firstLogoutMessage: String = "برای ورود به برنامه باید حق عضویت سالانه پرداخت شود. آیا می‌خواهید از حساب خارج شوید؟",
    val renewalLogoutMessage: String = "برای ادامه استفاده از سامانه باید عضویت سالانه تمدید شود. آیا می‌خواهید از حساب خارج شوید؟"
)

data class ClassDefaultsRemoteConfig(
    val minPassingScore: Double = 80.0,
    val minConditionalScore: Double = 70.0
)

object RemoteConfigManager {
    private const val PREFS = "RemoteConfigCache"
    private const val KEY_CONFIG_JSON = "config_json"
    private const val KEY_LAST_FETCH = "last_successful_fetch"
    private const val KEY_LOGO_URL = "logo_source_url"
    private const val REFRESH_INTERVAL_MS = 5L * 60L * 1000L

    private val gson = Gson()
    private val httpClient = OkHttpClient.Builder().build()
    private val listeners = CopyOnWriteArraySet<(AppRemoteConfig) -> Unit>()

    @Volatile private var initialized = false
    @Volatile private var currentConfig = AppRemoteConfig()
    @Volatile private var refreshRunning = false
    private lateinit var appContext: Context

    fun init(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            appContext = context.applicationContext
            currentConfig = loadCachedConfig() ?: AppRemoteConfig()
            initialized = true
        }
    }

    fun current(): AppRemoteConfig = currentConfig

    fun addListener(listener: (AppRemoteConfig) -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: (AppRemoteConfig) -> Unit) {
        listeners.remove(listener)
    }

    fun refreshIfStale(force: Boolean = false) {
        if (!initialized || refreshRunning) return
        val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val lastFetch = prefs.getLong(KEY_LAST_FETCH, 0L)
        if (!force && System.currentTimeMillis() - lastFetch < REFRESH_INTERVAL_MS) return
        refresh()
    }

    fun refresh() {
        if (!initialized || refreshRunning) return
        refreshRunning = true

        val primary = sanitizeHttpUrl(currentConfig.configUrl)
            ?: ApiConfig.BOOTSTRAP_CONFIG_URL
        val fallback = ApiConfig.BOOTSTRAP_CONFIG_URL
        val urls = if (primary == fallback) listOf(primary) else listOf(primary, fallback)
        fetchFromCandidates(urls, 0)
    }

    private fun fetchFromCandidates(urls: List<String>, index: Int) {
        if (index >= urls.size) {
            refreshRunning = false
            return
        }

        val request = Request.Builder()
            .url(urls[index])
            .header("Accept", "application/json")
            .get()
            .build()

        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                fetchFromCandidates(urls, index + 1)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val body = it.body?.string().orEmpty()
                    if (!it.isSuccessful || body.isBlank()) {
                        fetchFromCandidates(urls, index + 1)
                        return
                    }

                    val parsed = runCatching { parseEnvelope(body) }.getOrNull()
                    if (parsed == null) {
                        fetchFromCandidates(urls, index + 1)
                        return
                    }

                    val previous = currentConfig
                    currentConfig = parsed
                    persist(parsed)
                    refreshRunning = false

                    if (previous.apiBaseUrl != parsed.apiBaseUrl) {
                        RetrofitClient.invalidate()
                    }
                    if (
                        previous.logoUrl != parsed.logoUrl ||
                        !logoFile().isFile ||
                        cachedLogoSourceUrl() != parsed.logoUrl
                    ) {
                        refreshLogo(parsed.logoUrl)
                    }
                    notifyListeners(parsed)
                }
            }
        })
    }

    private fun parseEnvelope(raw: String): AppRemoteConfig? {
        val root = gson.fromJson(raw, JsonObject::class.java) ?: return null
        val status = root.string("status")
        if (status.isNotBlank() && status != "success") return null
        val config = root.obj("config") ?: return null
        return mergeWithFallback(config, currentConfig)
    }

    private fun mergeWithFallback(json: JsonObject, fallback: AppRemoteConfig): AppRemoteConfig {
        val contactJson = json.obj("contact")
        val reportJson = json.obj("reportCard")
        val membershipJson = json.obj("membership")
        val classDefaultsJson = json.obj("classDefaults")

        val contact = fallback.contact.copy(
            title = contactJson?.stringOr("title", fallback.contact.title) ?: fallback.contact.title,
            phone = contactJson?.stringAllowBlank("phone", fallback.contact.phone) ?: fallback.contact.phone,
            phoneDisplay = contactJson?.stringAllowBlank("phoneDisplay", fallback.contact.phoneDisplay) ?: fallback.contact.phoneDisplay,
            phoneLabel = contactJson?.stringOr("phoneLabel", fallback.contact.phoneLabel) ?: fallback.contact.phoneLabel,
            email = contactJson?.stringAllowBlank("email", fallback.contact.email) ?: fallback.contact.email,
            eitaaNumber = contactJson?.stringAllowBlank("eitaaNumber", fallback.contact.eitaaNumber) ?: fallback.contact.eitaaNumber,
            eitaaUrl = contactJson?.stringAllowBlank("eitaaUrl", fallback.contact.eitaaUrl) ?: fallback.contact.eitaaUrl,
            eitaaLabel = contactJson?.stringOr("eitaaLabel", fallback.contact.eitaaLabel) ?: fallback.contact.eitaaLabel,
            addressText = contactJson?.stringAllowBlank("addressText", fallback.contact.addressText) ?: fallback.contact.addressText,
            addressUrl = contactJson?.stringAllowBlank("addressUrl", fallback.contact.addressUrl) ?: fallback.contact.addressUrl,
            addressLabel = contactJson?.stringOr("addressLabel", fallback.contact.addressLabel) ?: fallback.contact.addressLabel
        )

        val report = fallback.reportCard.copy(
            schoolName = reportJson?.stringOr("schoolName", fallback.reportCard.schoolName) ?: fallback.reportCard.schoolName,
            schoolNameEn = reportJson?.stringOr("schoolNameEn", fallback.reportCard.schoolNameEn) ?: fallback.reportCard.schoolNameEn,
            title = reportJson?.stringOr("title", fallback.reportCard.title) ?: fallback.reportCard.title,
            contactLine = reportJson?.stringAllowBlank("contactLine", fallback.reportCard.contactLine) ?: fallback.reportCard.contactLine,
            subjectLabel = reportJson?.stringOr("subjectLabel", fallback.reportCard.subjectLabel) ?: fallback.reportCard.subjectLabel,
            scoreLabel = reportJson?.stringOr("scoreLabel", fallback.reportCard.scoreLabel) ?: fallback.reportCard.scoreLabel,
            outOfLabel = reportJson?.stringOr("outOfLabel", fallback.reportCard.outOfLabel) ?: fallback.reportCard.outOfLabel,
            totalLabel = reportJson?.stringOr("totalLabel", fallback.reportCard.totalLabel) ?: fallback.reportCard.totalLabel,
            totalScoreLabel = reportJson?.stringOr("totalScoreLabel", fallback.reportCard.totalScoreLabel) ?: fallback.reportCard.totalScoreLabel,
            passLabel = reportJson?.stringOr("passLabel", fallback.reportCard.passLabel) ?: fallback.reportCard.passLabel,
            conditionalLabel = reportJson?.stringOr("conditionalLabel", fallback.reportCard.conditionalLabel) ?: fallback.reportCard.conditionalLabel,
            failLabel = reportJson?.stringOr("failLabel", fallback.reportCard.failLabel) ?: fallback.reportCard.failLabel,
            incompleteLabel = reportJson?.stringOr("incompleteLabel", fallback.reportCard.incompleteLabel) ?: fallback.reportCard.incompleteLabel,
            unknownLabel = reportJson?.stringOr("unknownLabel", fallback.reportCard.unknownLabel) ?: fallback.reportCard.unknownLabel,
            studentIdLabel = reportJson?.stringOr("studentIdLabel", fallback.reportCard.studentIdLabel) ?: fallback.reportCard.studentIdLabel,
            studentNameLabel = reportJson?.stringOr("studentNameLabel", fallback.reportCard.studentNameLabel) ?: fallback.reportCard.studentNameLabel,
            classCodeLabel = reportJson?.stringOr("classCodeLabel", fallback.reportCard.classCodeLabel) ?: fallback.reportCard.classCodeLabel,
            classLabel = reportJson?.stringOr("classLabel", fallback.reportCard.classLabel) ?: fallback.reportCard.classLabel,
            levelLabel = reportJson?.stringOr("levelLabel", fallback.reportCard.levelLabel) ?: fallback.reportCard.levelLabel,
            termLabel = reportJson?.stringOr("termLabel", fallback.reportCard.termLabel) ?: fallback.reportCard.termLabel,
            textBookLabel = reportJson?.stringOr("textBookLabel", fallback.reportCard.textBookLabel) ?: fallback.reportCard.textBookLabel,
            managerSignatureLabel = reportJson?.stringOr("managerSignatureLabel", fallback.reportCard.managerSignatureLabel) ?: fallback.reportCard.managerSignatureLabel,
            teacherSignatureLabel = reportJson?.stringOr("teacherSignatureLabel", fallback.reportCard.teacherSignatureLabel) ?: fallback.reportCard.teacherSignatureLabel,
            dateLabel = reportJson?.stringOr("dateLabel", fallback.reportCard.dateLabel) ?: fallback.reportCard.dateLabel,
            fallbackMessage = reportJson?.stringOr("fallbackMessage", fallback.reportCard.fallbackMessage) ?: fallback.reportCard.fallbackMessage
        )

        val membership = fallback.membership.copy(
            durationMonths = membershipJson?.intOr("durationMonths", fallback.membership.durationMonths) ?: fallback.membership.durationMonths,
            firstToolbarTitle = membershipJson?.stringOr("firstToolbarTitle", fallback.membership.firstToolbarTitle) ?: fallback.membership.firstToolbarTitle,
            firstPageTitle = membershipJson?.stringOr("firstPageTitle", fallback.membership.firstPageTitle) ?: fallback.membership.firstPageTitle,
            firstDescription = membershipJson?.stringOr("firstDescription", fallback.membership.firstDescription) ?: fallback.membership.firstDescription,
            firstAmountLabel = membershipJson?.stringOr("firstAmountLabel", fallback.membership.firstAmountLabel) ?: fallback.membership.firstAmountLabel,
            firstFooter = membershipJson?.stringOr("firstFooter", fallback.membership.firstFooter) ?: fallback.membership.firstFooter,
            firstButton = membershipJson?.stringOr("firstButton", fallback.membership.firstButton) ?: fallback.membership.firstButton,
            renewalToolbarTitle = membershipJson?.stringOr("renewalToolbarTitle", fallback.membership.renewalToolbarTitle) ?: fallback.membership.renewalToolbarTitle,
            renewalPageTitle = membershipJson?.stringOr("renewalPageTitle", fallback.membership.renewalPageTitle) ?: fallback.membership.renewalPageTitle,
            renewalDescription = membershipJson?.stringOr("renewalDescription", fallback.membership.renewalDescription) ?: fallback.membership.renewalDescription,
            renewalAmountLabel = membershipJson?.stringOr("renewalAmountLabel", fallback.membership.renewalAmountLabel) ?: fallback.membership.renewalAmountLabel,
            renewalFooter = membershipJson?.stringOr("renewalFooter", fallback.membership.renewalFooter) ?: fallback.membership.renewalFooter,
            renewalButton = membershipJson?.stringOr("renewalButton", fallback.membership.renewalButton) ?: fallback.membership.renewalButton,
            firstLogoutMessage = membershipJson?.stringOr("firstLogoutMessage", fallback.membership.firstLogoutMessage) ?: fallback.membership.firstLogoutMessage,
            renewalLogoutMessage = membershipJson?.stringOr("renewalLogoutMessage", fallback.membership.renewalLogoutMessage) ?: fallback.membership.renewalLogoutMessage
        )

        val classDefaults = fallback.classDefaults.copy(
            minPassingScore = classDefaultsJson?.doubleOr("minPassingScore", fallback.classDefaults.minPassingScore) ?: fallback.classDefaults.minPassingScore,
            minConditionalScore = classDefaultsJson?.doubleOr("minConditionalScore", fallback.classDefaults.minConditionalScore) ?: fallback.classDefaults.minConditionalScore
        )

        val configuredFallbacks = json.array("classNameFallbacks")
            ?.mapNotNull { element -> element.takeIf { it.isJsonPrimitive }?.asString?.trim()?.takeIf { it.isNotBlank() } }
            ?.distinct()
            ?.takeIf { it.isNotEmpty() }
            ?: fallback.classNameFallbacks

        return AppRemoteConfig(
            configVersion = json.intOr("configVersion", fallback.configVersion),
            configUrl = sanitizeHttpUrl(json.stringOr("configUrl", fallback.configUrl)) ?: fallback.configUrl,
            apiBaseUrl = sanitizeBaseUrl(json.stringOr("apiBaseUrl", fallback.apiBaseUrl)) ?: fallback.apiBaseUrl,
            websiteUrl = sanitizeHttpUrl(json.stringOr("websiteUrl", fallback.websiteUrl)) ?: fallback.websiteUrl,
            schoolNameFa = json.stringOr("schoolNameFa", fallback.schoolNameFa),
            schoolShortNameFa = json.stringOr("schoolShortNameFa", fallback.schoolShortNameFa),
            logoUrl = sanitizeHttpUrl(json.stringOr("logoUrl", fallback.logoUrl)) ?: fallback.logoUrl,
            systemCreatorName = json.stringOr("systemCreatorName", fallback.systemCreatorName),
            managementFirstName = json.stringOr("managementFirstName", fallback.managementFirstName),
            managementLastName = json.stringOr("managementLastName", fallback.managementLastName),
            contact = contact,
            reportCard = report,
            membership = membership,
            classDefaults = classDefaults,
            classNameFallbacks = configuredFallbacks
        )
    }

    private fun persist(config: AppRemoteConfig) {
        appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_CONFIG_JSON, gson.toJson(config))
            .putLong(KEY_LAST_FETCH, System.currentTimeMillis())
            .apply()
    }

    private fun loadCachedConfig(): AppRemoteConfig? {
        val raw = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_CONFIG_JSON, null)
            ?.takeIf { it.isNotBlank() }
            ?: return null

        val json = runCatching { gson.fromJson(raw, JsonObject::class.java) }
            .getOrNull()
            ?: return null

        return runCatching {
            mergeWithFallback(json, AppRemoteConfig())
        }.getOrNull()
    }

    fun applyCachedLogo(imageView: ImageView, fallbackDrawable: Int) {
        val file = logoFile()
        val bitmap = if (file.isFile) runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull() else null
        if (bitmap != null) imageView.setImageBitmap(bitmap) else imageView.setImageResource(fallbackDrawable)
    }

    private fun refreshLogo(url: String) {
        val safeUrl = sanitizeHttpUrl(url) ?: return
        val request = Request.Builder().url(safeUrl).get().build()
        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = Unit

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val bytes = it.body?.bytes() ?: return
                    if (!it.isSuccessful || bytes.isEmpty()) return
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return
                    bitmap.recycle()
                    runCatching {
                        val file = logoFile()
                        file.parentFile?.mkdirs()
                        val tmp = File(file.parentFile, file.name + ".tmp")
                        tmp.writeBytes(bytes)
                        if (file.exists()) file.delete()
                        tmp.renameTo(file)
                        appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                            .edit().putString(KEY_LOGO_URL, safeUrl).apply()
                    }
                    notifyListeners(currentConfig)
                }
            }
        })
    }

    private fun logoFile(): File = File(appContext.filesDir, "remote_config/school_logo.bin")

    private fun cachedLogoSourceUrl(): String =
        appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LOGO_URL, "")
            .orEmpty()

    private fun notifyListeners(config: AppRemoteConfig) {
        listeners.forEach { listener -> runCatching { listener(config) } }
    }

    private fun sanitizeBaseUrl(value: String?): String? {
        val url = sanitizeHttpUrl(value) ?: return null
        return if (url.endsWith('/')) url else "$url/"
    }

    /**
     * Security rule for URLs fetched by the app itself:
     * Remote Config, API base URL, website metadata and remote logo must use HTTPS.
     *
     * If an old cache or server config contains http://, it is rejected and the
     * last safe/built-in HTTPS fallback remains in use.
     */
    private fun sanitizeHttpUrl(value: String?): String? {
        val trimmed = value?.trim().orEmpty()
        if (!trimmed.startsWith("https://", true)) return null
        return trimmed
    }

    private fun JsonObject.string(name: String): String =
        get(name)?.takeIf { it.isJsonPrimitive }?.asString?.trim().orEmpty()

    private fun JsonObject.stringOr(name: String, fallback: String): String =
        string(name).takeIf { it.isNotBlank() } ?: fallback

    private fun JsonObject.stringAllowBlank(name: String, fallback: String): String {
        if (!has(name)) return fallback
        val value = get(name)
        return if (value != null && value.isJsonPrimitive) value.asString.trim() else fallback
    }

    private fun JsonObject.intOr(name: String, fallback: Int): Int =
        runCatching { get(name)?.asInt }.getOrNull() ?: fallback

    private fun JsonObject.doubleOr(name: String, fallback: Double): Double =
        runCatching { get(name)?.asDouble }.getOrNull() ?: fallback

    private fun JsonObject.obj(name: String): JsonObject? =
        get(name)?.takeIf { it.isJsonObject }?.asJsonObject

    private fun JsonObject.array(name: String): JsonArray? =
        get(name)?.takeIf { it.isJsonArray }?.asJsonArray
}

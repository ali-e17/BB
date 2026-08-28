package com.example.bb

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.File
import java.util.UUID

class CreateAnnouncementActivity : BaseActivity() {

    private lateinit var role: UserRole
    private var currentUserId: String = ""

    private var availableClasses: List<ClassModel> = emptyList()
    private val selectedClassIds = linkedSetOf<String>()

    private var attachmentUri: Uri? = null
    private var attachmentName: String? = null
    private var attachmentMimeType: String? = null
    private var attachmentSizeBytes: Long? = null

    private lateinit var audienceToggle: MaterialButtonToggleGroup
    private lateinit var btnAudienceAll: MaterialButton
    private lateinit var txtAudienceMode: TextView
    private lateinit var cardClassSelection: MaterialCardView
    private lateinit var txtSelectedClassesSummary: TextView
    private lateinit var etTitle: TextInputEditText
    private lateinit var etBody: TextInputEditText
    private lateinit var txtTitleCounter: TextView
    private lateinit var txtBodyCounter: TextView
    private lateinit var cardSelectedAttachment: MaterialCardView
    private lateinit var txtSelectedFileName: TextView
    private lateinit var txtSelectedFileMeta: TextView
    private lateinit var btnSend: MaterialButton
    private var sending = false

    private val attachmentPicker =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@registerForActivityResult

            runCatching {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }

            val metadata = readAttachmentMetadata(uri)
            val maxSize = 20L * 1024L * 1024L
            if (metadata.sizeBytes != null && metadata.sizeBytes > maxSize) {
                AppToast.makeText(this, "حداکثر حجم مجاز برای فایل پیوست ۲۰ مگابایت است", Toast.LENGTH_LONG).show()
                return@registerForActivityResult
            }

            attachmentUri = uri
            attachmentName = metadata.name
            attachmentMimeType = contentResolver.getType(uri)
            attachmentSizeBytes = metadata.sizeBytes
            renderAttachment()
            AppToast.success(
                this,
                "پیوست «${attachmentName ?: "فایل انتخابی"}» آماده ارسال است"
            )
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_announcement)

        val prefs = getSharedPreferences("LocalAppPrefs", Context.MODE_PRIVATE)
        currentUserId = prefs.getString("CURRENT_USER_ID", "").orEmpty()
        role = runCatching {
            UserRole.valueOf(
                intent.getStringExtra("USER_ROLE")
                    ?: prefs.getString("CURRENT_USER_ROLE", "TEACHER").orEmpty()
            )
        }.getOrDefault(UserRole.TEACHER)

        if (role == UserRole.STUDENT) {
            AppToast.makeText(this, "دانش‌آموزان فقط امکان مشاهده اعلانات را دارند", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        applyAvailableClasses(AppDatabase.getAllClasses(false))

        bindViews()
        setupHeader()
        setupAudience()
        setupInputs()
        setupAttachment()
        setupSend()
        updateSendState()
        loadAvailableClasses()
    }

    private fun loadAvailableClasses() {
        RetrofitClient.instance.getClasses().enqueue(object : Callback<List<ClassModel>> {
            override fun onResponse(
                call: Call<List<ClassModel>>,
                response: Response<List<ClassModel>>
            ) {
                if (response.isSuccessful) {
                    val classes = response.body().orEmpty()
                    AppDatabase.replaceClasses(classes)
                    applyAvailableClasses(classes)
                } else if (availableClasses.isEmpty()) {
                    AppToast.makeText(
                        this@CreateAnnouncementActivity,
                        ApiErrorParser.userMessage(response, "دریافت فهرست کلاس‌ها برای ارسال اعلان کامل نشد"),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }

            override fun onFailure(call: Call<List<ClassModel>>, t: Throwable) {
                if (availableClasses.isEmpty()) {
                    AppToast.makeText(
                        this@CreateAnnouncementActivity,
                        ApiErrorParser.networkMessage(t, "دریافت فهرست کلاس‌ها برای ارسال اعلان") +
                            " کلاس ذخیره‌شده‌ای هم روی دستگاه وجود ندارد.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        })
    }

    private fun applyAvailableClasses(source: List<ClassModel>) {
        availableClasses = source
            .asSequence()
            .filter { it.status == ClassStatus.ACTIVE }
            .filter {
                role == UserRole.ADMIN ||
                    (role == UserRole.TEACHER && it.teacherId == currentUserId)
            }
            .distinctBy { it.id }
            .sortedBy { it.className.lowercase() }
            .toList()

        selectedClassIds.retainAll(availableClasses.map { it.id }.toSet())
        if (::txtSelectedClassesSummary.isInitialized) {
            renderSelectedClasses()
            updateSendState()
        }
    }

    private fun bindViews() {
        findViewById<ImageView>(R.id.btnCreateBack).setOnClickListener { finish() }

        audienceToggle = findViewById(R.id.audienceToggleGroup)
        btnAudienceAll = findViewById(R.id.btnAudienceAll)
        txtAudienceMode = findViewById(R.id.txtAudienceMode)
        cardClassSelection = findViewById(R.id.cardClassSelection)
        txtSelectedClassesSummary = findViewById(R.id.txtSelectedClassesSummary)
        etTitle = findViewById(R.id.etMessageTitle)
        etBody = findViewById(R.id.etMessageBody)
        txtTitleCounter = findViewById(R.id.txtTitleCounter)
        txtBodyCounter = findViewById(R.id.txtBodyCounter)
        cardSelectedAttachment = findViewById(R.id.cardSelectedAttachment)
        txtSelectedFileName = findViewById(R.id.txtSelectedFileName)
        txtSelectedFileMeta = findViewById(R.id.txtSelectedFileMeta)
        btnSend = findViewById(R.id.btnSendMessage)
    }

    private fun setupHeader() {
        findViewById<TextView>(R.id.txtComposerAccessHint).text = when (role) {
            UserRole.ADMIN -> "امکان ارسال به همه کلاس‌ها یا کلاس‌های انتخابی"
            UserRole.TEACHER -> "امکان ارسال فقط به کلاس‌های خودتان"
            UserRole.STUDENT -> ""
        }
        findViewById<TextView>(R.id.txtComposerRoleBadge).text =
            if (role == UserRole.ADMIN) "مدیر" else "استاد"
    }

    private fun setupAudience() {
        if (role == UserRole.TEACHER) {
            audienceToggle.visibility = View.GONE
            txtAudienceMode.visibility = View.VISIBLE
            txtAudienceMode.text = "کلاس‌های انتخابی"
            cardClassSelection.visibility = View.VISIBLE
        } else {
            txtAudienceMode.visibility = View.GONE
            audienceToggle.visibility = View.VISIBLE
            audienceToggle.check(R.id.btnAudienceSelected)
            cardClassSelection.visibility = View.VISIBLE
        }

        audienceToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            cardClassSelection.visibility =
                if (checkedId == R.id.btnAudienceAll) View.GONE else View.VISIBLE
            updateSendState()
        }

        findViewById<MaterialButton>(R.id.btnChooseClasses).setOnClickListener {
            showClassPicker()
        }

        renderSelectedClasses()
    }

    private fun showClassPicker() {
        if (availableClasses.isEmpty()) {
            AppToast.makeText(
                this,
                if (role == UserRole.TEACHER) "در حال حاضر کلاسی به این استاد تخصیص داده نشده است"
                else "کلاس فعالی وجود ندارد",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val labels = availableClasses.map {
            "${it.className}  •  ${it.daysOfWeek}  •  ${it.startTime} تا ${it.endTime}"
        }.toTypedArray()
        val checked = BooleanArray(availableClasses.size) {
            availableClasses[it].id in selectedClassIds
        }
        val workingSelection = selectedClassIds.toMutableSet()

        MaterialAlertDialogBuilder(this)
            .setTitle("انتخاب کلاس‌ها")
            .setMultiChoiceItems(labels, checked) { _, which, isChecked ->
                val classId = availableClasses[which].id
                if (isChecked) workingSelection += classId else workingSelection -= classId
            }
            .setNegativeButton("انصراف", null)
            .setPositiveButton("تأیید") { _, _ ->
                selectedClassIds.clear()
                selectedClassIds.addAll(workingSelection)
                renderSelectedClasses()
                updateSendState()
            }
            .show()
    }

    private fun renderSelectedClasses() {
        val names = availableClasses
            .filter { it.id in selectedClassIds }
            .map { it.className }

        if (names.isEmpty()) {
            txtSelectedClassesSummary.text = "در حال حاضر کلاسی انتخاب نشده است"
            txtSelectedClassesSummary.textDirection = View.TEXT_DIRECTION_RTL
            txtSelectedClassesSummary.gravity = Gravity.RIGHT
        } else {
            txtSelectedClassesSummary.text = names.joinToString("\n") { "• $it" }
            // نام کلاس‌ها انگلیسی هستند؛ هر کلاس در یک خط و کاملاً چپ‌چین نمایش داده می‌شود.
            txtSelectedClassesSummary.textDirection = View.TEXT_DIRECTION_LTR
            txtSelectedClassesSummary.gravity = Gravity.LEFT
        }
    }

    private fun setupInputs() {
        setupSimpleHint(
            input = etTitle,
            normalHint = "عنوان اعلان را بنویسید"
        )
        setupSimpleHint(
            input = etBody,
            normalHint = "متن کامل اعلان را بنویسید"
        )

        applyDynamicInputDirection(etTitle, multiline = false)
        applyDynamicInputDirection(etBody, multiline = true)

        etTitle.addTextChangedListener(
            counterWatcher(etTitle, txtTitleCounter, 120, multiline = false) {
                updateSendState()
            }
        )
        etBody.addTextChangedListener(
            counterWatcher(etBody, txtBodyCounter, 5000, multiline = true) {
                updateSendState()
            }
        )
    }

    /**
     * هینت فقط زمانی نمایش داده می‌شود که فیلد خالی و بدون فوکوس باشد.
     * هنگام فوکوس یا وجود متن، هینت کامل حذف می‌شود و هیچ Hint شناوری نداریم.
     */
    private fun setupSimpleHint(
        input: TextInputEditText,
        normalHint: String
    ) {
        fun refreshHint() {
            input.hint = if (!input.hasFocus() && input.text.isNullOrEmpty()) {
                normalHint
            } else {
                null
            }
        }

        input.setOnFocusChangeListener { _, _ ->
            refreshHint()
        }

        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(
                s: CharSequence?,
                start: Int,
                count: Int,
                after: Int
            ) = Unit

            override fun onTextChanged(
                s: CharSequence?,
                start: Int,
                before: Int,
                count: Int
            ) = Unit

            override fun afterTextChanged(s: Editable?) {
                refreshHint()
            }
        })

        refreshHint()
    }

    private fun counterWatcher(
        input: TextInputEditText,
        counter: TextView,
        max: Int,
        multiline: Boolean,
        onChanged: () -> Unit
    ) = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
        override fun afterTextChanged(s: Editable?) {
            counter.text = "${s?.length ?: 0} / $max"
            applyDynamicInputDirection(input, multiline)
            onChanged()
        }
    }

    private fun setupAttachment() {
        findViewById<MaterialButton>(R.id.btnAttachFile).setOnClickListener {
            attachmentPicker.launch(arrayOf("*/*"))
        }
        findViewById<ImageButton>(R.id.btnRemoveAttachment).setOnClickListener {
            attachmentUri = null
            attachmentName = null
            attachmentMimeType = null
            attachmentSizeBytes = null
            renderAttachment()
            AppToast.info(this, "پیوست از اعلان حذف شد")
        }
        renderAttachment()
    }

    private fun renderAttachment() {
        if (attachmentUri == null) {
            cardSelectedAttachment.visibility = View.GONE
            return
        }

        cardSelectedAttachment.visibility = View.VISIBLE
        txtSelectedFileName.text = attachmentName ?: "فایل پیوست"
        val sizeText = attachmentSizeBytes?.let(::formatFileSize) ?: "حجم نامشخص"
        txtSelectedFileMeta.text = listOfNotNull(
            attachmentMimeType?.takeIf { it.isNotBlank() },
            sizeText
        ).joinToString(" • ")
    }

    private fun setupSend() {
        btnSend.setOnClickListener {
            val title = etTitle.text?.toString()?.trim().orEmpty()
            val body = etBody.text?.toString()?.trim().orEmpty()
            val scope = if (
                role == UserRole.ADMIN &&
                audienceToggle.checkedButtonId == R.id.btnAudienceAll
            ) {
                AnnouncementScope.ALL_CLASSES
            } else {
                AnnouncementScope.SELECTED_CLASSES
            }

            when {
                title.isBlank() -> {
                    etTitle.requestFocus()
                    AppToast.warning(this, "عنوان اعلان را وارد کنید")
                    return@setOnClickListener
                }

                body.isBlank() -> {
                    etBody.requestFocus()
                    AppToast.warning(this, "متن اعلان را وارد کنید")
                    return@setOnClickListener
                }

                availableClasses.isEmpty() -> {
                    AppToast.warning(
                        this,
                        if (role == UserRole.TEACHER) {
                            "کلاس فعالی به شما تخصیص داده نشده است؛ امکان ارسال اعلان وجود ندارد."
                        } else {
                            "کلاس فعالی برای ارسال اعلان وجود ندارد."
                        }
                    )
                    return@setOnClickListener
                }

                scope == AnnouncementScope.SELECTED_CLASSES && selectedClassIds.isEmpty() -> {
                    AppToast.warning(this, "حداقل یک کلاس را برای دریافت اعلان انتخاب کنید")
                    return@setOnClickListener
                }
            }

            sendOnlineAnnouncement(title, body, scope)
        }
    }

    private fun sendOnlineAnnouncement(
        title: String,
        body: String,
        scope: AnnouncementScope
    ) {
        val announcementId = UUID.randomUUID().toString()
        val targetJson = Gson().toJson(
            if (scope == AnnouncementScope.SELECTED_CLASSES) selectedClassIds.toList()
            else emptyList<String>()
        )

        val temporaryFile = runCatching { createTemporaryAttachmentFile() }
            .getOrElse {
                AppToast.error(
                    this,
                    "خواندن فایل پیوست امکان‌پذیر نبود؛ فایل را مجدداً انتخاب کنید یا مجوز دسترسی به فایل را بررسی کنید"
                )
                return
            }
        val attachmentPart = temporaryFile?.let { file ->
            val mediaType = attachmentMimeType?.toMediaTypeOrNull()
                ?: "application/octet-stream".toMediaTypeOrNull()
            MultipartBody.Part.createFormData(
                "attachment",
                attachmentName ?: file.name,
                file.asRequestBody(mediaType)
            )
        }

        setSending(true)
        RetrofitClient.instance.createAnnouncement(
            id = announcementId.toRequestBody("text/plain".toMediaTypeOrNull()),
            title = title.toRequestBody("text/plain; charset=utf-8".toMediaTypeOrNull()),
            body = body.toRequestBody("text/plain; charset=utf-8".toMediaTypeOrNull()),
            scope = scope.name.toRequestBody("text/plain".toMediaTypeOrNull()),
            targetClassIds = targetJson.toRequestBody("application/json".toMediaTypeOrNull()),
            attachment = attachmentPart
        ).enqueue(object : Callback<CreateAnnouncementResponse> {
            override fun onResponse(
                call: Call<CreateAnnouncementResponse>,
                response: Response<CreateAnnouncementResponse>
            ) {
                temporaryFile?.delete()
                setSending(false)
                val result = response.body()
                if (response.isSuccessful && result?.status == "success") {
                    AppToast.makeText(
                        this@CreateAnnouncementActivity,
                        result.message.ifBlank { "اعلان با موفقیت ارسال شد" },
                        Toast.LENGTH_SHORT
                    ).show()
                    setResult(RESULT_OK)
                    finish()
                } else {
                    AppToast.error(
                        this@CreateAnnouncementActivity,
                        result?.message?.takeIf { it.isNotBlank() }
                            ?: ApiErrorParser.userMessage(
                                response,
                                "ارسال اعلان کامل نشد"
                            )
                    )
                }
            }

            override fun onFailure(call: Call<CreateAnnouncementResponse>, t: Throwable) {
                temporaryFile?.delete()
                setSending(false)
                AppToast.error(
                    this@CreateAnnouncementActivity,
                    ApiErrorParser.networkMessage(t, "ارسال اعلان")
                )
            }
        })
    }

    private fun createTemporaryAttachmentFile(): File? {
        val uri = attachmentUri ?: return null
        val suffix = attachmentName
            ?.substringAfterLast('.', "")
            ?.takeIf { it.isNotBlank() }
            ?.let { ".$it" }
            ?: ".tmp"
        val file = File.createTempFile("announcement_", suffix, cacheDir)
        contentResolver.openInputStream(uri)?.use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        } ?: error("Cannot open attachment")
        return file
    }

    private fun setSending(value: Boolean) {
        sending = value
        btnSend.text = if (value) "در حال ارسال..." else "ارسال اعلان"
        updateSendState()
    }

    /**
     * دکمه ارسال هنگام ناقص بودن فرم عمداً قابل لمس می‌ماند تا با لمس آن
     * دلیل دقیق آماده نبودن فرم به کاربر گفته شود. فقط هنگام ارسال واقعی غیرفعال می‌شود.
     */
    private fun updateSendState() {
        if (sending) {
            btnSend.isEnabled = false
            btnSend.alpha = 0.65f
            return
        }

        val titleReady = !etTitle.text.isNullOrBlank()
        val bodyReady = !etBody.text.isNullOrBlank()
        val audienceReady =
            availableClasses.isNotEmpty() && (
                (role == UserRole.ADMIN &&
                    audienceToggle.checkedButtonId == R.id.btnAudienceAll) ||
                    selectedClassIds.isNotEmpty()
            )

        btnSend.isEnabled = true
        btnSend.alpha = if (titleReady && bodyReady && audienceReady) 1f else 0.72f
    }


    private fun applyDynamicInputDirection(input: TextInputEditText, multiline: Boolean) {
        val rtl = isRtlText(input.text)

        input.textDirection = if (rtl) {
            View.TEXT_DIRECTION_RTL
        } else {
            View.TEXT_DIRECTION_LTR
        }
        input.textAlignment = View.TEXT_ALIGNMENT_GRAVITY
        input.gravity = (if (rtl) Gravity.RIGHT else Gravity.LEFT) or if (multiline) {
            Gravity.TOP
        } else {
            Gravity.CENTER_VERTICAL
        }
    }

    private fun isRtlText(text: CharSequence?): Boolean {
        if (text.isNullOrBlank()) return true
        for (char in text) {
            when (Character.getDirectionality(char)) {
                Character.DIRECTIONALITY_RIGHT_TO_LEFT,
                Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC,
                Character.DIRECTIONALITY_RIGHT_TO_LEFT_EMBEDDING,
                Character.DIRECTIONALITY_RIGHT_TO_LEFT_OVERRIDE -> return true

                Character.DIRECTIONALITY_LEFT_TO_RIGHT,
                Character.DIRECTIONALITY_LEFT_TO_RIGHT_EMBEDDING,
                Character.DIRECTIONALITY_LEFT_TO_RIGHT_OVERRIDE -> return false
            }
        }
        return true
    }

    private data class AttachmentMetadata(
        val name: String?,
        val sizeBytes: Long?
    )

    private fun readAttachmentMetadata(uri: Uri): AttachmentMetadata {
        var name: String? = null
        var size: Long? = null
        contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameIndex >= 0) name = cursor.getString(nameIndex)
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
            }
        }
        return AttachmentMetadata(name, size)
    }

    private fun formatFileSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes بایت"
        bytes < 1024 * 1024 -> String.format("%.1f کیلوبایت", bytes / 1024.0)
        else -> String.format("%.1f مگابایت", bytes / (1024.0 * 1024.0))
    }
}

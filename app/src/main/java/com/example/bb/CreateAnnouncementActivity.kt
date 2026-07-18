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
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import java.util.UUID

class CreateAnnouncementActivity : AppCompatActivity() {

    private lateinit var role: UserRole
    private var phone: String = ""

    private lateinit var availableClasses: List<ClassModel>
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
                Toast.makeText(this, "حداکثر حجم پیوست ۲۰ مگابایت است", Toast.LENGTH_LONG).show()
                return@registerForActivityResult
            }

            attachmentUri = uri
            attachmentName = metadata.name
            attachmentMimeType = contentResolver.getType(uri)
            attachmentSizeBytes = metadata.sizeBytes
            renderAttachment()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_announcement)

        val prefs = getSharedPreferences("LocalAppPrefs", Context.MODE_PRIVATE)
        phone = prefs.getString("CURRENT_USERNAME", "").orEmpty()
        role = runCatching {
            UserRole.valueOf(
                intent.getStringExtra("USER_ROLE")
                    ?: prefs.getString("CURRENT_USER_ROLE", "TEACHER").orEmpty()
            )
        }.getOrDefault(UserRole.TEACHER)

        if (role == UserRole.STUDENT) {
            Toast.makeText(this, "دانش‌آموز فقط امکان مشاهده اعلانات را دارد", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        availableClasses = when (role) {
            UserRole.ADMIN -> AppDatabase.getAllClasses(false)
            UserRole.TEACHER -> AppDatabase.getTeacherClasses(phone)
            UserRole.STUDENT -> emptyList()
        }.sortedBy { it.className }

        bindViews()
        setupHeader()
        setupAudience()
        setupInputs()
        setupAttachment()
        setupSend()
        updateSendState()
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
            Toast.makeText(
                this,
                if (role == UserRole.TEACHER) "هنوز کلاسی به این استاد تخصیص داده نشده است"
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
            txtSelectedClassesSummary.text = "هنوز کلاسی انتخاب نشده است"
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

            val scope = when {
                role == UserRole.ADMIN && audienceToggle.checkedButtonId == R.id.btnAudienceAll ->
                    AnnouncementScope.ALL_CLASSES
                else -> AnnouncementScope.SELECTED_CLASSES
            }

            if (title.isBlank() || body.isBlank()) {
                Toast.makeText(this, "عنوان و متن اعلان را کامل کنید", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (scope == AnnouncementScope.SELECTED_CLASSES && selectedClassIds.isEmpty()) {
                Toast.makeText(this, "حداقل یک کلاس را انتخاب کنید", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val announcement = Announcement(
                id = UUID.randomUUID().toString(),
                title = title,
                body = body,
                senderName = AppDatabase.getDisplayName(role, phone),
                senderPhone = phone,
                senderRole = if (role == UserRole.ADMIN) {
                    AnnouncementSenderRole.ADMIN
                } else {
                    AnnouncementSenderRole.TEACHER
                },
                createdAt = AppDatabase.currentTimestamp(),
                scope = scope,
                targetClassIds = if (scope == AnnouncementScope.SELECTED_CLASSES) {
                    selectedClassIds.toList()
                } else {
                    emptyList()
                },
                type = if (attachmentUri == null) MessageType.TEXT_ONLY else MessageType.ATTACHMENT,
                attachmentName = attachmentName,
                attachmentUrl = attachmentUri?.toString(),
                attachmentMimeType = attachmentMimeType,
                attachmentSizeBytes = attachmentSizeBytes
            )

            AppDatabase.addAnnouncement(announcement)
            // فرستنده پیام خودش را از همان ابتدا خوانده‌شده می‌بیند.
            AppDatabase.markAnnouncementRead(announcement.id, role, phone)
            Toast.makeText(this, "اعلان با موفقیت ثبت شد", Toast.LENGTH_SHORT).show()
            setResult(RESULT_OK)
            finish()
        }
    }

    private fun updateSendState() {
        val titleReady = !etTitle.text.isNullOrBlank()
        val bodyReady = !etBody.text.isNullOrBlank()
        val audienceReady =
            (role == UserRole.ADMIN &&
                    audienceToggle.checkedButtonId == R.id.btnAudienceAll &&
                    availableClasses.isNotEmpty()) ||
                    selectedClassIds.isNotEmpty()

        btnSend.isEnabled = titleReady && bodyReady && audienceReady
        btnSend.alpha = if (btnSend.isEnabled) 1f else 0.55f
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
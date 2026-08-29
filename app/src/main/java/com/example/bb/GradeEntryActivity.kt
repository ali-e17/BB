package com.example.bb

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.text.method.ReplacementTransformationMethod
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.Locale

class GradeEntryActivity : BaseActivity() {

    private lateinit var classId: String
    private lateinit var className: String
    private lateinit var rvStudents: RecyclerView
    private lateinit var draftButton: MaterialButton
    private lateinit var publishButton: MaterialButton
    private lateinit var progress: View
    private lateinit var studentsCountText: TextView
    private lateinit var readyCountText: TextView
    private lateinit var incompleteCountText: TextView
    private lateinit var gradeSummaryCard: View

    private var config: ReportConfigDto? = null
    private val students = mutableListOf<EditableStudent>()
    private lateinit var adapter: RosterAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_grade_entry)

        classId = intent.getStringExtra(EXTRA_CLASS_ID).orEmpty()
        className = intent.getStringExtra(EXTRA_CLASS_NAME).orEmpty()

        if (classId.isBlank()) {
            finish()
            return
        }

        findViewById<ImageView>(R.id.btnGradeBack).setOnClickListener { finish() }
        findViewById<TextView>(R.id.txtGradeClassName).text =
            className.ifBlank { "ورود نمرات" }
        findViewById<TextView>(R.id.txtGradeSubtitle).text =
            "مرحله ۳ از ۳ • ثبت نمرات کارنامه"

        gradeSummaryCard = findViewById(R.id.cardGradeSummary)
        rvStudents = findViewById(R.id.rvStudents)
        draftButton = findViewById(R.id.btnSaveDraft)
        publishButton = findViewById(R.id.btnPublishReports)
        progress = findViewById(R.id.progressGradeEntry)
        studentsCountText = findViewById(R.id.txtStudentsCount)
        readyCountText = findViewById(R.id.txtReadyCount)
        incompleteCountText = findViewById(R.id.txtIncompleteCount)

        rvStudents.layoutManager = LinearLayoutManager(this)
        rvStudents.itemAnimator = null
        rvStudents.setHasFixedSize(false)
        adapter = RosterAdapter()
        rvStudents.adapter = adapter
        setupKeyboardNavigation()

        draftButton.setOnClickListener { save(publish = false) }
        publishButton.setOnClickListener { requestPublish() }

        updateSummary()
        loadRoster()
    }

    /**
     * مدیریت IME برای صفحه ورود نمرات.
     *
     * اپ به‌صورت edge-to-edge اجرا می‌شود؛ بنابراین adjustResize روی همه
     * دستگاه‌ها کافی نیست. ارتفاع واقعی کیبورد به فضای قابل اسکرول
     * RecyclerView اضافه می‌شود تا فیلد فعال پشت کیبورد نرود.
     */
    /**
     * عمداً هیچ WindowInsets/IME padding سفارشی روی RecyclerView اعمال نمی‌کنیم.
     *
     * در این صفحه adjustResize فعال است؛ بنابراین وقتی کیبورد باز می‌شود،
     * خود پنجره کوچک می‌شود. RecyclerView نیز در XML به بالای gradeBottom
     * متصل است و واقعاً فضای قابل نمایش آن کم می‌شود.
     */
    private fun setupKeyboardNavigation() {
        rvStudents.clipToPadding = false
    }

    /**
     * از مکانیزم استاندارد خود Android برای آوردن فیلد فوکوس‌شده داخل
     * viewport استفاده می‌کنیم. این درخواست از EditText به والدها
     * (کارت -> RecyclerView) منتقل می‌شود.
     */
    private fun ensureScoreInputVisible(
        field: View,
        smooth: Boolean = true
    ) {
        if (!field.isAttachedToWindow) return

        field.post {
            if (!field.isAttachedToWindow) {
                return@post
            }

            val rect = android.graphics.Rect(
                0,
                -dp(8),
                field.width,
                field.height + dp(88)
            )

            field.requestRectangleOnScreen(
                rect,
                !smooth
            )

            // یک بار دیگر بعد از resize کامل پنجره.
            field.postDelayed({
                if (field.isAttachedToWindow) {
                    field.requestRectangleOnScreen(
                        android.graphics.Rect(
                            0,
                            -dp(8),
                            field.width,
                            field.height + dp(88)
                        ),
                        true
                    )
                }
            }, 180)
        }
    }

    private fun focusScoreField(
        field: TextInputEditText
    ) {
        field.requestFocus()

        val length = field.text?.length ?: 0
        if (length > 0) {
            field.setSelection(0, length)
        }

        ensureScoreInputVisible(
            field,
            smooth = false
        )

        field.postDelayed({
            val imm =
                getSystemService(
                    Context.INPUT_METHOD_SERVICE
                ) as InputMethodManager

            imm.showSoftInput(
                field,
                InputMethodManager.SHOW_IMPLICIT
            )

            // بعد از باز شدن کامل کیبورد یک بار دیگر موقعیت فیلد اصلاح می‌شود.
            field.postDelayed({
                ensureScoreInputVisible(
                    field,
                    smooth = false
                )
            }, 220)
        }, 60)
    }

    private fun hideKeyboard(view: View) {
        val imm =
            getSystemService(
                Context.INPUT_METHOD_SERVICE
            ) as InputMethodManager

        imm.hideSoftInputFromWindow(
            view.windowToken,
            0
        )
    }

    private fun loadRoster() {
        setLoading(true)

        RetrofitClient.instance.getReportRoster(classId)
            .enqueue(object : Callback<ReportRosterResponse> {
                override fun onResponse(
                    call: Call<ReportRosterResponse>,
                    response: Response<ReportRosterResponse>
                ) {
                    setLoading(false)
                    val body = response.body()

                    if (!response.isSuccessful ||
                        body?.status != "success" ||
                        body.config == null
                    ) {
                        toast(
                            body?.message?.takeIf { it.isNotBlank() }
                                ?: ApiErrorParser.userMessage(response, "دریافت فهرست نمرات کامل نشد")
                        )
                        return
                    }

                    config = body.config
                    students.clear()
                    students.addAll(
                        body.students.map { student ->
                            EditableStudent(
                                id = student.id,
                                name = student.name,
                                studentCode = student.studentCode,
                                cardId = student.cardId,
                                status = student.status,
                                revision = student.revision,
                                scores = body.config.components.associate { component ->
                                    component.id to student.scores[component.id]
                                }.toMutableMap()
                            )
                        }
                    )

                    adapter.notifyDataSetChanged()
                    updateSummary()
                    updateActionButtons()

                    if (students.isEmpty()) {
                        toast("دانش‌آموزی در سابقه این کلاس ثبت نشده است")
                    }
                }

                override fun onFailure(
                    call: Call<ReportRosterResponse>,
                    t: Throwable
                ) {
                    setLoading(false)
                    toast(ApiErrorParser.networkMessage(t, "دریافت فهرست نمرات"))
                }
            })
    }

    private fun requestPublish() {
        rvStudents.clearFocus()

        if (students.isEmpty()) {
            AppToast.warning(this, "دانش‌آموزی برای انتشار کارنامه در این کلاس وجود ندارد")
            return
        }

        val components = config?.components.orEmpty()
        val incomplete = students.any { student ->
            components.any { component -> student.scores[component.id] == null }
        }

        if (incomplete) {
            toast("برای انتشار نهایی، نمرات تمامی دانش‌آموزان باید کامل باشد")
            return
        }

        val isRepublish = students.any { it.status == "PUBLISHED" }
        MaterialAlertDialogBuilder(this)
            .setTitle(if (isRepublish) "انتشار مجدد کارنامه‌ها" else "انتشار کارنامه‌ها")
            .setMessage(
                if (isRepublish) {
                    "نسخه جدید جایگزین نسخه قبلی می‌شود و دانش‌آموز آخرین نتیجه را خواهد دید. ادامه می‌دهید؟"
                } else {
                    "پس از انتشار، دانش‌آموزان قادر به مشاهده نتایج خود خواهند بود. ادامه می‌دهید؟"
                }
            )
            .setNegativeButton("انصراف", null)
            .setPositiveButton(if (isRepublish) "انتشار مجدد" else "انتشار نهایی") { _, _ ->
                save(publish = true)
            }
            .show()
    }

    private fun save(publish: Boolean) {
        rvStudents.clearFocus()

        if (students.isEmpty()) {
            AppToast.warning(
                this,
                if (publish) "دانش‌آموزی برای انتشار کارنامه در این کلاس وجود ندارد"
                else "دانش‌آموزی برای ذخیره نمرات در این کلاس وجود ندارد"
            )
            return
        }

        val currentConfig = config ?: return toast(
            "تنظیمات کارنامه دریافت نشده است؛ لطفاً صفحه را مجدداً باز کنید یا اتصال اینترنت را بررسی کنید"
        )

        val payload = students.map { student ->
            SaveReportStudentRequest(
                studentId = student.id,
                expectedRevision = student.revision,
                scores = student.scores.toMap()
            )
        }

        setLoading(true)
        RetrofitClient.instance.saveReportCards(
            SaveReportCardsRequest(
                classId = classId,
                expectedConfigRevision = currentConfig.revision,
                publish = publish,
                students = payload
            )
        ).enqueue(object : Callback<SaveReportCardsResponse> {
            override fun onResponse(
                call: Call<SaveReportCardsResponse>,
                response: Response<SaveReportCardsResponse>
            ) {
                setLoading(false)
                val body = response.body()
                val apiError = ApiErrorParser.parse(response)

                if (response.isSuccessful && body?.status == "success") {
                    toast(
                        if (publish) {
                            "کارنامه‌ها با موفقیت منتشر شدند"
                        } else {
                            "پیش‌نویس با موفقیت ذخیره شد"
                        }
                    )
                    loadRoster()
                    return
                }

                toast(
                    body?.message?.takeIf { it.isNotBlank() }
                        ?: ApiErrorParser.userMessage(response, apiError, "ذخیره نمرات کامل نشد")
                )
                if (apiError?.code == "CONFIG_REVISION_CONFLICT" ||
                    apiError?.code == "REVISION_CONFLICT" ||
                    response.code() == 409
                ) {
                    loadRoster()
                }
            }

            override fun onFailure(call: Call<SaveReportCardsResponse>, t: Throwable) {
                setLoading(false)
                toast(ApiErrorParser.networkMessage(t, "ذخیره نمرات"))
            }
        })
    }

    /**
     * پیش‌نمایش کارنامه باید دقیقاً نمره‌های همین لحظه فرم را نشان بدهد.
     *
     * قبلاً دکمه چشم فقط cardId قبلی را باز می‌کرد. اگر معیارها تغییر کرده
     * بودند یا کارنامه قبلی invalidate شده بود، سرور یک کارت 0/100 بدون ردیف
     * داشت و همان نمایش داده می‌شد؛ حتی اگر کاربر نمره‌های جدید را در فرم
     * وارد کرده بود ولی هنوز ذخیره نکرده بود.
     *
     * حالا برای Draft، فقط همین دانش‌آموز را قبل از Preview ذخیره می‌کنیم.
     */
    private fun previewStudentWithLatestScores(
        student: EditableStudent
    ) {
        rvStudents.clearFocus()

        // کارنامه منتشرشده باید فقط بعد از «انتشار مجدد» تغییر کند.
        if (student.status == "PUBLISHED") {
            val existingCardId =
                student.cardId.orEmpty()

            if (existingCardId.isBlank()) {
                AppToast.warning(
                    this,
                    "شناسه کارنامه منتشرشده در دسترس نیست؛ صفحه را دوباره باز کنید"
                )
                return
            }

            openReportPreview(
                existingCardId
            )
            return
        }

        val currentConfig =
            config ?: return toast(
                "تنظیمات کارنامه دریافت نشده است؛ صفحه را دوباره باز کنید"
            )

        val hasAnyScore =
            currentConfig.components.any { component ->
                student.scores[component.id] != null
            }

        if (!hasAnyScore) {
            AppToast.warning(
                this,
                "برای مشاهده پیش‌نمایش، حداقل یک نمره برای این دانش‌آموز وارد کنید"
            )
            return
        }

        setLoading(true)

        RetrofitClient.instance.saveReportCards(
            SaveReportCardsRequest(
                classId = classId,
                expectedConfigRevision =
                    currentConfig.revision,
                publish = false,
                students = listOf(
                    SaveReportStudentRequest(
                        studentId = student.id,
                        expectedRevision =
                            student.revision,
                        scores =
                            student.scores.toMap()
                    )
                )
            )
        ).enqueue(
            object :
                Callback<SaveReportCardsResponse> {

                override fun onResponse(
                    call: Call<SaveReportCardsResponse>,
                    response: Response<SaveReportCardsResponse>
                ) {
                    setLoading(false)

                    val body =
                        response.body()

                    if (
                        response.isSuccessful &&
                        body?.status == "success"
                    ) {
                        val saved =
                            body.cards.firstOrNull {
                                it.studentId ==
                                    student.id
                            }

                        if (
                            saved == null ||
                            saved.cardId.isBlank()
                        ) {
                            AppToast.error(
                                this@GradeEntryActivity,
                                "پیش‌نویس ذخیره شد اما شناسه کارنامه از سرور دریافت نشد"
                            )
                            return
                        }

                        student.cardId =
                            saved.cardId
                        student.revision =
                            saved.revision
                        student.status =
                            saved.status

                        openReportPreview(
                            saved.cardId
                        )
                        return
                    }

                    toast(
                        body?.message
                            ?.takeIf {
                                it.isNotBlank()
                            }
                            ?: ApiErrorParser.userMessage(
                                response,
                                "ذخیره پیش‌نمایش کارنامه کامل نشد"
                            )
                    )
                }

                override fun onFailure(
                    call: Call<SaveReportCardsResponse>,
                    t: Throwable
                ) {
                    setLoading(false)

                    toast(
                        ApiErrorParser.networkMessage(
                            t,
                            "ذخیره پیش‌نمایش کارنامه"
                        )
                    )
                }
            }
        )
    }

    private fun openReportPreview(
        cardId: String
    ) {
        startActivity(
            Intent(
                this,
                ReportCardViewActivity::class.java
            ).putExtra(
                ReportCardViewActivity.EXTRA_REPORT_CARD_ID,
                cardId
            )
        )
    }

    private fun updateSummary() {
        val components = config?.components.orEmpty()
        val ready = if (components.isEmpty()) {
            0
        } else {
            students.count { student ->
                components.all { component ->
                    student.scores[component.id] != null
                }
            }
        }

        studentsCountText.text = students.size.toString()
        readyCountText.text = ready.toString()
        incompleteCountText.text = (students.size - ready).toString()
    }

    private fun updateActionButtons() {
        val hasPublished = students.any { it.status == "PUBLISHED" }
        val hasStudents = students.isNotEmpty()
        draftButton.visibility = if (hasPublished) View.GONE else View.VISIBLE
        publishButton.text = if (hasPublished) "انتشار مجدد" else "انتشار نهایی"
        draftButton.isEnabled = true
        publishButton.isEnabled = true
        draftButton.alpha = if (hasStudents) 1f else 0.72f
        publishButton.alpha = if (hasStudents) 1f else 0.72f
    }

    private fun setLoading(value: Boolean) {
        progress.visibility = if (value) View.VISIBLE else View.GONE
        rvStudents.isEnabled = !value
        if (value) {
            draftButton.isEnabled = false
            publishButton.isEnabled = false
        } else {
            updateActionButtons()
        }
    }

    /**
     * مقدار خامی که داخل EditText نگه داشته می‌شود.
     * انگلیسی و با نقطه است تا parsing و Backend پایدار بمانند.
     */
    private fun format(value: Double): String =
        UiTextFormatter.formatEnglishDecimal(value)

    /**
     * نمایش عدد در UI فارسی کارنامه: 12.5 -> ۱۲/۵
     */
    private fun formatPersian(value: Double): String =
        UiTextFormatter.formatPersianDecimalSlash(value)

    private fun toast(message: String) {
        AppToast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun roundedBadge(
        fillColor: Int,
        borderColor: Int
    ): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = 14f * resources.displayMetrics.density
        setColor(fillColor)
        setStroke(dp(1), borderColor)
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    /**
     * فقط نمایش نمره را فارسی می‌کند.
     *
     * مقدار واقعی داخل EditText تغییر نمی‌کند:
     * 12.5  -> مقدار واقعی
     * ۱۲/۵  -> چیزی که کاربر می‌بیند
     *
     * چون جایگزینی‌ها یک‌به‌یک و هم‌طول هستند، موقعیت Cursor و composing
     * کیبورد به‌هم نمی‌ریزد.
     */
    private class PersianScoreTransformationMethod :
        ReplacementTransformationMethod() {

        override fun getOriginal(): CharArray =
            charArrayOf(
                '0', '1', '2', '3', '4',
                '5', '6', '7', '8', '9',
                '.', ',', '،', '٫'
            )

        override fun getReplacement(): CharArray =
            charArrayOf(
                '۰', '۱', '۲', '۳', '۴',
                '۵', '۶', '۷', '۸', '۹',
                '/', '/', '/', '/'
            )
    }

    private data class EditableStudent(
        val id: String,
        val name: String,
        val studentCode: String,
        var cardId: String?,
        var status: String,
        var revision: Int,
        val scores: MutableMap<String, Double?>
    )

    private inner class RosterAdapter :
        RecyclerView.Adapter<RosterAdapter.ViewHolder>() {

        private var expandedStudentId: String? = null

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val headerLayout: View = view.findViewById(R.id.headerLayout)
            val expandableBody: View = view.findViewById(R.id.expandableBody)
            val txtStudentName: TextView = view.findViewById(R.id.txtStudentName)
            val txtStudentCode: TextView = view.findViewById(R.id.txtStudentCode)
            val txtStudentTotal: TextView = view.findViewById(R.id.txtStudentTotal)
            val txtStatusBadge: TextView = view.findViewById(R.id.txtStatusBadge)
            val imgExpandArrow: ImageView = view.findViewById(R.id.imgExpandArrow)
            val btnPreviewCard: ImageView = view.findViewById(R.id.btnPreviewCard)
            val containerGrades: LinearLayout = view.findViewById(R.id.containerGrades)
            val txtExpandedTotal: TextView = view.findViewById(R.id.txtExpandedTotal)
        }

        override fun onCreateViewHolder(
            parent: ViewGroup,
            viewType: Int
        ): ViewHolder = ViewHolder(
            LayoutInflater.from(parent.context).inflate(
                R.layout.item_student_grade,
                parent,
                false
            )
        )

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val student = students[position]
            val components = config?.components.orEmpty()
                .sortedBy { it.sortOrder }

            bindStudentHeader(holder, student, components)

            val isExpanded = student.id == expandedStudentId
            holder.expandableBody.visibility =
                if (isExpanded) View.VISIBLE else View.GONE
            holder.imgExpandArrow.rotation = if (isExpanded) 180f else 0f

            holder.headerLayout.setOnClickListener {
                val previousId = expandedStudentId
                expandedStudentId = if (isExpanded) null else student.id

                val previousIndex =
                    students.indexOfFirst { it.id == previousId }
                if (previousIndex >= 0) {
                    notifyItemChanged(previousIndex)
                }

                val newIndex =
                    students.indexOfFirst { it.id == expandedStudentId }
                if (newIndex >= 0 && newIndex != previousIndex) {
                    notifyItemChanged(newIndex)
                }
            }

            // چشم همیشه فعال است:
            // برای پیش‌نویس، قبل از باز شدن کارنامه همین دانش‌آموز
            // با آخرین نمره‌های داخل فرم ذخیره می‌شود تا Preview قدیمی نباشد.
            holder.btnPreviewCard.alpha = 1.0f

            holder.btnPreviewCard.setOnClickListener {
                previewStudentWithLatestScores(student)
            }

            holder.containerGrades.removeAllViews()
            val inflater = LayoutInflater.from(holder.itemView.context)
            val scoreInputs =
                mutableListOf<TextInputEditText>()

            components.forEach { component ->
                val row = inflater.inflate(
                    R.layout.item_grade_input_compact,
                    holder.containerGrades,
                    false
                )

                row.findViewById<TextView>(R.id.txtCompactCriterion).text =
                    component.title
                row.findViewById<TextView>(R.id.txtCompactMax).text =
                    "حداکثر نمره: ${formatPersian(component.maxScore)}"

                val input =
                    row.findViewById<TextInputEditText>(R.id.etCompactScore)

                // مقدار واقعی EditText همان عدد استاندارد باقی می‌ماند،
                // اما فقط در لایه نمایش، رقم‌ها فارسی دیده می‌شوند.
                // ReplacementTransformationMethod طول متن را تغییر نمی‌دهد؛
                // بنابراین Cursor، IME، TextWatcher و ذخیره نمره پایدار می‌مانند.
                input.transformationMethod =
                    PersianScoreTransformationMethod()

                input.isEnabled = true
                input.isFocusable = true
                input.isFocusableInTouchMode = true
                input.isClickable = true
                input.showSoftInputOnFocus = true
                input.setSelectAllOnFocus(true)

                scoreInputs += input

                student.scores[component.id]?.let {
                    input.setText(format(it))
                }

                fun normalizedScoreText(): String =
                    UiTextFormatter.normalizeEnglishDigits(
                        input.text?.toString()?.trim().orEmpty()
                    )
                        .replace('٫', '.')
                        .replace(',', '.')
                        .replace('/', '.')

                fun parsedScoreOrNull(): Double? {
                    val raw = normalizedScoreText()
                    if (raw.isBlank()) return null

                    val value = raw.toDoubleOrNull()
                        ?: return null

                    return value.takeIf {
                        it >= 0.0 && it <= component.maxScore
                    }
                }

                fun refreshStudentUiAfterEdit() {
                    if (!holder.itemView.isAttachedToWindow) {
                        updateSummary()
                        return
                    }

                    bindStudentHeader(
                        holder,
                        student,
                        components
                    )
                    updateSummary()
                }

                input.setOnFocusChangeListener { _, hasFocus ->
                    if (hasFocus) {
                        ensureScoreInputVisible(
                            input,
                            smooth = false
                        )

                        input.postDelayed({
                            ensureScoreInputVisible(
                                input,
                                smooth = false
                            )
                        }, 320)
                    } else {
                        val raw = normalizedScoreText()
                        val parsed = parsedScoreOrNull()

                        input.error =
                            if (raw.isNotBlank() && parsed == null) {
                                "نمره باید بین ۰ تا ${formatPersian(component.maxScore)} باشد"
                            } else {
                                null
                            }

                        refreshStudentUiAfterEdit()
                    }
                }

                row.findViewById<View>(R.id.btnCompactZero)
                    .setOnClickListener {
                        input.setText("0")
                        student.scores[component.id] = 0.0
                        refreshStudentUiAfterEdit()
                        focusScoreField(input)
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
                        // عمداً هیچ View، Header، Badge یا RecyclerView را
                        // در حین تایپ دوباره bind نمی‌کنیم.
                        // فقط مدل خام به‌روزرسانی می‌شود.
                        val raw =
                            UiTextFormatter.normalizeEnglishDigits(
                                s?.toString()?.trim().orEmpty()
                            )
                                .replace('٫', '.')
                                .replace(',', '.')

                        if (raw.isBlank()) {
                            student.scores[component.id] = null
                            return
                        }

                        val value = raw.toDoubleOrNull()

                        student.scores[component.id] =
                            value?.takeIf {
                                it >= 0.0 &&
                                    it <= component.maxScore
                            }
                    }
                })

                holder.containerGrades.addView(row)
            }

            scoreInputs.forEachIndexed { index, input ->
                val isLast =
                    index == scoreInputs.lastIndex

                input.imeOptions =
                    if (isLast) {
                        EditorInfo.IME_ACTION_DONE
                    } else {
                        EditorInfo.IME_ACTION_NEXT
                    }

                input.setOnEditorActionListener {
                        view,
                        actionId,
                        event ->

                    val enterPressed =
                        event?.let {
                            it.keyCode == KeyEvent.KEYCODE_ENTER &&
                                    it.action == KeyEvent.ACTION_DOWN
                        } == true

                    when {
                        !isLast &&
                                (
                                        actionId == EditorInfo.IME_ACTION_NEXT ||
                                                enterPressed
                                        ) -> {
                            focusScoreField(
                                scoreInputs[index + 1]
                            )
                            true
                        }

                        isLast &&
                                (
                                        actionId == EditorInfo.IME_ACTION_DONE ||
                                                enterPressed
                                        ) -> {
                            hideKeyboard(view)
                            view.clearFocus()
                            true
                        }

                        else -> false
                    }
                }
            }
        }

        private fun bindStudentHeader(
            holder: ViewHolder,
            student: EditableStudent,
            components: List<ReportComponentDto>
        ) {
            holder.txtStudentName.text =
                student.name.ifBlank { "بدون نام" }
            holder.txtStudentCode.text =
                "کد: ${student.studentCode.ifBlank { "—" }}"

            val currentTotal = components.sumOf { component ->
                student.scores[component.id] ?: 0.0
            }
            holder.txtStudentTotal.text =
                "نمره فعلی: ${formatPersian(currentTotal)}"
            holder.txtExpandedTotal.text =
                "جمع نمره‌های واردشده: ${formatPersian(currentTotal)} از ۱۰۰"

            val validCount = components.count { component ->
                student.scores[component.id] != null
            }

            when {
                validCount == 0 -> applyBadge(
                    holder.txtStatusBadge,
                    "خالی",
                    Color.parseColor("#B42318"),
                    Color.parseColor("#FEF3F2"),
                    Color.parseColor("#FECDCA")
                )

                validCount < components.size -> applyBadge(
                    holder.txtStatusBadge,
                    "ناقص",
                    Color.parseColor("#B54708"),
                    Color.parseColor("#FFFAEB"),
                    Color.parseColor("#FEDF89")
                )

                student.status == "PUBLISHED" -> applyBadge(
                    holder.txtStatusBadge,
                    "منتشر شده",
                    Color.parseColor("#175CD3"),
                    Color.parseColor("#EFF8FF"),
                    Color.parseColor("#B2DDFF")
                )

                else -> applyBadge(
                    holder.txtStatusBadge,
                    "آماده",
                    Color.parseColor("#067647"),
                    Color.parseColor("#ECFDF3"),
                    Color.parseColor("#ABEFC6")
                )
            }
        }

        private fun applyBadge(
            view: TextView,
            text: String,
            textColor: Int,
            fillColor: Int,
            borderColor: Int
        ) {
            view.text = text
            view.setTextColor(textColor)
            view.background = roundedBadge(fillColor, borderColor)
        }

        override fun getItemCount(): Int = students.size
    }

    companion object {
        const val EXTRA_CLASS_ID = "REPORT_CLASS_ID"
        const val EXTRA_CLASS_NAME = "REPORT_CLASS_NAME"
    }
}

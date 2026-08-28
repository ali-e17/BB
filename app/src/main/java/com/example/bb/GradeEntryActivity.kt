package com.example.bb

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsAnimationCompat
import androidx.core.view.WindowInsetsCompat
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

    private var baseRecyclerPaddingBottom: Int = 0
    private var currentImeBottom: Int = 0
    private var currentNavigationBottom: Int = 0

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
        adapter = RosterAdapter()
        rvStudents.adapter = adapter
        setupKeyboardAwareScoreEntry()

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
    private fun setupKeyboardAwareScoreEntry() {
        baseRecyclerPaddingBottom = rvStudents.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(rvStudents) { _, insets ->
            applyImeInsets(insets)
            insets
        }

        ViewCompat.setWindowInsetsAnimationCallback(
            rvStudents,
            object : WindowInsetsAnimationCompat.Callback(
                WindowInsetsAnimationCompat.Callback.DISPATCH_MODE_CONTINUE_ON_SUBTREE
            ) {
                override fun onProgress(
                    insets: WindowInsetsCompat,
                    runningAnimations: MutableList<WindowInsetsAnimationCompat>
                ): WindowInsetsCompat {
                    applyImeInsets(insets)
                    return insets
                }

                override fun onEnd(animation: WindowInsetsAnimationCompat) {
                    super.onEnd(animation)
                    val focused = currentFocus
                    if (focused != null && isDescendantOfRecycler(focused)) {
                        focused.postDelayed(
                            { ensureScoreInputVisible(focused, smooth = false) },
                            40
                        )
                    }
                }
            }
        )

        ViewCompat.requestApplyInsets(rvStudents)
    }

    private fun applyImeInsets(insets: WindowInsetsCompat) {
        val imeBottom =
            insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
        val navigationBottom =
            insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
        val imeVisible =
            insets.isVisible(WindowInsetsCompat.Type.ime())

        currentImeBottom =
            if (imeVisible) imeBottom else 0
        currentNavigationBottom =
            navigationBottom

        val extraBottom = if (imeVisible) {
            maxOf(
                0,
                imeBottom - navigationBottom
            )
        } else {
            0
        }

        val wantedBottom =
            baseRecyclerPaddingBottom + extraBottom

        if (rvStudents.paddingBottom != wantedBottom) {
            rvStudents.setPadding(
                rvStudents.paddingLeft,
                rvStudents.paddingTop,
                rvStudents.paddingRight,
                wantedBottom
            )
        }

        if (imeVisible) {
            val focused = currentFocus
            if (focused != null && isDescendantOfRecycler(focused)) {
                focused.post {
                    ensureScoreInputVisible(
                        focused,
                        smooth = false
                    )
                }
            }
        }
    }

    private fun isDescendantOfRecycler(view: View): Boolean {
        var current: View? = view

        while (current != null) {
            if (current === rvStudents) {
                return true
            }
            current = current.parent as? View
        }

        return false
    }

    /**
     * هر فیلد نمره را داخل ناحیه قابل مشاهده و بالای کیبورد نگه می‌دارد.
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

            val fieldRect = Rect()
            if (!field.getGlobalVisibleRect(fieldRect)) {
                return@post
            }

            val rootView = field.rootView
            val keyboardTop = if (currentImeBottom > 0) {
                rootView.height - currentImeBottom
            } else {
                rootView.height - currentNavigationBottom
            }

            val safeBottom =
                keyboardTop - dp(20)

            val summaryRect = Rect()
            gradeSummaryCard.getGlobalVisibleRect(summaryRect)
            val safeTop =
                summaryRect.bottom + dp(8)

            when {
                fieldRect.bottom > safeBottom -> {
                    val delta =
                        fieldRect.bottom - safeBottom + dp(18)

                    if (smooth) {
                        rvStudents.smoothScrollBy(0, delta)
                    } else {
                        rvStudents.scrollBy(0, delta)
                    }
                }

                fieldRect.top < safeTop -> {
                    val delta =
                        fieldRect.top - safeTop - dp(8)

                    if (smooth) {
                        rvStudents.smoothScrollBy(0, delta)
                    } else {
                        rvStudents.scrollBy(0, delta)
                    }
                }
            }
        }
    }

    private fun focusScoreField(
        field: TextInputEditText
    ) {
        field.requestFocus()

        val length =
            field.text?.length ?: 0

        if (length > 0) {
            field.setSelection(
                0,
                length
            )
        }

        ensureScoreInputVisible(field)

        field.postDelayed({
            val imm =
                getSystemService(
                    Context.INPUT_METHOD_SERVICE
                ) as InputMethodManager

            imm.showSoftInput(
                field,
                InputMethodManager.SHOW_IMPLICIT
            )

            field.postDelayed({
                ensureScoreInputVisible(
                    field,
                    smooth = false
                )
            }, 260)
        }, 50)
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
        ).enqueue(object : Callback<ApiResponse> {
            override fun onResponse(
                call: Call<ApiResponse>,
                response: Response<ApiResponse>
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

            override fun onFailure(call: Call<ApiResponse>, t: Throwable) {
                setLoading(false)
                toast(ApiErrorParser.networkMessage(t, "ذخیره نمرات"))
            }
        })
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

    private fun format(value: Double): String =
        if (value % 1.0 == 0.0) {
            value.toInt().toString()
        } else {
            String.format(Locale.US, "%.2f", value)
        }

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

    private data class EditableStudent(
        val id: String,
        val name: String,
        val studentCode: String,
        val cardId: String?,
        val status: String,
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

            holder.btnPreviewCard.alpha =
                if (student.cardId.isNullOrBlank()) 0.45f else 1.0f

            holder.btnPreviewCard.setOnClickListener {
                if (student.cardId.isNullOrBlank()) {
                    toast(
                        "ابتدا گزینه «ذخیره پیش‌نویس» را انتخاب کنید تا امکان مشاهده کارنامه فراهم شود"
                    )
                } else {
                    startActivity(
                        Intent(
                            this@GradeEntryActivity,
                            ReportCardViewActivity::class.java
                        ).putExtra(
                            ReportCardViewActivity.EXTRA_REPORT_CARD_ID,
                            student.cardId
                        )
                    )
                }
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
                    "حداکثر نمره: ${format(component.maxScore)}"

                val input =
                    row.findViewById<TextInputEditText>(R.id.etCompactScore)

                input.setSelectAllOnFocus(true)
                input.setOnFocusChangeListener { _, hasFocus ->
                    if (hasFocus) {
                        ensureScoreInputVisible(input)

                        input.postDelayed({
                            ensureScoreInputVisible(
                                input,
                                smooth = false
                            )
                        }, 280)
                    }
                }

                scoreInputs += input

                student.scores[component.id]?.let {
                    input.setText(format(it))
                }

                row.findViewById<View>(R.id.btnCompactZero)
                    .setOnClickListener {
                        input.setText("0")
                        input.clearFocus()
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
                        val raw =
                            UiTextFormatter.normalizeEnglishDigits(
                                s?.toString()?.trim().orEmpty()
                            )
                        val value = raw.toDoubleOrNull()

                        when {
                            raw.isBlank() -> {
                                student.scores[component.id] = null
                                input.error = null
                            }

                            value == null ||
                                    value < 0.0 ||
                                    value > component.maxScore -> {
                                student.scores[component.id] = null
                                input.error =
                                    "۰ تا ${format(component.maxScore)}"
                            }

                            else -> {
                                student.scores[component.id] = value
                                input.error = null
                            }
                        }

                        bindStudentHeader(holder, student, components)
                        updateSummary()
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
                "نمره فعلی: ${format(currentTotal)}"
            holder.txtExpandedTotal.text =
                "جمع نمره‌های واردشده: ${format(currentTotal)} از 100"

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

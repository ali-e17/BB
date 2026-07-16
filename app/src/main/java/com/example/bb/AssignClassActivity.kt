package com.example.bb

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class AssignClassActivity : AppCompatActivity() {

    private enum class ScreenMode { ASSIGNED, ADD_CLASS }

    private var teacherUsername: String = ""
    private var requestInFlight = false
    private var screenMode = ScreenMode.ASSIGNED

    private val allClasses = arrayListOf<ClassModel>()
    private val visibleClasses = arrayListOf<ClassModel>()

    private lateinit var toggleGroup: MaterialButtonToggleGroup
    private lateinit var btnAssignedTab: MaterialButton
    private lateinit var btnAddClassTab: MaterialButton
    private lateinit var layoutSearch: TextInputLayout
    private lateinit var etSearchClass: TextInputEditText
    private lateinit var rvClasses: RecyclerView
    private lateinit var tvEmptyState: TextView
    private lateinit var progressLoading: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_assign_class)

        teacherUsername = intent.getStringExtra(EXTRA_TEACHER_USERNAME).orEmpty()
        if (teacherUsername.isBlank()) {
            Toast.makeText(this, "استاد مشخص نشده است", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        findViewById<ImageView>(R.id.btnAssignBack).setOnClickListener { finish() }

        val teacherName = AppDatabase.getTeacherByUsername(teacherUsername)?.name ?: teacherUsername
        findViewById<TextView>(R.id.tvAssignTitle).text = "کلاس‌های $teacherName"

        toggleGroup = findViewById(R.id.toggleTeacherClasses)
        btnAssignedTab = findViewById(R.id.btnAssignedClassesTab)
        btnAddClassTab = findViewById(R.id.btnAddClassTab)
        layoutSearch = findViewById(R.id.layoutSearch)
        etSearchClass = findViewById(R.id.etSearchClass)
        rvClasses = findViewById(R.id.rvTeacherClasses)
        tvEmptyState = findViewById(R.id.tvTeacherClassesEmpty)
        progressLoading = findViewById(R.id.progressTeacherClasses)

        rvClasses.layoutManager = LinearLayoutManager(this)
        rvClasses.adapter = createClassesAdapter()

        toggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            screenMode = when (checkedId) {
                R.id.btnAddClassTab -> ScreenMode.ADD_CLASS
                else -> ScreenMode.ASSIGNED
            }
            etSearchClass.text?.clear()
            updateModeUi()
            renderCurrentList()
        }

        etSearchClass.doAfterTextChanged { renderCurrentList() }

        toggleGroup.check(R.id.btnAssignedClassesTab)
        fetchClasses()
    }

    private fun fetchClasses() {
        setLoading(true)
        RetrofitClient.instance.getClasses().enqueue(object : Callback<List<ClassModel>> {
            override fun onResponse(
                call: Call<List<ClassModel>>,
                response: Response<List<ClassModel>>
            ) {
                setLoading(false)
                if (!response.isSuccessful) {
                    useLocalClasses("سرور لیست کلاس‌ها را برنگرداند")
                    return
                }

                allClasses.clear()
                allClasses.addAll(response.body().orEmpty())
                AppDatabase.replaceClasses(allClasses)
                renderCurrentList()
            }

            override fun onFailure(call: Call<List<ClassModel>>, t: Throwable) {
                setLoading(false)
                useLocalClasses("اتصال به سرور برقرار نشد؛ کلاس‌های محلی نمایش داده شدند")
            }
        })
    }

    private fun useLocalClasses(message: String) {
        allClasses.clear()
        allClasses.addAll(AppDatabase.getAllClasses())
        renderCurrentList()
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun updateModeUi() {
        layoutSearch.hint = when (screenMode) {
            ScreenMode.ASSIGNED -> "جست‌وجو بین کلاس‌های استاد"
            ScreenMode.ADD_CLASS -> "جست‌وجوی نام، روز یا ساعت کلاس آزاد"
        }
    }

    private fun renderCurrentList() {
        val query = etSearchClass.text?.toString().orEmpty().trim()
        val assignedCount = allClasses.count {
            it.status == ClassStatus.ACTIVE && it.teacherPhone == teacherUsername
        }
        val availableCount = allClasses.count {
            it.status == ClassStatus.ACTIVE && it.teacherPhone.isNullOrBlank()
        }

        btnAssignedTab.text = "کلاس‌های استاد ($assignedCount)"
        btnAddClassTab.text = "افزودن کلاس ($availableCount)"

        visibleClasses.clear()
        val source = when (screenMode) {
            ScreenMode.ASSIGNED -> allClasses.asSequence().filter {
                it.status == ClassStatus.ACTIVE && it.teacherPhone == teacherUsername
            }
            ScreenMode.ADD_CLASS -> allClasses.asSequence().filter {
                it.status == ClassStatus.ACTIVE && it.teacherPhone.isNullOrBlank()
            }
        }

        visibleClasses.addAll(
            source
                .filter {
                    query.isBlank() ||
                        it.className.contains(query, ignoreCase = true) ||
                        it.daysOfWeek.contains(query, ignoreCase = true) ||
                        it.startTime.contains(query) ||
                        it.endTime.contains(query)
                }
                .sortedBy { it.className.lowercase() }
                .toList()
        )

        rvClasses.adapter?.notifyDataSetChanged()
        tvEmptyState.text = when (screenMode) {
            ScreenMode.ASSIGNED -> if (query.isBlank()) {
                "هنوز کلاسی به این استاد تخصیص داده نشده است"
            } else {
                "کلاس تخصیص‌یافته‌ای مطابق جست‌وجوی شما پیدا نشد"
            }
            ScreenMode.ADD_CLASS -> if (query.isBlank()) {
                "کلاس آزاد دیگری وجود ندارد"
            } else {
                "کلاس آزادی مطابق جست‌وجوی شما پیدا نشد"
            }
        }
        tvEmptyState.visibility = if (visibleClasses.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun createClassesAdapter(): RecyclerView.Adapter<ClassActionViewHolder> =
        object : RecyclerView.Adapter<ClassActionViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ClassActionViewHolder {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_class_action, parent, false)
                return ClassActionViewHolder(view)
            }

            override fun onBindViewHolder(holder: ClassActionViewHolder, position: Int) {
                val model = visibleClasses[position]
                holder.tvClassName.text = model.className
                holder.tvClassTime.text = model.classTime

                when (screenMode) {
                    ScreenMode.ASSIGNED -> {
                        holder.btnAction.text = "حذف"
                        holder.btnAction.isEnabled = !requestInFlight
                        holder.btnAction.setOnClickListener { confirmRemoveClass(model) }
                    }
                    ScreenMode.ADD_CLASS -> {
                        holder.btnAction.text = "افزودن"
                        holder.btnAction.isEnabled = !requestInFlight
                        holder.btnAction.setOnClickListener { assignClass(model) }
                    }
                }
            }

            override fun getItemCount(): Int = visibleClasses.size
        }

    private fun assignClass(model: ClassModel) {
        val assignedClasses = allClasses.filter {
            it.status == ClassStatus.ACTIVE && it.teacherPhone == teacherUsername
        }
        val conflictingClass = assignedClasses.firstOrNull { schedulesOverlap(it, model) }
        if (conflictingClass != null) {
            AlertDialog.Builder(this)
                .setTitle("تداخل برنامه استاد")
                .setMessage(
                    "زمان این کلاس با «${conflictingClass.className}» تداخل دارد. " +
                        "ابتدا برنامه یکی از کلاس‌ها را تغییر دهید."
                )
                .setPositiveButton("متوجه شدم", null)
                .show()
            return
        }

        updateTeacherAssignment(model, teacherUsername, "کلاس به استاد تخصیص داده شد")
    }

    private fun confirmRemoveClass(model: ClassModel) {
        AlertDialog.Builder(this)
            .setTitle("حذف تخصیص استاد")
            .setMessage("کلاس «${model.className}» از این استاد گرفته شود؟")
            .setPositiveButton("حذف تخصیص") { _, _ ->
                updateTeacherAssignment(model, null, "کلاس از استاد گرفته شد")
            }
            .setNegativeButton("انصراف", null)
            .show()
    }

    private fun updateTeacherAssignment(
        model: ClassModel,
        teacherPhone: String?,
        successMessage: String
    ) {
        if (requestInFlight) return
        requestInFlight = true
        renderCurrentList()

        RetrofitClient.instance.assignTeacherToClass(
            AssignTeacherRequest(classId = model.id, teacherPhone = teacherPhone)
        ).enqueue(object : Callback<ApiResponse> {
            override fun onResponse(call: Call<ApiResponse>, response: Response<ApiResponse>) {
                requestInFlight = false
                val result = response.body()
                if (response.isSuccessful && result?.status == "success") {
                    model.teacherPhone = teacherPhone
                    AppDatabase.upsertClass(model)
                    Toast.makeText(
                        this@AssignClassActivity,
                        result.message.ifBlank { successMessage },
                        Toast.LENGTH_SHORT
                    ).show()
                    fetchClasses()
                } else {
                    renderCurrentList()
                    Toast.makeText(
                        this@AssignClassActivity,
                        result?.message?.takeIf { it.isNotBlank() }
                            ?: "تغییر تخصیص استاد در سرور ثبت نشد",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }

            override fun onFailure(call: Call<ApiResponse>, t: Throwable) {
                requestInFlight = false
                renderCurrentList()
                Toast.makeText(
                    this@AssignClassActivity,
                    "ارتباط با سرور برای تغییر تخصیص استاد برقرار نشد",
                    Toast.LENGTH_LONG
                ).show()
            }
        })
    }

    private fun schedulesOverlap(first: ClassModel, second: ClassModel): Boolean {
        val firstDays = parseDays(first.daysOfWeek)
        val secondDays = parseDays(second.daysOfWeek)
        if (firstDays.intersect(secondDays).isEmpty()) return false

        val firstStart = ClassTimeUtils.parse(first.startTime)?.minutesFromMidnight ?: return false
        val firstEnd = ClassTimeUtils.parse(first.endTime)?.minutesFromMidnight ?: return false
        val secondStart = ClassTimeUtils.parse(second.startTime)?.minutesFromMidnight ?: return false
        val secondEnd = ClassTimeUtils.parse(second.endTime)?.minutesFromMidnight ?: return false

        return firstStart < secondEnd && secondStart < firstEnd
    }

    private fun parseDays(value: String): Set<String> = value
        .split("،", ",")
        .map {
            it.replace("\u200C", "")
                .replace(" ", "")
                .trim()
        }
        .filter { it.isNotBlank() }
        .toSet()

    private fun setLoading(loading: Boolean) {
        progressLoading.visibility = if (loading) View.VISIBLE else View.GONE
        rvClasses.visibility = if (loading) View.INVISIBLE else View.VISIBLE
    }

    class ClassActionViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvClassName: TextView = view.findViewById(R.id.tvClassName)
        val tvClassTime: TextView = view.findViewById(R.id.tvClassTime)
        val btnAction: MaterialButton = view.findViewById(R.id.btnAction)
    }

    companion object {
        const val EXTRA_TEACHER_USERNAME = "TEACHER_USERNAME"
    }
}

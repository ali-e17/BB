package ir.bayanebartar.app

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class TrashBinActivity : BaseActivity() {
    private lateinit var recycler: RecyclerView
    private lateinit var empty: TextView
    private lateinit var progress: View
    private var entity = "student"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_trash_bin)
        findViewById<ImageView>(R.id.btnTrashBack).setOnClickListener { finish() }
        recycler = findViewById(R.id.rvTrash)
        empty = findViewById(R.id.txtTrashEmpty)
        progress = findViewById(R.id.progressTrash)
        recycler.layoutManager = LinearLayoutManager(this)
        val filter = findViewById<MaterialAutoCompleteTextView>(R.id.dropdownTrashType)
        val labels = listOf("دانش‌آموزان", "اساتید", "کلاس‌ها")
        val values = listOf("student", "teacher", "class")
        filter.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, labels))
        filter.setText(labels.first(), false)
        filter.setOnItemClickListener { _, _, position, _ -> entity = values[position]; load() }
        load()
    }

    private fun load() {
        progress.visibility = View.VISIBLE
        RetrofitClient.instance.getTrash(entity).enqueue(object : Callback<List<TrashItem>> {
            override fun onResponse(call: Call<List<TrashItem>>, response: Response<List<TrashItem>>) {
                progress.visibility = View.GONE
                if (!response.isSuccessful) {
                    recycler.adapter = TrashAdapter(emptyList(), ::restore, ::permanentDelete)
                    empty.visibility = View.VISIBLE
                    AppToast.makeText(
                        this@TrashBinActivity,
                        ApiErrorParser.userMessage(response, "دریافت اطلاعات سطل زباله کامل نشد"),
                        Toast.LENGTH_LONG
                    ).show()
                    return
                }
                val list = response.body().orEmpty()
                recycler.adapter = TrashAdapter(list, ::restore, ::permanentDelete)
                empty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
            }
            override fun onFailure(call: Call<List<TrashItem>>, t: Throwable) {
                progress.visibility = View.GONE; empty.visibility = View.VISIBLE
                AppToast.makeText(
                    this@TrashBinActivity,
                    ApiErrorParser.networkMessage(t, "دریافت سطل زباله"),
                    Toast.LENGTH_LONG
                ).show()
            }
        })
    }

    private fun restore(item: TrashItem) {
        MaterialAlertDialogBuilder(this).setTitle("بازیابی ${item.name}")
            .setMessage("آیا این مورد به فهرست اصلی بازگردانده شود؟")
            .setNegativeButton("انصراف", null).setPositiveButton("بازیابی") { _, _ ->
                RetrofitClient.instance.restoreEntity(TrashRequest(entity, item.id)).enqueue(simpleCallback("بازیابی شد", "بازیابی مورد حذف‌شده"))
            }.show()
    }

    private fun permanentDelete(item: TrashItem) {
        val expected = item.code.ifBlank { item.id }
        val input = android.widget.EditText(this).apply { hint = expected; textDirection = View.TEXT_DIRECTION_LTR }
        MaterialAlertDialogBuilder(this).setTitle("حذف قطعی")
            .setMessage("فقط موارد بدون سابقه قابل حذف قطعی‌اند. برای تأیید، کد زیر را وارد کنید:\n$expected")
            .setView(input).setNegativeButton("انصراف", null).setPositiveButton("حذف قطعی") { _, _ ->
                RetrofitClient.instance.permanentDelete(PermanentDeleteRequest(entity, item.id, input.text.toString().trim()))
                    .enqueue(simpleCallback("حذف قطعی انجام شد", "حذف قطعی مورد"))
            }.show()
    }

    private fun simpleCallback(success: String, action: String) = object : Callback<ApiResponse> {
        override fun onResponse(call: Call<ApiResponse>, response: Response<ApiResponse>) {
            val body = response.body()
            AppToast.makeText(
                this@TrashBinActivity,
                if (response.isSuccessful && body?.status == "success") {
                    body.message.ifBlank { success }
                } else {
                    body?.message?.takeIf { it.isNotBlank() }
                        ?: ApiErrorParser.userMessage(response, "$action کامل نشد")
                },
                Toast.LENGTH_LONG
            ).show()
            if (response.isSuccessful && body?.status == "success") load()
        }
        override fun onFailure(call: Call<ApiResponse>, t: Throwable) = AppToast.makeText(
            this@TrashBinActivity,
            ApiErrorParser.networkMessage(t, action),
            Toast.LENGTH_LONG
        ).show()
    }

    private class TrashAdapter(
        private val items: List<TrashItem>, private val restore: (TrashItem) -> Unit, private val delete: (TrashItem) -> Unit
    ) : RecyclerView.Adapter<TrashAdapter.Holder>() {
        class Holder(v: View) : RecyclerView.ViewHolder(v) {
            val name: TextView = v.findViewById(R.id.txtTrashName)
            val meta: TextView = v.findViewById(R.id.txtTrashMeta)
            val restore: View = v.findViewById(R.id.btnTrashRestore)
            val delete: View = v.findViewById(R.id.btnTrashPermanentDelete)
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_trash, parent, false))
        override fun onBindViewHolder(h: Holder, position: Int) {
            val item = items[position]
            h.name.text = item.name

            h.meta.text =
                listOf(
                    item.code
                        .takeIf { it.isNotBlank() }
                        ?.let { "کد: $it" },

                    item.deletedAt
                        .takeIf { it.isNotBlank() }
                        ?.let {
                            "حذف: ${PersianDateUtils.formatDateTime(it)}"
                        },

                    item.reason
                        .takeIf { it.isNotBlank() }
                )
                    .filterNotNull()
                    .joinToString("  •  ")

            h.restore.setOnClickListener { restore(item) }
            h.delete.setOnClickListener { delete(item) }
        }
        override fun getItemCount() = items.size
    }
}

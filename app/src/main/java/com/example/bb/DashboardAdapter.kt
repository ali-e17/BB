package com.example.bb
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView

data class DashboardItem(val title: String, val desc: String, val iconResId: Int)

class DashboardAdapter(
    private val items: List<DashboardItem>,
    private val onItemClick: (DashboardItem) -> Unit
) : RecyclerView.Adapter<DashboardAdapter.DashboardViewHolder>() {

    class DashboardViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val islandCard: MaterialCardView = view.findViewById(R.id.islandCard)
        val imgIcon: ImageView = view.findViewById(R.id.imgIcon)
        val txtTitle: TextView = view.findViewById(R.id.txtTitle)
        val txtDesc: TextView = view.findViewById(R.id.txtDesc)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DashboardViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_dashboard_card, parent, false)
        return DashboardViewHolder(view)
    }

    override fun onBindViewHolder(holder: DashboardViewHolder, position: Int) {
        val item = items[position]
        holder.txtTitle.text = item.title
        holder.txtDesc.text = item.desc
        holder.imgIcon.setImageResource(item.iconResId)

        // جادوی جزیره‌ای: یکی در میون چپ و راست بشن
        val params = holder.islandCard.layoutParams as ConstraintLayout.LayoutParams
        if (position % 2 == 0) {
            // چسباندن به چپ
            params.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
            params.endToEnd = ConstraintLayout.LayoutParams.UNSET
            params.marginStart = 16 // فاصله از لبه گوشی
        } else {
            // چسباندن به راست
            params.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
            params.startToStart = ConstraintLayout.LayoutParams.UNSET
            params.marginEnd = 16
        }
        holder.islandCard.layoutParams = params

        holder.itemView.setOnClickListener { onItemClick(item) }
    }

    override fun getItemCount() = items.size
}
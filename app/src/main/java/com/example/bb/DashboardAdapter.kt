package com.example.bb

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

// کلاس داده برای نگهداری اطلاعات هر کارت
data class DashboardItem(val title: String, val iconResId: Int)

class DashboardAdapter(
    private val items: List<DashboardItem>,
    private val onItemClick: (DashboardItem) -> Unit
) : RecyclerView.Adapter<DashboardAdapter.DashboardViewHolder>() {

    class DashboardViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imgIcon: ImageView = view.findViewById(R.id.imgIcon)
        val txtTitle: TextView = view.findViewById(R.id.txtTitle)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DashboardViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_dashboard_card, parent, false)
        return DashboardViewHolder(view)
    }

    override fun onBindViewHolder(holder: DashboardViewHolder, position: Int) {
        val item = items[position]
        holder.txtTitle.text = item.title
        holder.imgIcon.setImageResource(item.iconResId)

        // هندل کردن کلیک روی هر کارت
        holder.itemView.setOnClickListener {
            onItemClick(item)
        }
    }

    override fun getItemCount() = items.size
}
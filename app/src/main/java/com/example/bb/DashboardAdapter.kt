package com.example.bb

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView

data class DashboardItem(val title: String, val desc: String, val iconResId: Int)

class DashboardAdapter(
    private val items: List<DashboardItem>,
    private val onItemClick: (DashboardItem) -> Unit
) : RecyclerView.Adapter<DashboardAdapter.DashboardViewHolder>() {

    class DashboardViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val card: MaterialCardView = view.findViewById(R.id.islandCard)
        val imgIcon: ImageView = view.findViewById(R.id.imgIcon)
        val txtTitle: TextView = view.findViewById(R.id.txtTitle)
        val txtDesc: TextView = view.findViewById(R.id.txtDesc)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DashboardViewHolder {
        return DashboardViewHolder(
            LayoutInflater.from(parent.context).inflate(R.layout.item_dashboard_card, parent, false)
        )
    }

    override fun onBindViewHolder(holder: DashboardViewHolder, position: Int) {
        val item = items[position]
        holder.txtTitle.text = item.title
        holder.txtDesc.text = item.desc
        holder.imgIcon.setImageResource(item.iconResId)
        holder.card.setOnClickListener { onItemClick(item) }
    }

    override fun getItemCount(): Int = items.size
}

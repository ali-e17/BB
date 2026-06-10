package com.example.bb

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class AnnouncementAdapter(
    private val announcements: List<Announcement>,
    private val onItemClick: (Announcement) -> Unit
) : RecyclerView.Adapter<AnnouncementAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val txtSender: TextView = view.findViewById(R.id.txtMsgSender)
        val txtTitle: TextView = view.findViewById(R.id.txtMsgTitle)
        val txtSnippet: TextView = view.findViewById(R.id.txtMsgSnippet)
        val txtDate: TextView = view.findViewById(R.id.txtMsgDate)
        val txtAvatar: TextView = view.findViewById(R.id.txtAvatar)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_announcement, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = announcements[position]
        holder.txtSender.text = item.senderName
        holder.txtTitle.text = item.title
        holder.txtSnippet.text = item.body
        holder.txtDate.text = item.date
        holder.txtAvatar.text = item.senderName.firstOrNull()?.toString() ?: "B"

        holder.itemView.setOnClickListener { onItemClick(item) }
    }

    override fun getItemCount() = announcements.size
}
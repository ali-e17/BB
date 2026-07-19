package com.example.bb

import android.graphics.Typeface
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView

class AnnouncementAdapter(
    private val onItemClick: (Announcement) -> Unit
) : RecyclerView.Adapter<AnnouncementAdapter.ViewHolder>() {

    private val announcements = mutableListOf<Announcement>()

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val card: MaterialCardView = view.findViewById(R.id.cardAnnouncement)
        val txtSender: TextView = view.findViewById(R.id.txtMsgSender)
        val txtTitle: TextView = view.findViewById(R.id.txtMsgTitle)
        val txtDate: TextView = view.findViewById(R.id.txtMsgDate)
        val txtReadState: TextView = view.findViewById(R.id.txtReadState)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_announcement, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = announcements[position]
        val isRead = item.isRead

        holder.txtSender.text = item.senderName
        holder.txtDate.text = item.createdAt
        holder.txtTitle.text = item.title
        applyDynamicAlignment(holder.txtTitle, item.title)

        holder.txtSender.setTypeface(null, if (isRead) Typeface.NORMAL else Typeface.BOLD)
        holder.txtTitle.setTypeface(null, if (isRead) Typeface.NORMAL else Typeface.BOLD)
        holder.txtReadState.text = if (isRead) "✓" else "●"
        holder.txtReadState.contentDescription = if (isRead) "خوانده‌شده" else "خوانده‌نشده"
        holder.txtReadState.setTextColor(
            ContextCompat.getColor(
                holder.itemView.context,
                if (isRead) R.color.sub_text else R.color.title_blue
            )
        )

        holder.card.strokeWidth = if (isRead) 1 else 2
        holder.card.strokeColor = ContextCompat.getColor(
            holder.itemView.context,
            if (isRead) R.color.sub_text else R.color.title_blue
        )
        val density = holder.itemView.resources.displayMetrics.density
        holder.card.cardElevation = density * if (isRead) 0.5f else 2f

        holder.itemView.setOnClickListener { onItemClick(item) }
    }

    override fun getItemCount(): Int = announcements.size

    fun updateData(items: List<Announcement>) {
        announcements.clear()
        announcements.addAll(items)
        notifyDataSetChanged()
    }

    fun markRead(announcementId: String) {
        val index = announcements.indexOfFirst { it.id == announcementId }
        if (index < 0 || announcements[index].isRead) return
        announcements[index] = announcements[index].copy(isRead = true)
        notifyItemChanged(index)
    }

    private fun applyDynamicAlignment(view: TextView, text: CharSequence?) {
        val rtl = isRtlText(text)
        view.textDirection = if (rtl) View.TEXT_DIRECTION_RTL else View.TEXT_DIRECTION_LTR
        view.gravity = (if (rtl) Gravity.RIGHT else Gravity.LEFT) or Gravity.CENTER_VERTICAL
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
}

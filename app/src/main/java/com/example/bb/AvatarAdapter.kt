package com.example.bb

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView

class AvatarAdapter(
    private val avatarResIds: List<Int>,
    private val onAvatarClick: (Int) -> Unit
) : RecyclerView.Adapter<AvatarAdapter.AvatarViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AvatarViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_avatar, parent, false)
        return AvatarViewHolder(view)
    }

    override fun onBindViewHolder(holder: AvatarViewHolder, position: Int) {
        val resId = avatarResIds[position]
        holder.ivAvatar.setImageResource(resId)

        // رفع مشکل اول: کلیک را مستقیماً روی خود ImageView قرار دادیم
        holder.ivAvatar.setOnClickListener {
            onAvatarClick(resId)
        }
    }

    override fun getItemCount(): Int = avatarResIds.size

    class AvatarViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ivAvatar: ImageView = itemView.findViewById(R.id.ivItemAvatar)
    }
}
package com.example.bb

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView

class DictionaryAdapter(
    private var items: List<DictionaryEntry>
) : RecyclerView.Adapter<DictionaryAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvWord: TextView = view.findViewById(R.id.tvWord)
        val tvPartOfSpeech: TextView = view.findViewById(R.id.tvPartOfSpeech)
        val cardPartOfSpeech: MaterialCardView = view.findViewById(R.id.cardPartOfSpeech)
        val tvDefinition: TextView = view.findViewById(R.id.tvDefinition)

        // بخش مثال‌ها
        val layoutExample: LinearLayout = view.findViewById(R.id.layoutExample)
        val tvExample: TextView = view.findViewById(R.id.tvExample)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_dictionary_entry, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val entry = items[position]

        holder.tvWord.text = entry.word
        holder.tvDefinition.text = entry.definition

        // مدیریت نقش کلمه (نمایش یا مخفی کردن)
        if (!entry.partOfSpeech.isNullOrEmpty()) {
            holder.cardPartOfSpeech.visibility = View.VISIBLE
            holder.tvPartOfSpeech.text = entry.partOfSpeech
        } else {
            holder.cardPartOfSpeech.visibility = View.GONE
        }

        // مدیریت نمایش مثال‌ها
        if (!entry.example.isNullOrEmpty()) {
            holder.layoutExample.visibility = View.VISIBLE
            holder.tvExample.text = "\"${entry.example}\"" // گذاشتن مثال داخل کوتیشن
        } else {
            holder.layoutExample.visibility = View.GONE
        }
    }

    override fun getItemCount() = items.size

    fun updateData(newItems: List<DictionaryEntry>) {
        items = newItems
        notifyDataSetChanged()
    }
}
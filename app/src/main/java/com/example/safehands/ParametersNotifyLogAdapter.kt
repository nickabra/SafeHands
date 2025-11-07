package com.example.safehands

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Locale

class ParametersNotifyLogAdapter(
    private val notifyList: List<ParameterNotification>
) : RecyclerView.Adapter<ParametersNotifyLogAdapter.NotifyViewHolder>() {

    class NotifyViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvTitle: TextView = itemView.findViewById(R.id.tvNotifyTitle)
        val tvBody: TextView = itemView.findViewById(R.id.tvNotifyBody)
        val tvTimestamp: TextView = itemView.findViewById(R.id.tvNotifyTimestamp)
        val cardView: CardView = itemView.findViewById(R.id.cardView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NotifyViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_notify_log, parent, false)
        return NotifyViewHolder(view)
    }

    override fun onBindViewHolder(holder: NotifyViewHolder, position: Int) {
        val item = notifyList[position]
        holder.tvTitle.text = item.title
        holder.tvBody.text = item.body
        holder.tvTimestamp.text = formatTimestamp(item.timestamp)

        // Applica l'effetto glass senza blur
        applyGlassEffect(holder.cardView)

        // Gestisci i margini in base alla posizione
        setItemMargins(holder.cardView, position)
    }

    override fun getItemCount(): Int = notifyList.size

    private fun applyGlassEffect(cardView: CardView) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            cardView.setRenderEffect(null)
        }
        cardView.radius = 20f
        cardView.cardElevation = 4f
        cardView.setCardBackgroundColor(0x60FFFFFF)
    }

    private fun formatTimestamp(timestamp: String): String {
        return try {
            val inputFormat = SimpleDateFormat("EEE MMM dd HH:mm:ss z yyyy", Locale.ENGLISH)
            val date = inputFormat.parse(timestamp)
            val outputFormat = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
            outputFormat.format(date)
        } catch (e: Exception) {
            "Data non disponibile"
        }
    }

    private fun setItemMargins(cardView: CardView, position: Int) {
        cardView.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            if (position == 0) {
                // Primo elemento: margine top 30dp
                topMargin = 30.dpToPx(cardView.context)
                bottomMargin = 8.dpToPx(cardView.context)
            } else {
                // Altri elementi: margine top 0dp, bottom 15dp
                topMargin = 0
                bottomMargin = 15.dpToPx(cardView.context)
            }
            // Margini laterali costanti
            marginStart = 16.dpToPx(cardView.context)
            marginEnd = 16.dpToPx(cardView.context)
        }
    }

    // Extension function per convertire dp in px
    private fun Int.dpToPx(context: android.content.Context): Int {
        return (this * context.resources.displayMetrics.density).toInt()
    }
}
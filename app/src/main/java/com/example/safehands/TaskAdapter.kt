package com.example.safehands

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView

class TaskAdapter(
    private val taskList: MutableList<Task>,
    private val isCaregiver: Boolean = false, // true = caregiver, false = parent
    private val onTaskClick: ((Task) -> Unit)? = null, // callback click normale
    private val onLongClick: ((Task, String) -> Unit)? = null // callback long click
) : RecyclerView.Adapter<TaskAdapter.TaskViewHolder>() {

    class TaskViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvName: TextView = itemView.findViewById(R.id.tvTaskName)
        val tvTime: TextView = itemView.findViewById(R.id.tvTaskTime)
        val card: CardView = itemView as CardView
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_task, parent, false)
        return TaskViewHolder(view)
    }

    override fun getItemCount() = taskList.size

    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
        val task = taskList[position]
        holder.tvName.text = task.name
        holder.tvTime.text = task.time

        // Imposta colore in base allo stato done
        holder.card.setCardBackgroundColor(
            if (task.done) 0xFFA8E6CF.toInt() else 0xFFFFFFFF.toInt()
        )

        if (isCaregiver) {
            // Click normale: cambia stato done
            holder.card.setOnClickListener {
                onTaskClick?.invoke(task)
            }

            // Disabilita long click
            holder.card.setOnLongClickListener(null)
        } else {
            // Parent: disabilita click normale
            holder.card.setOnClickListener(null)

            // Long click mostra popup
            holder.card.setOnLongClickListener {
                val popup = PopupMenu(holder.itemView.context, holder.itemView)
                popup.menu.add("Modifica")
                popup.menu.add("Elimina")
                popup.setOnMenuItemClickListener { item ->
                    onLongClick?.invoke(task, item.title.toString())
                    true
                }
                popup.show()
                true
            }
        }
    }
}
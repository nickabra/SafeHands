package com.example.safehands

import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.*
import com.google.firebase.functions.FirebaseFunctions

class TodoListFragment(private val parentUid: String) : Fragment() {

    private val taskList = mutableListOf<Task>()
    private lateinit var adapter: TaskAdapter
    private lateinit var firestore: FirebaseFirestore
    private lateinit var functions: FirebaseFunctions
    private var listenerRegistration: ListenerRegistration? = null
    private var caregiverId: String? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_todo_list, container, false)

        firestore = FirebaseFirestore.getInstance()
        functions = FirebaseFunctions.getInstance("europe-west1")

        caregiverId = requireContext().getSharedPreferences("safehands", Context.MODE_PRIVATE)
            .getString("caregiverId", null)

        val recyclerView = view.findViewById<RecyclerView>(R.id.todoRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        adapter = TaskAdapter(
            taskList,
            isCaregiver = true,
            onTaskClick = { task ->
                if (task.done) return@TaskAdapter
                AlertDialog.Builder(requireContext())
                    .setTitle("Conferma")
                    .setMessage("Sei sicuro di voler segnare il task \"${task.name}\" come completato?")
                    .setPositiveButton("Sì") { _, _ ->
                        task.done = !task.done
                        updateTaskStatusByCaregiver(task, task.done)
                    }
                    .setNegativeButton("No", null)
                    .show()
            }
        )

        recyclerView.adapter = adapter
        listenToTasks()

        return view
    }

    private fun updateTaskStatusByCaregiver(task: Task, newStatus: Boolean) {
        val prefs = requireContext().getSharedPreferences("safehands", Context.MODE_PRIVATE)
        val caregiverId = prefs.getString("caregiverId", null)
        val parentUid = prefs.getString("parentUid", null)

        if (caregiverId == null || parentUid == null) return

        val data = hashMapOf(
            "parentUid" to parentUid,
            "caregiverId" to caregiverId,
            "taskId" to task.id,
            "status" to newStatus
        )

        functions.getHttpsCallable("updateTaskStatus")
            .call(data)
            .addOnSuccessListener {
                task.done = newStatus
                adapter.notifyItemChanged(taskList.indexOf(task))
                Toast.makeText(requireContext(), "Task aggiornato", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Errore aggiornamento task: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun listenToTasks() {
        listenerRegistration = firestore.collection("todoLists")
            .document(parentUid)
            .collection("tasks")
            .orderBy("time")
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                if (snapshot != null) {
                    for (change in snapshot.documentChanges) {
                        val doc = change.document
                        val task = Task(
                            id = doc.id,
                            name = doc.getString("title") ?: "",
                            done = doc.getBoolean("status") ?: false,
                            time = doc.getString("time") ?: ""
                        )

                        when (change.type) {
                            DocumentChange.Type.ADDED -> {
                                taskList.add(task)
                                adapter.notifyItemInserted(taskList.size - 1)
                            }
                            DocumentChange.Type.MODIFIED -> {
                                val index = taskList.indexOfFirst { it.id == task.id }
                                if (index != -1) {
                                    taskList[index] = task
                                    adapter.notifyItemChanged(index)
                                }
                            }
                            DocumentChange.Type.REMOVED -> {
                                val index = taskList.indexOfFirst { it.id == task.id }
                                if (index != -1) {
                                    taskList.removeAt(index)
                                    adapter.notifyItemRemoved(index)
                                }
                            }
                        }
                    }
                }
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        listenerRegistration?.remove()
    }
}
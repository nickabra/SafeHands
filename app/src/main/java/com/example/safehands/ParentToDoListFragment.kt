package com.example.safehands

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.*
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

class ParentToDoListFragment : Fragment() {

    private val taskList = mutableListOf<Task>()
    private lateinit var adapter: TaskAdapter
    private lateinit var db: FirebaseFirestore
    private lateinit var parentUid: String

    // Drawer + navigation
    private var drawerLayout: DrawerLayout? = null
    private var navView: NavigationView? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // usa il layout con DrawerLayout (assicurati che sia quello corretto)
        val view = inflater.inflate(R.layout.fragment_parent_todo_list, container, false)

        db = FirebaseFirestore.getInstance()
        parentUid = FirebaseAuth.getInstance().currentUser?.uid ?: ""

        // RecyclerView + adapter (parent = not caregiver)
        val recyclerView = view.findViewById<RecyclerView>(R.id.parentTodoRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        adapter = TaskAdapter(
            taskList,
            isCaregiver = false, // parent view
            onLongClick = { task, action ->
                when (action) {
                    "Modifica" -> showEditTaskDialog(task)
                    "Elimina" -> deleteTask(task)
                }
            }
        )
        recyclerView.adapter = adapter

        // Drawer + menu
        drawerLayout = view.findViewById(R.id.drawerLayout)
        navView = view.findViewById(R.id.navigationView)
        val btnMenu = view.findViewById<View>(R.id.btnMenu)
        btnMenu?.setOnClickListener {
            drawerLayout?.openDrawer(GravityCompat.START)
        }

        navView?.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_update_phone -> {
                    drawerLayout?.closeDrawer(GravityCompat.START)
                    showUpdatePhoneDialog()
                    true
                }
                R.id.nav_notify_log -> {
                    drawerLayout?.closeDrawer(GravityCompat.START)
                    requireActivity().supportFragmentManager.beginTransaction()
                        .replace(R.id.fragmentContainerParent, ParametersNotifyLogFragment())
                        .addToBackStack(null) // così torni indietro con il tasto back
                        .commit()
                    true
                }
                R.id.nav_logout -> {
                    drawerLayout?.closeDrawer(GravityCompat.START)
                    FirebaseAuth.getInstance().signOut()
                    Toast.makeText(requireContext(), "Logout effettuato", Toast.LENGTH_SHORT).show()
                    // Torna a RoleSelection (o Login)
                    val intent = Intent(requireContext(), MAIN_RoleSelectionActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    requireActivity().finish()
                    true
                }
                else -> false
            }
        }
        // btn Aggiungi task
        val btnAdd = view.findViewById<Button>(R.id.btnAddTask)
        btnAdd?.setOnClickListener { showAddTaskDialog() }

        // Carica tasks
        loadTasks()

        return view
    }

    /* Firestore: carica tasks (ordinati lato server) */
    private fun loadTasks() {
        if (parentUid.isEmpty()) return

        db.collection("todoLists")
            .document(parentUid)
            .collection("tasks")
            .orderBy("time")
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Toast.makeText(requireContext(), "Errore aggiornamento tasks: ${error.message}", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                if (snapshots != null) {
                    taskList.clear()
                    for (doc in snapshots) {
                        val title = doc.getString("title") ?: ""
                        val status = doc.getBoolean("status") ?: false
                        val time = doc.getString("time") ?: ""
                        val id = doc.id
                        taskList.add(Task(id = id, name = title, time = time, done = status))
                    }
                    adapter.notifyDataSetChanged()
                }
            }
    }

    /* Aggiungi task (dialog) */
    private fun showAddTaskDialog() {
        val layout = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }
        val etTitle = EditText(requireContext()).apply { hint = "Titolo task" }
        val etTime = EditText(requireContext()).apply { hint = "Orario (es: 08:00)" }
        layout.addView(etTitle)
        layout.addView(etTime)

        AlertDialog.Builder(requireContext())
            .setTitle("Nuovo Task")
            .setView(layout)
            .setPositiveButton("Salva") { _, _ ->
                val title = etTitle.text.toString().trim()
                val time = etTime.text.toString().trim()
                if (title.isNotEmpty() && time.isNotEmpty()) {
                    addTaskToFirestore(title, time)
                } else {
                    Toast.makeText(requireContext(), "Compila tutti i campi", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun addTaskToFirestore(title: String, time: String) {
        if (parentUid.isEmpty()) return

        val progressDialog = AlertDialog.Builder(requireContext()).apply {
            setView(R.layout.dialog_loading) // Crea un layout custom
            setCancelable(false)
        }.create()

        val newTask = hashMapOf("title" to title, "time" to time, "status" to false)

        db.collection("todoLists")
            .document(parentUid)
            .collection("tasks")
            .add(newTask)
            .addOnSuccessListener { docRef ->
                progressDialog.dismiss()
                Toast.makeText(requireContext(), "Task aggiunto", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                progressDialog.dismiss()
                Toast.makeText(requireContext(), "Errore aggiunta task: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    /* Modifica task (dialog) */
    private fun showEditTaskDialog(task: Task) {
        val layout = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }
        val etTitle = EditText(requireContext()).apply { setText(task.name) }
        val etTime = EditText(requireContext()).apply { setText(task.time) }
        layout.addView(etTitle)
        layout.addView(etTime)

        AlertDialog.Builder(requireContext())
            .setTitle("Modifica Task")
            .setView(layout)
            .setPositiveButton("Salva") { _, _ ->
                val newTitle = etTitle.text.toString().trim()
                val newTime = etTime.text.toString().trim()
                if (newTitle.isNotEmpty() && newTime.isNotEmpty()) updateTask(task, newTitle, newTime)
                else Toast.makeText(requireContext(), "Compila tutti i campi", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun updateTask(task: Task, title: String, time: String) {
        db.collection("todoLists")
            .document(parentUid)
            .collection("tasks")
            .document(task.id)
            .update(mapOf("title" to title, "time" to time))
            .addOnSuccessListener {
                task.name = title
                task.time = time
                adapter.notifyItemChanged(taskList.indexOf(task))
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Errore aggiornamento: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    /* Elimina task */
    private fun deleteTask(task: Task) {
        db.collection("todoLists")
            .document(parentUid)
            .collection("tasks")
            .document(task.id)
            .delete()
            .addOnSuccessListener {
                val index = taskList.indexOf(task)
                if (index >= 0) {
                    taskList.removeAt(index)
                    adapter.notifyItemRemoved(index)
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Errore eliminazione: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    /* Update phone dialog (prefill se possibile) */
    private fun showUpdatePhoneDialog() {
        val user = FirebaseAuth.getInstance().currentUser ?: run {
            Toast.makeText(requireContext(), "Utente non autenticato", Toast.LENGTH_SHORT).show()
            return
        }
        val db = FirebaseFirestore.getInstance()
        val userDocId = (user.email ?: user.uid).replace(".", "_") // same format used at registration

        // leggi valore corrente (se presente) e poi mostra dialog
        db.collection("users").document(userDocId).get()
            .addOnSuccessListener { snapshot ->
                val currentPhone = snapshot.getString("phone") ?: ""
                showPhoneInputDialog(userDocId, currentPhone)
            }
            .addOnFailureListener {
                // se errore nel leggere, mostriamo comunque dialog vuoto
                showPhoneInputDialog(userDocId, "")
            }
    }

    private fun showPhoneInputDialog(userDocId: String, prefill: String) {
        val editText = EditText(requireContext())
        editText.inputType = InputType.TYPE_CLASS_PHONE
        editText.setText(prefill)
        editText.hint = "Nuovo numero di telefono"

        AlertDialog.Builder(requireContext())
            .setTitle("Aggiorna numero")
            .setView(editText)
            .setPositiveButton("Aggiorna") { _, _ ->
                val newPhone = editText.text.toString().trim()
                if (newPhone.isEmpty()) {
                    Toast.makeText(requireContext(), "Inserisci un numero valido", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val db = FirebaseFirestore.getInstance()
                db.collection("users")
                    .document(userDocId)
                    .update("phone", newPhone)
                    .addOnSuccessListener {
                        Toast.makeText(requireContext(), "Numero aggiornato", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener { e ->
                        // Se il documento non esiste (per qualche ragione), prova a crearlo
                        db.collection("users")
                            .document(userDocId)
                            .set(mapOf("phone" to newPhone, "createdAt" to FieldValue.serverTimestamp()))
                            .addOnSuccessListener {
                                Toast.makeText(requireContext(), "Numero aggiornato", Toast.LENGTH_SHORT).show()
                            }
                            .addOnFailureListener { e2 ->
                                Toast.makeText(requireContext(), "Errore aggiornamento: ${e2.message}", Toast.LENGTH_SHORT).show()
                            }
                    }
            }
            .setNegativeButton("Annulla", null)
            .show()
    }
}

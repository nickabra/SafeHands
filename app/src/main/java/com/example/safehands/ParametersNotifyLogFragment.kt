package com.example.safehands

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class ParametersNotifyLogFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ParametersNotifyLogAdapter
    private val notifyList = mutableListOf<ParameterNotification>()
    private var listenerRegistration: ListenerRegistration? = null
    private lateinit var db: FirebaseFirestore
    private lateinit var userDocId: String

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_parameters_notify_log, container, false)

        recyclerView = view.findViewById(R.id.parametersNotifyRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.setPadding(0, 0, 0, 32.dpToPx(requireContext()))

        adapter = ParametersNotifyLogAdapter(notifyList)
        recyclerView.adapter = adapter

        db = FirebaseFirestore.getInstance()
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            Toast.makeText(requireContext(), "Utente non autenticato", Toast.LENGTH_SHORT).show()
            return view
        }
        userDocId = currentUser.email?.replace(".", "_") ?: currentUser.uid
        Log.d("ParametersNotifyLogFragment", "UserDocId: $userDocId")

        // Listener realtime
        listenerRegistration = db.collection("users")
            .document(userDocId)
            .collection("parameters_notify_log")
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Toast.makeText(requireContext(), "Errore caricamento log", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    notifyList.clear()
                    for (doc in snapshot.documents) {
                        val item = ParameterNotification(
                            id = doc.id,
                            title = doc.getString("title") ?: "",
                            body = doc.getString("body") ?: "",
                            parameter = doc.getString("parameter") ?: "",
                            time = doc.getString("time") ?: "",
                            details = doc.getString("details") ?: "",
                            timestamp = doc.getTimestamp("timestamp")?.toDate()?.toString() ?: ""
                        )
                        notifyList.add(item)
                    }
                    adapter.notifyDataSetChanged()
                }
            }

        return view
    }

    override fun onDestroyView() {
        super.onDestroyView()
        listenerRegistration?.remove()
    }
}

fun Int.dpToPx(context: android.content.Context): Int {
    return (this * context.resources.displayMetrics.density).toInt()
}

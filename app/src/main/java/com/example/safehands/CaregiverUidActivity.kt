package com.example.safehands

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import java.util.*

class CaregiverUidActivity : AppCompatActivity() {
    private lateinit var functions: FirebaseFunctions
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_caregiver_email)

        functions = FirebaseFunctions.getInstance("europe-west1")
        db = FirebaseFirestore.getInstance()

        // Controllo se già salvato
        val prefs = getSharedPreferences("safehands", Context.MODE_PRIVATE)
        val savedParentUid = prefs.getString("parentUid", null)
        if (savedParentUid != null) {
            startActivity(Intent(this, CaregiverActivity::class.java))
            finish()
        }

        val btnContinue = findViewById<Button>(R.id.btnContinue)
        val etEmail = findViewById<EditText>(R.id.insertParentEmail)

        btnContinue.setOnClickListener {
            val parentEmail = etEmail.text.toString().trim()
            if (parentEmail.isNotEmpty()) {
                fetchParentUid(parentEmail)
            } else {
                Toast.makeText(this, "Inserisci un'email valida", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun fetchParentUid(parentEmail: String) {
        Log.d("CaregiverEmail", "Parent email: '$parentEmail'")

        val data = hashMapOf("parentEmail" to parentEmail)
        functions.getHttpsCallable("getUidFromEmail")
            .call(data)
            .addOnSuccessListener { result ->
                val parentUid = (result.data as Map<*, *>)["uid"] as String

                // Genera un caregiverId casuale
                val caregiverId = UUID.randomUUID().toString()

                // Crea documento caregiver in Firestore
                val caregiverDoc = hashMapOf(
                    "parentUid" to parentUid,
                    "parentEmail" to parentEmail,
                    "caregiverId" to caregiverId,
                    "createdAt" to Timestamp.now()
                )

                db.collection("caregivers")
                    .document("${parentUid}_$caregiverId")
                    .set(caregiverDoc)
                    .addOnSuccessListener {
                        // Salva locally parentUid e caregiverId
                        val prefs = getSharedPreferences("safehands", Context.MODE_PRIVATE)
                        prefs.edit()
                            .putString("parentUid", parentUid)
                            .putString("caregiverId", caregiverId)
                            .apply()

                        startActivity(Intent(this, CaregiverActivity::class.java))
                        finish()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Errore: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Parent non trovato: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}

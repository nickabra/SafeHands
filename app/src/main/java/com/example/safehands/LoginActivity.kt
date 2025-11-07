package com.example.safehands

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging

class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth = FirebaseAuth.getInstance()

        // 🔑 Controlla se l’utente è già loggato
        val currentUser = auth.currentUser
        if (currentUser != null) {
            // Se è loggato, vai direttamente a ParentActivity
            startActivity(Intent(this, ParentActivity::class.java))
            finish()
            return
        }

        // Se non è loggato, mostra la schermata di login
        setContentView(R.layout.activity_login)

        val emailEdit = findViewById<EditText>(R.id.editEmail)
        val passwordEdit = findViewById<EditText>(R.id.editPassword)
        val loginButton = findViewById<Button>(R.id.btnLogin)
        val registerButton = findViewById<Button>(R.id.btnRegister)

        loginButton.setOnClickListener {
            val email = emailEdit.text.toString()
            val password = passwordEdit.text.toString()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Inserisci email e password", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Toast.makeText(this, "Login effettuato", Toast.LENGTH_SHORT).show()
                        saveFcmToken(email) // FCM TOKEN
                        startActivity(Intent(this, ParentActivity::class.java))
                        finish()
                    } else {
                        Toast.makeText(this, "Login fallito: account non esistente. Registrati!", Toast.LENGTH_SHORT).show()
                    }
                }
        }

        registerButton.setOnClickListener {
            val email = emailEdit.text.toString().trim()
            val password = passwordEdit.text.toString().trim()

            if (email.isNotEmpty() && password.isNotEmpty()) {
                // Mostra dialog per inserire numero di telefono
                showPhoneDialogAndRegister(email, password)
            } else {
                Toast.makeText(this, "Inserisci email e password", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showPhoneDialogAndRegister(email: String, password: String) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Inserisci il numero di telefono")

        val input = EditText(this)
        input.inputType = InputType.TYPE_CLASS_PHONE
        input.hint = "Numero di telefono"
        builder.setView(input)

        builder.setPositiveButton("Conferma") { dialog, _ ->
            val phone = input.text.toString().trim()
            if (phone.isEmpty()) {
                Toast.makeText(this, "Devi inserire un numero di telefono", Toast.LENGTH_SHORT).show()
            } else {
                // Chiama la registrazione vera e propria
                registerUser(email, password, phone)
            }
            dialog.dismiss()
        }

        builder.setNegativeButton("Annulla") { dialog, _ ->
            dialog.cancel()
        }

        builder.show()
    }
    private fun registerUser(email: String, password: String, phone: String) {
        val auth = FirebaseAuth.getInstance()
        val db = FirebaseFirestore.getInstance()

        auth.createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener { result ->
                val uid = result.user?.uid ?: return@addOnSuccessListener

                // 1. Creazione documento in "parameters/<uid>/recived"
                val parametersDocRef = db.collection("parameters").document(uid)
                parametersDocRef.set(hashMapOf("createdAt" to FieldValue.serverTimestamp()))
                    .addOnSuccessListener {
                        parametersDocRef.collection("recived").document("init")
                            .set(hashMapOf("init" to true))
                    }

                // 2. Creazione documento in "todoLists/<uid>/tasks"
                val todoDocRef = db.collection("todoLists").document(uid)
                todoDocRef.set(hashMapOf("createdAt" to FieldValue.serverTimestamp()))
                    .addOnSuccessListener {
                        todoDocRef.collection("tasks").document("init")
                            .set(hashMapOf("init" to true))
                    }

                // 3. Creazione documento in "users/<email>" con campo phone e FCM TOKEN
                val safeEmail = email.replace(".", "_")
                db.collection("users").document(safeEmail)
                    .set(hashMapOf("phone" to phone))
                    .addOnSuccessListener {
                        saveFcmToken(email) // FCM TOKEN
                    }

                Toast.makeText(this, "Registrazione completata", Toast.LENGTH_SHORT).show()
                auth.signInWithEmailAndPassword(email, password)
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            Toast.makeText(this, "Login effettuato", Toast.LENGTH_SHORT).show()
                            startActivity(Intent(this, ParentActivity::class.java))
                            finish()
                        } else {
                            Toast.makeText(this, "Login fallito: Server Error. Riprovare più tardi.", Toast.LENGTH_SHORT).show()
                        }
                    }
            }
            .addOnFailureListener { e ->
                if (e is FirebaseAuthUserCollisionException) {
                    Toast.makeText(this, "Email già registrata", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Errore registrazione: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun saveFcmToken(userEmail: String) {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Toast.makeText(this, "Errore nel recupero del token FCM", Toast.LENGTH_SHORT).show()
                return@addOnCompleteListener
            }

            val token = task.result
            val safeEmail = userEmail.replace(".", "_")

            val db = FirebaseFirestore.getInstance()
            db.collection("users").document(safeEmail)
                .update("fcmToken", token)
                .addOnSuccessListener {
                    println("Token FCM salvato correttamente per $userEmail")
                }
                .addOnFailureListener { e ->
                    println("Errore salvataggio token: ${e.message}")
                }
        }
    }
}

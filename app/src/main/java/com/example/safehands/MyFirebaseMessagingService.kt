package com.example.safehands

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFirebaseMessagingService : FirebaseMessagingService() {

    private val CHANNEL_ID = "safehands_channel"
    private lateinit var functions: FirebaseFunctions

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("FCM", "Nuovo token generato: $token")

        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser != null) {
            val email = currentUser.email ?: return
            val safeEmail = email.replace(".", "_")

            val db = FirebaseFirestore.getInstance()
            db.collection("users").document(safeEmail)
                .update("fcmToken", token)
                .addOnSuccessListener {
                    Log.d("FCM", "Token aggiornato correttamente in Firestore")
                }
                .addOnFailureListener { e ->
                    Log.e("FCM", "Errore salvataggio token: ${e.message}")
                }
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        // Legge i dati inviati dalla cloud function
        val data = remoteMessage.data
        if (data.isEmpty()) return

        val eventType = data["eventType"] ?: return
        val title: String
        val body: String

        when (eventType) {
            "task_completed" -> {
                val taskName = data["taskName"] ?: "Task"
                title = "Task completato"
                body = "Il task \"$taskName\" è stato completato dal caregiver."
            }
            "parameter_sent" -> {
                val parameter = data["parameter"] ?: "Parametro"
                val time = data["time"] ?: ""

                // Titolo: "Nuova misurazione: <parameter>"
                title = "Nuova misurazione: $parameter"

                // Body: orario + altri valori (escludendo parameter e campi di sistema)
                val details = data.filterKeys {
                    it != "parameter" && it != "parameterName" && it != "time" && it != "eventType" && it != "parentUid"
                }
                    .map { "${it.key}: ${it.value}" }
                    .joinToString("\n")

                body = "Orario: $time\n$details"

                // Chiamata Cloud Function
                val currentUser = FirebaseAuth.getInstance().currentUser
                if (currentUser != null) {
                    val safeEmail = currentUser.email?.replace(".", "_") ?: return@onMessageReceived
                    functions = FirebaseFunctions.getInstance("europe-west1")

                    val payload = hashMapOf(
                        "parentEmail" to safeEmail,
                        "parameter" to parameter,
                        "title" to title,
                        "body" to body,
                        "time" to time,
                        "details" to details
                    )

                    functions
                        .getHttpsCallable("saveParameterNotification")
                        .call(payload)
                        .addOnSuccessListener {
                            Log.d("FCM", "Notifica parametro inviata a Cloud Function")
                        }
                        .addOnFailureListener { e ->
                            Log.e("FCM", "Errore chiamata Cloud Function: ${e.message}")
                        }
                }
            }
            else -> {
                title = "SafeHands"
                body = "Hai una nuova notifica"
            }
        }
        showNotification(title, body)
    }

    private fun showNotification(title: String, body: String) {
        // Creazione canale notifiche (Android O+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "SafeHands Notifications",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Canale per notifiche SafeHands"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }

        // Controllo permesso notifiche Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            Log.w("FCM", "Permesso notifiche negato")
            return
        }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.outline_exclamation_24) // sostituisci con icona della tua app
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body)) // per body più lungo
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        NotificationManagerCompat.from(this).notify(System.currentTimeMillis().toInt(), builder.build())
    }
}
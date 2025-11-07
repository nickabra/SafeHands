package com.example.safehands

import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.widget.Button
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MAIN_RoleSelectionActivity : AppCompatActivity() {
    private val REQUEST_NOTIFICATION_PERMISSION = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_role_selection)

        // Chiedi permesso notifiche
        checkNotificationPermission()

        val btnParent = findViewById<Button>(R.id.btnParent)
        val btnCaregiver = findViewById<Button>(R.id.btnCaregiver)

        btnParent.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
        }

        btnCaregiver.setOnClickListener {
            startActivity(Intent(this, CaregiverUidActivity::class.java))
        }
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                    REQUEST_NOTIFICATION_PERMISSION
                )
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_NOTIFICATION_PERMISSION) {
            if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                Toast.makeText(this, "Notifiche abilitate", Toast.LENGTH_SHORT).show()
            } else {
                AlertDialog.Builder(this)
                    .setTitle("Attenzione")
                    .setMessage("Se sei un parent è altamente consigliato attivare le notifiche altrimenti non si potranno ricevere gli aggiornamenti dal caregiver.")
                    .setPositiveButton("Attiva") { _, _ ->
                        // Ri-chiedi il permesso
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            ActivityCompat.requestPermissions(
                                this,
                                arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                                REQUEST_NOTIFICATION_PERMISSION
                            )
                        }
                    }
                    .setNeutralButton("Ok", null)
                    .show()
            }
        }
    }
}
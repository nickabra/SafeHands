package com.example.safehands

import android.app.Application
import com.google.firebase.FirebaseApp

class SafeHandsApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Inizializza Firebase
        FirebaseApp.initializeApp(this)
    }
}
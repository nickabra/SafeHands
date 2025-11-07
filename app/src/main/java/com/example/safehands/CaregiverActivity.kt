package com.example.safehands

import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class CaregiverActivity : AppCompatActivity() {

    private lateinit var firestore: FirebaseFirestore
    private var parentUid: String? = null
    private var todoListener: ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.caregiver_main) // usa lo stesso layout di MainActivity

        firestore = FirebaseFirestore.getInstance()
        parentUid = getSharedPreferences("safehands", Context.MODE_PRIVATE)
            .getString("parentUid", null)

        if(parentUid == null){
            // non dovrebbe succedere, ma sicurezza
            finish()
            return
        }

        // Mostra di default ToDo fragment
        replaceFragment(TodoListFragment(parentUid!!))

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNavigation)
        bottomNav.setOnItemSelectedListener { item ->
            when(item.itemId){
                R.id.nav_todo -> replaceFragment(TodoListFragment(parentUid!!))
                R.id.nav_parameters -> replaceFragment(ParametersFragment())
            }
            true
        }
    }

    private fun replaceFragment(fragment: Fragment){
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }

    override fun onDestroy() {
        super.onDestroy()
        todoListener?.remove() // rimuove eventuale listener Firestore
    }
}
package com.example.safehands

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.commit

class ParentActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_parent)

        // Carica il fragment della To-Do List del parente
        if (savedInstanceState == null) {
            supportFragmentManager.commit {
                replace(R.id.fragmentContainerParent, ParentToDoListFragment())
            }
        }
    }
}

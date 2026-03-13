package com.wype.security.ui.auth

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import com.wype.security.R
import com.wype.security.data.PrefsLite
import com.wype.security.ui.home.HomeActivity

class RegistrationActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_registration)

        val etEmail = findViewById<EditText>(R.id.emailEdit)
        val etPhone = findViewById<EditText>(R.id.phoneEdit)
        val etPass  = findViewById<EditText>(R.id.passEdit)

        findViewById<Button>(R.id.registerBtn).setOnClickListener {
            val e = etEmail.text.toString().trim()
            val p = etPhone.text.toString().trim()
            val s = etPass.text.toString().trim()

            if (e.isEmpty() || p.isEmpty() || s.isEmpty()) {
                if (e.isEmpty()) etEmail.error = "Email required"
                if (p.isEmpty()) etPhone.error = "Phone required"
                if (s.isEmpty()) etPass.error  = "Password required"
                return@setOnClickListener
            }
            PrefsLite.saveUser(this, e, p, s)
            startActivity(Intent(this, HomeActivity::class.java))
            finish()
        }
    }
}
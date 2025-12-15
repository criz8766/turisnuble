package cl.example.turisnuble.activities

import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import cl.example.turisnuble.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase

class ForgotPasswordActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_forgot_password)
        window.navigationBarColor = Color.WHITE

        // Botón Atrás
        val btnBack = findViewById<ImageButton>(R.id.btn_back_recover)
        btnBack.setOnClickListener {
            finish() // Vuelve al login
        }

        auth = Firebase.auth

        val etEmail = findViewById<EditText>(R.id.et_email_recover)
        val btnSend = findViewById<Button>(R.id.btn_send_recover)

        btnSend.setOnClickListener {
            val email = etEmail.text.toString().trim()
            if (email.isEmpty()) {
                etEmail.error = "Ingresa tu correo"
                return@setOnClickListener
            }

            auth.sendPasswordResetEmail(email)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Toast.makeText(this, "Correo enviado a $email", Toast.LENGTH_LONG).show()
                        finish() // Volver al login
                    } else {
                        Toast.makeText(this, "Error al enviar correo.", Toast.LENGTH_LONG).show()
                    }
                }
        }
    }
}
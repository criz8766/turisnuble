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

class RegisterActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)
        window.navigationBarColor = Color.WHITE

        val btnBack = findViewById<ImageButton>(R.id.btn_back_register)
        btnBack.setOnClickListener {
            finish() // Cierra esta actividad y vuelve al Login
        }

        auth = Firebase.auth

        val etEmail = findViewById<EditText>(R.id.et_email_reg)
        val etPassword = findViewById<EditText>(R.id.et_pass_reg)
        val btnRegister = findViewById<Button>(R.id.btn_do_register)

        btnRegister.setOnClickListener {
            val email = etEmail.text.toString()
            val password = etPassword.text.toString()

            if (email.isNotEmpty() && password.isNotEmpty()) {
                auth.createUserWithEmailAndPassword(email, password)
                    .addOnCompleteListener(this) { task ->
                        if (task.isSuccessful) {
                            val user = auth.currentUser
                            user?.sendEmailVerification()

                            Toast.makeText(
                                baseContext,
                                "Registro exitoso. Verifica tu correo.",
                                Toast.LENGTH_LONG
                            ).show()
                            auth.signOut() // Desloguear para forzar login verificado
                            finish() // Volver a la pantalla de Login
                        } else {
                            Toast.makeText(
                                baseContext,
                                "Error al registrar: ${task.exception?.message}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
            } else {
                Toast.makeText(this, "Completa todos los campos", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
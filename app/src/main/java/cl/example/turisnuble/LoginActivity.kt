package cl.example.turisnuble

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView // <-- IMPORTAR
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase

class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var googleSignInLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        auth = Firebase.auth

        // --- Configuración de Google Sign-In ---
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)

        googleSignInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                try {
                    val account = task.getResult(ApiException::class.java)!!
                    firebaseAuthWithGoogle(account.idToken!!)
                } catch (e: ApiException) {
                    Toast.makeText(this, "Falló el inicio con Google.", Toast.LENGTH_SHORT).show()
                }
            }
        }
        // --- Fin Configuración Google ---


        val etEmail = findViewById<EditText>(R.id.et_email)
        val etPassword = findViewById<EditText>(R.id.et_password)
        val btnLogin = findViewById<Button>(R.id.btn_login_email)
        val btnRegister = findViewById<Button>(R.id.btn_register_email)
        val btnGoogle = findViewById<Button>(R.id.btn_login_google)
        val btnInvitado = findViewById<Button>(R.id.btn_login_invitado)
        // --- NUEVO ELEMENTO ---
        val tvForgotPassword = findViewById<TextView>(R.id.tv_forgot_password)

        // --- LÓGICA DE RECUPERAR CONTRASEÑA ---
        tvForgotPassword.setOnClickListener {
            val email = etEmail.text.toString().trim()
            if (email.isEmpty()) {
                etEmail.error = "Ingresa tu correo para recuperarla"
                etEmail.requestFocus()
                return@setOnClickListener
            }

            auth.sendPasswordResetEmail(email)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Toast.makeText(this, "Correo de recuperación enviado a $email", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(this, "Error: No se pudo enviar el correo. Verifica que la dirección sea correcta.", Toast.LENGTH_LONG).show()
                    }
                }
        }
        // --- FIN LÓGICA DE RECUPERAR CONTRASEÑA ---


        // --- Lógica de Iniciar Sesión ---
        btnLogin.setOnClickListener {
            val email = etEmail.text.toString()
            val password = etPassword.text.toString()
            if (email.isNotEmpty() && password.isNotEmpty()) {
                auth.signInWithEmailAndPassword(email, password)
                    .addOnCompleteListener(this) { task ->
                        if (task.isSuccessful) {
                            val user = auth.currentUser
                            if (user != null && user.isEmailVerified) {
                                goToMainActivity()
                            } else {
                                Toast.makeText(baseContext, "Por favor, verifica tu correo electrónico.", Toast.LENGTH_LONG).show()
                                auth.signOut()
                            }
                        } else {
                            Toast.makeText(baseContext, "Error al iniciar sesión. Comprueba tus credenciales.", Toast.LENGTH_SHORT).show()
                        }
                    }
            }
        }

        // --- Lógica de Registrarse ---
        btnRegister.setOnClickListener {
            val email = etEmail.text.toString()
            val password = etPassword.text.toString()
            if (email.isNotEmpty() && password.isNotEmpty()) {
                auth.createUserWithEmailAndPassword(email, password)
                    .addOnCompleteListener(this) { task ->
                        if (task.isSuccessful) {
                            val user = auth.currentUser
                            user?.sendEmailVerification()
                                ?.addOnCompleteListener { verifyTask ->
                                    if (verifyTask.isSuccessful) {
                                        Toast.makeText(baseContext, "Registro exitoso. Revisa tu correo para verificar la cuenta.", Toast.LENGTH_LONG).show()
                                    } else {
                                        Toast.makeText(baseContext, "No se pudo enviar el correo de verificación.", Toast.LENGTH_SHORT).show()
                                    }
                                }
                        } else {
                            Toast.makeText(baseContext, "Error al registrar. El correo puede estar en uso.", Toast.LENGTH_LONG).show()
                        }
                    }
            }
        }

        // Lógica de Google
        btnGoogle.setOnClickListener {
            val signInIntent = googleSignInClient.signInIntent
            googleSignInLauncher.launch(signInIntent)
        }

        // Lógica de Invitado
        btnInvitado.setOnClickListener {
            goToMainActivity()
        }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    goToMainActivity()
                } else {
                    Toast.makeText(baseContext, "Falló la autenticación.", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun goToMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }
}
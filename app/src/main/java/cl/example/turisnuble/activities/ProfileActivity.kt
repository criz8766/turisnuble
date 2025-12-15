package cl.example.turisnuble.activities

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import cl.example.turisnuble.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase

class ProfileActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private var currentUser: FirebaseUser? = null

    private lateinit var etName: EditText
    private lateinit var tvEmail: TextView
    private lateinit var btnSave: Button
    private lateinit var btnResetPass: Button
    private lateinit var btnBack: ImageView
    private lateinit var btnDelete: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        window.navigationBarColor = Color.WHITE

        auth = Firebase.auth
        currentUser = auth.currentUser

        // Si por alguna razón el usuario es nulo, no deberíamos estar aquí.
        if (currentUser == null) {
            Toast.makeText(this, "Error: No se encontró usuario", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Inicializar vistas
        etName = findViewById(R.id.et_profile_name)
        tvEmail = findViewById(R.id.tv_profile_email)
        btnSave = findViewById(R.id.btn_profile_save_name)
        btnResetPass = findViewById(R.id.btn_profile_reset_password)
        btnBack = findViewById(R.id.btn_back_profile)
        btnDelete = findViewById(R.id.btn_profile_delete_account)

        // Cargar datos del perfil
        loadUserProfile()

        // Configurar Listeners
        btnBack.setOnClickListener {
            finish() // Cierra esta actividad y vuelve a MainActivity
        }

        btnSave.setOnClickListener {
            updateProfileName()
        }

        btnResetPass.setOnClickListener {
            sendPasswordReset()
        }

        btnDelete.setOnClickListener {
            showDeleteConfirmationDialog()
        }
    }

    private fun loadUserProfile() {
        currentUser?.let { user ->
            tvEmail.text = user.email
            // El nombre puede ser nulo o vacío si no se ha establecido
            if (!user.displayName.isNullOrEmpty()) {
                etName.setText(user.displayName)
            } else {
                etName.setText("") // O un placeholder como "Sin nombre"
            }
        }
    }

    private fun updateProfileName() {
        val newName = etName.text.toString().trim()

        if (newName.isEmpty()) {
            etName.error = "El nombre no puede estar vacío"
            return
        }

        currentUser?.let { user ->
            val profileUpdates = UserProfileChangeRequest.Builder()
                .setDisplayName(newName)
                .build()

            user.updateProfile(profileUpdates)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Toast.makeText(this, "Nombre actualizado correctamente", Toast.LENGTH_SHORT)
                            .show()
                        finish() // Vuelve a MainActivity
                    } else {
                        Toast.makeText(
                            this,
                            "Error al actualizar el nombre: ${task.exception?.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
        }
    }

    private fun sendPasswordReset() {
        currentUser?.email?.let { email ->
            auth.sendPasswordResetEmail(email)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Toast.makeText(
                            this,
                            "Correo de recuperación enviado a $email",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        Toast.makeText(
                            this,
                            "Error al enviar correo: ${task.exception?.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
        }
    }

    private fun showDeleteConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle("¿Eliminar cuenta?")
            .setMessage("¿Estás seguro de que quieres eliminar tu cuenta? Esta acción es permanente e irreversible.")
            .setPositiveButton("Sí, Eliminar") { _, _ ->
                deleteAccount()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun deleteAccount() {
        currentUser?.delete()
            ?.addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Toast.makeText(this, "Cuenta eliminada exitosamente.", Toast.LENGTH_SHORT)
                        .show()
                    // Enviamos al usuario de vuelta al Login y limpiamos el historial
                    val intent = Intent(this, LoginActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                } else {
                    Log.w("ProfileActivity", "Error al eliminar cuenta", task.exception)
                    // Manejar error común de "re-autenticación"
                    if (task.exception is FirebaseAuthRecentLoginRequiredException) {
                        // --- INICIO: CORRECCIÓN ---
                        Toast.makeText(
                            this,
                            "Esta es una operación sensible. Por favor, vuelve a iniciar sesión e inténtalo de nuevo.",
                            Toast.LENGTH_LONG
                        ).show()
                        // --- FIN: CORRECCIÓN ---

                        auth.signOut()
                        val intent = Intent(this, LoginActivity::class.java)
                        intent.flags =
                            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                        finish()
                    } else {
                        Toast.makeText(this, "Error: ${task.exception?.message}", Toast.LENGTH_LONG)
                            .show()
                    }
                }
            }
    }
}
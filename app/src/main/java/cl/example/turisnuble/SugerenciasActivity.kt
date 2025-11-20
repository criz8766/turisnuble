package cl.example.turisnuble

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class SugerenciasActivity : AppCompatActivity() {

    private lateinit var etSugerencia: TextInputEditText
    private lateinit var btnEnviar: MaterialButton
    private lateinit var btnBack: ImageView
    private lateinit var progressBar: ProgressBar
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sugerencias)

        auth = FirebaseAuth.getInstance()

        initViews()
        setupListeners()
    }

    private fun initViews() {
        etSugerencia = findViewById(R.id.et_sugerencia)
        btnEnviar = findViewById(R.id.btn_enviar)
        btnBack = findViewById(R.id.btn_back)
        progressBar = findViewById(R.id.progressBar)
    }

    private fun setupListeners() {
        btnBack.setOnClickListener {
            finish()
        }

        btnEnviar.setOnClickListener {
            enviarSugerencia()
        }
    }

    private fun enviarSugerencia() {
        val texto = etSugerencia.text.toString().trim()

        if (texto.isEmpty()) {
            etSugerencia.error = "Por favor escribe un mensaje"
            etSugerencia.requestFocus()
            return
        }

        setLoading(true)

        val user = auth.currentUser
        val userId = user?.uid ?: "anonimo"
        val userEmail = user?.email ?: "sin_email"

        val sugerenciaData = mapOf(
            "userId" to userId,
            "email" to userEmail,
            "mensaje" to texto,
            "timestamp" to System.currentTimeMillis(),
            "estado" to "pendiente",
            "version_app" to "1.0" // CORRECCIÓN: Texto fijo para evitar error de BuildConfig
        )

        FirebaseDatabase.getInstance().getReference("sugerencias")
            .push()
            .setValue(sugerenciaData)
            .addOnSuccessListener {
                Toast.makeText(this, "¡Sugerencia enviada con éxito!", Toast.LENGTH_LONG).show()
                etSugerencia.text?.clear()
                finish() // Volver al mapa automáticamente
            }
            .addOnFailureListener { e ->
                setLoading(false)
                Toast.makeText(this, "Error al enviar: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun setLoading(isLoading: Boolean) {
        if (isLoading) {
            progressBar.visibility = View.VISIBLE
            btnEnviar.visibility = View.INVISIBLE
            etSugerencia.isEnabled = false
        } else {
            progressBar.visibility = View.GONE
            btnEnviar.visibility = View.VISIBLE
            etSugerencia.isEnabled = true
        }
    }
}
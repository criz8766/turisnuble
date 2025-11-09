package cl.example.turisnuble

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.animation.AnimationUtils
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser // <-- IMPORTAR
import com.google.firebase.auth.GoogleAuthProvider // <-- IMPORTAR
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        supportActionBar?.hide()

        val logo: ImageView = findViewById(R.id.iv_logo_splash)
        val pulseAnimation = AnimationUtils.loadAnimation(this, R.anim.splash_logo_pulse)
        logo.startAnimation(pulseAnimation)

        auth = Firebase.auth

        lifecycleScope.launch(Dispatchers.IO) {

            GtfsDataManager.loadData(assets)

            val currentUser = auth.currentUser

            withContext(Dispatchers.Main) {
                // <-- INICIO CAMBIO
                if (currentUser != null && isUserVerified(currentUser)) {
                    // Caso 1: Usuario existe Y está verificado (o es de Google)
                    startMainActivity()
                } else {
                    // Caso 2: No hay usuario O no está verificado
                    startLoginActivity()
                }
                // <-- FIN CAMBIO
            }
        }
    }

    // <-- INICIO CAMBIO: Nueva función de ayuda
    private fun isUserVerified(user: FirebaseUser): Boolean {
        // Opción 1: El usuario está verificado por correo
        if (user.isEmailVerified) {
            return true
        }

        // Opción 2: El usuario inició sesión con Google (que ya está verificado por defecto)
        val isGoogleProvider = user.providerData.any {
            it.providerId == GoogleAuthProvider.PROVIDER_ID
        }

        return isGoogleProvider
    }
    // <-- FIN CAMBIO

    private fun startMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }

    private fun startLoginActivity() {
        val intent = Intent(this, LoginActivity::class.java)
        startActivity(intent)
        finish()
    }
}
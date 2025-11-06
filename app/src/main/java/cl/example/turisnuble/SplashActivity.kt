package cl.example.turisnuble

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.animation.AnimationUtils // <-- IMPORTAR
import android.widget.ImageView // <-- IMPORTAR
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        supportActionBar?.hide()

        // --- INICIO CÓDIGO DE ANIMACIÓN ---

        // 1. Encontrar el logo principal (el de encima)
        val logo: ImageView = findViewById(R.id.iv_logo_splash)

        // 2. Cargar la animación que creamos
        val pulseAnimation = AnimationUtils.loadAnimation(this, R.anim.splash_logo_pulse)

        // 3. Iniciar la animación en el logo
        logo.startAnimation(pulseAnimation)

        // --- FIN CÓDIGO DE ANIMACIÓN ---

        // Iniciamos la carga de datos (esto queda igual)
        lifecycleScope.launch(Dispatchers.IO) {

            // --- ESTA ES LA CARGA REAL ---
            GtfsDataManager.loadData(assets)

            // --- FIN DE LA CARGA ---

            withContext(Dispatchers.Main) {
                startMainActivity()
            }
        }
    }

    private fun startMainActivity() {
        // Creamos el intent para ir a la actividad principal
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)

        // Finalizamos el SplashActivity
        finish()
    }
}
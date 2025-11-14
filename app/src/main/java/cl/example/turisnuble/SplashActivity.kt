package cl.example.turisnuble

import android.annotation.SuppressLint
import android.content.Context // <-- IMPORTADO
import android.content.Intent
import android.os.Bundle
import android.util.Log // <-- IMPORTADO
import android.view.animation.AnimationUtils
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File // <-- IMPORTADO

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

            // --- INICIO DE MODIFICACIÓN: LÓGICA DE FASE 2 ---

            // 1. Verificar y copiar archivos en la primera ejecución
            prepareGtfsData()

            // 2. Aquí es donde debe ir su futura lógica de "actualización".
            // Por ejemplo:
            // if (hayNuevaVersion(servidorUrl)) {
            //     descargarNuevosJson(servidorUrl, this@SplashActivity.filesDir)
            // }

            // 3. Cargar datos desde el almacenamiento interno
            // Esta línea reemplaza a GtfsDataManager.loadData(assets)
            GtfsDataManager.loadData(this@SplashActivity)

            // --- FIN DE MODIFICACIÓN ---

            val currentUser = auth.currentUser

            withContext(Dispatchers.Main) {
                if (currentUser != null && isUserVerified(currentUser)) {
                    startMainActivity()
                } else {
                    startLoginActivity()
                }
            }
        }
    }

    /**
     * Verifica si los archivos GTFS existen en el almacenamiento interno.
     * Si no, los copia desde 'assets' (primera ejecución).
     */
    private fun prepareGtfsData() {
        val gtfsFiles = listOf(
            "routes.json",
            "stops.json",
            "shapes.json",
            "trips.json",
            "stop_times.json",
            "version.json" // Importante para la lógica de actualización
        )

        val internalStorageDir = this.filesDir
        val versionFile = File(internalStorageDir, "version.json")

        if (!versionFile.exists()) {
            // Si no existe, es la primera vez que se abre la app.
            // Copiamos todos los archivos desde 'assets' a 'filesDir'.
            Log.d("SplashActivity", "Primera ejecución: Copiando archivos base a almacenamiento interno...")
            gtfsFiles.forEach { fileName ->
                copyAssetToFile(fileName, this)
            }
        } else {
            Log.d("SplashActivity", "Archivos de datos ya existen en almacenamiento interno.")
            // En el futuro, aquí puedes verificar la versión remota contra la local.
        }
    }

    /**
     * Función helper para copiar un archivo desde 'assets' al almacenamiento interno 'filesDir'.
     */
    private fun copyAssetToFile(fileName: String, context: Context) {
        try {
            context.assets.open(fileName).use { inputStream ->
                File(context.filesDir, fileName).outputStream().use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            Log.d("SplashActivity", "Archivo $fileName copiado a filesDir.")
        } catch (e: Exception) {
            Log.e("SplashActivity", "Error copiando $fileName a filesDir.", e)
            // Considerar manejo de error más robusto si un archivo falta
        }
    }

    // Función de ayuda existente (sin cambios)
    private fun isUserVerified(user: FirebaseUser): Boolean {
        if (user.isEmailVerified) {
            return true
        }
        val isGoogleProvider = user.providerData.any {
            it.providerId == GoogleAuthProvider.PROVIDER_ID
        }
        return isGoogleProvider
    }

    // Función existente (sin cambios)
    private fun startMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }

    // Función existente (sin cambios)
    private fun startLoginActivity() {
        val intent = Intent(this, LoginActivity::class.java)
        startActivity(intent)
        finish()
    }
}
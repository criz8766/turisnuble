package cl.example.turisnuble

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.TextView
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
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var statusTextView: TextView

    // (Asegúrate de que esta URL sea la correcta)
    private val URL_BASE_REMOTA = "https://raw.githubusercontent.com/criz8766/datosgtfschillan/main/"

    private val GTFS_FILES = listOf(
        "routes.json",
        "stops.json",
        "shapes.json",
        "trips.json",
        "stopTimes.json",
        "version.json"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        supportActionBar?.hide()

        val logo: ImageView = findViewById(R.id.iv_logo_splash)
        val pulseAnimation = AnimationUtils.loadAnimation(this, R.anim.splash_logo_pulse)
        logo.startAnimation(pulseAnimation)

        statusTextView = findViewById(R.id.tv_splash_status)
        statusTextView.text = "Cargando..."

        auth = Firebase.auth

        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                prepareGtfsData(
                    onUpdateRequired = {
                        withContext(Dispatchers.Main) {
                            statusTextView.text = "Actualizando datos..."
                        }
                    }
                )
            }

            statusTextView.text = "Procesando datos..."
            GtfsDataManager.loadData(this@SplashActivity)

            val currentUser = auth.currentUser
            if (currentUser != null && isUserVerified(currentUser)) {
                startMainActivity()
            } else {
                startLoginActivity()
            }
        }
    }

    private suspend fun prepareGtfsData(onUpdateRequired: suspend () -> Unit) {
        val internalStorageDir = this.filesDir
        val versionFile = File(internalStorageDir, "version.json")

        if (!versionFile.exists()) {
            Log.d("SplashActivity", "Primera ejecución: Copiando archivos base a almacenamiento interno...")
            GTFS_FILES.forEach { fileName ->
                copyAssetToFile(fileName, this)
            }
        } else {
            Log.d("SplashActivity", "Iniciando chequeo de versión remota...")
            try {
                val cacheBuster = System.currentTimeMillis()
                val localVersionJson = JSONObject(versionFile.readText())
                val localVersion = localVersionJson.optLong("version", 0L)

                // --- INICIO DE MODIFICACIÓN: SINTAXIS CORREGIDA ---
                val remoteVersionUrl = "${URL_BASE_REMOTA}version.json?_t=$cacheBuster"
                // --- FIN DE MODIFICACIÓN ---

                val url = URL(remoteVersionUrl)
                val connection = url.openConnection() as HttpURLConnection
                connection.useCaches = false
                connection.setRequestProperty("Cache-Control", "no-cache")
                connection.setRequestProperty("Pragma", "no-cache")

                val remoteJsonString = connection.inputStream.bufferedReader().use { it.readText() }
                val remoteVersionJson = JSONObject(remoteJsonString)

                val remoteVersion = remoteVersionJson.optLong("version", 0L)

                Log.d("SplashActivity", "Versión Local: $localVersion, Versión Remota: $remoteVersion")

                if (remoteVersion > localVersion) {
                    Log.i("SplashActivity", "Nueva versión detectada. Descargando archivos GTFS...")
                    onUpdateRequired()

                    GTFS_FILES.forEach { fileName ->
                        downloadFile(fileName, internalStorageDir, cacheBuster)
                    }

                    Log.i("SplashActivity", "Actualización de archivos GTFS completada.")
                } else {
                    Log.d("SplashActivity", "Datos GTFS ya están actualizados.")
                }

            } catch (e: Exception) {
                Log.e("SplashActivity", "Error al chequear/descargar actualización. Usando datos locales.", e)
            }
        }
    }

    /**
     * Descarga un archivo desde la URL remota y lo guarda en almacenamiento interno.
     */
    private fun downloadFile(fileName: String, destinationDir: File, cacheBuster: Long) {
        try {
            // --- INICIO DE MODIFICACIÓN: SINTAXIS CORREGIDA ---
            val remoteUrl = "${URL_BASE_REMOTA}${fileName}?_t=$cacheBuster"
            // --- FIN DE MODIFICACIÓN ---

            val url = URL(remoteUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.useCaches = false
            connection.setRequestProperty("Cache-Control", "no-cache")
            connection.setRequestProperty("Pragma", "no-cache")

            val fileContent = connection.inputStream.bufferedReader().use { it.readText() }

            val destinationFile = File(destinationDir, fileName)
            destinationFile.writeText(fileContent)

            Log.d("SplashActivity", "Archivo $fileName descargado y guardado.")
        } catch (e: Exception) {
            Log.e("SplashActivity", "Error descargando $fileName", e)
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
        } catch (e: Exception)
        {
            Log.e("SplashActivity", "Error copiando $fileName a filesDir.", e)
        }
    }

    // --- FUNCIONES EXISTENTES (SIN CAMBIOS) ---

    private fun isUserVerified(user: FirebaseUser): Boolean {
        if (user.isEmailVerified) {
            return true
        }
        val isGoogleProvider = user.providerData.any { // Corregido 'isGooogleProvider'
            it.providerId == GoogleAuthProvider.PROVIDER_ID
        }
        return isGoogleProvider
    }

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
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

    // --- URLs BASE ---
    // 1. Para GTFS y version.json (Raíz)
    private val URL_BASE_GTFS = "https://criz8766.github.io/datosgtfschillan/"
    // 2. Para Turismo (Subcarpeta)
    private val URL_BASE_TURISMO = "https://criz8766.github.io/datosgtfschillan/puntoturistico/"

    // Archivos GTFS (se descargan de la raíz)
    private val GTFS_FILES = listOf(
        "routes.json",
        "stops.json",
        "shapes.json",
        "trips.json",
        "stopTimes.json",
        "version.json"
    )

    // Archivo de Turismo (se descarga de la subcarpeta)
    private val TURISMO_FILE = "turismo.json"

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

            // 1. Cargar GTFS
            GtfsDataManager.loadData(this@SplashActivity)

            // 2. Cargar Turismo (Pasando la URL de la carpeta de turismo para las imágenes)
            TurismoDataManager.loadData(this@SplashActivity, URL_BASE_TURISMO)

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
            // Copia inicial desde Assets (Asumimos que turismo.json también está en assets root)
            GTFS_FILES.forEach { fileName -> copyAssetToFile(fileName, this) }
            copyAssetToFile(TURISMO_FILE, this)
        } else {
            Log.d("SplashActivity", "Iniciando chequeo de versión remota...")
            try {
                val cacheBuster = System.currentTimeMillis()
                val localVersionJson = JSONObject(versionFile.readText())
                val localVersion = localVersionJson.optLong("version", 0L)

                // Verificamos version.json en la raíz (GTFS Base)
                val remoteVersionUrl = "${URL_BASE_GTFS}version.json?_t=$cacheBuster"
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
                    Log.i("SplashActivity", "Nueva versión detectada. Descargando archivos...")
                    onUpdateRequired()

                    // 1. Descargar GTFS (desde Raíz)
                    GTFS_FILES.forEach { fileName ->
                        downloadFile(fileName, URL_BASE_GTFS, internalStorageDir, cacheBuster)
                    }

                    // 2. Descargar Turismo (desde carpeta puntoturistico)
                    downloadFile(TURISMO_FILE, URL_BASE_TURISMO, internalStorageDir, cacheBuster)

                    Log.i("SplashActivity", "Actualización completada.")
                } else {
                    Log.d("SplashActivity", "Datos ya están actualizados.")
                }

            } catch (e: Exception) {
                Log.e("SplashActivity", "Error al chequear/descargar actualización. Usando datos locales.", e)
            }
        }
    }

    // Función helper para descargar aceptando una URL Base dinámica
    private fun downloadFile(fileName: String, baseUrl: String, destinationDir: File, cacheBuster: Long) {
        try {
            val remoteUrl = "${baseUrl}${fileName}?_t=$cacheBuster"

            val url = URL(remoteUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.useCaches = false
            connection.setRequestProperty("Cache-Control", "no-cache")
            connection.setRequestProperty("Pragma", "no-cache")

            val fileContent = connection.inputStream.bufferedReader().use { it.readText() }

            val destinationFile = File(destinationDir, fileName)
            destinationFile.writeText(fileContent)

            Log.d("SplashActivity", "Archivo $fileName descargado desde $baseUrl")
        } catch (e: Exception) {
            Log.e("SplashActivity", "Error descargando $fileName", e)
        }
    }

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
        }
    }

    private fun isUserVerified(user: FirebaseUser): Boolean {
        if (user.isEmailVerified) return true
        return user.providerData.any { it.providerId == GoogleAuthProvider.PROVIDER_ID }
    }

    private fun startMainActivity() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun startLoginActivity() {
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }
}
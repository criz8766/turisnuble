package cl.example.turisnuble.data

import android.content.Context
import android.util.Log
import cl.example.turisnuble.models.PuntoTuristico
import org.json.JSONArray
import java.io.File

object TurismoDataManager {

    val puntosTuristicos = mutableListOf<PuntoTuristico>()
    private var isDataLoaded = false

    // Recibe la URL base para construir la ruta completa de la imagen
    fun loadData(context: Context, baseUrl: String) {
        // Descomenta la siguiente línea si no quieres recargar cada vez (opcional)
        // if (isDataLoaded) return

        Log.d("TurismoDataManager", "Iniciando carga de datos de turismo...")

        try {
            val filesDir = context.filesDir
            val turismoFile = File(filesDir, "turismo.json")

            if (!turismoFile.exists()) {
                Log.e("TurismoDataManager", "turismo.json no encontrado en almacenamiento interno.")
                return
            }

            val turismoJsonString = turismoFile.bufferedReader().readText()
            val turismoArray = JSONArray(turismoJsonString)

            puntosTuristicos.clear()

            for (i in 0 until turismoArray.length()) {
                val obj = turismoArray.getJSONObject(i)

                val imagenNombre = obj.getString("imagenNombre")

                // Construimos la URL completa apuntando a tu carpeta 'puntoturistico'
                val imagenUrlCompleta = "$baseUrl$imagenNombre"

                val punto = PuntoTuristico(
                    id = obj.getInt("id"),
                    nombre = obj.getString("nombre"),
                    direccion = obj.getString("direccion"),
                    latitud = obj.getDouble("latitud"),
                    longitud = obj.getDouble("longitud"),
                    categoria = obj.getString("categoria"), // Leemos la categoría
                    imagenUrl = imagenUrlCompleta
                )
                puntosTuristicos.add(punto)
            }

            isDataLoaded = true
            Log.d("TurismoDataManager", "Cargados ${puntosTuristicos.size} puntos turísticos.")

        } catch (e: Exception) {
            Log.e("TurismoDataManager", "Error al cargar turismo.json", e)
            isDataLoaded = false
        }
    }
}
package cl.example.turisnuble

import android.content.res.AssetManager
import android.util.Log
import org.maplibre.android.geometry.LatLng

// --- CLASES DE DATOS PARA ORGANIZAR LA INFORMACIÓN GTFS ---

data class GtfsRoute(
    val routeId: String,
    val shortName: String,
    val longName: String,
    val color: String
)

data class GtfsStop(
    val stopId: String,
    val name: String,
    val location: LatLng
)

data class GtfsTrip(
    val routeId: String,
    val serviceId: String,
    val tripId: String,
    val directionId: Int,
    val shapeId: String
)

// --- NUEVA CLASE DE DATOS ---
data class GtfsStopTime(
    val tripId: String,
    val stopId: String,
    val stopSequence: Int
)

object GtfsDataManager {

    private var isDataLoaded = false

    // Colecciones para acceder rápidamente a los datos
    val routes = mutableMapOf<String, GtfsRoute>()
    val stops = mutableMapOf<String, GtfsStop>()
    val shapes = mutableMapOf<String, MutableList<LatLng>>()
    val trips = mutableMapOf<String, GtfsTrip>()

    // --- NUEVA COLECCIÓN ---
    // Un mapa que agrupa todas las paradas por su tripId
    private val stopTimesByTrip = mutableMapOf<String, MutableList<GtfsStopTime>>()

    fun loadData(assetManager: AssetManager) {
        if (isDataLoaded) return
        Log.d("GtfsDataManager", "Iniciando carga de datos GTFS...")

        try {
            // 1. Cargar Rutas (routes.txt)
            assetManager.open("routes.txt").bufferedReader().useLines { lines ->
                lines.drop(1).forEach { line ->
                    val tokens = line.split(',')
                    if (tokens.size > 7) {
                        val route = GtfsRoute(
                            routeId = tokens[0],
                            shortName = tokens[2],
                            longName = tokens[3],
                            color = "#${tokens[7]}"
                        )
                        routes[route.routeId] = route
                    }
                }
            }
            Log.d("GtfsDataManager", "Cargadas ${routes.size} rutas.")

            // 2. Cargar Paraderos (stops.txt)
            assetManager.open("stops.txt").bufferedReader().useLines { lines ->
                lines.drop(1).forEach { line ->
                    val tokens = line.split(',')
                    if (tokens.size > 5) {
                        val stop = GtfsStop(
                            stopId = tokens[0],
                            name = tokens[2],
                            location = LatLng(tokens[4].toDouble(), tokens[5].toDouble())
                        )
                        stops[stop.stopId] = stop
                    }
                }
            }
            Log.d("GtfsDataManager", "Cargados ${stops.size} paraderos.")

            // 3. Cargar Trazados (shapes.txt)
            assetManager.open("shapes.txt").bufferedReader().useLines { lines ->
                lines.drop(1).forEach { line ->
                    val tokens = line.split(',')
                    if (tokens.size > 3) {
                        val shapeId = tokens[0]
                        val point = LatLng(tokens[1].toDouble(), tokens[2].toDouble())
                        shapes.getOrPut(shapeId) { mutableListOf() }.add(point)
                    }
                }
            }
            Log.d("GtfsDataManager", "Cargados ${shapes.size} trazados.")

            // 4. Cargar Viajes (trips.txt)
            assetManager.open("trips.txt").bufferedReader().useLines { lines ->
                lines.drop(1).forEach { line ->
                    val tokens = line.split(',')
                    if (tokens.size > 7) {
                        val trip = GtfsTrip(
                            routeId = tokens[0],
                            serviceId = tokens[1],
                            tripId = tokens[2],
                            directionId = tokens[5].toInt(),
                            shapeId = tokens[7]
                        )
                        // Usamos una clave combinada para encontrar el viaje fácilmente después
                        val tripKey = "${trip.routeId}_${trip.directionId}"
                        if (!trips.containsKey(tripKey)) {
                            trips[tripKey] = trip
                        }
                    }
                }
            }
            Log.d("GtfsDataManager", "Cargados ${trips.size} viajes únicos.")

            // --- 5. NUEVA LÓGICA: Cargar Secuencia de Paraderos (stop_times.txt) ---
            assetManager.open("stop_times.txt").bufferedReader().useLines { lines ->
                lines.drop(1).forEach { line ->
                    val tokens = line.split(',')
                    if (tokens.size > 4) {
                        val stopTime = GtfsStopTime(
                            tripId = tokens[0],
                            stopId = tokens[3],
                            stopSequence = tokens[4].toInt()
                        )
                        stopTimesByTrip.getOrPut(stopTime.tripId) { mutableListOf() }.add(stopTime)
                    }
                }
            }
            Log.d("GtfsDataManager", "Cargadas ${stopTimesByTrip.size} secuencias de paraderos.")


            isDataLoaded = true
            Log.d("GtfsDataManager", "Carga de datos GTFS completada.")
        } catch (e: Exception) {
            Log.e("GtfsDataManager", "Error al cargar los datos GTFS.", e)
            isDataLoaded = false
        }
    }

    // --- NUEVA FUNCIÓN PÚBLICA ---
    /**
     * Devuelve la lista ordenada de paraderos para una ruta y dirección específicas.
     */
    fun getStopsForRoute(routeId: String, directionId: Int): List<GtfsStop> {
        // 1. Encontrar el tripId que corresponde a esta ruta y dirección
        val tripKey = "${routeId}_${directionId}"
        val representativeTripId = trips[tripKey]?.tripId ?: return emptyList()

        // 2. Usar el tripId para obtener la secuencia de paradas
        val stopTimes = stopTimesByTrip[representativeTripId] ?: return emptyList()

        // 3. Ordenar las paradas por su secuencia
        val sortedStopTimes = stopTimes.sortedBy { it.stopSequence }

        // 4. Convertir los IDs de parada en los objetos GtfsStop completos y devolver la lista
        return sortedStopTimes.mapNotNull { stopTime ->
            stops[stopTime.stopId]
        }
    }
}
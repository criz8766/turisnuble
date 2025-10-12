package cl.example.turisnuble

import android.content.res.AssetManager
import android.util.Log
import org.maplibre.android.geometry.LatLng

// Data classes para almacenar la información de los archivos GTFS
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
    val tripId: String,
    val routeId: String,
    val shapeId: String,
    val directionId: Int
)

// Usamos un objeto Singleton para que los datos se carguen una sola vez
object GtfsDataManager {

    private var isDataLoaded = false

    // Mapas para un acceso ultra rápido a los datos
    val routes = mutableMapOf<String, GtfsRoute>()
    val stops = mutableMapOf<String, GtfsStop>()
    val shapes = mutableMapOf<String, MutableList<LatLng>>()
    val trips = mutableMapOf<String, GtfsTrip>()

    fun loadData(assetManager: AssetManager) {
        if (isDataLoaded) return // Si ya cargamos los datos, no hacemos nada

        Log.d("GtfsDataManager", "Iniciando carga de datos GTFS...")

        try {
            // 1. Cargar Rutas (routes.txt)
            assetManager.open("routes.txt").bufferedReader().useLines { lines ->
                lines.drop(1).forEach { line ->
                    val tokens = line.split(',')
                    val route = GtfsRoute(
                        routeId = tokens[0],
                        shortName = tokens[2],
                        longName = tokens[3],
                        color = "#${tokens[7]}" // Añadimos '#' al color
                    )
                    routes[route.routeId] = route
                }
            }
            Log.d("GtfsDataManager", "Cargadas ${routes.size} rutas.")

            // 2. Cargar Paraderos (stops.txt)
            assetManager.open("stops.txt").bufferedReader().useLines { lines ->
                lines.drop(1).forEach { line ->
                    val tokens = line.split(',')
                    val stop = GtfsStop(
                        stopId = tokens[0],
                        name = tokens[2],
                        location = LatLng(tokens[4].toDouble(), tokens[5].toDouble())
                    )
                    stops[stop.stopId] = stop
                }
            }
            Log.d("GtfsDataManager", "Cargados ${stops.size} paraderos.")

            // 3. Cargar Formas/Trazados (shapes.txt)
            assetManager.open("shapes.txt").bufferedReader().useLines { lines ->
                lines.drop(1).forEach { line ->
                    val tokens = line.split(',')
                    val shapeId = tokens[0]
                    val point = LatLng(tokens[1].toDouble(), tokens[2].toDouble())
                    shapes.getOrPut(shapeId) { mutableListOf() }.add(point)
                }
            }
            Log.d("GtfsDataManager", "Cargadas ${shapes.size} formas de ruta.")

            // 4. Cargar Viajes (trips.txt) - crucial para conectar rutas y formas
            assetManager.open("trips.txt").bufferedReader().useLines { lines ->
                lines.drop(1).forEach { line ->
                    val tokens = line.split(',')
                    val trip = GtfsTrip(
                        routeId = tokens[0],
                        tripId = tokens[2],
                        directionId = tokens[5].toInt(),
                        shapeId = tokens[7]
                    )
                    // Solo guardamos un viaje por cada combinación de ruta y dirección para simplificar
                    val key = "${trip.routeId}_${trip.directionId}"
                    if (!trips.containsKey(key)) {
                        trips[key] = trip
                    }
                }
            }
            Log.d("GtfsDataManager", "Cargados ${trips.size} viajes únicos.")

            isDataLoaded = true
            Log.d("GtfsDataManager", "Carga de datos GTFS completada.")
        } catch (e: Exception) {
            Log.e("GtfsDataManager", "Error al cargar los datos GTFS.", e)
        }
    }
}
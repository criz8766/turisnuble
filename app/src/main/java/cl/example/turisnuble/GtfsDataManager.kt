package cl.example.turisnuble

import android.content.res.AssetManager
import android.util.Log
import org.maplibre.android.geometry.LatLng
import kotlin.math.pow
import android.location.Location // <-- IMPORTACIÓN NECESARIA

// Clases de datos (sin cambios)
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

data class GtfsStopTime(
    val tripId: String,
    val stopId: String,
    val stopSequence: Int
)

object GtfsDataManager {

    private var isDataLoaded = false

    val routes = mutableMapOf<String, GtfsRoute>()
    val stops = mutableMapOf<String, GtfsStop>()
    val shapes = mutableMapOf<String, MutableList<LatLng>>()
    val trips = mutableMapOf<String, GtfsTrip>()
    private val stopTimesByTrip = mutableMapOf<String, MutableList<GtfsStopTime>>()

    // --- NUEVA COLECCIÓN PARA BÚSQUEDAS RÁPIDAS (EXISTENTE) ---
    private val tripsByTripId = mutableMapOf<String, GtfsTrip>()


    fun loadData(assetManager: AssetManager) {
        if (isDataLoaded) return
        Log.d("GtfsDataManager", "Iniciando carga de datos GTFS...")

        try {
            // Carga de routes.txt (sin cambios)
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

            // Carga de stops.txt (sin cambios)
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

            // Carga de shapes.txt (sin cambios)
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

            // Carga de trips.txt (con una pequeña adición)
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
                        val tripKey = "${trip.routeId}_${trip.directionId}"
                        if (!trips.containsKey(tripKey)) {
                            trips[tripKey] = trip
                        }
                        // --- NUEVO: Guardamos el viaje por su ID para búsquedas rápidas ---
                        tripsByTripId[trip.tripId] = trip
                    }
                }
            }
            Log.d("GtfsDataManager", "Cargados ${trips.size} viajes únicos.")

            // Carga de stop_times.txt (sin cambios)
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

    // Función para obtener la secuencia de paraderos (sin cambios)
    fun getStopsForRoute(routeId: String, directionId: Int): List<GtfsStop> {
        val tripKey = "${routeId}_${directionId}"
        val representativeTripId = trips[tripKey]?.tripId ?: return emptyList()
        val stopTimes = stopTimesByTrip[representativeTripId] ?: return emptyList()
        val sortedStopTimes = stopTimes.sortedBy { it.stopSequence }
        return sortedStopTimes.mapNotNull { stopTime -> stops[stopTime.stopId] }
    }

    /**
     * Calcula una distancia en metros usando la API de Location de Android.
     */
    private fun distanceToInMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val results = FloatArray(1)
        Location.distanceBetween(lat1, lon1, lat2, lon2, results)
        return results[0]
    }

    /**
     * Devuelve una lista de los N paraderos más cercanos a una ubicación (lat, lon).
     */
    fun getNearbyStops(lat: Double, lon: Double, count: Int = 3): List<GtfsStop> {
        if (stops.isEmpty()) return emptyList()

        return stops.values
            .map { stop ->
                // Emparejamos el paradero con su distancia al punto de interés
                val distance = distanceToInMeters(lat, lon, stop.location.latitude, stop.location.longitude)
                Pair(stop, distance)
            }
            .sortedBy { it.second } // Ordenamos por la distancia más pequeña
            .take(count)           // Tomamos los N más cercanos
            .map { it.first }
    }

    /**
     * Devuelve una lista de todas las rutas (y sus direcciones) que pasan por un paradero específico.
     */
    fun getRoutesForStop(stopId: String): List<DisplayRouteInfo> {
        val uniqueRoutes = mutableSetOf<Pair<String, Int>>()

        // 1. Buscamos en todas las secuencias de paradas
        stopTimesByTrip.values.flatten().forEach { stopTime ->
            // 2. Si una secuencia contiene nuestro paradero...
            if (stopTime.stopId == stopId) {
                // 3. ...obtenemos la información del viaje correspondiente
                tripsByTripId[stopTime.tripId]?.let { trip ->
                    // 4. Y añadimos la combinación de ID de ruta y dirección a nuestra lista de resultados únicos
                    uniqueRoutes.add(Pair(trip.routeId, trip.directionId))
                }
            }
        }

        // 5. Convertimos los resultados en objetos DisplayRouteInfo para pasarlos al fragmento
        return uniqueRoutes.mapNotNull { (routeId, directionId) ->
            routes[routeId]?.let { route ->
                val directionName = if (directionId == 0) "Ida" else "Vuelta"
                DisplayRouteInfo(route, directionId, directionName)
            }
        }
    }
}
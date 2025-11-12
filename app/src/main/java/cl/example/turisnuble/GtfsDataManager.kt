package cl.example.turisnuble

import android.content.res.AssetManager
import android.util.Log
import org.maplibre.android.geometry.LatLng
import kotlin.math.pow
import android.location.Location
import org.json.JSONArray // <-- IMPORTACIÓN NECESARIA
import org.json.JSONObject // <-- IMPORTACIÓN NECESARIA

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
    private val tripsByTripId = mutableMapOf<String, GtfsTrip>()

    // --- FUNCIÓN MODIFICADA PARA LEER JSON ---
    fun loadData(assetManager: AssetManager) {
        if (isDataLoaded) return
        Log.d("GtfsDataManager", "Iniciando carga de datos GTFS desde JSON...")

        try {
            // Carga de routes.json
            val routesJsonString = assetManager.open("routes.json").bufferedReader().readText()
            val routesArray = JSONArray(routesJsonString)
            for (i in 0 until routesArray.length()) {
                val obj = routesArray.getJSONObject(i)
                val route = GtfsRoute(
                    routeId = obj.getString("route_id"),
                    shortName = obj.getString("route_short_name"),
                    longName = obj.getString("route_long_name"),
                    color = "#${obj.getString("route_color")}" // Asumiendo que el color en JSON no tiene el #
                )
                routes[route.routeId] = route
            }
            Log.d("GtfsDataManager", "Cargadas ${routes.size} rutas.")

            // Carga de stops.json
            val stopsJsonString = assetManager.open("stops.json").bufferedReader().readText()
            val stopsArray = JSONArray(stopsJsonString)
            for (i in 0 until stopsArray.length()) {
                val obj = stopsArray.getJSONObject(i)
                val stop = GtfsStop(
                    stopId = obj.getString("stop_id"),
                    name = obj.getString("stop_name"),
                    location = LatLng(obj.getDouble("stop_lat"), obj.getDouble("stop_lon"))
                )
                stops[stop.stopId] = stop
            }
            Log.d("GtfsDataManager", "Cargados ${stops.size} paraderos.")

            // Carga de shapes.json
            val shapesJsonString = assetManager.open("shapes.json").bufferedReader().readText()
            val shapesArray = JSONArray(shapesJsonString)
            for (i in 0 until shapesArray.length()) {
                val obj = shapesArray.getJSONObject(i)
                val shapeId = obj.getString("shape_id")
                val point = LatLng(obj.getDouble("shape_pt_lat"), obj.getDouble("shape_pt_lon"))
                shapes.getOrPut(shapeId) { mutableListOf() }.add(point)
                // NOTA: El sort por sequence no es necesario si el JSON ya viene ordenado.
                // Si no, habría que parsear "shape_pt_sequence" y ordenar después.
            }
            Log.d("GtfsDataManager", "Cargados ${shapes.size} trazados.")

            // Carga de trips.json
            val tripsJsonString = assetManager.open("trips.json").bufferedReader().readText()
            val tripsArray = JSONArray(tripsJsonString)
            for (i in 0 until tripsArray.length()) {
                val obj = tripsArray.getJSONObject(i)
                val trip = GtfsTrip(
                    routeId = obj.getString("route_id"),
                    serviceId = obj.getString("service_id"),
                    tripId = obj.getString("trip_id"),
                    directionId = obj.getInt("direction_id"),
                    shapeId = obj.getString("shape_id")
                )
                val tripKey = "${trip.routeId}_${trip.directionId}"
                if (!trips.containsKey(tripKey)) {
                    trips[tripKey] = trip
                }
                tripsByTripId[trip.tripId] = trip
            }
            Log.d("GtfsDataManager", "Cargados ${trips.size} viajes únicos.")

            // Carga de stop_times.json
            val stopTimesJsonString = assetManager.open("stop_times.json").bufferedReader().readText()
            val stopTimesArray = JSONArray(stopTimesJsonString)
            for (i in 0 until stopTimesArray.length()) {
                val obj = stopTimesArray.getJSONObject(i)
                val stopTime = GtfsStopTime(
                    tripId = obj.getString("trip_id"),
                    stopId = obj.getString("stop_id"),
                    stopSequence = obj.getInt("stop_sequence")
                )
                stopTimesByTrip.getOrPut(stopTime.tripId) { mutableListOf() }.add(stopTime)
            }
            Log.d("GtfsDataManager", "Cargadas ${stopTimesByTrip.size} secuencias de paraderos.")

            isDataLoaded = true
            Log.d("GtfsDataManager", "Carga de datos GTFS (JSON) completada.")
        } catch (e: Exception) {
            Log.e("GtfsDataManager", "Error al cargar los datos GTFS desde JSON.", e)
            isDataLoaded = false
        }
    }

    // --- EL RESTO DE FUNCIONES (getStopsForRoute, distanceToInMeters, getNearbyStops, getRoutesForStop) ---
    // --- NO REQUIEREN NINGÚN CAMBIO ---
    // ... (copiar el resto de funciones existentes)

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
                // Asumiendo que DisplayRouteInfo existe, basado en la función
                // Si no, reemplace la línea siguiente con lo que corresponda
                DisplayRouteInfo(route, directionId, directionName)
            }
        }
    }

}
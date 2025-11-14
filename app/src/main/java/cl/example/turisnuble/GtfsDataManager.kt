package cl.example.turisnuble

import android.content.Context // <-- CAMBIO: Importado Context
import android.util.Log
import org.maplibre.android.geometry.LatLng
import kotlin.math.pow
import android.location.Location
import org.json.JSONArray
import org.json.JSONObject
import java.io.File // <-- CAMBIO: Importado File para leer de almacenamiento interno

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

    // --- FUNCIÓN MODIFICADA PARA LEER DESDE ALMACENAMIENTO INTERNO ---
    fun loadData(context: Context) { // <-- CAMBIO: Recibe Context
        if (isDataLoaded) return
        Log.d("GtfsDataManager", "Iniciando carga de datos GTFS desde almacenamiento interno...")

        try {
            val filesDir = context.filesDir // <-- CAMBIO: Usamos el directorio de archivos internos

            // Carga de routes.json
            val routesJsonString = File(filesDir, "routes.json").bufferedReader().readText() // <-- CAMBIO
            val routesArray = JSONArray(routesJsonString)
            for (i in 0 until routesArray.length()) {
                val obj = routesArray.getJSONObject(i)
                val route = GtfsRoute(
                    routeId = obj.getString("route_id"),
                    shortName = obj.getString("route_short_name"),
                    longName = obj.getString("route_long_name"),
                    color = "#${obj.getString("route_color")}"
                )
                routes[route.routeId] = route
            }
            Log.d("GtfsDataManager", "Cargadas ${routes.size} rutas.")

            // Carga de stops.json
            val stopsJsonString = File(filesDir, "stops.json").bufferedReader().readText() // <-- CAMBIO
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
            val shapesJsonString = File(filesDir, "shapes.json").bufferedReader().readText() // <-- CAMBIO
            val shapesArray = JSONArray(shapesJsonString)
            for (i in 0 until shapesArray.length()) {
                val obj = shapesArray.getJSONObject(i)
                val shapeId = obj.getString("shape_id")
                val point = LatLng(obj.getDouble("shape_pt_lat"), obj.getDouble("shape_pt_lon"))
                shapes.getOrPut(shapeId) { mutableListOf() }.add(point)
            }
            Log.d("GtfsDataManager", "Cargados ${shapes.size} trazados.")

            // Carga de trips.json
            val tripsJsonString = File(filesDir, "trips.json").bufferedReader().readText() // <-- CAMBIO
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
            val stopTimesJsonString = File(filesDir, "stop_times.json").bufferedReader().readText() // <-- CAMBIO
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
            Log.d("GtfsDataManager", "Carga de datos GTFS (JSON, interno) completada.")
        } catch (e: Exception) {
            Log.e("GtfsDataManager", "Error al cargar los datos GTFS desde almacenamiento interno.", e)
            isDataLoaded = false
        }
    }

    // --- EL RESTO DE FUNCIONES (getStopsForRoute, distanceToInMeters, getNearbyStops, getRoutesForStop) ---
    // --- NO REQUIEREN NINGÚN CAMBIO ---

    fun getStopsForRoute(routeId: String, directionId: Int): List<GtfsStop> {
        val tripKey = "${routeId}_${directionId}"
        val representativeTripId = trips[tripKey]?.tripId ?: return emptyList()
        val stopTimes = stopTimesByTrip[representativeTripId] ?: return emptyList()
        val sortedStopTimes = stopTimes.sortedBy { it.stopSequence }
        return sortedStopTimes.mapNotNull { stopTime -> stops[stopTime.stopId] }
    }

    private fun distanceToInMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val results = FloatArray(1)
        Location.distanceBetween(lat1, lon1, lat2, lon2, results)
        return results[0]
    }

    fun getNearbyStops(lat: Double, lon: Double, count: Int = 3): List<GtfsStop> {
        if (stops.isEmpty()) return emptyList()

        return stops.values
            .map { stop ->
                val distance = distanceToInMeters(lat, lon, stop.location.latitude, stop.location.longitude)
                Pair(stop, distance)
            }
            .sortedBy { it.second }
            .take(count)
            .map { it.first }
    }

    fun getRoutesForStop(stopId: String): List<DisplayRouteInfo> {
        val uniqueRoutes = mutableSetOf<Pair<String, Int>>()

        stopTimesByTrip.values.flatten().forEach { stopTime ->
            if (stopTime.stopId == stopId) {
                tripsByTripId[stopTime.tripId]?.let { trip ->
                    uniqueRoutes.add(Pair(trip.routeId, trip.directionId))
                }
            }
        }

        return uniqueRoutes.mapNotNull { (routeId, directionId) ->
            routes[routeId]?.let { route ->
                val directionName = if (directionId == 0) "Ida" else "Vuelta"
                DisplayRouteInfo(route, directionId, directionName)
            }
        }
    }
}
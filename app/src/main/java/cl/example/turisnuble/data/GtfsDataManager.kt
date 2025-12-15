package cl.example.turisnuble.data

import android.content.Context
import android.location.Location
import android.util.Log
import cl.example.turisnuble.fragments.DisplayRouteInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.maplibre.android.geometry.LatLng
import java.io.File
import java.net.URL

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
    private var isRuralDataLoaded = false

    val routes = mutableMapOf<String, GtfsRoute>()
    val stops = mutableMapOf<String, GtfsStop>()
    val shapes = mutableMapOf<String, MutableList<LatLng>>()
    val trips = mutableMapOf<String, GtfsTrip>()
    private val stopTimesByTrip = mutableMapOf<String, MutableList<GtfsStopTime>>()
    private val tripsByTripId = mutableMapOf<String, GtfsTrip>()

    // --- Carga de datos locales (Micros) ---
    fun loadData(context: Context) {
        if (isDataLoaded) return
        Log.d("GtfsDataManager", "Cargando datos GTFS locales...")

        try {
            val filesDir = context.filesDir

            val routesJson = File(filesDir, "routes.json").bufferedReader().readText()
            val routesArray = JSONArray(routesJson)
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

            val stopsJson = File(filesDir, "stops.json").bufferedReader().readText()
            val stopsArray = JSONArray(stopsJson)
            for (i in 0 until stopsArray.length()) {
                val obj = stopsArray.getJSONObject(i)
                val stop = GtfsStop(
                    stopId = obj.getString("stop_id"),
                    name = obj.getString("stop_name"),
                    location = LatLng(obj.getDouble("stop_lat"), obj.getDouble("stop_lon"))
                )
                stops[stop.stopId] = stop
            }

            val shapesJson = File(filesDir, "shapes.json").bufferedReader().readText()
            val shapesArray = JSONArray(shapesJson)
            for (i in 0 until shapesArray.length()) {
                val obj = shapesArray.getJSONObject(i)
                val shapeId = obj.getString("shape_id")
                val point = LatLng(obj.getDouble("shape_pt_lat"), obj.getDouble("shape_pt_lon"))
                shapes.getOrPut(shapeId) { mutableListOf() }.add(point)
            }

            val tripsJson = File(filesDir, "trips.json").bufferedReader().readText()
            val tripsArray = JSONArray(tripsJson)
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
                if (!trips.containsKey(tripKey)) trips[tripKey] = trip
                tripsByTripId[trip.tripId] = trip
            }

            val stopTimesJson = File(filesDir, "stopTimes.json").bufferedReader().readText()
            val stopTimesArray = JSONArray(stopTimesJson)
            for (i in 0 until stopTimesArray.length()) {
                val obj = stopTimesArray.getJSONObject(i)
                val stopTime = GtfsStopTime(
                    tripId = obj.getString("trip_id"),
                    stopId = obj.getString("stop_id"),
                    stopSequence = obj.getInt("stop_sequence")
                )
                stopTimesByTrip.getOrPut(stopTime.tripId) { mutableListOf() }.add(stopTime)
            }

            isDataLoaded = true
        } catch (e: Exception) {
            Log.e("GtfsDataManager", "Error carga local", e)
        }
    }

    // --- Carga de datos rurales (GitHub) ---
    suspend fun loadRuralData() {
        if (isRuralDataLoaded) return
        withContext(Dispatchers.IO) {
            try {
                val url = URL("https://criz8766.github.io/datosgtfschillan/ruralshaperoute.json")
                val jsonString = url.readText()
                val jsonArray = JSONArray(jsonString)
                val tempShapes = mutableMapOf<String, MutableList<LatLng>>()

                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val shapeId = obj.getString("shape_id")
                    val lat = obj.getDouble("shape_pt_lat")
                    val lon = obj.getDouble("shape_pt_lon")
                    tempShapes.getOrPut(shapeId) { mutableListOf() }.add(LatLng(lat, lon))
                }

                withContext(Dispatchers.Main) {
                    tempShapes.forEach { (shapeId, points) ->
                        shapes[shapeId] = points

                        // ID único para evitar conflictos
                        val ruralRouteId = "rural_${shapeId.replace(" ", "_").lowercase()}"
                        val nombreFormateado = shapeId.replace("-", " - ").capitalize()

                        // Creamos la ruta con shortName="Rural" para identificarla
                        val newRoute = GtfsRoute(
                            routeId = ruralRouteId,
                            shortName = "Rural",
                            longName = nombreFormateado,
                            color = "#4CAF50"
                        )
                        routes[ruralRouteId] = newRoute

                        val newTrip = GtfsTrip(
                            routeId = ruralRouteId,
                            serviceId = "rural_service",
                            tripId = "trip_$ruralRouteId",
                            directionId = 0, // Solo Ida por defecto
                            shapeId = shapeId
                        )
                        trips["${ruralRouteId}_0"] = newTrip
                        tripsByTripId[newTrip.tripId] = newTrip
                    }
                    isRuralDataLoaded = true
                }
            } catch (e: Exception) {
                Log.e("GtfsDataManager", "Error carga rural: ${e.message}")
            }
        }
    }

    // Funciones auxiliares (sin cambios)
    fun getStopsForRoute(routeId: String, directionId: Int): List<GtfsStop> {
        val tripKey = "${routeId}_${directionId}"
        val tripId = trips[tripKey]?.tripId ?: return emptyList()
        val stopTimes = stopTimesByTrip[tripId] ?: return emptyList()
        return stopTimes.sortedBy { it.stopSequence }.mapNotNull { stops[it.stopId] }
    }

    private fun distanceToInMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val res = FloatArray(1)
        Location.distanceBetween(lat1, lon1, lat2, lon2, res)
        return res[0]
    }

    fun getNearbyStops(lat: Double, lon: Double, count: Int = 3): List<GtfsStop> {
        if (stops.isEmpty()) return emptyList()
        return stops.values.map {
            it to distanceToInMeters(
                lat,
                lon,
                it.location.latitude,
                it.location.longitude
            )
        }
            .sortedBy { it.second }.take(count).map { it.first }
    }

    fun getRoutesForStop(stopId: String): List<DisplayRouteInfo> {
        val result = mutableSetOf<Pair<String, Int>>()
        stopTimesByTrip.values.flatten()
            .forEach { if (it.stopId == stopId) tripsByTripId[it.tripId]?.let { t -> result.add(t.routeId to t.directionId) } }
        return result.mapNotNull { (rId, dId) ->
            routes[rId]?.let {
                DisplayRouteInfo(
                    it,
                    dId,
                    if (dId == 0) "Ida" else "Vuelta"
                )
            }
        }
    }

    private fun String.capitalize() =
        replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
}
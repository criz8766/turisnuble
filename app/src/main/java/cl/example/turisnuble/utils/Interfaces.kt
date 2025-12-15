package cl.example.turisnuble.utils

import cl.example.turisnuble.data.GtfsRoute
import cl.example.turisnuble.data.GtfsStop
import cl.example.turisnuble.models.PuntoTuristico

// Define aquí todas tus interfaces compartidas

interface RouteDrawer {
    fun drawRoute(route: GtfsRoute, directionId: Int)

    // --- AÑADE ESTA LÍNEA ---
    fun drawRouteSegment(route: GtfsRoute, directionId: Int, stopA: GtfsStop, stopB: GtfsStop)

    fun clearRoutes(recenterToUser: Boolean = true)
    fun displayStopAndNearbyStops(stop: GtfsStop)
}

interface TurismoActionHandler {
    fun centerMapOnPoint(lat: Double, lon: Double)
    fun showTurismoDetail(punto: PuntoTuristico)
}

interface DetalleTurismoNavigator {
    fun showRoutesForStop(stopId: String)
    fun hideDetailFragment()

    // --- AÑADE ESTA LÍNEA ---
    fun onGetDirectionsClicked(punto: PuntoTuristico)
}

interface ParaderoActionHandler {
    /**
     * Solicita encontrar una ruta de bus desde la ubicación actual del usuario
     * hasta un paradero específico.
     */
    fun onGetDirectionsToStop(stop: GtfsStop)
}
// file: app/src/main/java/cl/example/turisnuble/Interfaces.kt

package cl.example.turisnuble

// Define aquí todas tus interfaces compartidas

interface RouteDrawer {
    fun drawRoute(route: GtfsRoute, directionId: Int)
    // --- CAMBIO: Añadimos un parámetro opcional ---
    // Por defecto, se centrará en el usuario para no romper la funcionalidad
    // del botón "Mostrar buses cercanos".
    fun clearRoutes(recenterToUser: Boolean = true)
}

interface TurismoActionHandler {
    fun centerMapOnPoint(lat: Double, lon: Double)
    fun showTurismoDetail(punto: PuntoTuristico)
}

interface DetalleTurismoNavigator {
    fun showRoutesForStop(stopId: String)
    fun hideDetailFragment()
}

// ...y cualquier otra interfaz que necesites compartir entre Fragments y Activities
    
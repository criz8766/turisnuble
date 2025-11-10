package cl.example.turisnuble

import android.Manifest
import android.content.pm.PackageManager
// --- IMPORTACIONES NECESARIAS PARA CORREGIR ERRORES DE PINTURA Y DATOS ---
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
// ---
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.google.transit.realtime.GtfsRealtime
// --- IMPORTACIONES AÑADIDAS ---
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.maplibre.android.MapLibre
import org.maplibre.android.annotations.IconFactory
import org.maplibre.android.annotations.Marker
import org.maplibre.android.annotations.MarkerOptions
// --- IMPORTACIÓN AÑADIDA PARA 3D ---
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMap.CancelableCallback
import org.maplibre.android.maps.OnMapReadyCallback
import org.maplibre.android.maps.Style
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import org.maplibre.android.style.expressions.Expression.*
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import retrofit2.Retrofit
import retrofit2.converter.protobuf.ProtoConverterFactory

import android.widget.ImageButton
import android.widget.TextView

// --- IMPORTACIONES PARA EL MENÚ ---
import android.content.Intent
import android.widget.ImageView // Necesario para el optionsMenuButton
import androidx.appcompat.widget.PopupMenu
import com.google.firebase.auth.FirebaseAuth
// --- FIN IMPORTACIONES AÑADIDAS ---

// --- ### USA SOLAMENTE ESTAS DOS LÍNEAS PARA EXPRESSIONS ### ---
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.expressions.Expression.*
// --- ### Y ELIMINA CUALQUIER OTRA import org.maplibre.android.style.expressions... ### ---


class MainActivity : AppCompatActivity(),
    OnMapReadyCallback,
    RouteDrawer,
    TurismoActionHandler,
    DetalleTurismoNavigator,
    MapMover,
    ParaderoActionHandler {

    // --- Variables (sin cambios) ---
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var mapView: MapView
    private lateinit var map: MapLibreMap
    private lateinit var locationFab: FloatingActionButton
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<FrameLayout>
    private lateinit var tabLayout: TabLayout
    private lateinit var viewPager: ViewPager2
    private lateinit var pagerAdapter: ViewPagerAdapter
    private lateinit var searchView: androidx.appcompat.widget.SearchView
    private lateinit var searchResultsRecyclerView: androidx.recyclerview.widget.RecyclerView
    private lateinit var searchAdapter: SearchAdapter

    private lateinit var customNotificationView: View
    private lateinit var customNotificationMessage: TextView
    private lateinit var customNotificationDismiss: ImageButton

    private val sharedViewModel: SharedViewModel by viewModels()
    private var selectedRouteId: String? = null
    private var selectedDirectionId: Int? = null
    private var lastFeedMessage: GtfsRealtime.FeedMessage? = null
    private var currentRouteSourceId: String? = null
    private var currentRouteLayerId: String? = null
    private var mapStyle: Style? = null
    private var currentSelectedStopId: String? = null
    private val turismoMarkers = mutableListOf<Marker>()
    private var peekHeightInPixels: Int = 0

    private var currentInfoMarker: Marker? = null

    // --- VARIABLE DE AUTENTICACIÓN ---
    private lateinit var auth: FirebaseAuth

    // --- VARIABLES AÑADIDAS PARA OSCURO/CLARO/SATELITE ---
    private lateinit var mapStyleFab: FloatingActionButton
    private val styleLIGHT = "https://basemaps.cartocdn.com/gl/voyager-gl-style/style.json"
    private val styleDARK = "https://basemaps.cartocdn.com/gl/dark-matter-gl-style/style.json"
    private val styleSATELLITE_HYBRID = "https://api.maptiler.com/maps/hybrid/style.json?key=ya1iLYBcEKV62dZj18Tt"
    var currentMapStyleState = 0
    var isStyleLoading = false
    private var lastMapStyleClickTime: Long = 0L
    // --- FIN VARIABLES ---

    private var listMaxHeight: Int = 0
    private var detailMaxHeight: Int = 0

    // --- apiService (sin cambios) ---
    private val apiService: GtfsApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://turisnuble-api.onrender.com/")
            .addConverterFactory(ProtoConverterFactory.create())
            .build()
            .create(GtfsApiService::class.java)
    }

    // --- requestLocationPermissionLauncher (sin cambios) ---
    private val requestLocationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                showUserLocation()
                requestFreshLocation()
            } else {
                Toast.makeText(this, "Permiso de ubicación denegado.", Toast.LENGTH_LONG).show()
                centerMapOnPoint(-36.606, -72.102)
                findAndShowStopsAroundPoint(-36.606, -72.102)
            }
        }


    // --- LÓGICA DE "CÓMO LLEGAR" (SIN CAMBIOS) ---
    override fun onGetDirectionsToStop(stop: GtfsStop) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Se necesita permiso de ubicación para 'Cómo llegar'", Toast.LENGTH_LONG).show()
            requestLocationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            return
        }

        Log.d("Directions", "Buscando MEJOR ruta para Paradero ${stop.name} (${stop.stopId})")
        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, CancellationTokenSource().token)
            .addOnSuccessListener { location: Location? ->
                if (location == null) {
                    Toast.makeText(this, "No se pudo obtener tu ubicación actual", Toast.LENGTH_LONG).show()
                    return@addOnSuccessListener
                }

                val userLat = location.latitude
                val userLon = location.longitude
                val destLat = stop.location.latitude
                val destLon = stop.location.longitude

                // 1. Búsqueda con radio flexible para el usuario (Origen A)
                val originSearchRadii = listOf(500f, 1000f, 2500f) // 500m, 1km, 2.5km
                var stopsNearA: List<Pair<GtfsStop, Float>> = emptyList() // Guardamos la distancia

                Log.d("Directions", "Iniciando búsqueda de paraderos (A)...")
                for (radius in originSearchRadii) {
                    stopsNearA = GtfsDataManager.stops.values
                        .map { s -> Pair(s, distanceBetween(userLat, userLon, s.location.latitude, s.location.longitude)) }
                        .filter { it.second <= radius }
                        .sortedBy { it.second } // Ordenados por distancia
                    if (stopsNearA.isNotEmpty()) {
                        Log.d("Directions", "Paraderos (A) encontrados a ${radius}m (${stopsNearA.size} paraderos)")
                        break
                    }
                }

                if (stopsNearA.isEmpty()) {
                    Log.w("Directions", "No se encontraron paraderos (A) cerca del usuario (radio max ${originSearchRadii.last()}m)")
                    Toast.makeText(this, "No se encontraron paraderos cercanos a tu ubicación (radio max 2.5km)", Toast.LENGTH_LONG).show()
                    return@addOnSuccessListener
                }

                // 2. Búsqueda con radio flexible para el destino (Destino B)
                val destSearchRadii = listOf(500f, 1000f, 2500f)
                var stopsNearB: List<Pair<GtfsStop, Float>> = emptyList() // Guardamos la distancia

                Log.d("Directions", "Iniciando búsqueda de paraderos (B) cerca de ${stop.name}...")
                for (radius in destSearchRadii) {
                    stopsNearB = GtfsDataManager.stops.values
                        .map { s -> Pair(s, distanceBetween(destLat, destLon, s.location.latitude, s.location.longitude)) }
                        .filter { it.second <= radius }
                        .sortedBy { it.second } // Ordenados por distancia
                    if (stopsNearB.isNotEmpty()) {
                        Log.d("Directions", "Paraderos (B) encontrados a ${radius}m del destino (${stopsNearB.size} paraderos)")
                        break
                    }
                }

                if (stopsNearB.isEmpty()) {
                    stopsNearB = listOf(Pair(stop, 0f)) // Failsafe
                    Log.w("Directions", "No se encontraron paraderos cercanos a ${stop.name}, usando solo el paradero exacto.")
                }

                // 3. Lógica de búsqueda mejorada
                val validRoutes = mutableListOf<Triple<DisplayRouteInfo, GtfsStop, GtfsStop>>()
                var commonRoutesFound = false

                val stopsNearB_RouteSets = stopsNearB.associate { (stopB, _) ->
                    stopB.stopId to GtfsDataManager.getRoutesForStop(stopB.stopId)
                }

                for ((stopA, distA) in stopsNearA) {
                    val routesA = GtfsDataManager.getRoutesForStop(stopA.stopId)
                    val routeSetA = routesA.map { it.route.routeId to it.directionId }.toSet()

                    for ((stopB, distB) in stopsNearB) {
                        if (stopA.stopId == stopB.stopId) continue

                        val routesB = stopsNearB_RouteSets[stopB.stopId] ?: emptyList()
                        val commonRoutes = routesB.filter { routeSetA.contains(it.route.routeId to it.directionId) }

                        if (commonRoutes.isNotEmpty()) {
                            commonRoutesFound = true
                        }

                        for (commonRoute in commonRoutes) {
                            val stopSequence = GtfsDataManager.getStopsForRoute(commonRoute.route.routeId, commonRoute.directionId)
                            val indexA = stopSequence.indexOfFirst { it.stopId == stopA.stopId }
                            val indexB = stopSequence.indexOfFirst { it.stopId == stopB.stopId }

                            if (indexA != -1 && indexB != -1 && indexA < indexB) { // Valida A -> B
                                validRoutes.add(Triple(commonRoute, stopA, stopB))
                            }
                        }
                    }
                }

                // 4. Seleccionar la mejor ruta
                if (validRoutes.isNotEmpty()) {
                    Log.d("Directions", "Se encontraron ${validRoutes.size} rutas válidas. Seleccionando la 'mejor'...")

                    val bestOption = validRoutes.minByOrNull { (route, stopA, stopB) ->
                        val distA = stopsNearA.find { it.first.stopId == stopA.stopId }?.second ?: Float.MAX_VALUE
                        val distB = stopsNearB.find { it.first.stopId == stopB.stopId }?.second ?: Float.MAX_VALUE
                        distA + distB
                    }!!

                    val bestRouteFound = bestOption.first
                    val bestStopA = bestOption.second
                    val bestStopB = bestOption.third

                    val route = bestRouteFound.route
                    val directionId = bestRouteFound.directionId

                    Log.d("Directions", "MEJOR RUTA: ${route.shortName} (Desde ${bestStopA.name} a ${bestStopB.name})")

                    val toastMessage = if (bestStopB.stopId == stop.stopId) {
                        "Toma la Línea ${route.shortName} en ${bestStopA.name}"
                    } else {
                        "Toma la Línea ${route.shortName} en ${bestStopA.name} y baja en ${bestStopB.name}"
                    }
                    //Toast.makeText(this, toastMessage, Toast.LENGTH_LONG).show()
                    showCustomNotification(toastMessage)

                    hideDetailFragment()
                    drawRouteSegment(route, directionId, bestStopA, bestStopB)
                    sharedViewModel.selectStop(bestStopA.stopId)

                } else {
                    val toastMessage = if (commonRoutesFound) {
                        "Se encontraron rutas, pero ninguna va en la dirección correcta (A -> B)"
                    } else {
                        "No se encontró una ruta de micro directa en los paraderos cercanos"
                    }
                    //Toast.makeText(this, toastMessage, Toast.LENGTH_LONG).show()
                    showCustomNotification(toastMessage)
                    Log.d("Directions", "No se encontró NINGUNA ruta válida A -> B.")
                }
            }
            .addOnFailureListener {
                Log.e("Directions", "Error al obtener ubicación", it)
                Toast.makeText(this, "Error al obtener ubicación", Toast.LENGTH_LONG).show()
            }
    }

    // --- displayStopAndNearbyStops (sin cambios) ---
    override fun displayStopAndNearbyStops(stop: GtfsStop) {
        clearDrawnElements()
        selectedRouteId = null
        selectedDirectionId = null
        sharedViewModel.selectStop(stop.stopId)
        findAndShowStopsAroundPoint(stop.location.latitude, stop.location.longitude)
        centerMapOnPoint(stop.location.latitude, stop.location.longitude)
        val stopLocation = Location("").apply {
            latitude = stop.location.latitude
            longitude = stop.location.longitude
        }
        updateBusMarkers(stopLocation)
    }

    // --- onCreate (Modificaciones mínimas) ---
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MapLibre.getInstance(this)
        setContentView(R.layout.activity_main)

        // --- INICIALIZACIÓN DE AUTH ---
        auth = FirebaseAuth.getInstance()

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        val bottomSheet: FrameLayout = findViewById(R.id.bottom_sheet)
        bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet)
        val screenHeight = resources.displayMetrics.heightPixels
        bottomSheetBehavior.maxHeight = (screenHeight * 0.40).toInt()
        peekHeightInPixels = (46 * resources.displayMetrics.density).toInt()
        bottomSheetBehavior.peekHeight = peekHeightInPixels
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED

        setupTabs()
        setupBottomSheetCallback() // <-- Llama al callback para el padding dinámico

        mapView = findViewById(R.id.mapView)
        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync(this)

        locationFab = findViewById(R.id.location_fab)
        locationFab.setOnClickListener {
            sharedViewModel.setNearbyCalculationCenter(null)
            sharedViewModel.selectStop(null)
            clearRoutes(recenterToUser = true)
        }

        // --- CONEXIÓN DEL BOTÓN AÑADIDA ---
        mapStyleFab = findViewById(R.id.map_style_fab)
        mapStyleFab.setOnClickListener {

            // Define el tiempo de enfriamiento (ej. 2000 milisegundos = 2 segundos)
            val COOLDOWN_MS = 1500

            val now = System.currentTimeMillis()

            // Comprueba si ha pasado suficiente tiempo desde el último click
            if (now - lastMapStyleClickTime > COOLDOWN_MS) {
                // Sí pasó el tiempo: actualiza la marca de tiempo y ejecuta la acción
                lastMapStyleClickTime = now
                toggleMapStyle()
            }
            // Si no ha pasado el tiempo, el click simplemente se ignora (no se hace nada)
        }
        // --- FIN CONEXIÓN---

        searchView = findViewById(R.id.search_view)
        setupSearchListener()

        searchAdapter = SearchAdapter(emptyList()) { searchResult ->
            onSearchResultClicked(searchResult)
        }

        customNotificationView = findViewById(R.id.custom_notification_container)
        customNotificationMessage = customNotificationView.findViewById(R.id.tv_notification_message)
        customNotificationDismiss = customNotificationView.findViewById(R.id.btn_notification_dismiss)

        // Configura el botón de cierre (la 'X')
        customNotificationDismiss.setOnClickListener {
            hideCustomNotification()
        }

        // 2. Inicializamos el RecyclerView
        searchResultsRecyclerView = findViewById(R.id.search_results_recyclerview)
        searchResultsRecyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
        searchResultsRecyclerView.adapter = searchAdapter

        sharedViewModel.nearbyStops.observe(this) { nearbyStops ->
            if (selectedRouteId == null) {
                showParaderosOnMap(nearbyStops ?: emptyList())
            }
        }

        val bottomSheetContent = findViewById<View>(R.id.bottom_sheet_content)

        supportFragmentManager.addOnBackStackChangedListener {
            if (supportFragmentManager.backStackEntryCount == 0) {
                bottomSheetContent.visibility = View.VISIBLE
                sharedViewModel.setNearbyCalculationCenter(null)
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
                        loc?.let { findAndShowStopsAroundPoint(it.latitude, it.longitude) }
                            ?: findAndShowStopsAroundPoint(-36.606, -72.102)
                    }
                } else {
                    findAndShowStopsAroundPoint(-36.606, -72.102)
                }
            } else {
                bottomSheetContent.visibility = View.GONE
            }
        }

        sharedViewModel.selectedStopId.observe(this) { stopId ->
            currentSelectedStopId = stopId
            if (selectedRouteId != null && selectedDirectionId != null) {
                val paraderosDeRuta = GtfsDataManager.getStopsForRoute(selectedRouteId!!, selectedDirectionId!!)
                showParaderosOnMap(paraderosDeRuta)
            } else {
                sharedViewModel.nearbyStops.value?.let { showParaderosOnMap(it) }
            }
        }

        // --- LÓGICA DEL MENÚ DE OPCIONES --- AÑADIDO Y MODIFICADO ---
        val optionsMenuButton = findViewById<ImageView>(R.id.optionsMenuButton)
        optionsMenuButton.setOnClickListener { view ->
            val popup = PopupMenu(this, view)
            popup.menuInflater.inflate(R.menu.main_options_menu, popup.menu)

            // --- INICIO DE LA LÓGICA DINÁMICA ---
            val currentUser = auth.currentUser
            val menu = popup.menu

            if (currentUser == null) {
                // Es un INVITADO (auth.currentUser es null)
                menu.findItem(R.id.menu_perfil).isVisible = false
                menu.findItem(R.id.menu_favoritos).isVisible = false
                menu.findItem(R.id.menu_sugerencias).isVisible = false
            } else {
                // Es un USUARIO REGISTRADO
                menu.findItem(R.id.menu_perfil).isVisible = true
                menu.findItem(R.id.menu_favoritos).isVisible = true
                menu.findItem(R.id.menu_sugerencias).isVisible = true
            }
            // "Volver a iniciar sesión" (menu_logout) es visible para ambos
            // --- FIN DE LA LÓGICA DINÁMICA ---


            popup.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.menu_perfil -> {
                        // --- INICIO CAMBIO ---
                        // Toast.makeText(this, "Ir a Perfil (pendiente)", Toast.LENGTH_SHORT).show()
                        startActivity(Intent(this, ProfileActivity::class.java))
                        // --- FIN CAMBIO ---
                        true
                    }
                    R.id.menu_favoritos -> {
                        // ### INICIO DE LA MODIFICACIÓN ###
                        // Se reemplaza el Toast por el Intent
                        startActivity(Intent(this, FavoritosActivity::class.java))
                        // ### FIN DE LA MODIFICACIÓN ###
                        true
                    }
                    R.id.menu_sugerencias -> {
                        Toast.makeText(this, "Ir a Sugerencias (pendiente)", Toast.LENGTH_SHORT).show()
                        true
                    }
                    R.id.menu_logout -> {
                        cerrarSesion() // Esta función ya existe
                        true
                    }
                    else -> false
                }
            }

            popup.show()
        }
        // --- FIN LÓGICA DEL MENÚ ---

    } // --- FIN DE onCreate ---


    private fun handleBusClick(feature: Feature) {
        val routeId = feature.getStringProperty("routeId")
        val directionId = feature.getNumberProperty("directionId")?.toInt()
        val licensePlate = feature.getStringProperty("licensePlate")
        val geometry = feature.geometry() // Obtener la geometría del bus

        if (routeId == null || directionId == null || geometry !is Point) {
            Log.w("HandleBusClick", "Bus click con datos incompletos.")
            return
        }

        val route = GtfsDataManager.routes[routeId]
        if (route == null) {
            Log.w("HandleBusClick", "No se encontró la ruta $routeId.")
            return
        }

        // 1. Dibuja la ruta (Esto limpia el globo anterior)
        drawRoute(route, directionId)

        // 2. Muestra el globo de información (Marker con InfoWindow)

        val busLocation = LatLng(geometry.latitude(), geometry.longitude())

        // --- ### INICIO DE LA MODIFICACIÓN ### ---
        // Preparamos los textos para el Adapter
        val directionStr = if (directionId == 0) "Ida" else "Vuelta"
        val infoTitle = "Línea ${route.shortName} ($directionStr)"
        val infoSnippet = if (licensePlate.isNullOrBlank()) "Patente no disponible" else licensePlate
        // --- ### FIN DE LA MODIFICACIÓN ### ---


        // Crear un icono 1x1 transparente para que el marcador sea invisible
        val iconFactory = IconFactory.getInstance(this@MainActivity)
        val transparentBitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        val transparentIcon = iconFactory.fromBitmap(transparentBitmap)

        val markerOptions = MarkerOptions()
            .position(busLocation)
            .title(infoTitle)
            .snippet(infoSnippet)// El título ahora incluye (Ida) o (Vuelta)
            .icon(transparentIcon) // Usar el icono invisible

        currentInfoMarker = map.addMarker(markerOptions)
        map.selectMarker(currentInfoMarker!!) // Esto muestra el globo (info window)
    }

    private fun showCustomNotification(message: String) {
        customNotificationMessage.text = message
        customNotificationView.visibility = View.VISIBLE
    }

    /**
     * Oculta la vista de notificación personalizada.
     */
    private fun hideCustomNotification() {
        customNotificationView.visibility = View.GONE
    }

    /**
     * Realiza la búsqueda en todos los datos y actualiza el adaptador.
     */
    private fun performSearch(query: String) {
        // 1. Buscamos en Paraderos (SIN CAMBIOS)
        val paraderos = GtfsDataManager.stops.values
            .filter { it.name.lowercase().contains(query) || it.stopId.equals(query, ignoreCase = true) }
            .map { SearchResult(SearchResultType.PARADERO, it.name, "Paradero ${it.stopId}", it) }


        // --- INICIO DE LA MODIFICACIÓN SIMPLE ---

        // 2. Buscamos en Rutas (MODIFICADO)

        val queryLower = query.lowercase()
        var queryRutas = query // Por defecto, buscamos el texto tal cual

        // CASO 1: El usuario escribió "linea" o "línea" (y nada más)
        if (queryLower == "linea" || queryLower == "línea") {
            // Mostramos todas las rutas. La forma más simple es buscar un string vacío.
            queryRutas = ""
        }
        // CASO 2: El usuario escribió "linea 13" o "línea 13" (con espacio)
        else if (queryLower.startsWith("linea ")) {
            queryRutas = query.substring(6).trim() // 6 = "linea ".length
        } else if (queryLower.startsWith("línea ")) {
            queryRutas = query.substring(6).trim() // 6 = "línea ".length
        }

        val rutas = GtfsDataManager.routes.values
            .filter {
                // Filtro 1: El texto original está en el nombre corto o largo
                // (Ej: si un longName fuera "Linea 13 Centro", lo encontraría aquí)
                it.shortName.lowercase().contains(query) || it.longName.lowercase().contains(query) ||

                        // Filtro 2: Si modificamos la consulta (queryRutas != query)
                        // (Ej: query="línea", queryRutas="" -> MUESTRA TODAS)
                        // (Ej: query="línea 13", queryRutas="13" -> MUESTRA LA 13)
                        (queryRutas != query && (it.shortName.lowercase().contains(queryRutas) || it.longName.lowercase().contains(queryRutas)))
            }
            .map { SearchResult(SearchResultType.RUTA, it.shortName, it.longName, it) }

        // --- FIN DE LA MODIFICACIÓN SIMPLE ---


        // 3. Buscamos en Puntos de Turismo (SIN CAMBIOS)
        val turismo = DatosTurismo.puntosTuristicos
            .filter { it.nombre.lowercase().contains(query) }
            .map { SearchResult(SearchResultType.TURISMO, it.nombre, it.direccion, it) }

        // 4. Combinamos todas las listas y actualizamos el adapter (SIN CAMBIOS)
        val results = (paraderos + rutas + turismo).sortedBy { it.title }

        if (results.isNotEmpty()) {
            searchAdapter.updateResults(results)
            searchResultsRecyclerView.visibility = View.VISIBLE
        } else {
            hideSearchResults()
        }
    }

    /**
     * Se llama cuando el usuario hace click en un resultado de la lista.
     */
    private fun onSearchResultClicked(result: SearchResult) {
        //Toast.makeText(this, "Mostrando: ${result.title}", Toast.LENGTH_SHORT).show()
        showCustomNotification("Mostrando: ${result.title}")

        when (result.type) {
            SearchResultType.PARADERO -> {
                val paradero = result.originalObject as GtfsStop
                handleParaderoClick(paradero.stopId)
            }
            SearchResultType.TURISMO -> {
                val punto = result.originalObject as PuntoTuristico
                showTurismoDetail(punto)
            }
            SearchResultType.RUTA -> {
                val ruta = result.originalObject as GtfsRoute
                // Asumimos dirección 0 por defecto al buscar
                drawRoute(ruta, 0)
                showRouteDetail(ruta.routeId, 0)
            }
        }



        // Ocultamos todo
        searchView.setQuery("", false) // 1. Limpia el texto de la barra
        searchView.clearFocus()       // 2. Oculta el teclado
        hideSearchResults()           // 3. Oculta la lista de resultados
    }

    /**
     * Oculta el RecyclerView de resultados.
     */
    private fun hideSearchResults() {
        searchResultsRecyclerView.visibility = View.GONE
        searchAdapter.updateResults(emptyList()) // Limpiamos la lista
    }

    private fun setupSearchListener() {
        searchView.setOnQueryTextListener(object : androidx.appcompat.widget.SearchView.OnQueryTextListener {

            // Esta ya no es la principal, pero la mantenemos por si el usuario presiona Enter
            override fun onQueryTextSubmit(query: String?): Boolean {
                // Simplemente oculta el teclado
                searchView.clearFocus()
                return true
            }

            // ¡ESTA ES LA NUEVA LÓGICA IMPORTANTE!
            override fun onQueryTextChange(newText: String?): Boolean {
                val query = newText?.trim()

                // Si la búsqueda está vacía o es muy corta, ocultamos la lista
                if (query.isNullOrBlank()) {
                    hideSearchResults()
                } else {
                    // Si hay texto, realizamos la búsqueda
                    performSearch(query.lowercase())
                }
                return true
            }
        })

        // Añadimos un listener para cuando el usuario presiona la 'X' de limpiar
        searchView.setOnCloseListener {
            hideSearchResults()
            false // Devuelve false para que el SearchView maneje el borrado del texto
        }
    }

    // --- setupTabs (sin cambios) ---
    private fun setupTabs() {
        tabLayout = findViewById(R.id.tab_layout)
        viewPager = findViewById(R.id.view_pager)
        pagerAdapter = ViewPagerAdapter(this)
        viewPager.adapter = pagerAdapter

        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "Rutas cerca"
                1 -> "Turismo"
                2 -> "Rutas"
                else -> null
            }
        }.attach()
        viewPager.isUserInputEnabled = false
    }

    // --- setupBottomSheetCallback (CON LÓGICA DE PADDING) ---
    private fun setupBottomSheetCallback() {
        if (!::bottomSheetBehavior.isInitialized) {
            val bottomSheet: FrameLayout = findViewById(R.id.bottom_sheet)
            bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet)
        }

        bottomSheetBehavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                if (!::map.isInitialized) return
                var targetPadding = 0
                when (newState) {
                    BottomSheetBehavior.STATE_COLLAPSED -> {
                        targetPadding = peekHeightInPixels
                    }
                    BottomSheetBehavior.STATE_EXPANDED -> {
                        val screenHeight = resources.displayMetrics.heightPixels
                        targetPadding = screenHeight - bottomSheet.top
                    }
                    else -> return
                }
                map.setPadding(0, 0, 0, targetPadding)
                val currentTarget = map.cameraPosition.target
                currentTarget?.let { target ->
                    map.easeCamera(CameraUpdateFactory.newLatLng(target), 250)
                }
            }

            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                if (!::map.isInitialized) return
                val screenHeight = resources.displayMetrics.heightPixels
                val currentPadding = screenHeight - bottomSheet.top
                if (currentPadding > 0) {
                    map.setPadding(0, 0, 0, currentPadding)
                } else {
                    map.setPadding(0, 0, 0, 0)
                }
            }
        })
    }

    // --- onMapReady (MODIFICADO para usar onStyleLoaded) ---
    override fun onMapReady(mapLibreMap: MapLibreMap) {
        this.map = mapLibreMap

        // ### INICIO CÓDIGO A AÑADIR ###
        // Calcula un margen superior de 64dp (para que la brújula quede BAJO la barra)
        val topMarginPx = (70 * resources.displayMetrics.density).toInt()
        // Calcula un margen derecho de 16dp (estándar)
        val rightMarginPx = (16 * resources.displayMetrics.density).toInt()

        // Mueve la brújula para que no sea tapada
        map.uiSettings.setCompassMargins(0, topMarginPx, rightMarginPx, 0)
        // ### FIN CÓDIGO A AÑADIR ###

        // Carga el estilo CLARO (styleLIGHT) por defecto
        map.setStyle(styleLIGHT, Style.OnStyleLoaded { style ->
            onStyleLoaded(style) // Llama a la nueva función
        })

        // --- ### INICIO DE LA MODIFICACIÓN ### ---
        // Asignamos nuestro adapter personalizado para los globos de info
        map.setInfoWindowAdapter(CustomInfoWindowAdapter())
        // --- ### FIN DE LA MODIFICACIÓN ### ---

        // El click listener para los paraderos se queda aquí
        map.addOnMapClickListener { point ->
            val screenPoint = map.projection.toScreenLocation(point)

            // 1. Priorizar click en el bus
            val busFeatures = map.queryRenderedFeatures(screenPoint, "bus-layer")
            if (busFeatures.isNotEmpty()) {
                busFeatures[0]?.let {
                    handleBusClick(it) // Llama a la nueva función
                }
                return@addOnMapClickListener true // Click manejado
            }

            // 2. Si no, click en el paradero
            val paraderoFeatures = map.queryRenderedFeatures(screenPoint, "paradero-layer")
            if (paraderoFeatures.isNotEmpty()) {
                val stopId = paraderoFeatures[0].getStringProperty("stop_id")
                if (stopId != null) {
                    handleParaderoClick(stopId) // Esta función ahora limpiará el globo
                }
                return@addOnMapClickListener true // Click manejado
            }

            // --- INICIO DE MODIFICACIÓN ---
            // 3. Click en el mapa (ni bus, ni paradero)
            clearInfoMarker() // Limpia el globo de info del bus
            // --- FIN DE MODIFICACIÓN ---

            true
        }
    }

    // --- NUEVA FUNCIÓN onStyleLoaded ---
    /**
     * Carga todas las capas, íconos y marcadores en el estilo actual del mapa.
     * Se llama cada vez que se cambia el estilo (2D o 3D).
     */
    private fun onStyleLoaded(style: Style) {
        this.mapStyle = style
        Log.d("MainActivity", "Estilo cargado: ${style.uri}")

        // Recarga todos los elementos visuales
        loadMapIcons(style)
        setupParaderoLayer(style)
        setupBusLayer(style)
        addTurismoMarkers() // Esto vuelve a añadir los marcadores de turismo

        // Vuelve a activar la ubicación en el nuevo estilo
        enableLocation(style)
        startBusDataFetching()


        // Reinicia el dibujado de rutas si había una seleccionada
        if (selectedRouteId != null && selectedDirectionId != null) {
            val route = GtfsDataManager.routes[selectedRouteId]
            if (route != null) {
                drawRoute(route, selectedDirectionId!!)
            }
        } else {
            // Asegura que los paraderos cercanos se muestren
            sharedViewModel.nearbyStops.value?.let { showParaderosOnMap(it) }
        }

        // Vuelve a aplicar el padding del BottomSheet
        if (::bottomSheetBehavior.isInitialized && ::map.isInitialized) {
            val screenHeight = resources.displayMetrics.heightPixels
            val currentPadding = screenHeight - findViewById<View>(R.id.bottom_sheet).top
            if (currentPadding > 0) {
                map.setPadding(0, 0, 0, currentPadding)
            } else {
                map.setPadding(0, 0, 0, peekHeightInPixels) // Failsafe
            }
        }

        isStyleLoading = false

        // Vuelve a iniciar el "fetch" de buses
        // (No es necesario llamarlo explícitamente, el loop en startBusDataFetching se encarga)
    }

    // --- NUEVA FUNCIÓN toggleMapStyle ---
    /**
     * Cambia entre el estilo 2D y 3D.
     */
    private fun toggleMapStyle() {
        if (!::map.isInitialized || isStyleLoading) return

        isStyleLoading = true

        // Incrementa el estado y vuelve a 0 si pasa de 2
        // (0 = Claro, 1 = Oscuro, 2 = Satélite)
        currentMapStyleState = (currentMapStyleState + 1) % 3

        when (currentMapStyleState) {

            0 -> { // ESTADO 0: Aplicar CLARO
                Log.d("MapStyle", "Cambiando a Modo Claro")
                map.setStyle(styleLIGHT, Style.OnStyleLoaded { style ->
                    onStyleLoaded(style) // Recarga todo
                })

            }

            1 -> { // ESTADO 1: Aplicar OSCURO
                Log.d("MapStyle", "Cambiando a Modo Oscuro")
                map.setStyle(styleDARK, Style.OnStyleLoaded { style ->
                    onStyleLoaded(style) // Recarga todo
                })
            }

            2 -> { // ESTADO 2: Aplicar SATÉLITE
                Log.d("MapStyle", "Cambiando a Modo Satélite")
                map.setStyle(styleSATELLITE_HYBRID, Style.OnStyleLoaded { style ->
                    onStyleLoaded(style) // Recarga todo
                })
            }
        }
    }

    // --- loadMapIcons (Corregido) ---
    private fun loadMapIcons(style: Style) {
        try {
            style.addImage("paradero-icon", BitmapFactory.decodeResource(resources, R.drawable.ic_paradero))
            style.addImage("paradero-icon-selected", BitmapFactory.decodeResource(resources, R.drawable.ic_paradero_selected))

            val originalBusBitmap = BitmapFactory.decodeResource(resources, R.drawable.ic_bus)
            val scaledBusBitmap = Bitmap.createScaledBitmap(originalBusBitmap, 60, 60, false)
            style.addImage("bus-icon-default", scaledBusBitmap)

            val routeIcons = mapOf(
                "467" to R.drawable.linea_2, "468" to R.drawable.linea_3, "469" to R.drawable.linea_4,
                "470" to R.drawable.linea_6, "471" to R.drawable.linea_7, "472" to R.drawable.linea_8,
                "954" to R.drawable.linea_7, "478" to R.drawable.linea_14, "477" to R.drawable.linea_14,
                "473" to R.drawable.linea_10, "476" to R.drawable.linea_13, "474" to R.drawable.linea_13,
                "475" to R.drawable.linea_13, "466" to R.drawable.linea_1
            )
            routeIcons.forEach { (routeId, resourceId) ->
                try {
                    val bmp = BitmapFactory.decodeResource(resources, resourceId)
                    val scaledBmp = Bitmap.createScaledBitmap(bmp, 60, 60, false)
                    style.addImage("bus-icon-$routeId", scaledBmp)
                } catch (e: Exception) { Log.e("LoadIcons", "Error icono ruta $routeId: ${e.message}") }
            }
            Log.d("LoadIcons", "Íconos cargados.")
        } catch (e: Exception) {
            Log.e("LoadIcons", "Error cargando íconos base", e)
            if (style.getImage("bus-icon-default") == null) {
                val defaultBmp = Bitmap.createBitmap(60, 60, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(defaultBmp)
                val paint = Paint().apply { color = Color.BLUE }
                canvas.drawCircle(30f, 30f, 25f, paint)
                style.addImage("bus-icon-default", defaultBmp)
                Log.w("LoadIcons", "Usando ícono de bus por defecto generado.")
            }
        }
    }

    // --- handleParaderoClick (Corregido) ---
    private fun handleParaderoClick(stopId: String) {
        clearInfoMarker()
        sharedViewModel.selectStop(stopId)
        GtfsDataManager.stops[stopId]?.let { stop ->
            centerMapOnPoint(stop.location.latitude, stop.location.longitude)
        }
        val routesForStop = GtfsDataManager.getRoutesForStop(stopId)
        if (routesForStop.isNotEmpty()) {
            //Toast.makeText(this, "Mostrando rutas para el paradero $stopId", Toast.LENGTH_SHORT).show()
            showCustomNotification("Mostrando rutas para el paradero $stopId")
            if (supportFragmentManager.backStackEntryCount > 0) {
                supportFragmentManager.popBackStack()
                findViewById<View>(R.id.bottom_sheet_content).visibility = View.VISIBLE
            }
            sharedViewModel.setRouteFilter(routesForStop)
            viewPager.setCurrentItem(2, true)
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
        } else {
            // Toast.makeText(this, "No se encontraron rutas para el paradero $stopId", Toast.LENGTH_SHORT).show()
            showCustomNotification("No se encontraron rutas para el paradero $stopId")
            sharedViewModel.setRouteFilter(emptyList())
        }
    }

    // --- showTurismoDetail (Corregido) ---
    override fun showTurismoDetail(punto: PuntoTuristico) {
        if (!::map.isInitialized) {
            doShowTurismoDetail(punto)
            return
        }
        map.animateCamera(
            CameraUpdateFactory.newLatLngZoom(LatLng(punto.latitud, punto.longitud), 16.0),
            1500,
            object : CancelableCallback {
                override fun onCancel() { doShowTurismoDetail(punto) }

                override fun onFinish() {
                    doShowTurismoDetail(punto)
                    val turismoLocation = LatLng(punto.latitud, punto.longitud)
                    sharedViewModel.setNearbyCalculationCenter(turismoLocation)
                    findAndShowStopsAroundPoint(turismoLocation.latitude, turismoLocation.longitude)
                    val loc = Location("").apply { latitude = punto.latitud; longitude = punto.longitud }
                    updateBusMarkers(loc)
                }
            }
        )
    }

    private fun doShowTurismoDetail(punto: PuntoTuristico) {
        clearInfoMarker()
        val fragment = DetalleTurismoFragment.newInstance(punto)
        findViewById<View>(R.id.bottom_sheet_content).visibility = View.GONE
        supportFragmentManager.beginTransaction()
            .replace(R.id.bottom_sheet, fragment)
            .addToBackStack("turismo_detail")
            .commitAllowingStateLoss()
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
    }

    // --- findAndShowStopsAroundPoint (Corregido) ---
    private fun findAndShowStopsAroundPoint(lat: Double, lon: Double) {
        lifecycleScope.launch(Dispatchers.Default) {
            val paraderosCercanos = GtfsDataManager.stops.values
                .map { stop ->
                    Pair(stop, distanceBetween(lat, lon, stop.location.latitude, stop.location.longitude))
                }
                .filter { it.second <= 500 }
                .sortedBy { it.second }
                .map { it.first }

            withContext(Dispatchers.Main) {
                sharedViewModel.setNearbyStops(paraderosCercanos)
            }
        }
    }

    fun showRouteDetail(routeId: String, directionId: Int) {
        val fragment = DetalleRutaFragment.newInstance(routeId, directionId)
        findViewById<View>(R.id.bottom_sheet_content).visibility = View.GONE
        supportFragmentManager.beginTransaction()
            .replace(R.id.bottom_sheet, fragment)
            .addToBackStack("ruta_detail")
            .commit()
    }

    // --- showRoutesForStop (sin cambios) ---
    override fun showRoutesForStop(stopId: String) {
        val routesForStop = GtfsDataManager.getRoutesForStop(stopId)
        sharedViewModel.setRouteFilter(routesForStop)
        viewPager.setCurrentItem(2, true)
        supportFragmentManager.popBackStack("turismo_detail", androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE)
        findViewById<View>(R.id.bottom_sheet_content).visibility = View.VISIBLE
    }

    // --- hideDetailFragment (sin cambios) ---
    override fun hideDetailFragment() {
        supportFragmentManager.popBackStack()
        findViewById<View>(R.id.bottom_sheet_content).visibility = View.VISIBLE
    }

    // --- centerMapOnPoint (sin cambios) ---
    override fun centerMapOnPoint(lat: Double, lon: Double) {
        if (::map.isInitialized) {
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(lat, lon), 16.0), 1500)
        }
    }

    // --- setupBusLayer (Corregido) ---
    // --- setupBusLayer (MODIFICADO para Modo Espejo) ---
    private fun setupBusLayer(style: Style) {

        // 1. Definimos los íconos (TU CÓDIGO ORIGINAL, SIN CAMBIOS)
        val routeIcons = mapOf(
            "467" to R.drawable.linea_2, "468" to R.drawable.linea_3, "469" to R.drawable.linea_4,
            "470" to R.drawable.linea_6, "471" to R.drawable.linea_7, "472" to R.drawable.linea_8,
            "954" to R.drawable.linea_7, "478" to R.drawable.linea_14, "477" to R.drawable.linea_14,
            "473" to R.drawable.linea_10, "476" to R.drawable.linea_13, "474" to R.drawable.linea_13,
            "475" to R.drawable.linea_13, "466" to R.drawable.linea_1,
        )

        // --- INICIO DE MODIFICACIÓN ---

        // 2. Definimos los íconos ESPEJO (NUEVO)
        // (Asegúrate de que estos archivos .png existan en res/drawable)
        val routeIconsEspejo = mapOf(
            "467" to R.drawable.linea_2_espejo, "468" to R.drawable.linea_3_espejo, "469" to R.drawable.linea_4_espejo,
            "470" to R.drawable.linea_6_espejo, "471" to R.drawable.linea_7_espejo, "472" to R.drawable.linea_8_espejo,
            "954" to R.drawable.linea_7_espejo, "478" to R.drawable.linea_14_espejo, "477" to R.drawable.linea_14_espejo,
            "473" to R.drawable.linea_10_espejo, "476" to R.drawable.linea_13_espejo, "474" to R.drawable.linea_13_espejo,
            "475" to R.drawable.linea_13_espejo, "466" to R.drawable.linea_1_espejo
        )

        // 3. Cargar TODOS los íconos (Normales y Espejo)
        try {
            // Carga el bus genérico (default) y su espejo
            try {
                style.addImage("bus-icon-default-normal", BitmapFactory.decodeResource(resources, R.drawable.ic_bus))
            } catch (e: Exception) { Log.e("SetupBusLayer", "Error cargando ic_bus.png", e) }

            try {
                style.addImage("bus-icon-default-espejo", BitmapFactory.decodeResource(resources, R.drawable.ic_bus_espejo))
            } catch (e: Exception) { Log.e("SetupBusLayer", "Error cargando ic_bus_espejo.png", e) }

            // Carga los íconos de ruta (linea_X) y sus espejos
            routeIcons.forEach { (routeId, resourceId) ->
                try {
                    style.addImage("bus-icon-$routeId-normal", BitmapFactory.decodeResource(resources, resourceId))
                } catch (e: Exception) { Log.e("SetupBusLayer", "Error al cargar icono normal $routeId: ${e.message}") }
            }
            routeIconsEspejo.forEach { (routeId, resourceId) ->
                try {
                    style.addImage("bus-icon-$routeId-espejo", BitmapFactory.decodeResource(resources, resourceId))
                } catch (e: Exception) { Log.e("SetupBusLayer", "Error al cargar icono espejo $routeId: ${e.message}") }
            }

        } catch (e: Exception) { Log.e("SetupBusLayer", "Error al cargar los íconos: ${e.message}") }

        // 4. Añadir la fuente (TU CÓDIGO ORIGINAL, SIN CAMBIOS)
        style.addSource(GeoJsonSource("bus-source"))

        // 5. Construir la lógica de 'cases' para el switchCase
        val cases = mutableListOf<Expression>()

        routeIcons.keys.forEach { routeId ->
            cases.add(eq(get("routeId"), literal(routeId))) // Condición: ej. routeId == "467"
            cases.add(
                // Valor si la condición es verdadera:
                switchCase(
                    eq(get("directionId"), 0), literal("bus-icon-$routeId-espejo"), // Si Ida (0) -> linea_2_espejo
                    eq(get("directionId"), 1), literal("bus-icon-$routeId-normal"), // Si Vuelta (1) -> linea_2
                    literal("bus-icon-$routeId-normal") // Fallback
                )
            )
        }

        // 6. Añadir el 'default' al final para los buses genéricos
        cases.add(
            switchCase(
                eq(get("directionId"), 0), literal("bus-icon-default-espejo"), // Ida -> ic_bus_espejo
                eq(get("directionId"), 1), literal("bus-icon-default-normal"), // Vuelta -> ic_bus
                literal("bus-icon-default-normal") // Fallback
            )
        )
        // --- FIN DE MODIFICACIÓN ---


        val busLayer = SymbolLayer("bus-layer", "bus-source").apply {
            withProperties(
                // 7. Aplicar la lógica de 'cases' al 'iconImage'
                PropertyFactory.iconImage(switchCase(*cases.toTypedArray())),

                // --- El resto de tus propiedades NO CAMBIAN ---
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.iconIgnorePlacement(true),
                PropertyFactory.iconSize(0.5f),
                PropertyFactory.iconOpacity(interpolate(linear(), zoom(), stop(11.99f, 0f), stop(12f, 1f))),

                // Tu lógica de "I" / "V" (sin cambios)
            )
        }
        style.addLayer(busLayer)
    }


    // --- setupParaderoLayer (CORREGIDO: Vuelve a los valores originales) ---
    private fun setupParaderoLayer(style: Style) {
        if (style.getSource("paradero-source") == null) {
            style.addSource(GeoJsonSource("paradero-source"))
        }
        if (style.getLayer("paradero-layer") == null) {
            val paraderoLayer = SymbolLayer("paradero-layer", "paradero-source").apply {
                withProperties(
                    PropertyFactory.iconImage(
                        switchCase(
                            eq(get("selected"), literal(true)), literal("paradero-icon-selected"),
                            literal("paradero-icon")
                        )
                    ),
                    PropertyFactory.iconAllowOverlap(true),
                    PropertyFactory.iconIgnorePlacement(false),
                    PropertyFactory.iconSize(
                        switchCase(
                            eq(get("selected"), literal(true)), literal(0.07f),
                            literal(0.05f)
                        )
                    ),
                    PropertyFactory.iconOpacity(interpolate(linear(), zoom(), stop(12.99f, 0f), stop(13f, 1f))),
                    PropertyFactory.textField(get("stop_id")),
                    PropertyFactory.textSize(10f),
                    PropertyFactory.textColor(Color.BLACK),
                    PropertyFactory.textOffset(arrayOf(0f, 1.5f)),
                    PropertyFactory.textOpacity(interpolate(linear(), zoom(), stop(15.49f, 0f), stop(15.5f, 1f)))
                )
            }
            style.addLayer(paraderoLayer)
        }
    }

    private fun requestFreshLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            centerMapOnPoint(-36.606, -72.102)
            findAndShowStopsAroundPoint(-36.606, -72.102)
            updateBusMarkers(null)
            return
        }
        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, CancellationTokenSource().token)
            .addOnSuccessListener { location: Location? ->
                if (location != null) {
                    map.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(location.latitude, location.longitude), 15.0))
                    updateBusMarkers(location)
                    findAndShowStopsAroundPoint(location.latitude, location.longitude)
                } else {
                    centerMapOnPoint(-36.606, -72.102)
                    findAndShowStopsAroundPoint(-36.606, -72.102)
                    updateBusMarkers(null)
                }
            }
            .addOnFailureListener {
                centerMapOnPoint(-36.606, -72.102)
                findAndShowStopsAroundPoint(-36.606, -72.102)
                updateBusMarkers(null)
            }
    }

    private fun showParaderosOnMap(paraderos: List<GtfsStop>) {
        if (!::map.isInitialized || mapStyle?.getSource("paradero-source") == null) {
            return
        }
        lifecycleScope.launch(Dispatchers.Default) {
            val paraderoFeatures = paraderos.map { stop ->
                val point = Point.fromLngLat(stop.location.longitude, stop.location.latitude)
                val feature = Feature.fromGeometry(point)
                feature.addStringProperty("stop_id", stop.stopId)
                feature.addBooleanProperty("selected", stop.stopId == currentSelectedStopId)
                feature
            }
            val featureCollection = FeatureCollection.fromFeatures(paraderoFeatures)
            withContext(Dispatchers.Main) {
                mapStyle?.getSourceAs<GeoJsonSource>("paradero-source")?.setGeoJson(featureCollection)
            }
        }
    }

    override fun drawRoute(route: GtfsRoute, directionId: Int) {
        clearInfoMarker()
        clearDrawnElements()
        selectedRouteId = route.routeId
        selectedDirectionId = directionId
        val style = mapStyle ?: return

        val trip = GtfsDataManager.trips.values.find { it.routeId == route.routeId && it.directionId == directionId }
        val shapeId = trip?.shapeId

        if (shapeId == null) {
            Toast.makeText(this, "Trazado no disponible.", Toast.LENGTH_SHORT).show()
            return
        }
        val routePoints = GtfsDataManager.shapes[shapeId] ?: return

        val geoJsonString = """{"type": "Feature", "geometry": {"type": "LineString", "coordinates": [${routePoints.joinToString { "[${it.longitude},${it.latitude}]" }}]}}"""

        val sourceId = "route-source-${route.routeId}-$directionId"
        // Asegurarse de que el source no exista ya (al cambiar de estilo)
        if (style.getSource(sourceId) == null) {
            style.addSource(GeoJsonSource(sourceId, geoJsonString))
        }
        currentRouteSourceId = sourceId

        val layerId = "route-layer-${route.routeId}-$directionId"
        // Asegurarse de que la capa no exista ya (al cambiar de estilo)
        if (style.getLayer(layerId) == null) {
            val routeLayer = LineLayer(layerId, sourceId).apply {
                withProperties(
                    PropertyFactory.lineColor(Color.parseColor(if (route.color.startsWith("#")) route.color else "#${route.color}")),
                    PropertyFactory.lineWidth(5f),
                    PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                    PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
                )
            }

            val belowLayer = style.layers.find { it.id == "bus-layer" || it.id == "paradero-layer" }?.id
            if (belowLayer != null) { style.addLayerBelow(routeLayer, belowLayer)} else { style.addLayer(routeLayer)}
        }

        currentRouteLayerId = layerId
        val paraderosDeRuta = GtfsDataManager.getStopsForRoute(route.routeId, directionId)
        showParaderosOnMap(paraderosDeRuta)

        val bounds = LatLngBounds.Builder().includes(routePoints).build()

        map.easeCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100), 1500)
        updateBusMarkers()
    }

    override fun clearRoutes(recenterToUser: Boolean) {
        clearInfoMarker()
        clearDrawnElements()
        selectedRouteId = null
        selectedDirectionId = null
        sharedViewModel.selectStop(null)
        sharedViewModel.setNearbyCalculationCenter(null)

        updateBusMarkers()
        Toast.makeText(this, "Mostrando buses cercanos", Toast.LENGTH_SHORT).show()

        if (recenterToUser) {
            requestFreshLocation()
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                    location?.let {
                        findAndShowStopsAroundPoint(it.latitude, it.longitude)
                    }
                }
            }
        }
    }

    private fun clearDrawnElements() {
        mapStyle?.let { style ->
            currentRouteLayerId?.let { if (style.getLayer(it) != null) style.removeLayer(it) }
            currentRouteSourceId?.let { if (style.getSource(it) != null) style.removeSource(it) }
        }
        currentRouteLayerId = null
        currentRouteSourceId = null
        // No limpies los paraderos aquí, deja que onStyleLoaded o el observer se encarguen
        // showParaderosOnMap(emptyList())
    }

    private fun addTurismoMarkers() {
        // --- MODIFICACIÓN IMPORTANTE ---
        // Los marcadores de MapLibre (que usas para turismo)
        // se BORRAN automáticamente cuando cambias el estilo.
        // Esta función ahora se llamará desde 'onStyleLoaded'
        // para volver a ponerlos.

        val iconFactory = IconFactory.getInstance(this@MainActivity)
        val originalBitmap = BitmapFactory.decodeResource(resources, R.drawable.ic_turismo)
        val scaledBitmap = Bitmap.createScaledBitmap(originalBitmap, 80, 80, false)
        val icon = iconFactory.fromBitmap(scaledBitmap)

        turismoMarkers.forEach { it.remove() } // Limpia la lista interna
        turismoMarkers.clear()

        DatosTurismo.puntosTuristicos.forEach { punto ->
            val marker = map.addMarker( // 'map.addMarker' los añade al estilo actual
                MarkerOptions().position(LatLng(punto.latitud, punto.longitud))
                    .title(punto.nombre)
                    .snippet(punto.id.toString())
                    .icon(icon)
            )
            turismoMarkers.add(marker) // Guarda la referencia
        }

        map.setOnMarkerClickListener { marker ->
            // Asegurarse de que el click es de un marcador de turismo
            if (turismoMarkers.contains(marker)) {
                marker.snippet?.toIntOrNull()?.let { puntoId ->
                    DatosTurismo.puntosTuristicos.find { it.id == puntoId }?.let { punto ->
                        showTurismoDetail(punto)
                        return@setOnMarkerClickListener true
                    }
                }
            }
            // Devuelve 'false' para que el click en paraderos (que no son Markers) funcione
            return@setOnMarkerClickListener false
        }
    }


    private fun startBusDataFetching() {
        lifecycleScope.launch {
            while (isActive) {
                // --- MODIFICACIÓN ---
                // Solo busca buses si el mapa y el estilo están listos
                if (::map.isInitialized && mapStyle != null && mapStyle!!.isFullyLoaded) {
                    val centerLocation = if(::map.isInitialized) {
                        val lastKnownLoc = try { map.locationComponent.lastKnownLocation } catch (e: Exception) { null }
                        if (lastKnownLoc != null) {
                            lastKnownLoc
                        } else {
                            map.cameraPosition.target?.let { target ->
                                Location("mapCenter").apply { latitude = target.latitude; longitude = target.longitude }
                            }
                        }
                    } else {
                        null
                    }
                    fetchBusData(centerLocation)
                }
                delay(10000)
            }
        }
    }

    private fun fetchBusData(centerLocation: Location? = null) {
        lifecycleScope.launch {
            try {
                val response = apiService.getVehiclePositions("chillan")
                if (response.isSuccessful) {
                    response.body()?.let {
                        lastFeedMessage = it
                        sharedViewModel.setFeedMessage(it)
                    }
                    updateBusMarkers(centerLocation)
                } else {
                    Log.e("API_ERROR", "Error en la respuesta: ${response.code()}")
                }
            } catch (e: IOException) {
                Log.e("API_ERROR", "Fallo en la conexión", e)
            } catch (e: Exception) {
                Log.e("API_ERROR", "Fallo general en fetchBusData", e)
            }
        }
    }

    private fun distanceBetween(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val results = FloatArray(1)
        Location.distanceBetween(lat1, lon1, lat2, lon2, results)
        return results[0]
    }

    private fun updateBusMarkers(centerLocation: Location? = null) {
        val feed = lastFeedMessage ?: return

        // --- MODIFICACIÓN ---
        // Asegurarse de que el estilo esté listo antes de intentar actualizarlo
        if (mapStyle == null || !mapStyle!!.isFullyLoaded) {
            return
        }

        val location = centerLocation ?: (if(::map.isInitialized) try { map.locationComponent.lastKnownLocation } catch (e: Exception) { null } else null)

        lifecycleScope.launch(Dispatchers.Default) {
            val busFeatures = mutableListOf<Feature>()

            for (entity in feed.entityList) {
                if (entity.hasVehicle() && entity.vehicle.hasTrip() && entity.vehicle.hasPosition()) {
                    val vehicle = entity.vehicle
                    val trip = vehicle.trip
                    val position = vehicle.position
                    var shouldShow = false

                    if (selectedRouteId != null) {
                        if (selectedRouteId == trip.routeId && selectedDirectionId == trip.directionId) {
                            shouldShow = true
                        }
                    } else {
                        if (location != null) {
                            val distance = distanceBetween(
                                location.latitude, location.longitude,
                                position.latitude.toDouble(), position.longitude.toDouble()
                            )
                            if (distance <= 1000) { // Radio de 1km
                                shouldShow = true
                            }
                        } else {
                            shouldShow = false
                        }
                    }

                    if (shouldShow) {
                        val point = Point.fromLngLat(position.longitude.toDouble(), position.latitude.toDouble())
                        val feature = Feature.fromGeometry(point)

                        // --- INICIO DE MODIFICACIÓN ---
                        // Añadimos más datos al "feature" del bus
                        feature.addStringProperty("routeId", trip.routeId)
                        feature.addNumberProperty("directionId", trip.directionId) // ID de Dirección
                        feature.addNumberProperty("bearing", position.bearing)

                        // Añadir info del vehículo (con chequeos de seguridad)
                        if (vehicle.hasVehicle()) {
                            val vehicleDesc = vehicle.vehicle
                            if (vehicleDesc.hasLicensePlate()) {
                                feature.addStringProperty("licensePlate", vehicleDesc.licensePlate) // Patente
                            }
                            if (vehicleDesc.hasId()) {
                                feature.addStringProperty("vehicleId", vehicleDesc.id) // ID del vehículo
                            }
                        }
                        // --- FIN DE MODIFICACIÓN ---

                        busFeatures.add(feature)
                    }
                }
            }
            val featureCollection = FeatureCollection.fromFeatures(busFeatures)
            withContext(Dispatchers.Main) {
                // Chequeo final por si el estilo cambió justo ahora
                if (mapStyle != null && mapStyle!!.isFullyLoaded) {
                    mapStyle?.getSourceAs<GeoJsonSource>("bus-source")?.setGeoJson(featureCollection)
                }
            }
        }
    }

    @SuppressWarnings("MissingPermission")
    private fun enableLocation(style: Style) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            showUserLocation()
        } else {
            requestLocationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    @SuppressWarnings("MissingPermission")
    private fun showUserLocation() {
        if (!::map.isInitialized || map.style == null || !map.style!!.isFullyLoaded) {
            Log.w("ShowUserLocation", "Mapa o estilo no listos.")
            if(::map.isInitialized && map.style != null && !map.style!!.isFullyLoaded) {
                lifecycleScope.launch { delay(500); showUserLocation() }
            }
            return
        }
        try {
            val locationComponent = map.locationComponent
            locationComponent.activateLocationComponent(
                LocationComponentActivationOptions.builder(this, map.style!!)
                    .useDefaultLocationEngine(true)
                    .build())
            locationComponent.isLocationComponentEnabled = true
            locationComponent.cameraMode = CameraMode.TRACKING
            locationComponent.renderMode = RenderMode.NORMAL
            requestFreshLocation()
        } catch (e: Exception) {
            Log.e("ShowUserLocation", "Error al activar LocationComponent", e)
        }
    }


    // ===== INICIO DE LAS 4 NUEVAS FUNCIONES PARA "CÓMO LLEGAR" =====

    /**
     * Función auxiliar: Busca TODOS los GtfsStop dentro de un radio.
     */
    private fun findAllNearbyStops(lat: Double, lon: Double, radiusInMeters: Float = 500f): List<GtfsStop> {
        return GtfsDataManager.stops.values
            .map { stop ->
                Pair(stop, distanceBetween(lat, lon, stop.location.latitude, stop.location.longitude))
            }
            .filter { it.second <= radiusInMeters }
            .sortedBy { it.second }
            .map { it.first }
    }

    /**
     * Función auxiliar: Encuentra el índice del punto del trazado (Shape) más cercano a un paradero (GtfsStop).
     */
    private fun findNearestShapePointIndex(shapePoints: List<LatLng>, stop: GtfsStop): Int {
        return shapePoints.indices.minByOrNull { index ->
            distanceBetween(stop.location.latitude, stop.location.longitude, shapePoints[index].latitude, shapePoints[index].longitude)
        } ?: -1
    }

    /**
     * Dibuja solo el segmento de la ruta desde el Paradero A hasta el Paradero B.
     */
    override fun drawRouteSegment(route: GtfsRoute, directionId: Int, stopA: GtfsStop, stopB: GtfsStop) {
        clearDrawnElements()
        selectedRouteId = route.routeId
        selectedDirectionId = directionId
        val style = mapStyle ?: return
        Log.d("DrawRouteSegment", "Dibujando segmento A->B para ${route.shortName}")

        val trip = GtfsDataManager.trips.values.find { it.routeId == route.routeId && it.directionId == directionId }
        val shapeId = trip?.shapeId
        val routePoints = GtfsDataManager.shapes[shapeId] // Esto es List<LatLng>

        if (routePoints == null || routePoints.isEmpty()) {
            Toast.makeText(this, "Trazado no disponible.", Toast.LENGTH_SHORT).show()
            return
        }

        val indexA = findNearestShapePointIndex(routePoints, stopA)
        val indexB = findNearestShapePointIndex(routePoints, stopB)

        if (indexA == -1 || indexB == -1 || indexA >= indexB) {
            Log.w("DrawRouteSegment", "Error al encontrar segmento (A:$indexA, B:$indexB). Dibujando ruta completa.")
            drawRoute(route, directionId) // Fallback a ruta completa
            return
        }

        val slicedRoutePoints = routePoints.subList(indexA, indexB + 1)

        val coordinates = slicedRoutePoints.joinToString(prefix = "[", postfix = "]") { "[${it.longitude},${it.latitude}]" }
        val geoJsonString = """{"type": "Feature", "geometry": {"type": "LineString", "coordinates": $coordinates}}"""

        val sourceId = "route-source-segment"
        // Asegurarse de que el source no exista ya (al cambiar de estilo)
        if (style.getSource(sourceId) == null) {
            style.addSource(GeoJsonSource(sourceId, geoJsonString))
        }
        currentRouteSourceId = sourceId

        val layerId = "route-layer-segment"
        // Asegurarse de que la capa no exista ya (al cambiar de estilo)
        if (style.getLayer(layerId) == null) {
            currentRouteLayerId = layerId
            val routeLayer = LineLayer(layerId, sourceId).apply {
                withProperties(
                    PropertyFactory.lineColor(Color.parseColor(if (route.color.startsWith("#")) route.color else "#${route.color}")),
                    PropertyFactory.lineWidth(6f),
                    PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                    PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                    PropertyFactory.lineOpacity(0.8f)
                )
            }
            val belowLayer = style.layers.find { it.id == "bus-layer" || it.id == "paradero-layer" }?.id
            if (belowLayer != null) { style.addLayerBelow(routeLayer, belowLayer) } else { style.addLayer(routeLayer) }
        }

        showParaderosOnMap(listOf(stopA, stopB))
        Log.d("DrawRouteSegment", "Mostrando paraderos A y B.")

        if (slicedRoutePoints.isNotEmpty()) {
            val bounds = LatLngBounds.Builder().includes(slicedRoutePoints).build()
            map.easeCamera(CameraUpdateFactory.newLatLngBounds(bounds, 150), 1000)
        }
    }

    /**
     * Implementación de la interfaz para el botón "Cómo Llegar" a PUNTO TURÍSTICO.
     */
    override fun onGetDirectionsClicked(punto: PuntoTuristico) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Se necesita permiso de ubicación para 'Cómo llegar'", Toast.LENGTH_LONG).show()
            requestLocationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            return
        }

        Log.d("Directions", "Buscando ruta para ${punto.nombre}")
        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, CancellationTokenSource().token)
            .addOnSuccessListener { location: Location? ->
                if (location == null) {
                    Toast.makeText(this, "No se pudo obtener tu ubicación actual", Toast.LENGTH_LONG).show()
                    return@addOnSuccessListener
                }

                val userLat = location.latitude
                val userLon = location.longitude
                val puntoLat = punto.latitud
                val puntoLon = punto.longitud

                // Búsqueda con radio flexible
                var stopsNearA: List<GtfsStop> = emptyList()
                var stopsNearB: List<GtfsStop> = emptyList()
                val searchRadii = listOf(500f, 1000f, 2000f) // 500m, 1km, 2km
                var radiusUsedA: Float? = null
                var radiusUsedB: Float? = null

                Log.d("Directions", "Iniciando búsqueda de paraderos...")

                for (radius in searchRadii) {
                    stopsNearA = findAllNearbyStops(userLat, userLon, radius)
                    if (stopsNearA.isNotEmpty()) {
                        radiusUsedA = radius
                        Log.d("Directions", "Paraderos (A) encontrados a ${radius}m")
                        break
                    }
                }

                for (radius in searchRadii) {
                    stopsNearB = findAllNearbyStops(puntoLat, puntoLon, radius)
                    if (stopsNearB.isNotEmpty()) {
                        radiusUsedB = radius
                        Log.d("Directions", "Paraderos (B) encontrados a ${radius}m")
                        break
                    }
                }

                if (stopsNearA.isEmpty()) {
                    Log.w("Directions", "No se encontraron paraderos (A) cerca del usuario (radio max ${searchRadii.last()}m)")
                    Toast.makeText(this, "No se encontraron paraderos cercanos a tu ubicación (radio max 2km)", Toast.LENGTH_LONG).show()
                    return@addOnSuccessListener
                }
                if (stopsNearB.isEmpty()) {
                    Log.w("Directions", "No se encontraron paraderos (B) cerca del destino (radio max ${searchRadii.last()}m)")
                    Toast.makeText(this, "No se encontraron paraderos cercanos al destino (radio max 2km)", Toast.LENGTH_LONG).show()
                    return@addOnSuccessListener
                }

                Log.d("Directions", "Búsqueda exitosa. A: ${stopsNearA.size} (radio ${radiusUsedA}m), B: ${stopsNearB.size} (radio ${radiusUsedB}m)")

                var bestRouteFound: DisplayRouteInfo? = null
                var bestStopA: GtfsStop? = null
                var bestStopB: GtfsStop? = null
                var commonRoutesFound = false

                for (stopA in stopsNearA) {
                    val routesA = GtfsDataManager.getRoutesForStop(stopA.stopId)
                    val routeSetA = routesA.map { it.route.routeId to it.directionId }.toSet()



                    for (stopB in stopsNearB) {
                        if (stopA.stopId == stopB.stopId) continue

                        val routesB = GtfsDataManager.getRoutesForStop(stopB.stopId)
                        val commonRoutes = routesB.filter { routeSetA.contains(it.route.routeId to it.directionId) }

                        if (commonRoutes.isNotEmpty()) {
                            commonRoutesFound = true
                        }

                        for (commonRoute in commonRoutes) {
                            val stopSequence = GtfsDataManager.getStopsForRoute(commonRoute.route.routeId, commonRoute.directionId)
                            val indexA = stopSequence.indexOfFirst { it.stopId == stopA.stopId }
                            val indexB = stopSequence.indexOfFirst { it.stopId == stopB.stopId }

                            if (indexA != -1 && indexB != -1 && indexA < indexB) { // Valida A -> B
                                bestRouteFound = commonRoute
                                bestStopA = stopA
                                bestStopB = stopB
                                break
                            }
                        }
                        if (bestRouteFound != null) break
                    }
                    if (bestRouteFound != null) break
                }

                if (bestRouteFound != null && bestStopA != null && bestStopB != null) {
                    val route = bestRouteFound.route
                    val directionId = bestRouteFound.directionId

                    Log.d("Directions", "Ruta válida: ${route.shortName} (Desde ${bestStopA.name} a ${bestStopB.name})")
                    // Toast.makeText(this, "Toma la Línea ${route.shortName} en ${bestStopA.name}", Toast.LENGTH_LONG).show()
                    showCustomNotification("Toma la Línea ${route.shortName} en ${bestStopA.name}") //

                    drawRouteSegment(route, directionId, bestStopA, bestStopB)

                    sharedViewModel.selectStop(bestStopA.stopId)

                } else {
                    val toastMessage = if (commonRoutesFound) {
                        "Se encontraron rutas, pero ninguna va en la dirección correcta (A -> B)"
                    } else {
                        "No se encontró una ruta de bus directa"
                    }
                    //Toast.makeText(this, toastMessage, Toast.LENGTH_LONG).show()
                    showCustomNotification(toastMessage)
                    Log.d("Directions", "No se encontró ruta válida A -> B.")

                    val boundsBuilder = LatLngBounds.Builder()
                    stopsNearA.firstOrNull()?.let { boundsBuilder.include(LatLng(it.location.latitude, it.location.longitude)) }
                    stopsNearB.firstOrNull()?.let { boundsBuilder.include(LatLng(it.location.latitude, it.location.longitude)) }
                    boundsBuilder.include(LatLng(userLat, userLon))
                    boundsBuilder.include(LatLng(puntoLat, puntoLon))

                    try {
                        val bounds = boundsBuilder.build()
                        map.easeCamera(CameraUpdateFactory.newLatLngBounds(bounds, 150), 1000)
                    } catch (e: Exception) {
                        Log.e("Directions", "Error al crear bounds para centrar mapa", e)
                    }
                }
            }
            .addOnFailureListener {
                Log.e("Directions", "Error al obtener ubicación", it)
                Toast.makeText(this, "Error al obtener ubicación", Toast.LENGTH_LONG).show()
            }
    }

    // ===== FIN DE LAS 4 NUEVAS FUNCIONES =====


    // --- Ciclo de Vida (sin cambios) ---
    override fun onStart() { super.onStart(); mapView.onStart() }
    override fun onResume() { super.onResume(); mapView.onResume() }
    override fun onPause() { super.onPause(); mapView.onPause() }
    override fun onStop() { super.onStop(); mapView.onStop() }
    override fun onSaveInstanceState(outState: Bundle) { super.onSaveInstanceState(outState); mapView.onSaveInstanceState(outState) }
    override fun onLowMemory() { super.onLowMemory(); mapView.onLowMemory() }
    override fun onDestroy() { super.onDestroy(); mapView.onDestroy() }

    private fun clearInfoMarker() {
        currentInfoMarker?.let {
            if (::map.isInitialized) {
                map.deselectMarker(it) // Oculta el globo
            }
            it.remove()            // Elimina el marcador invisible
        }
        currentInfoMarker = null
    }

    private inner class CustomInfoWindowAdapter : MapLibreMap.InfoWindowAdapter {

        override fun getInfoWindow(marker: Marker): View? {
            // Solo queremos personalizar el globo del bus (currentInfoMarker)
            // Para los de turismo (turismoMarkers), usamos 'null' para
            // que sigan usando el globo por defecto.
            if (marker != currentInfoMarker) {
                return null
            }

            // Inflamos nuestro layout personalizado
            val view = layoutInflater.inflate(R.layout.custom_bus_info_window, null)
            val tvLine: TextView = view.findViewById(R.id.tv_info_line)
            val tvPatent: TextView = view.findViewById(R.id.tv_info_patent)

            // Asignamos los textos que pusimos en handleBusClick
            tvLine.text = marker.title
            tvPatent.text = marker.snippet

            // Ocultamos la patente si no hay
            if (marker.snippet.isNullOrBlank()) {
                tvPatent.visibility = View.GONE
            } else {
                tvPatent.visibility = View.VISIBLE
            }

            return view
        }

        // --- ### FUNCIÓN ELIMINADA ### ---
        // La función getInfoContents(marker: Marker) se eliminó
        // porque no existe en la interfaz de MapLibre.
    }

    // --- FUNCIÓN CERRAR SESIÓN --- (Sin cambios, solo movida al final)
    private fun cerrarSesion() {
        auth.signOut() // Cerrar sesión en Firebase

        // Volver a LoginActivity
        val intent = Intent(this, LoginActivity::class.java)
        // Limpiar la pila de actividades para que el usuario no pueda "volver"
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)

        finish() // Finalizar MainActivity
    }
}
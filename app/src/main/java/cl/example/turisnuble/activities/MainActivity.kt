package cl.example.turisnuble.activities

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.location.Location
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import cl.example.turisnuble.R
import cl.example.turisnuble.adapters.SearchAdapter
import cl.example.turisnuble.adapters.ViewPagerAdapter
import cl.example.turisnuble.data.GtfsApiService
import cl.example.turisnuble.data.GtfsDataManager
import cl.example.turisnuble.data.GtfsRoute
import cl.example.turisnuble.data.GtfsStop
import cl.example.turisnuble.data.SharedViewModel
import cl.example.turisnuble.data.TurismoDataManager
import cl.example.turisnuble.fragments.DetalleRutaFragment
import cl.example.turisnuble.fragments.DetalleTurismoFragment
import cl.example.turisnuble.fragments.DisplayRouteInfo
import cl.example.turisnuble.models.PuntoTuristico
import cl.example.turisnuble.models.SearchResult
import cl.example.turisnuble.models.SearchResultType
import cl.example.turisnuble.utils.DetalleTurismoNavigator
import cl.example.turisnuble.utils.MapMover
import cl.example.turisnuble.utils.ParaderoActionHandler
import cl.example.turisnuble.utils.RouteDrawer
import cl.example.turisnuble.utils.TurismoActionHandler
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.google.firebase.auth.FirebaseAuth
import com.google.transit.realtime.GtfsRealtime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.maplibre.android.MapLibre
import org.maplibre.android.annotations.IconFactory
import org.maplibre.android.annotations.Marker
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMap.CancelableCallback
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.OnMapReadyCallback
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.expressions.Expression.eq
import org.maplibre.android.style.expressions.Expression.get
import org.maplibre.android.style.expressions.Expression.interpolate
import org.maplibre.android.style.expressions.Expression.linear
import org.maplibre.android.style.expressions.Expression.literal
import org.maplibre.android.style.expressions.Expression.stop
import org.maplibre.android.style.expressions.Expression.switchCase
import org.maplibre.android.style.expressions.Expression.zoom
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import retrofit2.Retrofit
import retrofit2.converter.protobuf.ProtoConverterFactory
import java.io.IOException

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path

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
    private lateinit var searchView: SearchView
    private lateinit var searchResultsRecyclerView: RecyclerView
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
    private var currentRouteCasingLayerId: String? = null // Borde de ruta

    private var mapStyle: Style? = null
    private var currentSelectedStopId: String? = null

    // Variable para recordar el ID del destino (Punto B)
    private var currentDestinationStopId: String? = null

    private var peekHeightInPixels: Int = 0

    private var currentInfoMarker: Marker? = null

    private var currentRouteArrowLayerId: String? = null

    // --- ID DEL BUS QUE ESTAMOS SIGUIENDO (Simple) ---
    private var trackedBusId: String? = null
    // ------------------------------------------------

    private var isMapRotated = false

    // --- VARIABLE DE AUTENTICACIÓN ---
    private lateinit var auth: FirebaseAuth

    // --- VARIABLES AÑADIDAS PARA OSCURO/CLARO/SATELITE ---
    private lateinit var mapStyleFab: FloatingActionButton
    private val styleLIGHT = "https://basemaps.cartocdn.com/gl/voyager-gl-style/style.json"
    private val styleDARK = "https://basemaps.cartocdn.com/gl/dark-matter-gl-style/style.json"
    private val styleSATELLITE_HYBRID =
        "https://api.maptiler.com/maps/hybrid/style.json?key=ya1iLYBcEKV62dZj18Tt"
    var currentMapStyleState = 0
    var isStyleLoading = false
    private var lastMapStyleClickTime: Long = 0L
    // --- FIN VARIABLES ---

    private var listMaxHeight: Int = 0
    private var detailMaxHeight: Int = 0

    // --- NUEVO: Variable para doble click al salir ---
    private var doubleBackToExitPressedOnce = false

    // --- VARIABLE NUEVA PARA ACCIONES DIFERIDAS (Intents desde Favoritos) ---
    private var pendingAction: (() -> Unit)? = null

    // --- VARIABLE PARA ESTADO DEL BOTÓN DE UBICACIÓN (0: Centrar, 1: Reset) ---
    private var locationButtonState = 0

    private var preventRouteZoom = false

    // --- VARIABLES PARA EL FILTRO DE CAPAS ---
    private var isBusesVisible = true
    private var isParaderosVisible = true
    private var isTurismoVisible = true
    // -----------------------------------------
    // ----------------------------------------------

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
                // findAndShowStopsAroundPoint(-36.606, -72.102) // Ya no filtramos por zona
                showAllStops()
            }
        }


    // --- LÓGICA DE "CÓMO LLEGAR" (SIN CAMBIOS) ---
    // 1. CÓMO LLEGAR (Mantiene la Tarjeta de Viaje con hora estimada)
    override fun onGetDirectionsToStop(stop: GtfsStop) {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(
                this,
                "Se necesita permiso de ubicación para 'Cómo llegar'",
                Toast.LENGTH_LONG
            ).show()
            requestLocationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            return
        }

        Log.d("Directions", "Buscando MEJOR ruta para Paradero ${stop.name} (${stop.stopId})")
        fusedLocationClient.getCurrentLocation(
            Priority.PRIORITY_HIGH_ACCURACY,
            CancellationTokenSource().token
        )
            .addOnSuccessListener { location: Location? ->
                if (location == null) {
                    Toast.makeText(
                        this,
                        "No se pudo obtener tu ubicación actual",
                        Toast.LENGTH_LONG
                    ).show()
                    return@addOnSuccessListener
                }

                val userLat = location.latitude
                val userLon = location.longitude

                // 1. Búsqueda con radio flexible para el usuario (Origen A)
                val originSearchRadii = listOf(500f, 1000f, 2500f)
                var stopsNearA: List<Pair<GtfsStop, Float>> = emptyList()

                for (radius in originSearchRadii) {
                    stopsNearA = GtfsDataManager.stops.values
                        .map { s ->
                            Pair(
                                s,
                                distanceBetween(
                                    userLat,
                                    userLon,
                                    s.location.latitude,
                                    s.location.longitude
                                )
                            )
                        }
                        .filter { it.second <= radius }
                        .sortedBy { it.second }
                    if (stopsNearA.isNotEmpty()) break
                }

                if (stopsNearA.isEmpty()) {
                    Toast.makeText(
                        this,
                        "No se encontraron paraderos cercanos a tu ubicación",
                        Toast.LENGTH_LONG
                    ).show()
                    return@addOnSuccessListener
                }

                // 2. Búsqueda con radio flexible para el destino (Destino B)
                val destSearchRadii = listOf(500f, 1000f, 2500f)
                var stopsNearB: List<Pair<GtfsStop, Float>> = emptyList()

                for (radius in destSearchRadii) {
                    stopsNearB = GtfsDataManager.stops.values
                        .map { s ->
                            Pair(
                                s,
                                distanceBetween(
                                    stop.location.latitude,
                                    stop.location.longitude,
                                    s.location.latitude,
                                    s.location.longitude
                                )
                            )
                        }
                        .filter { it.second <= radius }
                        .sortedBy { it.second }
                    if (stopsNearB.isNotEmpty()) break
                }

                if (stopsNearB.isEmpty()) {
                    stopsNearB = listOf(Pair(stop, 0f))
                }

                // 3. Lógica de búsqueda de rutas comunes
                val validRoutes = mutableListOf<Triple<DisplayRouteInfo, GtfsStop, GtfsStop>>()
                var commonRoutesFound = false

                val stopsNearB_RouteSets = stopsNearB.associate { (stopB, _) ->
                    stopB.stopId to GtfsDataManager.getRoutesForStop(stopB.stopId)
                }

                for ((stopA, _) in stopsNearA) {
                    val routesA = GtfsDataManager.getRoutesForStop(stopA.stopId)
                    val routeSetA = routesA.map { it.route.routeId to it.directionId }.toSet()

                    for ((stopB, _) in stopsNearB) {
                        if (stopA.stopId == stopB.stopId) continue

                        val routesB = stopsNearB_RouteSets[stopB.stopId] ?: emptyList()
                        val commonRoutes =
                            routesB.filter { routeSetA.contains(it.route.routeId to it.directionId) }

                        if (commonRoutes.isNotEmpty()) {
                            commonRoutesFound = true
                        }

                        for (commonRoute in commonRoutes) {
                            val stopSequence = GtfsDataManager.getStopsForRoute(
                                commonRoute.route.routeId,
                                commonRoute.directionId
                            )
                            val indexA = stopSequence.indexOfFirst { it.stopId == stopA.stopId }
                            val indexB = stopSequence.indexOfFirst { it.stopId == stopB.stopId }

                            if (indexA != -1 && indexB != -1 && indexA < indexB) {
                                validRoutes.add(Triple(commonRoute, stopA, stopB))
                            }
                        }
                    }
                }

                // 4. Seleccionar la mejor ruta y mostrar tarjeta
                if (validRoutes.isNotEmpty()) {
                    val bestOption = validRoutes.minByOrNull { (_, stopA, stopB) ->
                        val distA = stopsNearA.find { it.first.stopId == stopA.stopId }?.second
                            ?: Float.MAX_VALUE
                        val distB = stopsNearB.find { it.first.stopId == stopB.stopId }?.second
                            ?: Float.MAX_VALUE
                        distA + distB
                    }!!

                    val bestRouteFound = bestOption.first
                    val bestStopA = bestOption.second
                    val bestStopB = bestOption.third
                    val route = bestRouteFound.route

                    // Calcular duración y hora estimada
                    val duracionMin = calcularDuracionEstimada(bestStopA, bestStopB)
                    val arrivalCalendar = Calendar.getInstance()
                    arrivalCalendar.add(Calendar.MINUTE, duracionMin)
                    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
                    val horaLlegada = sdf.format(arrivalCalendar.time)

                    AlertDialog.Builder(this)
                        .setTitle("¡Ruta Encontrada! \uD83D\uDE8C")
                        .setMessage(
                            "Toma la Línea ${route.shortName} en: \n${bestStopA.name}\n\n" +
                                    "⏱️ Tiempo de viaje aprox: $duracionMin min\n" +
                                    "\uD83C\uDFC1 Hora llegada estimada: $horaLlegada\n\n" +
                                    "Baja en: ${bestStopB.name}"
                        )
                        .setPositiveButton("Ver en Mapa") { _, _ ->
                            hideDetailFragment()
                            drawRouteSegment(route, bestRouteFound.directionId, bestStopA, bestStopB)
                            sharedViewModel.selectStop(bestStopA.stopId)
                        }
                        .setNegativeButton("Cancelar", null)
                        .show()

                } else {
                    val toastMessage = if (commonRoutesFound) {
                        "Se encontraron rutas, pero ninguna va en la dirección correcta (A -> B)"
                    } else {
                        "No se encontró una ruta de micro directa en los paraderos cercanos"
                    }
                    showCustomNotification(toastMessage)
                }
            }
            .addOnFailureListener {
                Log.e("Directions", "Error al obtener ubicación", it)
                Toast.makeText(this, "Error al obtener ubicación", Toast.LENGTH_LONG).show()
            }
    }

    // --- displayStopAndNearbyStops ---
    override fun displayStopAndNearbyStops(stop: GtfsStop) {
        clearDrawnElements()
        selectedRouteId = null
        selectedDirectionId = null
        currentDestinationStopId = null // Limpiamos el destino previo
        sharedViewModel.selectStop(stop.stopId)

        // Aunque seleccionamos un paradero, el usuario pidió ver TODOS por defecto si no hay ruta.
        // Pero aquí es "detalle de paradero", así que quizás quiera ver el contexto.
        // Mantendremos findAndShowStopsAroundPoint SOLO para actualizar la lista del ViewPager,
        // pero el mapa mostrará TODOS los paraderos si no hay ruta dibujada,
        // o solo el seleccionado.
        // Para cumplir "mostrar todos", llamamos a showAllStops() PERO marcando el seleccionado.

        findAndShowStopsAroundPoint(stop.location.latitude, stop.location.longitude)
        centerMapOnPoint(stop.location.latitude, stop.location.longitude)

        showAllStops() // Muestra todos, pero showParaderosOnMap se encargará de pintar el seleccionado distinto.

        val stopLocation = Location("").apply {
            latitude = stop.location.latitude
            longitude = stop.location.longitude
        }
        updateBusMarkers(stopLocation)
    }

    // --- onCreate ---
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MapLibre.getInstance(this)
        setContentView(R.layout.activity_main)

        // --- GESTIÓN INTELIGENTE DEL BOTÓN ATRÁS ---
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // NIVEL 1: Dejar que el sistema cierre fragmentos de detalle (Turismo/Ruta)
                if (supportFragmentManager.backStackEntryCount > 0) {
                    supportFragmentManager.popBackStack()
                    return
                }

                // NIVEL 2: Limpiar el mapa si hay rutas o selecciones ("Limpiar pantalla")
                // Se usa false para no mover la cámara bruscamente
                if (selectedRouteId != null || currentSelectedStopId != null || currentInfoMarker != null) {
                    clearRoutes(recenterToUser = false)
                    return
                }

                // NIVEL 3: Si el menú de abajo está tapando el mapa (expandido), bájalo
                if (::bottomSheetBehavior.isInitialized && bottomSheetBehavior.state == BottomSheetBehavior.STATE_EXPANDED) {
                    bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
                    return
                }

                // NIVEL 4: Salir de la app con seguridad (Doble confirmación)
                if (doubleBackToExitPressedOnce) {
                    finish()
                    return
                }

                doubleBackToExitPressedOnce = true
                Toast.makeText(
                    this@MainActivity,
                    "Presiona atrás de nuevo para salir",
                    Toast.LENGTH_SHORT
                ).show()

                Handler(Looper.getMainLooper()).postDelayed({
                    doubleBackToExitPressedOnce = false
                }, 2000)
            }
        })
        // ---------------------------------------------

        // 3. Hacer la barra de navegación (abajo) blanca sólida
        window.navigationBarColor = Color.WHITE

        // --- INICIALIZACIÓN DE AUTH ---
        auth = FirebaseAuth.getInstance()

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Configuración inicial del BottomSheet
        val bottomSheet: FrameLayout = findViewById(R.id.bottom_sheet)
        bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet)

        // Altura inicial: 40% de la pantalla
        val screenHeight = resources.displayMetrics.heightPixels
        bottomSheetBehavior.maxHeight = (screenHeight * 0.40).toInt()

        peekHeightInPixels = (46 * resources.displayMetrics.density).toInt()
        bottomSheetBehavior.peekHeight = peekHeightInPixels
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED

        setupTabs()
        setupBottomSheetCallback()

        mapView = findViewById(R.id.mapView)
        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync(this)

        locationFab = findViewById(R.id.location_fab)

        // --- LÓGICA SIMPLIFICADA DEL BOTÓN DE UBICACIÓN + HAPTIC FEEDBACK ---
        locationFab.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY) // Vibración

            // Estados: 0=Centrar, 1=Reset Total
            when (locationButtonState) {
                0 -> { // 1. Centrar en usuario
                    val locationComponent = map.locationComponent
                    if (locationComponent.isLocationComponentActivated) {
                        locationComponent.cameraMode = CameraMode.TRACKING
                        locationComponent.renderMode = RenderMode.NORMAL
                        locationButtonState = 1 // Siguiente click -> Reset
                        Toast.makeText(this, "Centrado en tu ubicación", Toast.LENGTH_SHORT).show()
                    }
                }

                1 -> { // 2. Reset Total (Limpiar Rutas y Mostrar todos los paraderos)
                    clearRoutes(recenterToUser = true)
                    locationButtonState = 0 // Vuelve al inicio (Centrar)
                }
            }
        }

        // --- CONEXIÓN DEL BOTÓN DE ESTILO + HAPTIC FEEDBACK ---
        mapStyleFab = findViewById(R.id.map_style_fab)
        mapStyleFab.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY) // Vibración

            val COOLDOWN_MS = 1500
            val now = System.currentTimeMillis()
            if (now - lastMapStyleClickTime > COOLDOWN_MS) {
                lastMapStyleClickTime = now
                toggleMapStyle()
            }
        }

        // --- NUEVO: CONEXIÓN DEL BOTÓN DE CAPAS (FILTRO) ---
        val layersFab: FloatingActionButton = findViewById(R.id.layers_fab)
        layersFab.setOnClickListener {
            showLayerFilterDialog()
        }
        // ---------------------------------------------------

        searchView = findViewById(R.id.search_view)
        setupSearchListener()

        searchAdapter = SearchAdapter(emptyList()) { searchResult ->
            onSearchResultClicked(searchResult)
        }

        customNotificationView = findViewById(R.id.custom_notification_container)
        customNotificationMessage =
            customNotificationView.findViewById(R.id.tv_notification_message)
        customNotificationDismiss =
            customNotificationView.findViewById(R.id.btn_notification_dismiss)

        customNotificationDismiss.setOnClickListener {
            hideCustomNotification()
        }

        searchResultsRecyclerView = findViewById(R.id.search_results_recyclerview)
        searchResultsRecyclerView.layoutManager = LinearLayoutManager(this)
        searchResultsRecyclerView.adapter = searchAdapter

        sharedViewModel.nearbyStops.observe(this) { nearbyStops ->
            if (selectedRouteId == null) {
                showAllStops()
            }
        }

        val bottomSheetContent = findViewById<View>(R.id.bottom_sheet_content)

        // --- LISTENER DE NAVEGACIÓN (Restaurar altura al volver) ---
        supportFragmentManager.addOnBackStackChangedListener {
            if (supportFragmentManager.backStackEntryCount == 0) {
                // ESTAMOS EN LA PANTALLA PRINCIPAL:

                // 1. Restauramos la altura al 40% original
                val currentScreenHeight = resources.displayMetrics.heightPixels
                bottomSheetBehavior.maxHeight = (currentScreenHeight * 0.40).toInt()

                // 2. Mostramos el contenido principal del menú
                bottomSheetContent.visibility = View.VISIBLE

                // 3. Reseteamos lógica de paraderos cercanos
                sharedViewModel.setNearbyCalculationCenter(null)
                if (ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
                        loc?.let { findAndShowStopsAroundPoint(it.latitude, it.longitude) }
                            ?: findAndShowStopsAroundPoint(-36.606, -72.102)
                    }
                } else {
                    findAndShowStopsAroundPoint(-36.606, -72.102)
                }
            } else {
                // ESTAMOS EN UN DETALLE (Turismo, Ruta, etc):
                bottomSheetContent.visibility = View.GONE
            }
        }

        sharedViewModel.selectedStopId.observe(this) { stopId ->
            currentSelectedStopId = stopId
            if (selectedRouteId != null && selectedDirectionId != null) {
                val paraderosDeRuta =
                    GtfsDataManager.getStopsForRoute(selectedRouteId!!, selectedDirectionId!!)
                showParaderosOnMap(paraderosDeRuta)
            } else {
                showAllStops()
            }
        }

        // --- LÓGICA DEL MENÚ DE OPCIONES ---
        val optionsMenuButton = findViewById<ImageView>(R.id.optionsMenuButton)
        optionsMenuButton.setOnClickListener { view ->
            val popup = PopupMenu(this, view)
            popup.menuInflater.inflate(R.menu.main_options_menu, popup.menu)

            val currentUser = auth.currentUser
            val menu = popup.menu

            if (currentUser == null) {
                menu.findItem(R.id.menu_perfil).isVisible = false
                menu.findItem(R.id.menu_favoritos).isVisible = false
                menu.findItem(R.id.menu_sugerencias).isVisible = false
            } else {
                menu.findItem(R.id.menu_perfil).isVisible = true
                menu.findItem(R.id.menu_favoritos).isVisible = true
                menu.findItem(R.id.menu_sugerencias).isVisible = true
            }

            popup.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.menu_perfil -> {
                        startActivity(Intent(this, ProfileActivity::class.java))
                        true
                    }

                    R.id.menu_favoritos -> {
                        startActivity(Intent(this, FavoritosActivity::class.java))
                        true
                    }

                    R.id.menu_sugerencias -> {
                        startActivity(Intent(this, SugerenciasActivity::class.java))
                        true
                    }

                    R.id.menu_logout -> {
                        cerrarSesion()
                        true
                    }

                    else -> false
                }
            }
            popup.show()
        }

        // --- PROCESAR INTENT INICIAL ---
        processIntent(intent)
    } // --- FIN DE onCreate ---

    // --- NUEVO MÉTODO: Se llama cuando la app ya está abierta y recibe un nuevo Intent ---
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent) // Actualizar el intent guardado
        processIntent(intent) // Procesar la nueva instrucción
    }

    // --- FUNCIONES PARA EL FILTRO DE CAPAS ---

    // --- NUEVO: Calcula duración estimada para la tarjeta de viaje ---
    private fun calcularDuracionEstimada(stopA: GtfsStop, stopB: GtfsStop): Int {
        // Distancia lineal
        val distancia = distanceBetween(
            stopA.location.latitude, stopA.location.longitude,
            stopB.location.latitude, stopB.location.longitude
        )
        // Factor de corrección (1.4x) para aproximar la distancia real por calle
        val distanciaRealAprox = distancia * 1.4

        // Velocidad promedio micro: ~20 km/h = ~333 metros/min
        return (distanciaRealAprox / 333).toInt().coerceAtLeast(2) // Mínimo 2 min
    }

    // --- NUEVO: Obtiene predicción para el globo del mapa ---
    // --- NUEVO: Obtiene predicción (CON VALIDACIÓN DE RUTA) ---
    // --- NUEVO: Obtiene predicción (CON VALIDACIÓN "YA PASÓ") ---
    private fun obtenerPrediccionTiempoReal(routeId: String, directionId: Int, vehicleLat: Double, vehicleLon: Double): String? {
        val stopId = currentSelectedStopId ?: return null
        val stop = GtfsDataManager.stops[stopId] ?: return null

        // 1. VALIDACIÓN DE RUTA: ¿Esta micro pasa por aquí?
        val paraderosDeLaRuta = GtfsDataManager.getStopsForRoute(routeId, directionId)
        if (paraderosDeLaRuta.none { it.stopId == stopId }) return null

        // 2. INTENTO POR API (TripUpdate)
        val nowSeconds = System.currentTimeMillis() / 1000
        val updates = lastFeedMessage?.entityList?.filter {
            it.hasTripUpdate() && it.tripUpdate.trip.routeId == routeId && it.tripUpdate.trip.directionId == directionId
        } ?: emptyList()

        for (entity in updates) {
            val stopUpdate = entity.tripUpdate.stopTimeUpdateList.find { it.stopId == stopId && it.hasArrival() }
            if (stopUpdate != null) {
                val diff = (stopUpdate.arrival.time - nowSeconds) / 60

                // MEJORA: Si la predicción es muy antigua (hace más de 2 min), asumimos que ya pasó
                if (diff < -2) return null

                return if (diff <= 0) "Llegando" else "$diff min"
            }
        }

        // 3. FALLBACK GPS: Cálculo geométrico sobre el trazado
        val trip = GtfsDataManager.trips.values.find { it.routeId == routeId && it.directionId == directionId }
        val shapePoints = GtfsDataManager.shapes[trip?.shapeId]

        if (shapePoints != null && shapePoints.isNotEmpty()) {
            // Buscamos en qué "índice" del camino está el paradero
            // (Nota: Esto podría optimizarse guardándolo en memoria, pero para pocos buses está bien)
            val stopIndex = findNearestShapePointIndex(shapePoints, stop)

            // Buscamos en qué "índice" del camino está el Bus
            val busLocation = GtfsStop("bus_temp", "bus", LatLng(vehicleLat, vehicleLon)) // Wrapper temporal
            val busIndex = findNearestShapePointIndex(shapePoints, busLocation)

            // ¡AQUÍ ESTÁ LA MAGIA!
            // Si el bus va en el punto 100 y el paradero es el 80... ya pasó.
            if (busIndex > stopIndex) {
                return null
            }
        }

        // Si pasó las pruebas, calculamos la distancia final
        val results = FloatArray(1)
        Location.distanceBetween(vehicleLat, vehicleLon, stop.location.latitude, stop.location.longitude, results)
        val dist = results[0]

        if (dist > 20000) return null

        val time = (dist / 416).toInt()
        return if (time <= 0) "Llegando" else "$time min"
    }

    private fun showLayerFilterDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Filtrar Mapa")

        // Opciones del menú
        val items = arrayOf("Buses en tiempo real", "Paraderos", "Puntos Turísticos")
        val checkedItems = booleanArrayOf(isBusesVisible, isParaderosVisible, isTurismoVisible)

        builder.setMultiChoiceItems(items, checkedItems) { _, which, isChecked ->
            when (which) {
                0 -> { // Buses
                    isBusesVisible = isChecked
                    toggleLayerVisibility("bus-layer", isChecked)
                }

                1 -> { // Paraderos
                    isParaderosVisible = isChecked
                    toggleLayerVisibility("paradero-layer", isChecked)
                }

                2 -> { // Turismo
                    isTurismoVisible = isChecked
                    toggleLayerVisibility("turismo-layer", isChecked)
                }
            }
        }

        builder.setPositiveButton("Cerrar", null)
        builder.show()
    }

    private fun toggleLayerVisibility(layerId: String, isVisible: Boolean) {
        if (::map.isInitialized) {
            map.getStyle { style ->
                style.getLayer(layerId)?.setProperties(
                    PropertyFactory.visibility(if (isVisible) Property.VISIBLE else Property.NONE)
                )
            }
        }
    }
    // -----------------------------------------

    // --- NUEVA FUNCIÓN ---
    private fun updateTurismoSelection(selectedId: Int?) {
        val style = mapStyle ?: return
        val source = style.getSourceAs<GeoJsonSource>("turismo-source") ?: return

        lifecycleScope.launch(Dispatchers.Default) {
            val features = TurismoDataManager.puntosTuristicos.map { punto ->
                Feature.fromGeometry(Point.fromLngLat(punto.longitud, punto.latitud)).apply {
                    addNumberProperty("id", punto.id)
                    // Marcamos como 'true' solo si el ID coincide
                    addBooleanProperty("selected", punto.id == selectedId)
                }
            }
            withContext(Dispatchers.Main) {
                source.setGeoJson(FeatureCollection.fromFeatures(features))
            }
        }
    }

    // --- NUEVO MÉTODO: Lógica central para navegar desde Favoritos ---
    private fun processIntent(intent: Intent?) {
        if (intent == null) return

        val actionType = intent.getStringExtra("EXTRA_ACTION_TYPE") ?: return
        val id = intent.getStringExtra("EXTRA_ID") ?: return

        // Definimos la acción a realizar
        val action = {
            when (actionType) {
                "TURISMO" -> {
                    val puntoId = id.toIntOrNull()
                    val punto = TurismoDataManager.puntosTuristicos.find { it.id == puntoId }
                    if (punto != null) {
                        showTurismoDetail(punto)
                    } else {
                        Toast.makeText(this, "Punto turístico no encontrado", Toast.LENGTH_SHORT)
                            .show()
                    }
                }

                "PARADERO" -> {
                    // handleParaderoClick ya gestiona el centrado, el ViewModel y la UI
                    handleParaderoClick(id)
                }

                "RUTA" -> {
                    val route = GtfsDataManager.routes[id]
                    if (route != null) {
                        // Asumimos dirección 0 (Ida) por defecto para favoritos de ruta
                        drawRoute(route, 0)
                        showRouteDetail(route.routeId, 0)
                    } else {
                        Toast.makeText(this, "Ruta no encontrada", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        // Ejecutamos la acción SOLO si el mapa y el estilo están listos
        if (::map.isInitialized && mapStyle != null && mapStyle!!.isFullyLoaded) {
            action()
        } else {
            // Si no están listos, guardamos la acción para después (en onStyleLoaded)
            pendingAction = action
        }
    }


    // 2. CLIC EN EL BUS (Limpio: Solo Línea y Patente)
    private fun handleBusClick(feature: Feature) {
        val geometry = feature.geometry() as? Point ?: return
        val clickLat = geometry.latitude()
        val clickLon = geometry.longitude()

        val feed = lastFeedMessage
        var foundBusId: String? = null
        var foundRouteId: String? = null
        var foundDirectionId: Int? = null
        var foundLicensePlate: String? = null

        if (feed != null) {
            val closestEntity = feed.entityList
                .filter { it.hasVehicle() && it.vehicle.hasPosition() }
                .minByOrNull { entity ->
                    val pos = entity.vehicle.position
                    val results = FloatArray(1)
                    Location.distanceBetween(
                        clickLat,
                        clickLon,
                        pos.latitude.toDouble(),
                        pos.longitude.toDouble(),
                        results
                    )
                    results[0]
                }

            if (closestEntity != null) {
                val v = closestEntity.vehicle
                foundBusId =
                    if (v.hasVehicle() && v.vehicle.hasId()) v.vehicle.id else v.trip.tripId
                foundRouteId = v.trip.routeId
                foundDirectionId = v.trip.directionId
                if (v.hasVehicle() && v.vehicle.hasLicensePlate()) {
                    foundLicensePlate = v.vehicle.licensePlate
                }
            }
        }

        if (foundBusId == null || foundRouteId == null) return

        trackedBusId = foundBusId

        val route = GtfsDataManager.routes[foundRouteId] ?: return
        if (selectedRouteId != foundRouteId) {
            preventRouteZoom = true
            drawRoute(route, foundDirectionId ?: 0)
            preventRouteZoom = false
        }

        val directionStr = if (foundDirectionId == 0) "Ida" else "Vuelta"
        val infoTitle = "Línea ${route.shortName} ($directionStr)"

        // Volvemos al snippet simple sin tiempos confusos
        val infoSnippet =
            if (!foundLicensePlate.isNullOrBlank()) "Patente: $foundLicensePlate" else "Patente no disponible"

        val iconFactory = IconFactory.getInstance(this@MainActivity)
        val transparentIcon =
            iconFactory.fromBitmap(Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888))

        currentInfoMarker?.let { map.removeMarker(it) }

        currentInfoMarker = map.addMarker(
            MarkerOptions()
                .position(LatLng(clickLat, clickLon))
                .title(infoTitle)
                .snippet(infoSnippet)
                .icon(transparentIcon)
        )

        map.selectMarker(currentInfoMarker!!)
        map.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(clickLat, clickLon), 16.0), 1000)
    }

    private fun showCustomNotification(message: String) {
        customNotificationMessage.text = message
        customNotificationView.visibility = View.VISIBLE
    }

    private fun hideCustomNotification() {
        customNotificationView.visibility = View.GONE
    }

    private fun performSearch(query: String) {
        val paraderos = GtfsDataManager.stops.values
            .filter {
                it.name.lowercase().contains(query) || it.stopId.equals(
                    query,
                    ignoreCase = true
                )
            }
            .map { SearchResult(SearchResultType.PARADERO, it.name, "Paradero ${it.stopId}", it) }

        val queryLower = query.lowercase()
        var queryRutas = query

        if (queryLower == "linea" || queryLower == "línea") {
            queryRutas = ""
        } else if (queryLower.startsWith("linea ")) {
            queryRutas = query.substring(6).trim()
        } else if (queryLower.startsWith("línea ")) {
            queryRutas = query.substring(6).trim()
        }

        val rutas = GtfsDataManager.routes.values
            .filter {
                it.shortName.lowercase().contains(query) || it.longName.lowercase()
                    .contains(query) ||
                        (queryRutas != query && (it.shortName.lowercase()
                            .contains(queryRutas) || it.longName.lowercase().contains(queryRutas)))
            }
            .map { SearchResult(SearchResultType.RUTA, it.shortName, it.longName, it) }

        val turismo = TurismoDataManager.puntosTuristicos
            .filter { it.nombre.lowercase().contains(query) }
            .map { SearchResult(SearchResultType.TURISMO, it.nombre, it.direccion, it) }

        val results = (paraderos + rutas + turismo).sortedBy { it.title }

        if (results.isNotEmpty()) {
            searchAdapter.updateResults(results)
            searchResultsRecyclerView.visibility = View.VISIBLE
        } else {
            hideSearchResults()
        }
    }

    private fun onSearchResultClicked(result: SearchResult) {
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
                drawRoute(ruta, 0)
                showRouteDetail(ruta.routeId, 0)
            }
        }

        searchView.setQuery("", false)
        searchView.clearFocus()
        hideSearchResults()
    }

    private fun hideSearchResults() {
        searchResultsRecyclerView.visibility = View.GONE
        searchAdapter.updateResults(emptyList())
    }

    private fun setupSearchListener() {
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                searchView.clearFocus()
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                val query = newText?.trim()
                if (query.isNullOrBlank()) {
                    hideSearchResults()
                } else {
                    performSearch(query.lowercase())
                }
                return true
            }
        })

        searchView.setOnCloseListener {
            hideSearchResults()
            false
        }
    }

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

    private fun setupBottomSheetCallback() {
        if (!::bottomSheetBehavior.isInitialized) {
            val bottomSheet: FrameLayout = findViewById(R.id.bottom_sheet)
            bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet)
        }

        bottomSheetBehavior.addBottomSheetCallback(object :
            BottomSheetBehavior.BottomSheetCallback() {
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

        val topMarginPx = (70 * resources.displayMetrics.density).toInt()
        val rightMarginPx = (16 * resources.displayMetrics.density).toInt()
        map.uiSettings.setCompassMargins(0, topMarginPx, rightMarginPx, 0)

        // Carga el estilo CLARO (styleLIGHT) por defecto
        map.setStyle(styleLIGHT, Style.OnStyleLoaded { style ->
            onStyleLoaded(style)
        })

        // --- MEJORA: DETECTAR ARRASTRE PARA RESETEAR BOTÓN DE UBICACIÓN ---
        map.addOnCameraMoveStartedListener { reason ->
            if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) {
                // Si el usuario mueve el mapa con el dedo, el botón "olvida" el estado de reset
                // y vuelve a estar listo para "Centrar" en el próximo clic.
                locationButtonState = 0
            }
        }
        // ------------------------------------------------------------------

        map.addOnCameraIdleListener {
            val newBearing = map.cameraPosition.bearing
            val shouldMapBeRotated = newBearing > 90 && newBearing < 270

            if (shouldMapBeRotated == isMapRotated) {
                return@addOnCameraIdleListener
            }

            isMapRotated = shouldMapBeRotated
            Log.d("MapRotation", "Cambiando a estado rotado: $isMapRotated")

            map.getStyle { style ->
                val busLayer = style.getLayer("bus-layer") as? SymbolLayer
                busLayer?.let {
                    it.setProperties(
                        PropertyFactory.iconImage(createBusIconExpression(isMapRotated))
                    )
                }
            }
        }

        map.infoWindowAdapter = CustomInfoWindowAdapter()

        map.addOnMapClickListener { point ->
            val screenPoint = map.projection.toScreenLocation(point)

            // 1. Bus click
            val busFeatures = map.queryRenderedFeatures(screenPoint, "bus-layer")
            if (busFeatures.isNotEmpty()) {
                busFeatures[0]?.let {
                    handleBusClick(it)
                }
                return@addOnMapClickListener true
            }

            // 2. Paradero click
            val paraderoFeatures = map.queryRenderedFeatures(screenPoint, "paradero-layer")
            if (paraderoFeatures.isNotEmpty()) {
                val stopId = paraderoFeatures[0].getStringProperty("stop_id")
                if (stopId != null) {
                    handleParaderoClick(stopId)
                }
                return@addOnMapClickListener true
            }

            // 3. Turismo click
            val turismoFeatures = map.queryRenderedFeatures(screenPoint, "turismo-layer")
            if (turismoFeatures.isNotEmpty()) {
                val id = turismoFeatures[0].getNumberProperty("id")?.toInt()
                if (id != null) {
                    TurismoDataManager.puntosTuristicos.find { it.id == id }?.let { punto ->
                        showTurismoDetail(punto)
                    }
                }
                return@addOnMapClickListener true
            }

            clearInfoMarker()
            true
        }
    }

    // --- NUEVA FUNCIÓN onStyleLoaded (MODIFICADA para pendingAction) ---
    private fun onStyleLoaded(style: Style) {
        this.mapStyle = style
        Log.d("MainActivity", "Estilo cargado: ${style.uri}")

        loadMapIcons(style)
        setupParaderoLayer(style)
        setupBusLayer(style)
        setupTurismoLayer(style) // Asegúrate de tener esta función implementada como vimos antes

        enableLocation(style)
        startBusDataFetching()

        if (selectedRouteId != null && selectedDirectionId != null) {
            val route = GtfsDataManager.routes[selectedRouteId]
            if (route != null) {
                drawRoute(route, selectedDirectionId!!)
            }
        } else {
            // Mostramos TODOS los paraderos si no hay ruta seleccionada
            showAllStops()
        }

        if (::bottomSheetBehavior.isInitialized && ::map.isInitialized) {
            val screenHeight = resources.displayMetrics.heightPixels
            val currentPadding = screenHeight - findViewById<View>(R.id.bottom_sheet).top
            if (currentPadding > 0) {
                map.setPadding(0, 0, 0, currentPadding)
            } else {
                map.setPadding(0, 0, 0, peekHeightInPixels)
            }
        }

        isStyleLoading = false

        // --- NUEVO: APLICAR FILTROS GUARDADOS ---
        // Esto asegura que si el usuario ocultó algo, se mantenga oculto al cambiar el estilo del mapa
        toggleLayerVisibility("bus-layer", isBusesVisible)
        toggleLayerVisibility("paradero-layer", isParaderosVisible)
        toggleLayerVisibility("turismo-layer", isTurismoVisible)
        // ----------------------------------------

        // --- EJECUTAR ACCIÓN PENDIENTE (SI EXISTE) ---
        pendingAction?.invoke()
        pendingAction = null
    }

    private fun toggleMapStyle() {
        if (!::map.isInitialized || isStyleLoading) return

        isStyleLoading = true
        currentMapStyleState = (currentMapStyleState + 1) % 3

        when (currentMapStyleState) {
            0 -> {
                Log.d("MapStyle", "Cambiando a Modo Claro")
                map.setStyle(styleLIGHT, Style.OnStyleLoaded { style -> onStyleLoaded(style) })
            }

            1 -> {
                Log.d("MapStyle", "Cambiando a Modo Oscuro")
                map.setStyle(styleDARK, Style.OnStyleLoaded { style -> onStyleLoaded(style) })
            }

            2 -> {
                Log.d("MapStyle", "Cambiando a Modo Satélite")
                map.setStyle(
                    styleSATELLITE_HYBRID,
                    Style.OnStyleLoaded { style -> onStyleLoaded(style) })
            }
        }
    }

    // --- FUNCIÓN: Carga TODOS los iconos ---
    private fun loadMapIcons(style: Style) {
        try {
            // --- 1. Generar Flecha de Dirección (CORREGIDO) ---
            val arrowBitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(arrowBitmap)

            // Configuración explícita del pincel de borde (outline)
            val paintOutline = Paint()
            paintOutline.color = Color.BLACK
            paintOutline.style = android.graphics.Paint.Style.STROKE // Ruta completa para evitar error
            paintOutline.strokeWidth = 22f
            paintOutline.strokeCap = Paint.Cap.ROUND
            paintOutline.strokeJoin = Paint.Join.ROUND
            paintOutline.isAntiAlias = true

            // Definimos la forma de la flecha (Un chevron >)
            val path = Path().apply {
                moveTo(25f, 20f)
                lineTo(75f, 50f)
                lineTo(25f, 80f)
            }
            canvas.drawPath(path, paintOutline)

            // Configuración explícita del pincel de relleno (fill)
            val paintFill = Paint()
            paintFill.color = Color.WHITE
            paintFill.style = android.graphics.Paint.Style.STROKE // Ruta completa
            paintFill.strokeWidth = 12f
            paintFill.strokeCap = Paint.Cap.ROUND
            paintFill.strokeJoin = Paint.Join.ROUND
            paintFill.isAntiAlias = true

            canvas.drawPath(path, paintFill)

            style.addImage("route-arrow", arrowBitmap)
            // -----------------------------------------------------

            // --- 2. Cargar Iconos de Paraderos ---
            style.addImage("paradero-icon", BitmapFactory.decodeResource(resources, R.drawable.ic_paradero))
            style.addImage("paradero-icon-selected", BitmapFactory.decodeResource(resources, R.drawable.ic_paradero_selected))
            try { style.addImage("paradero-icon-finish", BitmapFactory.decodeResource(resources, R.drawable.ic_paradero_finish)) } catch (e: Exception) {}

            // --- 3. Cargar Iconos de Turismo ---
            try {
                val turismoBitmap = BitmapFactory.decodeResource(resources, R.drawable.ic_turismo)
                val scaledTurismo = Bitmap.createScaledBitmap(turismoBitmap, 80, 80, false)
                style.addImage("turismo-icon", scaledTurismo)

                val turismoSelectedBitmap = BitmapFactory.decodeResource(resources, R.drawable.ic_turismo_selected)
                val scaledTurismoSelected = Bitmap.createScaledBitmap(turismoSelectedBitmap, 90, 90, false)
                style.addImage("turismo-icon-selected", scaledTurismoSelected)
            } catch (e: Exception) { Log.e("LoadIcons", "Error cargando ic_turismo", e) }

            // --- 4. Cargar Iconos de Buses ---
            val originalBusBitmap = BitmapFactory.decodeResource(resources, R.drawable.ic_bus)
            val scaledBusBitmap = Bitmap.createScaledBitmap(originalBusBitmap, 60, 60, false)
            style.addImage("bus-icon-default-normal", scaledBusBitmap)

            try {
                val originalBusEspejo = BitmapFactory.decodeResource(resources, R.drawable.ic_bus_espejo)
                val scaledBusEspejo = Bitmap.createScaledBitmap(originalBusEspejo, 60, 60, false)
                style.addImage("bus-icon-default-espejo", scaledBusEspejo)
            } catch (e: Exception) { Log.e("LoadIcons", "Error ic_bus_espejo", e) }

            val routeIcons = mapOf(
                "467" to R.drawable.linea_2, "468" to R.drawable.linea_3, "469" to R.drawable.linea_4,
                "470" to R.drawable.linea_6, "471" to R.drawable.linea_7, "472" to R.drawable.linea_8,
                "954" to R.drawable.linea_7, "478" to R.drawable.linea_14, "477" to R.drawable.linea_14,
                "473" to R.drawable.linea_10, "476" to R.drawable.linea_13, "474" to R.drawable.linea_13,
                "475" to R.drawable.linea_13, "466" to R.drawable.linea_1
            )
            val routeIconsEspejo = mapOf(
                "467" to R.drawable.linea_2_espejo, "468" to R.drawable.linea_3_espejo, "469" to R.drawable.linea_4_espejo,
                "470" to R.drawable.linea_6_espejo, "471" to R.drawable.linea_7_espejo, "472" to R.drawable.linea_8_espejo,
                "954" to R.drawable.linea_7_espejo, "478" to R.drawable.linea_14_espejo, "477" to R.drawable.linea_14_espejo,
                "473" to R.drawable.linea_10_espejo, "476" to R.drawable.linea_13_espejo, "474" to R.drawable.linea_13_espejo,
                "475" to R.drawable.linea_13_espejo, "466" to R.drawable.linea_1_espejo
            )

            routeIcons.forEach { (id, res) -> try { style.addImage("bus-icon-$id-normal", Bitmap.createScaledBitmap(BitmapFactory.decodeResource(resources, res), 60, 60, false)) } catch (e: Exception) {} }
            routeIconsEspejo.forEach { (id, res) -> try { style.addImage("bus-icon-$id-espejo", Bitmap.createScaledBitmap(BitmapFactory.decodeResource(resources, res), 60, 60, false)) } catch (e: Exception) {} }

        } catch (e: Exception) {
            Log.e("LoadIcons", "Error cargando íconos base", e)
        }
    }

    // --- NUEVA FUNCIÓN: Configura la capa de turismo con zoom dinámico ---
    private fun setupTurismoLayer(style: Style) {
        // 1. Fuente de datos (inicialmente sin selección)
        if (style.getSource("turismo-source") == null) {
            val features = TurismoDataManager.puntosTuristicos.map { punto ->
                Feature.fromGeometry(Point.fromLngLat(punto.longitud, punto.latitud)).apply {
                    addNumberProperty("id", punto.id)
                    addBooleanProperty("selected", false) // Inicialmente ningún punto seleccionado
                }
            }
            style.addSource(
                GeoJsonSource(
                    "turismo-source",
                    FeatureCollection.fromFeatures(features)
                )
            )
        }

        // 2. Capa visual con lógica condicional
        if (style.getLayer("turismo-layer") == null) {
            val layer = SymbolLayer("turismo-layer", "turismo-source").apply {
                withProperties(
                    // LÓGICA DE ICONO: Si selected=true usa el icono destacado, si no, el normal
                    PropertyFactory.iconImage(
                        switchCase(
                            eq(get("selected"), literal(true)), literal("turismo-icon-selected"),
                            literal("turismo-icon")
                        )
                    ),
                    // LÓGICA DE TAMAÑO: Aumenta un 10% si está seleccionado
                    PropertyFactory.iconSize(
                        switchCase(
                            eq(get("selected"), literal(true)), literal(1.1f),
                            literal(1.0f)
                        )
                    ),
                    PropertyFactory.iconAllowOverlap(true),
                    PropertyFactory.iconIgnorePlacement(true),
                    // Zoom fade in/out
                    PropertyFactory.iconOpacity(
                        interpolate(
                            linear(),
                            zoom(),
                            stop(12.5f, 0f),
                            stop(13.5f, 1f)
                        )
                    )
                )
            }
            style.addLayer(layer)
        }
    }

    // --- FUNCIÓN RENOVADA: VUELTA A LO SIMPLE (SIN CLUSTERING) ---
    private fun setupParaderoLayer(style: Style) {
        // 1. FUENTE (SOURCE) - SIN CLUSTERING
        if (style.getSource("paradero-source") == null) {
            style.addSource(GeoJsonSource("paradero-source"))
        }

        // 2. CAPA DE PARADEROS INDIVIDUALES
        if (style.getLayer("paradero-layer") == null) {
            val paraderoLayer = SymbolLayer("paradero-layer", "paradero-source").apply {
                withProperties(
                    PropertyFactory.iconImage(
                        switchCase(
                            // --- LÓGICA ACTUALIZADA ---
                            // 1. Si es DESTINO (finish) -> Icono finish
                            eq(get("isDestination"), literal(true)),
                            literal("paradero-icon-finish"),
                            // 2. Si es SELECCIONADO (origen) -> Icono seleccionado
                            eq(get("selected"), literal(true)),
                            literal("paradero-icon-selected"),
                            // 3. Defecto -> Icono normal
                            literal("paradero-icon")
                        )
                    ),
                    PropertyFactory.iconAllowOverlap(true),
                    PropertyFactory.iconIgnorePlacement(false),
                    PropertyFactory.iconSize(
                        switchCase(
                            // Agrandamos si es destino o seleccionado
                            eq(get("isDestination"), literal(true)), literal(0.07f),
                            eq(get("selected"), literal(true)), literal(0.07f),
                            literal(0.05f)
                        )
                    ),
                    // Mantenemos tu lógica original de opacidad para los paraderos individuales
                    PropertyFactory.iconOpacity(
                        interpolate(
                            linear(),
                            zoom(),
                            stop(10.99f, 0f),
                            stop(11f, 1f)
                        )
                    ),
                    PropertyFactory.textField(get("stop_id")),
                    PropertyFactory.textSize(10f),
                    PropertyFactory.textColor(Color.BLACK),
                    PropertyFactory.textOffset(arrayOf(0f, 1.5f)),
                    PropertyFactory.textOpacity(
                        interpolate(
                            linear(),
                            zoom(),
                            stop(15.49f, 0f),
                            stop(15.5f, 1f)
                        )
                    )
                )
            }
            style.addLayer(paraderoLayer)
        }
    }

    private fun handleParaderoClick(stopId: String) {
        clearInfoMarker()
        sharedViewModel.selectStop(stopId)
        GtfsDataManager.stops[stopId]?.let { stop ->
            centerMapOnPoint(stop.location.latitude, stop.location.longitude)
        }
        val routesForStop = GtfsDataManager.getRoutesForStop(stopId)
        if (routesForStop.isNotEmpty()) {
            showCustomNotification("Mostrando rutas para el paradero $stopId")
            if (supportFragmentManager.backStackEntryCount > 0) {
                supportFragmentManager.popBackStack()
                findViewById<View>(R.id.bottom_sheet_content).visibility = View.VISIBLE
            }
            sharedViewModel.setRouteFilter(routesForStop)
            viewPager.setCurrentItem(2, true)
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
        } else {
            showCustomNotification("No se encontraron rutas para el paradero $stopId")
            sharedViewModel.setRouteFilter(emptyList())
        }
    }

    override fun showTurismoDetail(punto: PuntoTuristico) {
        if (!::map.isInitialized) {
            doShowTurismoDetail(punto)
            return
        }
        map.animateCamera(
            CameraUpdateFactory.newLatLngZoom(LatLng(punto.latitud, punto.longitud), 16.0),
            1500,
            object : CancelableCallback {
                override fun onCancel() {
                    doShowTurismoDetail(punto)
                }

                override fun onFinish() {
                    doShowTurismoDetail(punto)
                    val turismoLocation = LatLng(punto.latitud, punto.longitud)
                    sharedViewModel.setNearbyCalculationCenter(turismoLocation)
                    findAndShowStopsAroundPoint(turismoLocation.latitude, turismoLocation.longitude)
                    val loc =
                        Location("").apply { latitude = punto.latitud; longitude = punto.longitud }
                    updateBusMarkers(loc)
                }
            }
        )
    }

    private fun doShowTurismoDetail(punto: PuntoTuristico) {
        clearInfoMarker()

        // 1. Marcar visualmente el punto en el mapa (Icono seleccionado)
        updateTurismoSelection(punto.id)

        // 2. Ajustar altura dinámica del menú (55% de la pantalla para mejor visión)
        val screenHeight = resources.displayMetrics.heightPixels
        bottomSheetBehavior.maxHeight = (screenHeight * 0.55).toInt()

        // 3. Mostrar el fragmento de detalle
        val fragment = DetalleTurismoFragment.Companion.newInstance(punto)
        findViewById<View>(R.id.bottom_sheet_content).visibility = View.GONE
        supportFragmentManager.beginTransaction()
            .replace(R.id.bottom_sheet, fragment)
            .addToBackStack("turismo_detail")
            .commitAllowingStateLoss()

        bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
    }

    private fun findAndShowStopsAroundPoint(lat: Double, lon: Double) {
        lifecycleScope.launch(Dispatchers.Default) {
            val paraderosCercanos = GtfsDataManager.stops.values
                .map { stop ->
                    Pair(
                        stop,
                        distanceBetween(lat, lon, stop.location.latitude, stop.location.longitude)
                    )
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
        val fragment = DetalleRutaFragment.Companion.newInstance(routeId, directionId)
        findViewById<View>(R.id.bottom_sheet_content).visibility = View.GONE
        supportFragmentManager.beginTransaction()
            .replace(R.id.bottom_sheet, fragment)
            .addToBackStack("ruta_detail")
            .commit()
    }

    override fun showRoutesForStop(stopId: String) {
        val routesForStop = GtfsDataManager.getRoutesForStop(stopId)
        sharedViewModel.setRouteFilter(routesForStop)
        viewPager.setCurrentItem(2, true)
        supportFragmentManager.popBackStack(
            "turismo_detail",
            FragmentManager.POP_BACK_STACK_INCLUSIVE
        )
        findViewById<View>(R.id.bottom_sheet_content).visibility = View.VISIBLE
    }

    override fun hideDetailFragment() {
        supportFragmentManager.popBackStack()
        findViewById<View>(R.id.bottom_sheet_content).visibility = View.VISIBLE
    }

    override fun centerMapOnPoint(lat: Double, lon: Double) {
        if (::map.isInitialized) {
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(lat, lon), 16.0), 1500)
        }
    }

    private fun setupBusLayer(style: Style) {
        val routeIcons = mapOf(
            "467" to R.drawable.linea_2,
            "468" to R.drawable.linea_3,
            "469" to R.drawable.linea_4,
            "470" to R.drawable.linea_6,
            "471" to R.drawable.linea_7,
            "472" to R.drawable.linea_8,
            "954" to R.drawable.linea_7,
            "478" to R.drawable.linea_14,
            "477" to R.drawable.linea_14,
            "473" to R.drawable.linea_10,
            "476" to R.drawable.linea_13,
            "474" to R.drawable.linea_13,
            "475" to R.drawable.linea_13,
            "466" to R.drawable.linea_1,
        )

        val routeIconsEspejo = mapOf(
            "467" to R.drawable.linea_2_espejo,
            "468" to R.drawable.linea_3_espejo,
            "469" to R.drawable.linea_4_espejo,
            "470" to R.drawable.linea_6_espejo,
            "471" to R.drawable.linea_7_espejo,
            "472" to R.drawable.linea_8_espejo,
            "954" to R.drawable.linea_7_espejo,
            "478" to R.drawable.linea_14_espejo,
            "477" to R.drawable.linea_14_espejo,
            "473" to R.drawable.linea_10_espejo,
            "476" to R.drawable.linea_13_espejo,
            "474" to R.drawable.linea_13_espejo,
            "475" to R.drawable.linea_13_espejo,
            "466" to R.drawable.linea_1_espejo
        )

        try {
            try {
                style.addImage(
                    "bus-icon-default-normal",
                    BitmapFactory.decodeResource(resources, R.drawable.ic_bus)
                )
            } catch (e: Exception) {
                Log.e("SetupBusLayer", "Error cargando ic_bus.png", e)
            }

            try {
                style.addImage(
                    "bus-icon-default-espejo",
                    BitmapFactory.decodeResource(resources, R.drawable.ic_bus_espejo)
                )
            } catch (e: Exception) {
                Log.e("SetupBusLayer", "Error cargando ic_bus_espejo.png", e)
            }

            routeIcons.forEach { (routeId, resourceId) ->
                try {
                    style.addImage(
                        "bus-icon-$routeId-normal",
                        BitmapFactory.decodeResource(resources, resourceId)
                    )
                } catch (e: Exception) {
                    Log.e("SetupBusLayer", "Error al cargar icono normal $routeId: ${e.message}")
                }
            }
            routeIconsEspejo.forEach { (routeId, resourceId) ->
                try {
                    style.addImage(
                        "bus-icon-$routeId-espejo",
                        BitmapFactory.decodeResource(resources, resourceId)
                    )
                } catch (e: Exception) {
                    Log.e("SetupBusLayer", "Error al cargar icono espejo $routeId: ${e.message}")
                }
            }

        } catch (e: Exception) {
            Log.e("SetupBusLayer", "Error al cargar los íconos: ${e.message}")
        }

        style.addSource(GeoJsonSource("bus-source"))

        val iconExpression = createBusIconExpression(isMapRotated)

        val busLayer = SymbolLayer("bus-layer", "bus-source").apply {
            withProperties(
                PropertyFactory.iconImage(iconExpression),
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.iconIgnorePlacement(true),
                PropertyFactory.iconSize(0.5f),
                PropertyFactory.iconOpacity(
                    interpolate(
                        linear(),
                        zoom(),
                        stop(11.99f, 0f),
                        stop(12f, 1f)
                    )
                ),
            )
        }
        style.addLayer(busLayer)
    }

    private fun requestFreshLocation() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            centerMapOnPoint(-36.606, -72.102)
            // findAndShowStopsAroundPoint(-36.606, -72.102) // Eliminado
            showAllStops() // Agregado
            updateBusMarkers(null)
            return
        }
        fusedLocationClient.getCurrentLocation(
            Priority.PRIORITY_HIGH_ACCURACY,
            CancellationTokenSource().token
        )
            .addOnSuccessListener { location: Location? ->
                if (location != null) {
                    map.animateCamera(
                        CameraUpdateFactory.newLatLngZoom(
                            LatLng(
                                location.latitude,
                                location.longitude
                            ), 15.0
                        )
                    )
                    updateBusMarkers(location)
                    findAndShowStopsAroundPoint(location.latitude, location.longitude)
                } else {
                    centerMapOnPoint(-36.606, -72.102)
                    // findAndShowStopsAroundPoint(-36.606, -72.102) // Eliminado
                    showAllStops() // Agregado
                    updateBusMarkers(null)
                }
            }
            .addOnFailureListener {
                centerMapOnPoint(-36.606, -72.102)
                // findAndShowStopsAroundPoint(-36.606, -72.102) // Eliminado
                showAllStops() // Agregado
                updateBusMarkers(null)
            }
    }

    // --- FUNCIÓN MODIFICADA: Ahora marca origen y destino por separado ---
    private fun showParaderosOnMap(paraderos: List<GtfsStop>) {
        if (!::map.isInitialized || mapStyle?.getSource("paradero-source") == null) {
            return
        }
        lifecycleScope.launch(Dispatchers.Default) {
            val paraderoFeatures = paraderos.map { stop ->
                val point = Point.fromLngLat(stop.location.longitude, stop.location.latitude)
                val feature = Feature.fromGeometry(point)
                feature.addStringProperty("stop_id", stop.stopId)

                // --- CORRECCIÓN AQUÍ ---
                // Separamos las propiedades
                val isSelected = (stop.stopId == currentSelectedStopId)
                val isDestination = (stop.stopId == currentDestinationStopId)

                feature.addBooleanProperty("selected", isSelected)
                feature.addBooleanProperty("isDestination", isDestination)

                feature
            }
            val featureCollection = FeatureCollection.fromFeatures(paraderoFeatures)
            withContext(Dispatchers.Main) {
                mapStyle?.getSourceAs<GeoJsonSource>("paradero-source")
                    ?.setGeoJson(featureCollection)
            }
        }
    }

    // --- NUEVA FUNCIÓN AUXILIAR: Muestra TODOS los paraderos ---
    private fun showAllStops() {
        showParaderosOnMap(GtfsDataManager.stops.values.toList())
    }

    override fun drawRoute(route: GtfsRoute, directionId: Int) {
        clearInfoMarker()
        clearDrawnElements()
        selectedRouteId = route.routeId
        selectedDirectionId = directionId
        currentDestinationStopId = null

        locationButtonState = 1

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
        if (style.getSource(sourceId) == null) {
            style.addSource(GeoJsonSource(sourceId, geoJsonString))
        }
        currentRouteSourceId = sourceId

        // 1. Capa Borde (Casing)
        val casingLayerId = "route-layer-casing-${route.routeId}-$directionId"
        if (style.getLayer(casingLayerId) == null) {
            val casingLayer = LineLayer(casingLayerId, sourceId).apply {
                withProperties(
                    PropertyFactory.lineColor(Color.WHITE),
                    PropertyFactory.lineWidth(9f),
                    PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                    PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
                )
            }
            val belowLayer = style.layers.find { it.id == "bus-layer" || it.id == "paradero-layer" }?.id
            if (belowLayer != null) {
                style.addLayerBelow(casingLayer, belowLayer)
            } else {
                style.addLayer(casingLayer)
            }
        }
        currentRouteCasingLayerId = casingLayerId

        // 2. Capa Color Ruta
        val layerId = "route-layer-${route.routeId}-$directionId"
        if (style.getLayer(layerId) == null) {
            val routeLayer = LineLayer(layerId, sourceId).apply {
                withProperties(
                    PropertyFactory.lineColor(Color.parseColor(if (route.color.startsWith("#")) route.color else "#${route.color}")),
                    PropertyFactory.lineWidth(5f),
                    PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                    PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
                )
            }
            style.addLayerAbove(routeLayer, casingLayerId)
        }
        currentRouteLayerId = layerId

        // 3. NUEVO: Capa de Flechas de Dirección (CORREGIDA)
        val arrowLayerId = "route-arrow-layer-${route.routeId}"
        if (style.getLayer(arrowLayerId) == null) {
            val arrowLayer = SymbolLayer(arrowLayerId, sourceId).apply {
                withProperties(
                    PropertyFactory.iconImage("route-arrow"),
                    // Usamos las propiedades correctas de MapLibre
                    PropertyFactory.symbolPlacement(Property.SYMBOL_PLACEMENT_LINE),
                    PropertyFactory.symbolSpacing(70f),
                    PropertyFactory.iconAllowOverlap(true),
                    PropertyFactory.iconIgnorePlacement(true),
                    PropertyFactory.iconSize(0.6f),
                    PropertyFactory.iconOpacity(1.0f)
                )
            }
            style.addLayerAbove(arrowLayer, layerId)
            currentRouteArrowLayerId = arrowLayerId
        }

        val paraderosDeRuta = GtfsDataManager.getStopsForRoute(route.routeId, directionId)
        showParaderosOnMap(paraderosDeRuta)

        val bounds = LatLngBounds.Builder().includes(routePoints).build()

        if (!preventRouteZoom) {
            map.easeCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100), 1500)
        }

        updateBusMarkers()
    }

    override fun clearRoutes(recenterToUser: Boolean) {
        // 1. Limpiezas básicas
        clearInfoMarker()
        clearDrawnElements()

        // 2. Resetear variables de estado
        selectedRouteId = null
        selectedDirectionId = null
        currentDestinationStopId = null // Resetear destino
        sharedViewModel.selectStop(null)
        sharedViewModel.setNearbyCalculationCenter(null)

        // 3. Desmarcar punto turístico seleccionado (vuelve al icono normal)
        updateTurismoSelection(null)

        // 4. Actualizar marcadores de bus
        updateBusMarkers()

        // (Opcional) Toast informativo
        // Toast.makeText(this, "Mapa restablecido", Toast.LENGTH_SHORT).show()

        // 5. Mostrar todos los paraderos nuevamente
        showAllStops()

        // 6. Lógica de cámara
        if (recenterToUser) {
            requestFreshLocation()
        }
        // NOTA: Se eliminó el 'else' para que la cámara no salte si limpias con el botón atrás.
        // El usuario se queda viendo la zona del mapa donde estaba.
    }

    private fun clearDrawnElements() {
        mapStyle?.let { style ->
            currentRouteLayerId?.let { if (style.getLayer(it) != null) style.removeLayer(it) }
            currentRouteCasingLayerId?.let { if (style.getLayer(it) != null) style.removeLayer(it) }

            // Borrar flechas
            currentRouteArrowLayerId?.let { if (style.getLayer(it) != null) style.removeLayer(it) }

            currentRouteSourceId?.let { if (style.getSource(it) != null) style.removeSource(it) }
        }
        currentRouteLayerId = null
        currentRouteCasingLayerId = null
        currentRouteArrowLayerId = null
        currentRouteSourceId = null
    }

    private fun startBusDataFetching() {
        lifecycleScope.launch {
            while (isActive) {
                if (::map.isInitialized && mapStyle != null && mapStyle!!.isFullyLoaded) {
                    val centerLocation = if (::map.isInitialized) {
                        val lastKnownLoc = try {
                            map.locationComponent.lastKnownLocation
                        } catch (e: Exception) {
                            null
                        }
                        if (lastKnownLoc != null) {
                            lastKnownLoc
                        } else {
                            map.cameraPosition.target?.let { target ->
                                Location("mapCenter").apply {
                                    latitude = target.latitude; longitude = target.longitude
                                }
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

    // 3. ACTUALIZAR MARCADORES (Limpio: Sin lógica de tiempos)
    private fun updateBusMarkers(centerLocation: Location? = null) {
        val feed = lastFeedMessage ?: return
        if (mapStyle == null || !mapStyle!!.isFullyLoaded) return

        lifecycleScope.launch(Dispatchers.Default) {
            val busFeatures = mutableListOf<Feature>()

            var trackedBusNewPosition: LatLng? = null
            var trackedBusNewTitle: String? = null
            var trackedBusNewSnippet: String? = null

            for (entity in feed.entityList) {
                if (entity.hasVehicle() && entity.vehicle.hasTrip() && entity.vehicle.hasPosition()) {
                    val vehicle = entity.vehicle
                    val trip = vehicle.trip
                    val position = vehicle.position

                    val thisBusId =
                        if (vehicle.hasVehicle() && vehicle.vehicle.hasId()) vehicle.vehicle.id else trip.tripId

                    // Si es el bus que seguimos
                    if (trackedBusId != null && thisBusId == trackedBusId) {
                        trackedBusNewPosition =
                            LatLng(position.latitude.toDouble(), position.longitude.toDouble())

                        val route = GtfsDataManager.routes[trip.routeId]
                        val directionStr = if (trip.directionId == 0) "Ida" else "Vuelta"
                        val nombreLinea = route?.shortName ?: ""

                        trackedBusNewTitle = "Línea $nombreLinea ($directionStr)"

                        // Snippet simple
                        trackedBusNewSnippet =
                            if (vehicle.hasVehicle() && vehicle.vehicle.hasLicensePlate()) {
                                "Patente: ${vehicle.vehicle.licensePlate}"
                            } else {
                                "Patente no disponible"
                            }
                    }

                    var shouldShow = false
                    if (selectedRouteId != null) {
                        if (selectedRouteId == trip.routeId && selectedDirectionId == trip.directionId) shouldShow =
                            true
                    } else {
                        if (centerLocation != null) {
                            val distance = distanceBetween(
                                centerLocation.latitude, centerLocation.longitude,
                                position.latitude.toDouble(), position.longitude.toDouble()
                            )
                            if (distance <= 1000) shouldShow = true
                        }
                    }
                    if (trackedBusId != null && thisBusId == trackedBusId) shouldShow = true

                    if (shouldShow) {
                        val point = Point.fromLngLat(
                            position.longitude.toDouble(),
                            position.latitude.toDouble()
                        )
                        val feature = Feature.fromGeometry(point)
                        feature.addStringProperty("routeId", trip.routeId)
                        feature.addNumberProperty("directionId", trip.directionId)
                        feature.addNumberProperty("bearing", position.bearing)
                        if (vehicle.hasVehicle() && vehicle.vehicle.hasLicensePlate()) {
                            feature.addStringProperty("licensePlate", vehicle.vehicle.licensePlate)
                        }
                        busFeatures.add(feature)
                    }
                }
            }

            withContext(Dispatchers.Main) {
                if (mapStyle != null && mapStyle!!.isFullyLoaded) {
                    mapStyle?.getSourceAs<GeoJsonSource>("bus-source")
                        ?.setGeoJson(FeatureCollection.fromFeatures(busFeatures))
                }

                if (trackedBusNewPosition != null && trackedBusNewTitle != null) {
                    currentInfoMarker?.let { map.removeMarker(it) }

                    val iconFactory = IconFactory.getInstance(this@MainActivity)
                    val transparentIcon =
                        iconFactory.fromBitmap(Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888))

                    val newMarker = map.addMarker(
                        MarkerOptions()
                            .position(trackedBusNewPosition!!)
                            .title(trackedBusNewTitle)
                            .snippet(trackedBusNewSnippet)
                            .icon(transparentIcon)
                    )

                    currentInfoMarker = newMarker
                    map.selectMarker(newMarker)
                }
            }
        }
    }

    @SuppressWarnings("MissingPermission")
    private fun enableLocation(style: Style) {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            showUserLocation()
        } else {
            requestLocationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    @SuppressWarnings("MissingPermission")
    private fun showUserLocation() {
        if (!::map.isInitialized || map.style == null || !map.style!!.isFullyLoaded) {
            Log.w("ShowUserLocation", "Mapa o estilo no listos.")
            if (::map.isInitialized && map.style != null && !map.style!!.isFullyLoaded) {
                lifecycleScope.launch { delay(500); showUserLocation() }
            }
            return
        }
        try {
            val locationComponent = map.locationComponent
            locationComponent.activateLocationComponent(
                LocationComponentActivationOptions.builder(this, map.style!!)
                    .useDefaultLocationEngine(true)
                    .build()
            )
            locationComponent.isLocationComponentEnabled = true
            locationComponent.cameraMode = CameraMode.TRACKING
            locationComponent.renderMode = RenderMode.NORMAL
            requestFreshLocation()
        } catch (e: Exception) {
            Log.e("ShowUserLocation", "Error al activar LocationComponent", e)
        }
    }


    // ===== FUNCIONES AUXILIARES =====

    private fun findAllNearbyStops(
        lat: Double,
        lon: Double,
        radiusInMeters: Float = 500f
    ): List<GtfsStop> {
        return GtfsDataManager.stops.values
            .map { stop ->
                Pair(
                    stop,
                    distanceBetween(lat, lon, stop.location.latitude, stop.location.longitude)
                )
            }
            .filter { it.second <= radiusInMeters }
            .sortedBy { it.second }
            .map { it.first }
    }

    private fun findNearestShapePointIndex(shapePoints: List<LatLng>, stop: GtfsStop): Int {
        return shapePoints.indices.minByOrNull { index ->
            distanceBetween(
                stop.location.latitude,
                stop.location.longitude,
                shapePoints[index].latitude,
                shapePoints[index].longitude
            )
        } ?: -1
    }

    override fun drawRouteSegment(
        route: GtfsRoute,
        directionId: Int,
        stopA: GtfsStop,
        stopB: GtfsStop
    ) {
        clearDrawnElements()
        selectedRouteId = route.routeId
        selectedDirectionId = directionId
        currentDestinationStopId = stopB.stopId // --- GUARDAMOS EL DESTINO ---

        val style = mapStyle ?: return
        Log.d("DrawRouteSegment", "Dibujando segmento A->B para ${route.shortName}")

        val trip =
            GtfsDataManager.trips.values.find { it.routeId == route.routeId && it.directionId == directionId }
        val shapeId = trip?.shapeId
        val routePoints = GtfsDataManager.shapes[shapeId]

        if (routePoints == null || routePoints.isEmpty()) {
            Toast.makeText(this, "Trazado no disponible.", Toast.LENGTH_SHORT).show()
            return
        }

        val indexA = findNearestShapePointIndex(routePoints, stopA)
        val indexB = findNearestShapePointIndex(routePoints, stopB)

        if (indexA == -1 || indexB == -1 || indexA >= indexB) {
            Log.w(
                "DrawRouteSegment",
                "Error al encontrar segmento (A:$indexA, B:$indexB). Dibujando ruta completa."
            )
            drawRoute(route, directionId)
            return
        }

        val slicedRoutePoints = routePoints.subList(indexA, indexB + 1)

        val coordinates = slicedRoutePoints.joinToString(
            prefix = "[",
            postfix = "]"
        ) { "[${it.longitude},${it.latitude}]" }
        val geoJsonString =
            """{"type": "Feature", "geometry": {"type": "LineString", "coordinates": $coordinates}}"""

        val sourceId = "route-source-segment"
        if (style.getSource(sourceId) == null) {
            style.addSource(GeoJsonSource(sourceId, geoJsonString))
        }
        currentRouteSourceId = sourceId

        // --- 1. CAPA DE BORDE (CASING) PARA EL SEGMENTO ---
        val casingLayerId = "route-layer-segment-casing"
        if (style.getLayer(casingLayerId) == null) {
            val casingLayer = LineLayer(casingLayerId, sourceId).apply {
                withProperties(
                    PropertyFactory.lineColor(Color.WHITE),
                    PropertyFactory.lineWidth(10f), // Un poco más grueso para el segmento
                    PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                    PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                    PropertyFactory.lineOpacity(0.8f)
                )
            }
            val belowLayer =
                style.layers.find { it.id == "bus-layer" || it.id == "paradero-layer" || it.id == "paradero-clusters" }?.id
            if (belowLayer != null) {
                style.addLayerBelow(casingLayer, belowLayer)
            } else {
                style.addLayer(casingLayer)
            }
        }
        currentRouteCasingLayerId = casingLayerId

        // --- 2. CAPA NORMAL PARA EL SEGMENTO ---
        val layerId = "route-layer-segment"
        if (style.getLayer(layerId) == null) {
            val routeLayer = LineLayer(layerId, sourceId).apply {
                withProperties(
                    PropertyFactory.lineColor(Color.parseColor(if (route.color.startsWith("#")) route.color else "#${route.color}")),
                    PropertyFactory.lineWidth(6f),
                    PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                    PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                    PropertyFactory.lineOpacity(0.8f)
                )
            }
            style.addLayerAbove(routeLayer, casingLayerId)
        }
        currentRouteLayerId = layerId

        showParaderosOnMap(listOf(stopA, stopB))
        Log.d("DrawRouteSegment", "Mostrando paraderos A y B.")

        if (slicedRoutePoints.isNotEmpty()) {
            val bounds = LatLngBounds.Builder().includes(slicedRoutePoints).build()
            map.easeCamera(CameraUpdateFactory.newLatLngBounds(bounds, 150), 1000)
        }
    }

    override fun onGetDirectionsClicked(punto: PuntoTuristico) {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(
                this,
                "Se necesita permiso de ubicación para 'Cómo llegar'",
                Toast.LENGTH_LONG
            ).show()
            requestLocationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            return
        }

        Log.d("Directions", "Buscando ruta para ${punto.nombre}")
        fusedLocationClient.getCurrentLocation(
            Priority.PRIORITY_HIGH_ACCURACY,
            CancellationTokenSource().token
        )
            .addOnSuccessListener { location: Location? ->
                if (location == null) {
                    Toast.makeText(
                        this,
                        "No se pudo obtener tu ubicación actual",
                        Toast.LENGTH_LONG
                    ).show()
                    return@addOnSuccessListener
                }

                val userLat = location.latitude
                val userLon = location.longitude
                val puntoLat = punto.latitud
                val puntoLon = punto.longitud

                var stopsNearA: List<GtfsStop> = emptyList()
                var stopsNearB: List<GtfsStop> = emptyList()
                val searchRadii = listOf(500f, 1000f, 2000f)
                var radiusUsedA: Float? = null
                var radiusUsedB: Float? = null

                for (radius in searchRadii) {
                    stopsNearA = findAllNearbyStops(userLat, userLon, radius)
                    if (stopsNearA.isNotEmpty()) {
                        radiusUsedA = radius
                        break
                    }
                }

                for (radius in searchRadii) {
                    stopsNearB = findAllNearbyStops(puntoLat, puntoLon, radius)
                    if (stopsNearB.isNotEmpty()) {
                        radiusUsedB = radius
                        break
                    }
                }

                if (stopsNearA.isEmpty()) {
                    Log.w("Directions", "No se encontraron paraderos (A) cerca del usuario")
                    Toast.makeText(
                        this,
                        "No se encontraron paraderos cercanos a tu ubicación",
                        Toast.LENGTH_LONG
                    ).show()
                    return@addOnSuccessListener
                }
                if (stopsNearB.isEmpty()) {
                    Log.w("Directions", "No se encontraron paraderos (B) cerca del destino")
                    Toast.makeText(
                        this,
                        "No se encontraron paraderos cercanos al destino",
                        Toast.LENGTH_LONG
                    ).show()
                    return@addOnSuccessListener
                }

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
                        val commonRoutes =
                            routesB.filter { routeSetA.contains(it.route.routeId to it.directionId) }

                        if (commonRoutes.isNotEmpty()) {
                            commonRoutesFound = true
                        }

                        for (commonRoute in commonRoutes) {
                            val stopSequence = GtfsDataManager.getStopsForRoute(
                                commonRoute.route.routeId,
                                commonRoute.directionId
                            )
                            val indexA = stopSequence.indexOfFirst { it.stopId == stopA.stopId }
                            val indexB = stopSequence.indexOfFirst { it.stopId == stopB.stopId }

                            if (indexA != -1 && indexB != -1 && indexA < indexB) {
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

                    Log.d("Directions", "Ruta válida: ${route.shortName}")
                    showCustomNotification("Toma la Línea ${route.shortName} en ${bestStopA.name}")

                    drawRouteSegment(route, directionId, bestStopA, bestStopB)
                    sharedViewModel.selectStop(bestStopA.stopId)

                } else {
                    val toastMessage = if (commonRoutesFound) {
                        "Se encontraron rutas, pero ninguna va en la dirección correcta (A -> B)"
                    } else {
                        "No se encontró una ruta de bus directa"
                    }
                    showCustomNotification(toastMessage)
                    Log.d("Directions", "No se encontró ruta válida A -> B.")

                    val boundsBuilder = LatLngBounds.Builder()
                    stopsNearA.firstOrNull()?.let {
                        boundsBuilder.include(
                            LatLng(
                                it.location.latitude,
                                it.location.longitude
                            )
                        )
                    }
                    stopsNearB.firstOrNull()?.let {
                        boundsBuilder.include(
                            LatLng(
                                it.location.latitude,
                                it.location.longitude
                            )
                        )
                    }
                    boundsBuilder.include(LatLng(userLat, userLon))
                    boundsBuilder.include(LatLng(puntoLat, puntoLon))

                    try {
                        val bounds = boundsBuilder.build()
                        map.easeCamera(CameraUpdateFactory.newLatLngBounds(bounds, 150), 1000)
                    } catch (e: Exception) {
                        Log.e("Directions", "Error al crear bounds", e)
                    }
                }
            }
            .addOnFailureListener {
                Log.e("Directions", "Error al obtener ubicación", it)
                Toast.makeText(this, "Error al obtener ubicación", Toast.LENGTH_LONG).show()
            }
    }

    // --- Ciclo de Vida (sin cambios) ---
    override fun onStart() {
        super.onStart(); mapView.onStart()
    }

    override fun onResume() {
        super.onResume(); mapView.onResume()
    }

    override fun onPause() {
        super.onPause(); mapView.onPause()
    }

    override fun onStop() {
        super.onStop(); mapView.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState); mapView.onSaveInstanceState(outState)
    }

    override fun onLowMemory() {
        super.onLowMemory(); mapView.onLowMemory()
    }

    override fun onDestroy() {
        super.onDestroy(); mapView.onDestroy()
    }

    private fun clearInfoMarker() {
        currentInfoMarker?.let {
            if (::map.isInitialized) map.deselectMarker(it)
            it.remove()
        }
        currentInfoMarker = null

        // Cortamos el hilo (dejamos de rastrear)
        trackedBusId = null
    }

    private inner class CustomInfoWindowAdapter : MapLibreMap.InfoWindowAdapter {

        override fun getInfoWindow(marker: Marker): View? {
            if (marker != currentInfoMarker) {
                return null
            }

            val view = layoutInflater.inflate(R.layout.custom_bus_info_window, null)
            val tvLine: TextView = view.findViewById(R.id.tv_info_line)
            val tvPatent: TextView = view.findViewById(R.id.tv_info_patent)

            tvLine.text = marker.title
            tvPatent.text = marker.snippet

            if (marker.snippet.isNullOrBlank()) {
                tvPatent.visibility = View.GONE
            } else {
                tvPatent.visibility = View.VISIBLE
            }

            return view
        }
    }

    private fun createBusIconExpression(isRotated: Boolean): Expression {
        val cases = mutableListOf<Expression>()

        val routeIds = listOf(
            "466",
            "467",
            "468",
            "469",
            "470",
            "471",
            "472",
            "954",
            "478",
            "477",
            "473",
            "476",
            "474",
            "475"
        )

        val iconForIda = if (isRotated) "normal" else "espejo"
        val iconForVuelta = if (isRotated) "espejo" else "normal"

        routeIds.forEach { routeId ->
            cases.add(eq(get("routeId"), literal(routeId)))

            cases.add(
                switchCase(
                    eq(get("directionId"), 0), literal("bus-icon-$routeId-$iconForIda"),
                    eq(get("directionId"), 1), literal("bus-icon-$routeId-$iconForVuelta"),
                    literal("bus-icon-$routeId-normal")
                )
            )
        }

        cases.add(
            switchCase(
                eq(get("directionId"), 0), literal("bus-icon-default-$iconForIda"),
                eq(get("directionId"), 1), literal("bus-icon-default-$iconForVuelta"),
                literal("bus-icon-default-normal")
            )
        )

        return switchCase(*cases.toTypedArray())
    }

    private fun cerrarSesion() {
        auth.signOut()
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}
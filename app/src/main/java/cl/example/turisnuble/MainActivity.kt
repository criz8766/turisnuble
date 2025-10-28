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
import org.maplibre.android.style.expressions.Expression
import retrofit2.Retrofit
import retrofit2.converter.protobuf.ProtoConverterFactory

class MainActivity : AppCompatActivity(),
    OnMapReadyCallback,
    RouteDrawer,
    TurismoActionHandler,
    DetalleTurismoNavigator,
    MapMover {

    // --- Variables (sin cambios) ---
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var mapView: MapView
    private lateinit var map: MapLibreMap
    private lateinit var locationFab: FloatingActionButton
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<FrameLayout>
    private lateinit var tabLayout: TabLayout
    private lateinit var viewPager: ViewPager2
    private lateinit var pagerAdapter: ViewPagerAdapter
    private val sharedViewModel: SharedViewModel by viewModels()
    private var selectedRouteId: String? = null
    private var selectedDirectionId: Int? = null
    private var lastFeedMessage: GtfsRealtime.FeedMessage? = null
    private var currentRouteSourceId: String? = null
    private var currentRouteLayerId: String? = null
    private var mapStyle: Style? = null
    private var currentSelectedStopId: String? = null
    private val turismoMarkers = mutableListOf<Marker>()

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

        GtfsDataManager.loadData(assets)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        val bottomSheet: FrameLayout = findViewById(R.id.bottom_sheet)
        bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet)
        val screenHeight = resources.displayMetrics.heightPixels
        bottomSheetBehavior.maxHeight = (screenHeight * 0.30).toInt()
        val peekHeightInPixels = (46 * resources.displayMetrics.density).toInt()
        bottomSheetBehavior.peekHeight = peekHeightInPixels
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED

        setupTabs()
        setupBottomSheetCallback()

        mapView = findViewById(R.id.mapView)
        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync(this)

        locationFab = findViewById(R.id.location_fab)
        locationFab.setOnClickListener {
            sharedViewModel.setNearbyCalculationCenter(null)
            sharedViewModel.selectStop(null)
            clearRoutes(recenterToUser = true)
        }

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

    // --- setupBottomSheetCallback (sin cambios) ---
    private fun setupBottomSheetCallback() {
        bottomSheetBehavior = BottomSheetBehavior.from(findViewById(R.id.bottom_sheet))
        // ... (listeners si son necesarios) ...
    }

    // --- onMapReady (sin cambios) ---
    override fun onMapReady(mapLibreMap: MapLibreMap) {
        this.map = mapLibreMap
        val styleUrl = "https://basemaps.cartocdn.com/gl/positron-gl-style/style.json"

        map.setStyle(styleUrl) { style ->
            this.mapStyle = style
            Log.d("MainActivity", "Estilo cargado.")

            loadMapIcons(style)
            setupParaderoLayer(style)
            setupBusLayer(style)
            addTurismoMarkers()

            enableLocation(style)
            startBusDataFetching()

            map.addOnMapClickListener { point ->
                val screenPoint = map.projection.toScreenLocation(point)
                val features = map.queryRenderedFeatures(screenPoint, "paradero-layer")
                if (features.isNotEmpty()) {
                    val stopId = features[0].getStringProperty("stop_id")
                    if (stopId != null) {
                        handleParaderoClick(stopId)
                    }
                }
                true
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
        sharedViewModel.selectStop(stopId)
        GtfsDataManager.stops[stopId]?.let { stop ->
            centerMapOnPoint(stop.location.latitude, stop.location.longitude)
        }
        val routesForStop = GtfsDataManager.getRoutesForStop(stopId)
        if (routesForStop.isNotEmpty()) {
            Toast.makeText(this, "Mostrando rutas para el paradero $stopId", Toast.LENGTH_SHORT).show()
            if (supportFragmentManager.backStackEntryCount > 0) {
                supportFragmentManager.popBackStack()
                findViewById<View>(R.id.bottom_sheet_content).visibility = View.VISIBLE
            }
            sharedViewModel.setRouteFilter(routesForStop)
            viewPager.setCurrentItem(2, true)
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
        } else {
            Toast.makeText(this, "No se encontraron rutas para el paradero $stopId", Toast.LENGTH_SHORT).show()
            sharedViewModel.setRouteFilter(emptyList()) // <-- CORRECCIÓN: Pasa lista vacía
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
    // --- FIN CORRECCIÓN ---


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
    private fun setupBusLayer(style: Style) {
        val routeIcons = mapOf(
            "467" to R.drawable.linea_2, "468" to R.drawable.linea_3, "469" to R.drawable.linea_4,
            "470" to R.drawable.linea_6, "471" to R.drawable.linea_7, "472" to R.drawable.linea_8,
            "954" to R.drawable.linea_7, "478" to R.drawable.linea_14, "477" to R.drawable.linea_14,
            "473" to R.drawable.linea_10, "476" to R.drawable.linea_13, "474" to R.drawable.linea_13,
            "475" to R.drawable.linea_13, "466" to R.drawable.linea_1,
        )
        try {
            style.addImage("bus-icon-default", BitmapFactory.decodeResource(resources, R.drawable.ic_bus))
            routeIcons.forEach { (routeId, resourceId) ->
                try {
                    style.addImage("bus-icon-$routeId", BitmapFactory.decodeResource(resources, resourceId))
                } catch (e: Exception) { Log.e("SetupBusLayer", "Error al cargar icono para ruta $routeId: ${e.message}") }
            }
        } catch (e: Exception) { Log.e("SetupBusLayer", "Error al cargar los íconos: ${e.message}") }
        style.addSource(GeoJsonSource("bus-source"))
        val cases = routeIcons.keys.flatMap { routeId ->
            listOf(eq(get("routeId"), literal(routeId)), literal("bus-icon-$routeId"))
        }.toTypedArray()
        val busLayer = SymbolLayer("bus-layer", "bus-source").apply {
            withProperties(
                PropertyFactory.iconImage(switchCase(*cases, literal("bus-icon-default"))),
                PropertyFactory.iconAllowOverlap(true), PropertyFactory.iconIgnorePlacement(true),
                PropertyFactory.iconSize(0.5f),
                PropertyFactory.iconOpacity(interpolate(linear(), zoom(), stop(11.99f, 0f), stop(12f, 1f)))
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
                    // --- ¡¡¡CORRECCIÓN AQUÍ!!! ---
                    // Vuelvo a los valores que tenías originalmente (o similares)
                    PropertyFactory.iconSize(
                        switchCase(
                            eq(get("selected"), literal(true)), literal(0.07f),
                            literal(0.05f)
                        )
                    ),
                    // --- FIN CORRECCIÓN ---
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
    // --- FIN CORRECCIÓN ---


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
        style.addSource(GeoJsonSource(sourceId, geoJsonString))
        currentRouteSourceId = sourceId
        val layerId = "route-layer-${route.routeId}-$directionId"
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

        currentRouteLayerId = layerId
        val paraderosDeRuta = GtfsDataManager.getStopsForRoute(route.routeId, directionId)
        showParaderosOnMap(paraderosDeRuta)

        val bounds = LatLngBounds.Builder().includes(routePoints).build()

        map.easeCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100), 1500)
        updateBusMarkers()
    }

    override fun clearRoutes(recenterToUser: Boolean) {
        clearDrawnElements()
        selectedRouteId = null
        selectedDirectionId = null
        sharedViewModel.selectStop(null)
        sharedViewModel.setNearbyCalculationCenter(null)

        // --- ¡¡¡CORRECCIÓN CLAVE AQUÍ!!! ---
        // Pasamos null para que RutasFragment muestre TODAS las rutas

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
        showParaderosOnMap(emptyList())
    }

    private fun addTurismoMarkers() {
        val iconFactory = IconFactory.getInstance(this@MainActivity)
        val originalBitmap = BitmapFactory.decodeResource(resources, R.drawable.ic_turismo)
        val scaledBitmap = Bitmap.createScaledBitmap(originalBitmap, 80, 80, false)
        val icon = iconFactory.fromBitmap(scaledBitmap)

        turismoMarkers.forEach { it.remove() }
        turismoMarkers.clear()

        DatosTurismo.puntosTuristicos.forEach { punto ->
            val marker = map.addMarker(
                MarkerOptions().position(LatLng(punto.latitud, punto.longitud))
                    .title(punto.nombre)
                    .snippet(punto.id.toString()) // <-- CAMBIO: Usar ID en snippet
                    .icon(icon)
            )
            turismoMarkers.add(marker)
        }

        map.setOnMarkerClickListener { marker ->
            marker.snippet?.toIntOrNull()?.let { puntoId ->
                DatosTurismo.puntosTuristicos.find { it.id == puntoId }?.let { punto ->
                    showTurismoDetail(punto)
                    return@setOnMarkerClickListener true
                }
            }
            return@setOnMarkerClickListener false
        }
    }


    private fun startBusDataFetching() {
        lifecycleScope.launch {
            while (isActive) {
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
                delay(20000)
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
                        feature.addStringProperty("routeId", trip.routeId)
                        feature.addNumberProperty("bearing", position.bearing)
                        busFeatures.add(feature)
                    }
                }
            }
            val featureCollection = FeatureCollection.fromFeatures(busFeatures)
            withContext(Dispatchers.Main) {
                mapStyle?.getSourceAs<GeoJsonSource>("bus-source")?.setGeoJson(featureCollection)
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
            locationComponent.renderMode = RenderMode.COMPASS
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
     * CORREGIDO: Usa List<LatLng> y 'latitude'/'longitude'
     */
    private fun findNearestShapePointIndex(shapePoints: List<LatLng>, stop: GtfsStop): Int {
        return shapePoints.indices.minByOrNull { index ->
            distanceBetween(stop.location.latitude, stop.location.longitude, shapePoints[index].latitude, shapePoints[index].longitude)
        } ?: -1
    }

    /**
     * ¡NUEVA FUNCIÓN!
     * Dibuja solo el segmento de la ruta desde el Paradero A hasta el Paradero B.
     * CORREGIDO: Usa List<LatLng> y 'latitude'/'longitude'.
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
        currentRouteSourceId = sourceId
        style.addSource(GeoJsonSource(sourceId, geoJsonString))

        val layerId = "route-layer-segment"
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

        showParaderosOnMap(listOf(stopA, stopB))
        Log.d("DrawRouteSegment", "Mostrando paraderos A y B.")

        if (slicedRoutePoints.isNotEmpty()) {
            val bounds = LatLngBounds.Builder().includes(slicedRoutePoints).build()
            map.easeCamera(CameraUpdateFactory.newLatLngBounds(bounds, 150), 1000)
        }
    }

    /**
     * Implementación de la interfaz para el botón "Cómo Llegar".
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

                val stopsNearA = findAllNearbyStops(location.latitude, location.longitude, 500f)
                val stopsNearB = findAllNearbyStops(punto.latitud, punto.longitud, 500f)

                if (stopsNearA.isEmpty() || stopsNearB.isEmpty()) {
                    Toast.makeText(this, "No se encontraron paraderos cercanos a ti o al destino", Toast.LENGTH_LONG).show()
                    return@addOnSuccessListener
                }

                Log.d("Directions", "Buscando conexión entre ${stopsNearA.size} (A) y ${stopsNearB.size} (B) paraderos")

                var bestRouteFound: DisplayRouteInfo? = null
                var bestStopA: GtfsStop? = null
                var bestStopB: GtfsStop? = null
                var commonRoutesFound = false // Para saber si encontramos rutas aunque sea en mala dirección

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
                    Toast.makeText(this, "Toma la Línea ${route.shortName} en ${bestStopA.name}", Toast.LENGTH_LONG).show()

                    drawRouteSegment(route, directionId, bestStopA, bestStopB)

                    sharedViewModel.selectStop(bestStopA.stopId) // Resalta paradero A

                } else {
                    val toastMessage = if (commonRoutesFound) {
                        "Se encontraron rutas, pero ninguna va en la dirección correcta (A -> B)"
                    } else {
                        "No se encontró una ruta de bus directa"
                    }
                    Toast.makeText(this, toastMessage, Toast.LENGTH_LONG).show()
                    Log.d("Directions", "No se encontró ruta válida A -> B en el radio de 500m.")

                    val bounds = LatLngBounds.Builder()
                        .include(LatLng(stopsNearA.first().location.latitude, stopsNearA.first().location.longitude))
                        .include(LatLng(stopsNearB.first().location.latitude, stopsNearB.first().location.longitude))
                        .build()
                    map.easeCamera(CameraUpdateFactory.newLatLngBounds(bounds, 150), 1000)
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
}
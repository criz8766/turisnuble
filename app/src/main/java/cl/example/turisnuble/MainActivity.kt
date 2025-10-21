package cl.example.turisnuble

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.Color
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

    private val apiService: GtfsApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://datamanager.dtpr.transapp.cl/")
            .addConverterFactory(ProtoConverterFactory.create())
            .build()
            .create(GtfsApiService::class.java)
    }

    private val requestLocationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                showUserLocation()
                requestFreshLocation()
            } else {
                Toast.makeText(this, "Permiso de ubicación denegado.", Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MapLibre.getInstance(this)
        setContentView(R.layout.activity_main)

        GtfsDataManager.loadData(assets)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        val bottomSheet: FrameLayout = findViewById(R.id.bottom_sheet)
        bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet)
        val screenHeight = resources.displayMetrics.heightPixels
        bottomSheetBehavior.maxHeight = screenHeight / 3
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
            requestFreshLocation()
        }

        val bottomSheetContent = findViewById<View>(R.id.bottom_sheet_content)

        supportFragmentManager.addOnBackStackChangedListener {
            if (supportFragmentManager.backStackEntryCount == 0) {
                bottomSheetContent.visibility = View.VISIBLE
            } else {
                bottomSheetContent.visibility = View.GONE
            }
        }

        sharedViewModel.nearbyStops.observe(this) { nearbyStops ->
            if (selectedRouteId == null) {
                showParaderosOnMap(nearbyStops)
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
        bottomSheetBehavior = BottomSheetBehavior.from(findViewById(R.id.bottom_sheet))
        bottomSheetBehavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {}
            override fun onSlide(bottomSheet: View, slideOffset: Float) {}
        })
    }

    override fun onMapReady(mapLibreMap: MapLibreMap) {
        this.map = mapLibreMap
        val styleUrl = "https://basemaps.cartocdn.com/gl/positron-gl-style/style.json"

        map.setStyle(styleUrl) { style ->
            this.mapStyle = style
            enableLocation(style)
            startBusDataFetching()
            addTurismoMarkers()
            setupBusLayer(style)
            setupParaderoLayer(style)

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
            }
            sharedViewModel.setRouteFilter(routesForStop)
            viewPager.setCurrentItem(2, true)
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
        } else {
            Toast.makeText(this, "No se encontraron rutas para el paradero $stopId", Toast.LENGTH_SHORT).show()
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
                    // --- ¡LÓGICA MEJORADA! ---
                    // 1. Muestra el detalle del punto turístico.
                    doShowTurismoDetail(punto)
                    // 2. Busca y muestra los paraderos alrededor de ese punto.
                    findAndShowStopsAroundPoint(punto.latitud, punto.longitud)
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

    // --- ¡NUEVA FUNCIÓN! ---
    // Busca y muestra los paraderos en un radio de 500 metros desde un punto específico.
    private fun findAndShowStopsAroundPoint(lat: Double, lon: Double) {
        val paraderosCercanos = GtfsDataManager.stops.values
            .map { stop ->
                Pair(stop, distanceBetween(lat, lon, stop.location.latitude, stop.location.longitude))
            }
            .filter { it.second <= 500 } // 500 metros de radio
            .map { it.first }

        showParaderosOnMap(paraderosCercanos)
    }

    fun showRouteDetail(routeId: String, directionId: Int) {
        val fragment = DetalleRutaFragment.newInstance(routeId, directionId)
        findViewById<View>(R.id.bottom_sheet_content).visibility = View.GONE
        supportFragmentManager.beginTransaction()
            .add(R.id.bottom_sheet, fragment)
            .addToBackStack(null)
            .commit()
    }

    override fun showRoutesForStop(stopId: String) {
        val routesForStop = GtfsDataManager.getRoutesForStop(stopId)
        sharedViewModel.setRouteFilter(routesForStop)
        viewPager.setCurrentItem(2, true)
        supportFragmentManager.popBackStack("turismo_detail", 1)
    }

    override fun hideDetailFragment() {
        supportFragmentManager.popBackStack("turismo_detail", 1)
    }

    override fun centerMapOnPoint(lat: Double, lon: Double) {
        if (::map.isInitialized) {
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(lat, lon), 16.0), 1500)
        }
    }

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

    private fun setupParaderoLayer(style: Style) {
        try {
            style.addImage("paradero-icon", BitmapFactory.decodeResource(resources, R.drawable.ic_paradero))
            style.addImage("paradero-icon-selected", BitmapFactory.decodeResource(resources, R.drawable.ic_paradero_selected))
        } catch (e: Exception) {
            Log.w("SetupParadero", "No se encontró 'ic_paradero.png' o 'ic_paradero_selected.png'.")
        }
        style.addSource(GeoJsonSource("paradero-source"))
        val paraderoLayer = SymbolLayer("paradero-layer", "paradero-source").apply {
            withProperties(
                PropertyFactory.iconImage(
                    switchCase(
                        eq(get("selected"), literal(true)), literal("paradero-icon-selected"),
                        literal("paradero-icon")
                    )
                ),
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.iconIgnorePlacement(true),
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
                PropertyFactory.textOffset(arrayOf(0f, 1.5f))
            )
        }
        style.addLayer(paraderoLayer)
    }

    private fun requestFreshLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, CancellationTokenSource().token)
            .addOnSuccessListener { location: Location? ->
                if (location != null) {
                    map.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(location.latitude, location.longitude), 15.0))
                    updateBusMarkers()
                }
            }
    }

    private fun showParaderosOnMap(paraderos: List<GtfsStop>) {
        val paraderoFeatures = paraderos.map { stop ->
            val point = Point.fromLngLat(stop.location.longitude, stop.location.latitude)
            val feature = Feature.fromGeometry(point)
            feature.addStringProperty("stop_id", stop.stopId)
            feature.addBooleanProperty("selected", stop.stopId == currentSelectedStopId)
            feature
        }
        mapStyle?.getSourceAs<GeoJsonSource>("paradero-source")?.setGeoJson(FeatureCollection.fromFeatures(paraderoFeatures))
    }

    override fun drawRoute(route: GtfsRoute, directionId: Int) {
        clearDrawnElements()
        selectedRouteId = route.routeId
        selectedDirectionId = directionId
        val style = mapStyle ?: return
        val tripKey = "${route.routeId}_$directionId"
        val shapeId = GtfsDataManager.trips[tripKey]?.shapeId
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
                PropertyFactory.lineColor(Color.parseColor(route.color)),
                PropertyFactory.lineWidth(5f),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
            )
        }
        style.addLayer(routeLayer)
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
        updateBusMarkers()
        Toast.makeText(this, "Mostrando buses cercanos", Toast.LENGTH_SHORT).show()
        if (recenterToUser) {
            requestFreshLocation()
        }
        sharedViewModel.nearbyStops.value?.let { showParaderosOnMap(it) }
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
        val scaledBitmap = android.graphics.Bitmap.createScaledBitmap(originalBitmap, 80, 80, false)
        val icon = iconFactory.fromBitmap(scaledBitmap)
        DatosTurismo.puntosTuristicos.forEach { punto ->
            turismoMarkers.add(map.addMarker(
                MarkerOptions().position(LatLng(punto.latitud, punto.longitud))
                    .title(punto.nombre).snippet(punto.direccion).icon(icon)
            ))
        }
    }

    private fun startBusDataFetching() {
        lifecycleScope.launch {
            while (isActive) {
                fetchBusData()
                delay(40000)
            }
        }
    }

    private fun fetchBusData() {
        lifecycleScope.launch {
            try {
                val response = apiService.getVehiclePositions("chillan", "9f057ee0-3807-4340-aefa-17553326eec0")
                if (response.isSuccessful) {
                    response.body()?.let {
                        lastFeedMessage = it
                        sharedViewModel.setFeedMessage(it)
                    }
                    updateBusMarkers()
                } else {
                    Log.e("API_ERROR", "Error en la respuesta: ${response.code()}")
                }
            } catch (e: Exception) {
                Log.e("API_ERROR", "Fallo en la conexión", e)
            }
        }
    }

    private fun distanceBetween(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val results = FloatArray(1)
        Location.distanceBetween(lat1, lon1, lat2, lon2, results)
        return results[0]
    }

    private fun updateBusMarkers() {
        val feed = lastFeedMessage ?: return
        val userLocation = map.locationComponent.lastKnownLocation
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
                    if (userLocation != null) {
                        if (distanceBetween(userLocation.latitude, userLocation.longitude, position.latitude.toDouble(), position.longitude.toDouble()) <= 1000) {
                            shouldShow = true
                        }
                    }
                }
                if (shouldShow) {
                    val point = Point.fromLngLat(position.longitude.toDouble(), position.latitude.toDouble())
                    val feature = Feature.fromGeometry(point)
                    feature.addStringProperty("routeId", trip.routeId)
                    busFeatures.add(feature)
                }
            }
        }
        mapStyle?.getSourceAs<GeoJsonSource>("bus-source")?.setGeoJson(FeatureCollection.fromFeatures(busFeatures))
    }

    private fun enableLocation(style: Style) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            showUserLocation()
            requestFreshLocation()
        } else {
            requestLocationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    @SuppressWarnings("MissingPermission")
    private fun showUserLocation() {
        map.locationComponent.activateLocationComponent(LocationComponentActivationOptions.builder(this, map.style!!).build())
        map.locationComponent.isLocationComponentEnabled = true
        map.locationComponent.cameraMode = CameraMode.TRACKING
        map.locationComponent.renderMode = RenderMode.COMPASS
    }

    override fun onStart() { super.onStart(); mapView.onStart() }
    override fun onResume() { super.onResume(); mapView.onResume() }
    override fun onPause() { super.onPause(); mapView.onPause() }
    override fun onStop() { super.onStop(); mapView.onStop() }
    override fun onSaveInstanceState(outState: Bundle) { super.onSaveInstanceState(outState); mapView.onSaveInstanceState(outState) }
    override fun onLowMemory() { super.onLowMemory(); mapView.onLowMemory() }
    override fun onDestroy() { super.onDestroy(); mapView.onDestroy() }
}
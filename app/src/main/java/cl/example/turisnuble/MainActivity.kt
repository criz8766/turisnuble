// MainActivity.kt - Versión COMPLETA y actualizada

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
import androidx.activity.viewModels // Import necesario
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

class MainActivity : AppCompatActivity(), OnMapReadyCallback, RouteDrawer, MapMover {

    private val sharedViewModel: SharedViewModel by viewModels()
    // ... (el resto de tus variables de clase se mantienen igual)
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var mapView: MapView
    private lateinit var map: MapLibreMap
    private lateinit var locationFab: FloatingActionButton
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<FrameLayout>
    private lateinit var tabLayout: TabLayout
    private lateinit var viewPager: ViewPager2
    private lateinit var pagerAdapter: ViewPagerAdapter
    private var selectedRouteId: String? = null
    private var selectedDirectionId: Int? = null
    private var lastFeedMessage: GtfsRealtime.FeedMessage? = null
    private var currentRouteSourceId: String? = null
    private var currentRouteLayerId: String? = null
    private var mapStyle: Style? = null
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

        // ... (el resto de onCreate se mantiene igual)
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
    }

    // ... (todas las demás funciones se mantienen igual HASTA fetchBusData)

    private fun fetchBusData() {
        lifecycleScope.launch {
            try {
                val response = apiService.getVehiclePositions("chillan", "9f057ee0-3807-4340-aefa-17553326eec0")
                if (response.isSuccessful) {
                    val feed = response.body()
                    if (feed != null) {
                        lastFeedMessage = feed // Mantenemos la copia local para el mapa
                        sharedViewModel.setFeedMessage(feed) // Y actualizamos el ViewModel
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

    // ... (el resto de MainActivity.kt se mantiene exactamente igual)
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
        }
    }

    private fun setupBusLayer(style: Style) {
        val busBitmap = BitmapFactory.decodeResource(resources, R.drawable.ic_bus)
        style.addImage("bus-icon", busBitmap)
        style.addSource(GeoJsonSource("bus-source"))
        val busLayer = SymbolLayer("bus-layer", "bus-source").apply {
            withProperties(
                PropertyFactory.iconImage("bus-icon"),
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.iconIgnorePlacement(true),
                PropertyFactory.iconSize(0.05f),
                PropertyFactory.iconOpacity(
                    interpolate(linear(), zoom(),
                        stop(11.99f, 0f),
                        stop(12f, 1f)
                    )
                )
            )
        }
        style.addLayer(busLayer)
    }

    private fun setupParaderoLayer(style: Style) {
        try {
            val paraderoBitmap = BitmapFactory.decodeResource(resources, R.drawable.ic_paradero)
            style.addImage("paradero-icon", paraderoBitmap)
        } catch (e: Exception) {
            Log.w("SetupParadero", "No se encontró 'ic_paradero.png'.")
        }
        style.addSource(GeoJsonSource("paradero-source"))
        val paraderoLayer = SymbolLayer("paradero-layer", "paradero-source").apply {
            withProperties(
                PropertyFactory.iconImage("paradero-icon"),
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.iconIgnorePlacement(true),
                PropertyFactory.iconSize(0.05f),
                PropertyFactory.iconOpacity(
                    interpolate(linear(), zoom(),
                        stop(12.99f, 0f),
                        stop(13f, 1f)
                    )
                )
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

    override fun drawRoute(route: GtfsRoute, directionId: Int) {
        clearDrawnElements()
        selectedRouteId = route.routeId
        selectedDirectionId = directionId

        val style = mapStyle ?: return

        val tripKey = "${route.routeId}_$directionId"
        val shapeId = GtfsDataManager.trips[tripKey]?.shapeId

        if (shapeId == null) {
            Toast.makeText(this, "Trazado no disponible para esta ruta.", Toast.LENGTH_SHORT).show()
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

        val bounds = LatLngBounds.Builder().includes(routePoints).build()
        map.easeCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100), 1500)

        updateBusMarkers()
    }

    override fun clearRoutes() {
        clearDrawnElements()
        selectedRouteId = null
        selectedDirectionId = null
        updateBusMarkers()
        Toast.makeText(this, "Mostrando buses cercanos", Toast.LENGTH_SHORT).show()
        requestFreshLocation()
    }

    private fun clearDrawnElements() {
        mapStyle?.let { style ->
            currentRouteLayerId?.let { if (style.getLayer(it) != null) style.removeLayer(it) }
            currentRouteSourceId?.let { if (style.getSource(it) != null) style.removeSource(it) }
        }
        currentRouteLayerId = null
        currentRouteSourceId = null
    }

    override fun centerMapOnPoint(lat: Double, lon: Double) {
        if (::map.isInitialized) {
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(lat, lon), 16.0), 1500)
        }
    }

    private fun addTurismoMarkers() {
        val iconFactory = IconFactory.getInstance(this@MainActivity)
        val originalBitmap = BitmapFactory.decodeResource(resources, R.drawable.ic_turismo)
        val scaledBitmap = android.graphics.Bitmap.createScaledBitmap(originalBitmap, 80, 80, false)
        val icon = iconFactory.fromBitmap(scaledBitmap)
        DatosTurismo.puntosTuristicos.forEach { punto ->
            turismoMarkers.add(map.addMarker(
                MarkerOptions()
                    .position(LatLng(punto.latitud, punto.longitud))
                    .title(punto.nombre)
                    .snippet(punto.direccion)
                    .icon(icon)
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
                        val distanceToUser = distanceBetween(userLocation.latitude, userLocation.longitude, position.latitude.toDouble(), position.longitude.toDouble())
                        if (distanceToUser <= 1000) {
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

    override fun onStart() {
        super.onStart()
        mapView.onStart()
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }

    override fun onStop() {
        super.onStop()
        mapView.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView.onSaveInstanceState(outState)
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView.onLowMemory()
    }

    override fun onDestroy() {
        super.onDestroy()
        mapView.onDestroy()
    }
}
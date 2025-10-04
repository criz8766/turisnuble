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
import org.maplibre.android.annotations.Marker
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
import java.io.IOException

class MainActivity : AppCompatActivity(), OnMapReadyCallback, RouteDrawer {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var mapView: MapView
    private lateinit var map: MapLibreMap
    private lateinit var locationFab: FloatingActionButton
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<FrameLayout>
    private lateinit var tabLayout: TabLayout
    private lateinit var viewPager: ViewPager2
    private lateinit var pagerAdapter: ViewPagerAdapter

    private var selectedRouteInfo: RutaInfo? = null
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

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        val bottomSheet: FrameLayout = findViewById(R.id.bottom_sheet)
        bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet)

        val screenHeight = resources.displayMetrics.heightPixels
        val quarterScreenHeight = screenHeight / 3
        bottomSheetBehavior.maxHeight = quarterScreenHeight
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
        bottomSheetBehavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {}
            override fun onSlide(bottomSheet: View, slideOffset: Float) {}
        })
    }

    override fun onMapReady(mapLibreMap: MapLibreMap) {
        this.map = mapLibreMap
        val styleUrl = "https://tiles.openfreemap.org/styles/liberty"
        map.setStyle(styleUrl) { style ->
            this.mapStyle = style
            enableLocation(style)
            startBusDataFetching()
            addTurismoMarkers()
            setupBusLayer(style)
        }
    }

    // --- FUNCIÓN CON LOS VALORES AJUSTADOS ---
    private fun setupBusLayer(style: Style) {
        val busBitmap = BitmapFactory.decodeResource(resources, R.drawable.ic_bus)
        style.addImage("bus-icon", busBitmap)

        style.addSource(GeoJsonSource("bus-source"))

        val busLayer = SymbolLayer("bus-layer", "bus-source").apply {
            withProperties(
                PropertyFactory.iconImage("bus-icon"),
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.iconIgnorePlacement(true),

                // --- CAMBIO 1: TAMAÑO DEL ÍCONO ---
                // Reducimos el tamaño de 0.7f a 0.5f. Puedes jugar con este valor.
                PropertyFactory.iconSize(0.05f),

                PropertyFactory.iconOpacity(
                    interpolate(linear(), zoom(),
                        // --- CAMBIO 2: NIVEL DE ZOOM ---
                        // Hacemos que los íconos empiecen a aparecer desde el nivel de zoom 10,
                        // en lugar de 12, para que se vean desde más lejos.
                        stop(10.99f, 0f), // Por debajo de 10, son invisibles
                        stop(11f, 1f)     // A partir de 10, son visibles
                    )
                )
            )
        }
        style.addLayer(busLayer)
    }

    private fun requestFreshLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return

        Log.d("LocationUpdate", "Pidiendo una ubicación fresca...")
        Toast.makeText(this, "Actualizando ubicación...", Toast.LENGTH_SHORT).show()
        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, CancellationTokenSource().token)
            .addOnSuccessListener { location: Location? ->
                if (location != null) {
                    Log.d("LocationUpdate", "Ubicación fresca recibida: ${location.latitude}, ${location.longitude}")
                    map.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(location.latitude, location.longitude), 15.0))
                    updateBusMarkers()
                } else {
                    Log.w("LocationUpdate", "No se pudo obtener una ubicación fresca (resultado nulo).")
                }
            }
            .addOnFailureListener { e ->
                Log.e("LocationUpdate", "Error al pedir la ubicación fresca.", e)
            }
    }

    override fun clearRoutes() {
        mapStyle?.let { style ->
            currentRouteLayerId?.let { if (style.getLayer(it) != null) style.removeLayer(it) }
            currentRouteSourceId?.let { if (style.getSource(it) != null) style.removeSource(it) }
        }
        currentRouteLayerId = null
        currentRouteSourceId = null
        selectedRouteInfo = null
        updateBusMarkers()
    }

    override fun drawRoute(routeInfo: RutaInfo) {
        this.selectedRouteInfo = routeInfo
        drawRouteFromKml(mapStyle, routeInfo.fileName, routeInfo.color)
        updateBusMarkers()
    }

    private fun drawRouteFromKml(style: Style?, fileName: String, lineColor: String) {
        if (style == null) return
        try {
            val kmlString = assets.open(fileName).bufferedReader().use { it.readText() }
            val coordinatesString = kmlString.substringAfter("<LineString>").substringAfter("<coordinates>").substringBefore("</coordinates>").trim()
            if (coordinatesString.isEmpty()) return

            val routePoints = mutableListOf<LatLng>()
            coordinatesString.split(" ").forEach { pair ->
                val lonLat = pair.split(",")
                if (lonLat.size >= 2) {
                    try {
                        routePoints.add(LatLng(lonLat[1].toDouble(), lonLat[0].toDouble()))
                    } catch (e: NumberFormatException) { /* Ignorar */ }
                }
            }
            if (routePoints.isEmpty()) return

            val geoJsonString = """{"type": "Feature", "geometry": {"type": "LineString", "coordinates": [${routePoints.joinToString { "[${it.longitude},${it.latitude}]" }}]}}"""
            val sourceId = "route-source-$fileName"
            style.addSource(GeoJsonSource(sourceId, geoJsonString))
            currentRouteSourceId = sourceId

            val layerId = "route-layer-$fileName"
            val routeLayer = LineLayer(layerId, sourceId).apply {
                withProperties(PropertyFactory.lineColor(Color.parseColor(lineColor)), PropertyFactory.lineWidth(5f), PropertyFactory.lineCap(Property.LINE_CAP_ROUND), PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND))
            }
            style.addLayer(routeLayer)
            currentRouteLayerId = layerId

            val bounds = LatLngBounds.Builder().includes(routePoints).build()
            map.easeCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100), 1500)
        } catch (e: Exception) {
            Log.e("DrawRouteError", "Error al dibujar la ruta desde $fileName", e)
        }
    }

    private fun addTurismoMarkers() {
        // ... (Esta función se mantiene igual)
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
                    lastFeedMessage = response.body()
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
                if (selectedRouteInfo != null) {
                    if (selectedRouteInfo?.routeId == trip.routeId && selectedRouteInfo?.directionId == trip.directionId) {
                        shouldShow = true
                    }
                } else {
                    if (userLocation != null) {
                        val distanceToUser = distanceBetween(userLocation.latitude, userLocation.longitude, position.latitude.toDouble(), position.longitude.toDouble())
                        if (distanceToUser <= 1000) {
                            shouldShow = true
                        }
                    } else {
                        shouldShow = false
                    }
                }

                if (shouldShow) {
                    val point = Point.fromLngLat(position.longitude.toDouble(), position.latitude.toDouble())
                    busFeatures.add(Feature.fromGeometry(point))
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
package cl.example.turisnuble

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
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
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import retrofit2.Retrofit
import retrofit2.converter.protobuf.ProtoConverterFactory
import java.io.IOException

class MainActivity : AppCompatActivity(), OnMapReadyCallback, RouteDrawer {

    private lateinit var mapView: MapView
    private lateinit var map: MapLibreMap
    private lateinit var locationFab: FloatingActionButton
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<FrameLayout>

    private lateinit var tabLayout: TabLayout
    private lateinit var viewPager: ViewPager2
    private lateinit var pagerAdapter: ViewPagerAdapter

    // Variables para gestionar la ruta dibujada
    private var currentRouteSourceId: String? = null
    private var currentRouteLayerId: String? = null
    private var mapStyle: Style? = null

    private val busMarkers = mutableListOf<Marker>()
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
            } else {
                Toast.makeText(this, "Permiso de ubicación denegado.", Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MapLibre.getInstance(this)
        setContentView(R.layout.activity_main)

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
            if (::map.isInitialized && map.locationComponent.isLocationComponentActivated) {
                val lastKnownLocation = map.locationComponent.lastKnownLocation
                if (lastKnownLocation != null) {
                    map.animateCamera(CameraUpdateFactory.newLatLngZoom(
                        LatLng(lastKnownLocation.latitude, lastKnownLocation.longitude),
                        15.0
                    ))
                }
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
        bottomSheetBehavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {}
            override fun onSlide(bottomSheet: View, slideOffset: Float) {}
        })
    }

    override fun onMapReady(mapLibreMap: MapLibreMap) {
        this.map = mapLibreMap
        val styleUrl = "https://tiles.openfreemap.org/styles/liberty"

        map.setStyle(styleUrl) { style ->
            this.mapStyle = style // Guardamos la referencia al estilo para usarla después
            enableLocation(style)
            startBusDataFetching()
            addTurismoMarkers()
            // Ya no dibujamos ninguna ruta al iniciar
        }
    }

    override fun clearRoutes() {
        mapStyle?.let { style ->
            // Si hay una capa de ruta dibujada, la quitamos
            currentRouteLayerId?.let { id ->
                if (style.getLayer(id) != null) {
                    style.removeLayer(id)
                    Log.d("RouteDrawer", "Capa de ruta '$id' eliminada.")
                }
            }
            // Si hay una fuente de datos de ruta, la quitamos
            currentRouteSourceId?.let { id ->
                if (style.getSource(id) != null) {
                    style.removeSource(id)
                    Log.d("RouteDrawer", "Fuente de ruta '$id' eliminada.")
                }
            }
        }
        currentRouteLayerId = null
        currentRouteSourceId = null
    }

    override fun drawRoute(fileName: String, color: String) {
        drawRouteFromKml(mapStyle, fileName, color)
    }

    private fun drawRouteFromKml(style: Style?, fileName: String, lineColor: String) {
        if (style == null) {
            Log.e("DrawRouteError", "El estilo del mapa no está listo para dibujar.")
            return
        }

        try {
            Log.d("DrawRoute", "Iniciando lectura del archivo: $fileName")
            val kmlString = assets.open(fileName).bufferedReader().use { it.readText() }
            Log.d("DrawRoute", "Archivo $fileName leído correctamente.")

            val coordinatesString = kmlString.substringAfter("<LineString>")
                .substringAfter("<coordinates>")
                .substringBefore("</coordinates>")
                .trim()

            if (coordinatesString.isEmpty()) {
                Log.e("DrawRouteError", "No se encontraron coordenadas en el archivo $fileName")
                return
            }
            Log.d("DrawRoute", "Coordenadas extraídas con éxito.")

            val routePoints = mutableListOf<LatLng>()
            val coordinatesPairs = coordinatesString.split(" ")
            for (pair in coordinatesPairs) {
                val lonLat = pair.split(",")
                if (lonLat.size >= 2) {
                    try {
                        routePoints.add(LatLng(lonLat[1].toDouble(), lonLat[0].toDouble()))
                    } catch (e: NumberFormatException) {
                        Log.w("DrawRouteWarning", "Ignorando par de coordenadas mal formado: $pair")
                    }
                }
            }

            if (routePoints.isEmpty()) {
                Log.e("DrawRouteError", "La lista de puntos de la ruta está vacía después de procesar.")
                return
            }
            Log.d("DrawRoute", "Se procesaron ${routePoints.size} puntos para la ruta.")

            val geoJsonString = """
                {
                  "type": "Feature",
                  "properties": {},
                  "geometry": {
                    "type": "LineString",
                    "coordinates": [
                      ${ routePoints.joinToString(separator = ",") { "[${it.longitude},${it.latitude}]" } }
                    ]
                  }
                }
            """.trimIndent()

            val sourceId = "route-source-$fileName"
            style.addSource(GeoJsonSource(sourceId, geoJsonString))
            currentRouteSourceId = sourceId
            Log.d("DrawRoute", "Fuente GeoJSON '$sourceId' añadida al mapa.")

            val layerId = "route-layer-$fileName"
            val routeLayer = LineLayer(layerId, sourceId).apply {
                withProperties(
                    PropertyFactory.lineColor(Color.parseColor(lineColor)),
                    PropertyFactory.lineWidth(5f),
                    PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                    PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
                )
            }
            style.addLayer(routeLayer)
            currentRouteLayerId = layerId
            Log.d("DrawRoute", "Capa '$layerId' añadida al mapa.")
            Toast.makeText(this, "Mostrando ruta: $fileName", Toast.LENGTH_SHORT).show()

            val boundsBuilder = LatLngBounds.Builder()
            routePoints.forEach { boundsBuilder.include(it) }
            val bounds = boundsBuilder.build()
            map.easeCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100), 1500)

        } catch (e: IOException) {
            Log.e("DrawRouteError", "No se pudo encontrar el archivo: $fileName en la carpeta assets", e)
            Toast.makeText(this, "Error: No se encuentra el archivo $fileName", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Log.e("DrawRouteError", "Ocurrió un error inesperado al dibujar la ruta desde $fileName", e)
            Toast.makeText(this, "Error al procesar el archivo de ruta", Toast.LENGTH_LONG).show()
        }
    }

    private fun addTurismoMarkers() {
        val iconFactory = IconFactory.getInstance(this@MainActivity)
        val originalBitmap = BitmapFactory.decodeResource(resources, R.drawable.ic_turismo)
        val scaledBitmap = Bitmap.createScaledBitmap(originalBitmap, 80, 80, false)
        val icon = iconFactory.fromBitmap(scaledBitmap)

        for (punto in DatosTurismo.puntosTuristicos) {
            val marker = map.addMarker(MarkerOptions()
                .position(LatLng(punto.latitud, punto.longitud))
                .title(punto.nombre)
                .snippet(punto.direccion)
                .icon(icon)
            )
            turismoMarkers.add(marker)
        }
    }

    private fun startBusDataFetching() {
        lifecycleScope.launch {
            while (isActive) {
                fetchBusData()
                delay(90000)
            }
        }
    }

    private fun fetchBusData() {
        val apiKey = "9f057ee0-3807-4340-aefa-17553326eec0"

        lifecycleScope.launch {
            try {
                val response = apiService.getVehiclePositions("chillan", apiKey)
                if (response.isSuccessful) {
                    val feed = response.body()
                    if (feed != null && feed.entityCount > 0) {
                        Log.d("API_SUCCESS", "✅ Buses encontrados: ${feed.entityCount}")
                        updateBusMarkers(feed)
                    } else {
                        Log.w("API_SUCCESS", "Respuesta exitosa pero sin datos de buses.")
                        clearBusMarkers()
                    }
                } else {
                    Log.e("API_ERROR", "Error en la respuesta: ${response.code()} - ${response.errorBody()?.string()}")
                }
            } catch (e: Exception) {
                Log.e("API_ERROR", "Fallo en la conexión o procesamiento", e)
            }
        }
    }

    private fun updateBusMarkers(feed: GtfsRealtime.FeedMessage) {
        clearBusMarkers()

        val iconFactory = IconFactory.getInstance(this@MainActivity)

        val originalBitmap = BitmapFactory.decodeResource(resources, R.drawable.ic_bus)
        val scaledBitmap = Bitmap.createScaledBitmap(originalBitmap, 80, 80, false)
        val icon = iconFactory.fromBitmap(scaledBitmap)

        for (entity in feed.entityList) {
            if (entity.hasVehicle()) {
                val vehicle = entity.vehicle
                val position = vehicle.position
                val busId = vehicle.vehicle.id

                val marker = map.addMarker(MarkerOptions()
                    .position(LatLng(position.latitude.toDouble(), position.longitude.toDouble()))
                    .title("Bus ID: $busId")
                    .icon(icon)
                )
                busMarkers.add(marker)
            }
        }
    }

    private fun clearBusMarkers() {
        for (marker in busMarkers) {
            map.removeMarker(marker)
        }
        busMarkers.clear()
    }

    private fun enableLocation(loadedMapStyle: Style) {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED -> {
                showUserLocation()
            }
            else -> {
                requestLocationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }
    }

    @SuppressWarnings("MissingPermission")
    private fun showUserLocation() {
        val locationComponent = map.locationComponent
        locationComponent.activateLocationComponent(
            LocationComponentActivationOptions.builder(this, map.style!!).build()
        )
        locationComponent.isLocationComponentEnabled = true
        locationComponent.cameraMode = CameraMode.TRACKING
        locationComponent.renderMode = RenderMode.COMPASS
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
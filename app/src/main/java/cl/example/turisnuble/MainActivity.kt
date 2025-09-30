package cl.example.turisnuble

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.OnMapReadyCallback
import org.maplibre.android.maps.Style
import retrofit2.Retrofit
import retrofit2.converter.protobuf.ProtoConverterFactory

class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mapView: MapView
    private lateinit var map: MapLibreMap
    private lateinit var locationFab: FloatingActionButton
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<FrameLayout>

    private lateinit var tabLayout: TabLayout
    private lateinit var viewPager: ViewPager2
    private lateinit var pagerAdapter: ViewPagerAdapter

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

        // --- LÓGICA DE ALTURA CORREGIDA ---

        // 1. Calculamos la altura deseada (1/4 de la pantalla)
        val screenHeight = resources.displayMetrics.heightPixels
        val quarterScreenHeight = screenHeight / 3

        // 2. Establecemos esa altura como la máxima altura que puede alcanzar el panel
        bottomSheetBehavior.maxHeight = quarterScreenHeight

        // 3. Definimos la altura en estado colapsado (peekHeight)
        val peekHeightInPixels = (46 * resources.displayMetrics.density).toInt()
        bottomSheetBehavior.peekHeight = peekHeightInPixels

        // 4. Estado inicial.
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED

        // --- FIN DE LA LÓGICA ---

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
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                // No es necesario hacer cambios aquí ahora
            }

            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                // No necesitamos hacer nada aquí
            }
        })
    }


    override fun onMapReady(mapLibreMap: MapLibreMap) {
        this.map = mapLibreMap
        val styleUrl = "https://tiles.openfreemap.org/styles/liberty"

        map.setStyle(styleUrl) { style ->
            enableLocation(style)
            startBusDataFetching()
            addTurismoMarkers()
        }
    }

    // ... (El resto del archivo MainActivity.kt permanece sin cambios)
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
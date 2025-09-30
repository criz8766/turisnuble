package cl.example.turisnuble

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.floatingactionbutton.FloatingActionButton // <-- NUEVO IMPORT
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory // <-- NUEVO IMPORT
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.OnMapReadyCallback
import org.maplibre.android.maps.Style
import com.google.android.material.bottomsheet.BottomSheetBehavior
import android.widget.FrameLayout

import androidx.lifecycle.lifecycleScope
import com.google.transit.realtime.GtfsRealtime
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.protobuf.ProtoConverterFactory
import android.util.Log

class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mapView: MapView
    private lateinit var map: MapLibreMap
    private lateinit var locationFab: FloatingActionButton // <-- NUEVA VARIABLE
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<FrameLayout> // <-- NUEVO OBJETO

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
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED // Estado inicial
        bottomSheetBehavior.peekHeight = 150 // Altura en píxeles cuando está colapsado


        mapView = findViewById(R.id.mapView)
        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync(this)

        // --- NUEVO: Inicialización y listener del botón ---
        locationFab = findViewById(R.id.location_fab)
        locationFab.setOnClickListener {
            // Verificamos que el mapa y el componente de ubicación estén listos
            if (::map.isInitialized && map.locationComponent.isLocationComponentActivated) {
                val lastKnownLocation = map.locationComponent.lastKnownLocation
                if (lastKnownLocation != null) {
                    // Animamos la cámara a la ubicación del usuario
                    map.animateCamera(CameraUpdateFactory.newLatLngZoom(
                        org.maplibre.android.geometry.LatLng(lastKnownLocation.latitude, lastKnownLocation.longitude),
                        15.0 // Un zoom más cercano
                    ))
                }
            }
        }
    }

    override fun onMapReady(mapLibreMap: MapLibreMap) {
        this.map = mapLibreMap
        val styleUrl = "https://tiles.openfreemap.org/styles/liberty"

        map.setStyle(styleUrl) { style ->
            // Ya no centramos la cámara en Chillán, dejamos que la ubicación del usuario lo haga
            enableLocation(style)
            fetchBusData()
        }
    }

    private fun fetchBusData() {
        // REEMPLAZA "TU_API_KEY" CON LA CLAVE REAL QUE RECIBISTE POR CORREO
        val apiKey = "9f057ee0-3807-4340-aefa-17553326eec0"

        lifecycleScope.launch {
            try {
                val response = apiService.getVehiclePositions("chillan", apiKey)
                if (response.isSuccessful) {
                    val feed = response.body()
                    if (feed != null && feed.entityCount > 0) {
                        Log.d("API_SUCCESS", "✅ Buses encontrados: ${feed.entityCount}")
                        for (entity in feed.entityList) {
                            if (entity.hasVehicle()) {
                                val vehicle = entity.vehicle
                                val position = vehicle.position
                                Log.d("API_SUCCESS", "  -> Bus ID: ${vehicle.vehicle.id}, Lat: ${position.latitude}, Lon: ${position.longitude}")
                            }
                        }
                    } else {
                        Log.w("API_SUCCESS", "Respuesta exitosa pero sin datos de buses.")
                    }
                } else {
                    Log.e("API_ERROR", "Error en la respuesta: ${response.code()} - ${response.errorBody()?.string()}")
                }
            } catch (e: Exception) {
                Log.e("API_ERROR", "Fallo en la conexión o procesamiento", e)
            }
        }
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
        locationComponent.cameraMode = CameraMode.TRACKING // La cámara sigue al usuario al inicio
        locationComponent.renderMode = RenderMode.COMPASS
    }


    // --- El manejo del ciclo de vida sigue siendo el mismo ---
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
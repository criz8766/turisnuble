package cl.example.turisnuble.data

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import cl.example.turisnuble.fragments.DisplayRouteInfo
import com.google.transit.realtime.GtfsRealtime
import org.maplibre.android.geometry.LatLng

class SharedViewModel : ViewModel() {

    private val _feedMessage = MutableLiveData<GtfsRealtime.FeedMessage>()
    val feedMessage: LiveData<GtfsRealtime.FeedMessage> = _feedMessage

    private val _nearbyStops = MutableLiveData<List<GtfsStop>>()
    val nearbyStops: LiveData<List<GtfsStop>> = _nearbyStops

    private val _routeFilter = MutableLiveData<List<DisplayRouteInfo>?>()
    val routeFilter: LiveData<List<DisplayRouteInfo>?> = _routeFilter

    private val _selectedStopId = MutableLiveData<String?>()
    val selectedStopId: LiveData<String?> = _selectedStopId

    // --- NUEVO: Guarda el centro de interés para los cálculos de cercanía ---
    private val _nearbyCalculationCenter = MutableLiveData<LatLng?>()
    val nearbyCalculationCenter: LiveData<LatLng?> = _nearbyCalculationCenter

    fun setFeedMessage(feed: GtfsRealtime.FeedMessage) {
        _feedMessage.value = feed
    }

    fun setNearbyStops(stops: List<GtfsStop>) {
        _nearbyStops.value = stops
    }

    fun setRouteFilter(filteredRoutes: List<DisplayRouteInfo>) {
        _routeFilter.value = filteredRoutes
    }

    fun clearRouteFilter() {
        _routeFilter.value = null
    }

    fun selectStop(stopId: String?) {
        if (_selectedStopId.value == stopId) {
            _selectedStopId.value = null
        } else {
            _selectedStopId.value = stopId
        }
    }

    // --- NUEVA FUNCIÓN: Para establecer o limpiar el punto de interés ---
    fun setNearbyCalculationCenter(center: LatLng?) {
        _nearbyCalculationCenter.value = center
    }
}
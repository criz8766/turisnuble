package cl.example.turisnuble

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.transit.realtime.GtfsRealtime

class SharedViewModel : ViewModel() {

    private val _feedMessage = MutableLiveData<GtfsRealtime.FeedMessage>()
    val feedMessage: LiveData<GtfsRealtime.FeedMessage> = _feedMessage

    private val _nearbyStops = MutableLiveData<List<GtfsStop>>()
    val nearbyStops: LiveData<List<GtfsStop>> = _nearbyStops

    private val _routeFilter = MutableLiveData<List<DisplayRouteInfo>?>()
    val routeFilter: LiveData<List<DisplayRouteInfo>?> = _routeFilter

    // --- Dato para el paradero seleccionado ---
    private val _selectedStopId = MutableLiveData<String?>()
    val selectedStopId: LiveData<String?> = _selectedStopId

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

    // --- NUEVA FUNCIÓN: Para seleccionar o deseleccionar un paradero ---
    fun selectStop(stopId: String?) {
        // Si el ID que llega es el mismo que ya está seleccionado, lo limpiamos (deseleccionar).
        // Si es diferente, lo seleccionamos.
        if (_selectedStopId.value == stopId) {
            _selectedStopId.value = null
        } else {
            _selectedStopId.value = stopId
        }
    }
}
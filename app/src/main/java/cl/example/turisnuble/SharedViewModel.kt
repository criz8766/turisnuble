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

    // --- NUEVO CANAL PARA EL FILTRO DE RUTAS ---
    private val _routeFilter = MutableLiveData<List<DisplayRouteInfo>?>()
    val routeFilter: LiveData<List<DisplayRouteInfo>?> = _routeFilter

    fun setFeedMessage(feed: GtfsRealtime.FeedMessage) {
        _feedMessage.value = feed
    }

    fun setNearbyStops(stops: List<GtfsStop>) {
        _nearbyStops.value = stops
    }

    // --- NUEVAS FUNCIONES PARA MANEJAR EL FILTRO ---
    fun setRouteFilter(filteredRoutes: List<DisplayRouteInfo>) {
        _routeFilter.value = filteredRoutes
    }

    fun clearRouteFilter() {
        _routeFilter.value = null
    }
}
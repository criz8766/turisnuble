package cl.example.turisnuble

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.transit.realtime.GtfsRealtime

class SharedViewModel : ViewModel() {

    private val _feedMessage = MutableLiveData<GtfsRealtime.FeedMessage>()
    val feedMessage: LiveData<GtfsRealtime.FeedMessage> = _feedMessage

    // --- NUEVO CANAL PARA COMPARTIR PARADEROS CERCANOS ---
    private val _nearbyStops = MutableLiveData<List<GtfsStop>>()
    val nearbyStops: LiveData<List<GtfsStop>> = _nearbyStops

    fun setFeedMessage(feed: GtfsRealtime.FeedMessage) {
        _feedMessage.value = feed
    }

    fun setNearbyStops(stops: List<GtfsStop>) {
        _nearbyStops.value = stops
    }
}
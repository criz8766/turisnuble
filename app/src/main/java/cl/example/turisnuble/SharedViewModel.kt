package cl.example.turisnuble

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.transit.realtime.GtfsRealtime

class SharedViewModel : ViewModel() {
    // LiveData para almacenar el último feed de la API
    private val _feedMessage = MutableLiveData<GtfsRealtime.FeedMessage>()
    val feedMessage: LiveData<GtfsRealtime.FeedMessage> = _feedMessage

    fun setFeedMessage(feed: GtfsRealtime.FeedMessage) {
        _feedMessage.value = feed
    }
}
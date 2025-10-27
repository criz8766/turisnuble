package cl.example.turisnuble

import com.google.transit.realtime.GtfsRealtime
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path

interface GtfsApiService {
    @GET("data/gtfs-rt/{city}.proto")
    suspend fun getVehiclePositions(
        @Path("city") city: String
    ): Response<GtfsRealtime.FeedMessage>
}
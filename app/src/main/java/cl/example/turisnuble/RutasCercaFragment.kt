package cl.example.turisnuble

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.location.LocationServices
import com.google.transit.realtime.GtfsRealtime
import org.maplibre.android.geometry.LatLng
import java.util.concurrent.TimeUnit

// Nuevas clases de datos para la lista
data class LlegadaInfo(val linea: String, val tiempoLlegadaMin: Int)
data class ParaderoConLlegadas(val paradero: GtfsStop, val llegadas: List<LlegadaInfo>)

class RutasCercaFragment : Fragment() {

    private val sharedViewModel: SharedViewModel by activityViewModels()
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ParaderosCercanosAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_rutas_cerca, container, false)
        recyclerView = view.findViewById(R.id.recycler_view_rutas_cerca)
        recyclerView.layoutManager = LinearLayoutManager(context)
        adapter = ParaderosCercanosAdapter(emptyList())
        recyclerView.adapter = adapter

        // Observamos los datos de la API que vienen del ViewModel
        sharedViewModel.feedMessage.observe(viewLifecycleOwner) { feed ->
            // Cada vez que llegan nuevos datos de la API, recalculamos todo
            findNearbyStopsAndArrivals(feed)
        }

        return view
    }

    // Al reanudar el fragmento, volvemos a calcular por si la ubicación cambió
    override fun onResume() {
        super.onResume()
        sharedViewModel.feedMessage.value?.let { findNearbyStopsAndArrivals(it) }
    }

    private fun findNearbyStopsAndArrivals(feed: GtfsRealtime.FeedMessage) {
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location == null) return@addOnSuccessListener

                val userLatLng = LatLng(location.latitude, location.longitude)

                val paraderosCercanos = GtfsDataManager.stops.values
                    .filter { userLatLng.distanceTo(it.location) <= 500 }
                    .sortedBy { userLatLng.distanceTo(it.location) }

                val paraderosConLlegadas = paraderosCercanos.map { paradero ->
                    val llegadas = mutableListOf<LlegadaInfo>()

                    // Buscamos en todo el feed de la API las llegadas a este paradero
                    for (entity in feed.entityList) {
                        if (entity.hasTripUpdate()) {
                            for (stopUpdate in entity.tripUpdate.stopTimeUpdateList) {
                                if (stopUpdate.stopId == paradero.stopId) {
                                    val routeId = entity.tripUpdate.trip.routeId
                                    val linea = GtfsDataManager.routes[routeId]?.shortName ?: "Desconocida"

                                    val tiempoLlegada = stopUpdate.arrival.time
                                    val tiempoActual = System.currentTimeMillis() / 1000
                                    val diffSegundos = tiempoLlegada - tiempoActual
                                    val diffMinutos = TimeUnit.SECONDS.toMinutes(diffSegundos).toInt()

                                    if (diffMinutos >= 0) {
                                        llegadas.add(LlegadaInfo(linea, diffMinutos))
                                    }
                                }
                            }
                        }
                    }
                    ParaderoConLlegadas(paradero, llegadas.sortedBy { it.tiempoLlegadaMin })
                }

                // Actualizamos el adaptador con la nueva lista
                adapter.updateData(paraderosConLlegadas)
            }
        } catch (e: SecurityException) { /* Sin permisos */ }
    }
}

// Adaptador actualizado para manejar la nueva estructura de datos
class ParaderosCercanosAdapter(private var data: List<ParaderoConLlegadas>) :
    RecyclerView.Adapter<ParaderosCercanosAdapter.ParaderoViewHolder>() {

    class ParaderoViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val nombreParadero: TextView = view.findViewById(R.id.nombre_paradero)
        val llegadasBuses: TextView = view.findViewById(R.id.llegadas_buses)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ParaderoViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_paradero_cercano, parent, false)
        return ParaderoViewHolder(view)
    }

    override fun onBindViewHolder(holder: ParaderoViewHolder, position: Int) {
        val item = data[position]
        holder.nombreParadero.text = "${item.paradero.stopId} - ${item.paradero.name}"

        if (item.llegadas.isEmpty()) {
            holder.llegadasBuses.text = "No hay próximas llegadas."
        } else {
            // Formateamos el texto para mostrar las próximas llegadas
            val llegadasTexto = item.llegadas.take(3).joinToString(separator = " | ") {
                "Línea ${it.linea}: ${if (it.tiempoLlegadaMin == 0) "Ahora" else "${it.tiempoLlegadaMin} min"}"
            }
            holder.llegadasBuses.text = llegadasTexto
        }
    }

    override fun getItemCount() = data.size

    fun updateData(newData: List<ParaderoConLlegadas>) {
        this.data = newData
        notifyDataSetChanged()
    }
}
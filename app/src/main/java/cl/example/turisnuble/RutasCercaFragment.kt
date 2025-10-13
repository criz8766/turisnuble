package cl.example.turisnuble

import android.content.Context
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

// --- CAMBIO 1: Añadimos 'directionId' a la información de la llegada ---
data class LlegadaInfo(val linea: String, val directionId: Int, val tiempoLlegadaMin: Int)
data class ParaderoConLlegadas(val paradero: GtfsStop, val llegadas: List<LlegadaInfo>)

class RutasCercaFragment : Fragment() {

    private val sharedViewModel: SharedViewModel by activityViewModels()
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ParaderosCercanosAdapter
    private var mapMover: MapMover? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is MapMover) {
            mapMover = context
        } else {
            throw RuntimeException("$context must implement MapMover")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_rutas_cerca, container, false)
        recyclerView = view.findViewById(R.id.recycler_view_rutas_cerca)
        recyclerView.layoutManager = LinearLayoutManager(context)

        adapter = ParaderosCercanosAdapter(emptyList()) { paraderoSeleccionado ->
            mapMover?.centerMapOnPoint(paraderoSeleccionado.location.latitude, paraderoSeleccionado.location.longitude)
        }
        recyclerView.adapter = adapter

        sharedViewModel.feedMessage.observe(viewLifecycleOwner) { feed ->
            findNearbyStopsAndArrivals(feed)
        }
        return view
    }

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

                sharedViewModel.setNearbyStops(paraderosCercanos)

                val paraderosConLlegadas = paraderosCercanos.map { paradero ->
                    val llegadas = mutableListOf<LlegadaInfo>()
                    for (entity in feed.entityList) {
                        if (entity.hasTripUpdate()) {
                            for (stopUpdate in entity.tripUpdate.stopTimeUpdateList) {
                                if (stopUpdate.stopId == paradero.stopId) {
                                    val trip = entity.tripUpdate.trip
                                    val routeId = trip.routeId
                                    // --- CAMBIO 2: Obtenemos el directionId del viaje ---
                                    val directionId = trip.directionId
                                    val linea = GtfsDataManager.routes[routeId]?.shortName ?: "Desc."
                                    val tiempoLlegada = stopUpdate.arrival.time
                                    val tiempoActual = System.currentTimeMillis() / 1000
                                    val diffSegundos = tiempoLlegada - tiempoActual
                                    val diffMinutos = TimeUnit.SECONDS.toMinutes(diffSegundos).toInt()

                                    if (diffMinutos >= 0) {
                                        // Guardamos también la dirección
                                        llegadas.add(LlegadaInfo(linea, directionId, diffMinutos))
                                    }
                                }
                            }
                        }
                    }
                    ParaderoConLlegadas(paradero, llegadas.sortedBy { it.tiempoLlegadaMin })
                }
                adapter.updateData(paraderosConLlegadas)
            }
        } catch (e: SecurityException) { /* Sin permisos */ }
    }

    override fun onDetach() {
        super.onDetach()
        mapMover = null
    }
}

class ParaderosCercanosAdapter(
    private var data: List<ParaderoConLlegadas>,
    private val onItemClick: (GtfsStop) -> Unit
) : RecyclerView.Adapter<ParaderosCercanosAdapter.ParaderoViewHolder>() {

    // 1. ViewHolder actualizado para encontrar el nuevo TextView del "chip"
    class ParaderoViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val stopIdChip: TextView = view.findViewById(R.id.stop_id_chip)
        val nombreParadero: TextView = view.findViewById(R.id.nombre_paradero)
        val llegadasBuses: TextView = view.findViewById(R.id.llegadas_buses)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ParaderoViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_paradero_cercano, parent, false)
        return ParaderoViewHolder(view)
    }

    override fun onBindViewHolder(holder: ParaderoViewHolder, position: Int) {
        val item = data[position]

        // 2. Asignamos el texto a cada TextView por separado
        holder.stopIdChip.text = item.paradero.stopId
        holder.nombreParadero.text = item.paradero.name

        // La lógica de las llegadas no cambia
        if (item.llegadas.isEmpty()) {
            holder.llegadasBuses.text = "No hay próximas llegadas."
        } else {
            val llegadasTexto = item.llegadas.take(3).joinToString(separator = "  |  ") { llegada ->
                val lineaText = if (llegada.linea == "13A" || llegada.linea == "13B") "13" else llegada.linea
                val directionText = if (llegada.directionId == 0) "Ida" else "Vuelta"
                val tiempoText = if (llegada.tiempoLlegadaMin == 0) "Ahora" else "${llegada.tiempoLlegadaMin} min"
                "Línea $lineaText $directionText: $tiempoText"
            }
            holder.llegadasBuses.text = llegadasTexto
        }

        holder.itemView.setOnClickListener {
            onItemClick(item.paradero)
        }
    }

    override fun getItemCount() = data.size

    fun updateData(newData: List<ParaderoConLlegadas>) {
        this.data = newData
        notifyDataSetChanged()
    }
}
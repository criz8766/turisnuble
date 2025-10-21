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
import android.location.Location

// Las data classes no cambian
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
            // --- LÓGICA SIMPLIFICADA Y CORRECTA ---
            // 1. Solo le decimos al ViewModel qué paradero seleccionar (para el ícono rojo).
            sharedViewModel.selectStop(paraderoSeleccionado.stopId)
            // 2. Y le pedimos a la MainActivity que mueva el mapa hacia él.
            mapMover?.centerMapOnPoint(paraderoSeleccionado.location.latitude, paraderoSeleccionado.location.longitude)
            // No reseteamos nada. El contexto del punto turístico se mantiene.
        }
        recyclerView.adapter = adapter

        // Observamos si cambia el centro de interés para recalcular la lista.
        sharedViewModel.nearbyCalculationCenter.observe(viewLifecycleOwner) {
            sharedViewModel.feedMessage.value?.let { feed -> findNearbyStopsAndArrivals(feed) }
        }

        // Observamos si llegan nuevos datos de buses para recalcular la lista.
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
        val calculationCenter = sharedViewModel.nearbyCalculationCenter.value

        if (calculationCenter != null) {
            // Si hay un punto de interés (turístico), usamos sus coordenadas.
            calculateStops(feed, calculationCenter.latitude, calculationCenter.longitude)
        } else {
            // Si no, volvemos al comportamiento original: usar la ubicación del GPS.
            val fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
            try {
                fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                    location?.let {
                        calculateStops(feed, it.latitude, it.longitude)
                    }
                }
            } catch (e: SecurityException) { /* Sin permisos */ }
        }
    }

    // --- Nueva función de ayuda para no repetir código ---
    private fun calculateStops(feed: GtfsRealtime.FeedMessage, lat: Double, lon: Double) {
        // La lógica de cálculo es la misma, pero ahora es reutilizable.
        val paraderosCercanos = GtfsDataManager.stops.values
            .map { stop -> Pair(stop, distanceTo(lat, lon, stop.location.latitude, stop.location.longitude)) }
            .filter { it.second <= 500 }
            .sortedBy { it.second }
            .map { it.first }

        // Actualizamos la lista de paraderos en el ViewModel para que el mapa se redibuje.
        sharedViewModel.setNearbyStops(paraderosCercanos)

        val paraderosConLlegadas = paraderosCercanos.map { paradero ->
            val llegadas = mutableListOf<LlegadaInfo>()
            for (entity in feed.entityList) {
                if (entity.hasTripUpdate()) {
                    for (stopUpdate in entity.tripUpdate.stopTimeUpdateList) {
                        if (stopUpdate.stopId == paradero.stopId) {
                            val trip = entity.tripUpdate.trip
                            val linea = GtfsDataManager.routes[trip.routeId]?.shortName ?: "Desc."
                            val tiempoLlegada = stopUpdate.arrival.time
                            val tiempoActual = System.currentTimeMillis() / 1000
                            val diffMinutos = TimeUnit.SECONDS.toMinutes(tiempoLlegada - tiempoActual).toInt()
                            if (diffMinutos >= 0) {
                                llegadas.add(LlegadaInfo(linea, trip.directionId, diffMinutos))
                            }
                        }
                    }
                }
            }
            ParaderoConLlegadas(paradero, llegadas.sortedBy { it.tiempoLlegadaMin })
        }
        adapter.updateData(paraderosConLlegadas)
    }

    private fun distanceTo(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val results = FloatArray(1)
        Location.distanceBetween(lat1, lon1, lat2, lon2, results)
        return results[0]
    }

    override fun onDetach() {
        super.onDetach()
        mapMover = null
    }
}

// La clase ParaderosCercanosAdapter no necesita cambios, puede quedar como está.
class ParaderosCercanosAdapter(
    private var data: List<ParaderoConLlegadas>,
    private val onItemClick: (GtfsStop) -> Unit
) : RecyclerView.Adapter<ParaderosCercanosAdapter.ParaderoViewHolder>() {

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

        holder.stopIdChip.text = item.paradero.stopId
        holder.nombreParadero.text = item.paradero.name

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
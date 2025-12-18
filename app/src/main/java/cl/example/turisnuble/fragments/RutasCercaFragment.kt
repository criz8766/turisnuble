package cl.example.turisnuble.fragments

import android.content.Context
import android.location.Location
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import cl.example.turisnuble.R
import cl.example.turisnuble.data.GtfsDataManager
import cl.example.turisnuble.data.GtfsStop
import cl.example.turisnuble.data.SharedViewModel
import cl.example.turisnuble.utils.DetalleTurismoNavigator // <--- IMPORTANTE
import cl.example.turisnuble.utils.MapMover
import com.google.android.gms.location.LocationServices
import com.google.transit.realtime.GtfsRealtime
import java.util.concurrent.TimeUnit

// Las data classes no cambian
data class LlegadaInfo(val linea: String, val directionId: Int, val tiempoLlegadaMin: Int)
data class ParaderoConLlegadas(val paradero: GtfsStop, val llegadas: List<LlegadaInfo>)

class RutasCercaFragment : Fragment() {

    private val sharedViewModel: SharedViewModel by activityViewModels()
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ParaderosCercanosAdapter

    // Interfaces para comunicarse con MainActivity
    private var mapMover: MapMover? = null
    private var routeNavigator: DetalleTurismoNavigator? = null // <--- NUEVO

    override fun onAttach(context: Context) {
        super.onAttach(context)
        // 1. Conectar MapMover
        if (context is MapMover) {
            mapMover = context
        } else {
            throw RuntimeException("$context must implement MapMover")
        }

        // 2. Conectar Navegador de Rutas (Para ir a los resultados) <--- NUEVO
        if (context is DetalleTurismoNavigator) {
            routeNavigator = context
        } else {
            throw RuntimeException("$context must implement DetalleTurismoNavigator")
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
            // --- LÓGICA CORREGIDA ---

            // 1. Centrar mapa (Ya lo hacías)
            mapMover?.centerMapOnPoint(
                paraderoSeleccionado.location.latitude,
                paraderoSeleccionado.location.longitude
            )

            // 2. ¡IR A LOS RESULTADOS! (Esto faltaba)
            // Esto le dice al MainActivity: "Cambia de pestaña, muestra las rutas de este paradero"
            routeNavigator?.showRoutesForStop(paraderoSeleccionado.stopId)
        }
        recyclerView.adapter = adapter

        sharedViewModel.nearbyCalculationCenter.observe(viewLifecycleOwner) {
            findNearbyStopsAndArrivals(sharedViewModel.feedMessage.value)
        }

        sharedViewModel.feedMessage.observe(viewLifecycleOwner) { feed ->
            findNearbyStopsAndArrivals(feed)
        }
        return view
    }

    override fun onResume() {
        super.onResume()
        findNearbyStopsAndArrivals(sharedViewModel.feedMessage.value)
    }

    private fun findNearbyStopsAndArrivals(feed: GtfsRealtime.FeedMessage?) {
        val calculationCenter = sharedViewModel.nearbyCalculationCenter.value

        if (calculationCenter != null) {
            calculateStops(feed, calculationCenter.latitude, calculationCenter.longitude)
        } else {
            val fusedLocationClient =
                LocationServices.getFusedLocationProviderClient(requireActivity())
            try {
                fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                    location?.let {
                        calculateStops(feed, it.latitude, it.longitude)
                    }
                }
            } catch (e: SecurityException) { /* Sin permisos */ }
        }
    }

    private fun calculateStops(feed: GtfsRealtime.FeedMessage?, lat: Double, lon: Double) {
        val paraderosCercanos = GtfsDataManager.stops.values
            .map { stop ->
                Pair(
                    stop,
                    distanceTo(lat, lon, stop.location.latitude, stop.location.longitude)
                )
            }
            .filter { it.second <= 500 }
            .sortedBy { it.second }
            .map { it.first }

        sharedViewModel.setNearbyStops(paraderosCercanos)

        val paraderosConLlegadas = paraderosCercanos.map { paradero ->
            val llegadas = mutableListOf<LlegadaInfo>()

            if (feed != null) {
                for (entity in feed.entityList) {
                    if (entity.hasTripUpdate()) {
                        for (stopUpdate in entity.tripUpdate.stopTimeUpdateList) {
                            if (stopUpdate.stopId == paradero.stopId) {
                                val trip = entity.tripUpdate.trip
                                val linea = GtfsDataManager.routes[trip.routeId]?.shortName ?: "Desc."
                                val tiempoLlegada = stopUpdate.arrival.time
                                val tiempoActual = System.currentTimeMillis() / 1000
                                val diffMinutos =
                                    TimeUnit.SECONDS.toMinutes(tiempoLlegada - tiempoActual).toInt()
                                if (diffMinutos >= 0) {
                                    llegadas.add(LlegadaInfo(linea, trip.directionId, diffMinutos))
                                }
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
        routeNavigator = null // Limpiamos la referencia
    }
}

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
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_paradero_cercano, parent, false)
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
                val lineaText =
                    if (llegada.linea == "13A" || llegada.linea == "13B") "13" else llegada.linea
                val directionText = if (llegada.directionId == 0) "Ida" else "Vuelta"
                val tiempoText =
                    if (llegada.tiempoLlegadaMin == 0) "Ahora" else "${llegada.tiempoLlegadaMin} min"
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
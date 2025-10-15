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
    // --- NUEVO: Añadimos una referencia para poder limpiar el mapa ---
    private var routeDrawer: RouteDrawer? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        mapMover = context as? MapMover
        routeDrawer = context as? RouteDrawer

        if (mapMover == null || routeDrawer == null) {
            throw RuntimeException("$context must implement MapMover and RouteDrawer")
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
            // --- ¡AQUÍ ESTÁ LA LÓGICA CORRECTA! ---

            // 1. Primero, limpiamos cualquier filtro o ruta dibujada en el mapa,
            //    usando la misma función que el botón "Mostrar buses cercanos".
            routeDrawer?.clearRoutes(recenterToUser = false)

            // 2. Después, centramos el mapa en el paradero que el usuario seleccionó.
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

    private fun distanceTo(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val results = FloatArray(1)
        Location.distanceBetween(lat1, lon1, lat2, lon2, results)
        return results[0]
    }

    private fun findNearbyStopsAndArrivals(feed: GtfsRealtime.FeedMessage) {
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location == null) return@addOnSuccessListener

                val userLat = location.latitude
                val userLon = location.longitude

                val paraderosCercanos = GtfsDataManager.stops.values
                    .map { stop ->
                        Pair(stop, distanceTo(userLat, userLon, stop.location.latitude, stop.location.longitude))
                    }
                    .filter { it.second <= 500 }
                    .sortedBy { it.second }
                    .map { it.first }

                sharedViewModel.setNearbyStops(paraderosCercanos)

                val paraderosConLlegadas = paraderosCercanos.map { paradero ->
                    val llegadas = mutableListOf<LlegadaInfo>()
                    for (entity in feed.entityList) {
                        if (entity.hasTripUpdate()) {
                            for (stopUpdate in entity.tripUpdate.stopTimeUpdateList) {
                                if (stopUpdate.stopId == paradero.stopId) {
                                    val trip = entity.tripUpdate.trip
                                    val routeId = trip.routeId
                                    val directionId = trip.directionId
                                    val linea = GtfsDataManager.routes[routeId]?.shortName ?: "Desc."
                                    val tiempoLlegada = stopUpdate.arrival.time
                                    val tiempoActual = System.currentTimeMillis() / 1000
                                    val diffSegundos = tiempoLlegada - tiempoActual
                                    val diffMinutos = TimeUnit.SECONDS.toMinutes(diffSegundos).toInt()

                                    if (diffMinutos >= 0) {
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
        // --- Limpiamos la referencia al salir ---
        routeDrawer = null
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
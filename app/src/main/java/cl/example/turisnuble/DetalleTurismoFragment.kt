package cl.example.turisnuble

import android.content.Context
import android.os.Bundle
import android.util.Log // <-- AÑADE ESTA IMPORTACIÓN
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button // <-- AÑADE ESTA IMPORTACIÓN
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.Serializable

// ... (el resto de la clase, companion object, onAttach, etc. sin cambios) ...
class DetalleTurismoFragment : Fragment() {

    private var navigator: DetalleTurismoNavigator? = null

    companion object {
        private const val ARG_PUNTO_TURISTICO = "punto_turistico"

        fun newInstance(punto: PuntoTuristico): DetalleTurismoFragment {
            val fragment = DetalleTurismoFragment()
            val args = Bundle().apply {
                putSerializable(ARG_PUNTO_TURISTICO, punto)
            }
            fragment.arguments = args
            return fragment
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is DetalleTurismoNavigator) {
            navigator = context
        } else {
            throw RuntimeException("$context must implement DetalleTurismoNavigator")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_detalle_turismo, container, false)

        @Suppress("DEPRECATION")
        val puntoTuristico = arguments?.getSerializable(ARG_PUNTO_TURISTICO) as? PuntoTuristico
            ?: return view

        // Configurar botón de volver: sale de la pantalla de detalle
        view.findViewById<ImageButton>(R.id.back_button).setOnClickListener {
            navigator?.hideDetailFragment()
        }

        // --- AÑADIDO: Encontrar el botón ---
        val btnComoLlegar: Button = view.findViewById(R.id.btn_como_llegar)

        // 1. Mostrar la información del punto turístico
        view.findViewById<ImageView>(R.id.imagen_punto_turistico).setImageResource(puntoTuristico.imagenId)
        view.findViewById<TextView>(R.id.nombre_punto_turistico).text = puntoTuristico.nombre
        view.findViewById<TextView>(R.id.direccion_punto_turistico).text = puntoTuristico.direccion

        // 2. Encontrar y mostrar los paraderos cercanos
        val nearbyStops = GtfsDataManager.getNearbyStops(puntoTuristico.latitud, puntoTuristico.longitud, 3)

        val recyclerView = view.findViewById<RecyclerView>(R.id.recycler_view_paraderos_cercanos)
        recyclerView.layoutManager = LinearLayoutManager(context)

        val noParaderosTextView = view.findViewById<TextView>(R.id.no_paraderos_cercanos)

        if (nearbyStops.isEmpty()) {
            noParaderosTextView.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        } else {
            noParaderosTextView.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE

            // Adaptador para mostrar los paraderos y las rutas que pasan.
            val adapter = ParaderosCercanosAdapter(nearbyStops) { stop ->
                // Acción al hacer clic: ir a la pestaña de Rutas y filtrar.
                navigator?.showRoutesForStop(stop.stopId)
            }
            recyclerView.adapter = adapter
        }

        // --- AÑADIDO: Lógica del botón ---
        btnComoLlegar.setOnClickListener {
            Log.d("DetalleTurismo", "Botón 'Cómo Llegar' presionado para ${puntoTuristico.nombre}")
            navigator?.onGetDirectionsClicked(puntoTuristico) // Llama a MainActivity
            navigator?.hideDetailFragment() // Cierra este fragmento
        }
        // --- FIN AÑADIDO ---

        return view
    }

    override fun onDetach() {
        super.onDetach()
        navigator = null
    }

    // Adaptador interno (sin cambios)
    private inner class ParaderosCercanosAdapter(
        private val stops: List<GtfsStop>,
        private val onItemClick: (GtfsStop) -> Unit
    ) : RecyclerView.Adapter<ParaderosCercanosAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val stopIdChip: TextView = view.findViewById(R.id.stop_id_chip)
            val nombreParadero: TextView = view.findViewById(R.id.nombre_paradero)
            val lineasBuses: TextView = view.findViewById(R.id.llegadas_buses)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_paradero_cercano, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val stop = stops[position]
            holder.stopIdChip.text = stop.stopId
            holder.nombreParadero.text = stop.name

            val routesInfo = GtfsDataManager.getRoutesForStop(stop.stopId)
            val routesText = if (routesInfo.isNotEmpty()) {
                routesInfo
                    .distinctBy { it.route.shortName }
                    .sortedBy { it.route.shortName }
                    .joinToString(" | ") { displayRoute ->
                        val shortName = displayRoute.route.shortName
                        if (shortName == "13A" || shortName == "13B") "Línea 13" else "Línea $shortName"
                    }
            } else {
                "No hay rutas disponibles"
            }
            holder.lineasBuses.text = "Rutas: $routesText"
            holder.itemView.setOnClickListener {
                onItemClick(stop)
            }
        }

        override fun getItemCount() = stops.size
    }
}
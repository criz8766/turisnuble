package cl.example.turisnuble

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

// FIX: Definición canónica de MapMover para todo el paquete.
interface MapMover {
    fun centerMapOnPoint(lat: Double, lon: Double)
}

class DetalleRutaFragment : Fragment() {

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
        val view = inflater.inflate(R.layout.fragment_detalle_ruta, container, false)

        val recyclerView = view.findViewById<RecyclerView>(R.id.recycler_view_paraderos)
        val backButton = view.findViewById<ImageButton>(R.id.back_button)
        val headerTitle = view.findViewById<TextView>(R.id.route_name_header)

        recyclerView.layoutManager = LinearLayoutManager(context)

        val routeId = arguments?.getString("routeId")
        val directionId = arguments?.getInt("directionId", -1)

        if (routeId != null && directionId != null && directionId != -1) {
            val route = GtfsDataManager.routes[routeId]
            val directionText = if (directionId == 0) "Ida" else "Vuelta"

            // Lógica para agrupar 13A y 13B en el título
            val shortName = route?.shortName
            val lineaText = if (shortName == "13A" || shortName == "13B") "13" else shortName

            headerTitle.text = "Línea $lineaText - $directionText"

            val paraderos = GtfsDataManager.getStopsForRoute(routeId, directionId)

            recyclerView.adapter = ParaderosDetalleAdapter(paraderos) { paraderoSeleccionado ->
                // Al hacer clic, le pedimos a MainActivity que mueva el mapa
                mapMover?.centerMapOnPoint(paraderoSeleccionado.location.latitude, paraderoSeleccionado.location.longitude)
            }
        }

        backButton.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        return view
    }

    override fun onDetach() {
        super.onDetach()
        mapMover = null
    }

    companion object {
        fun newInstance(routeId: String, directionId: Int): DetalleRutaFragment {
            val fragment = DetalleRutaFragment()
            val args = Bundle()
            args.putString("routeId", routeId)
            args.putInt("directionId", directionId)
            fragment.arguments = args
            return fragment
        }
    }
}

class ParaderosDetalleAdapter(
    private val paraderos: List<GtfsStop>,
    private val onItemClick: (GtfsStop) -> Unit
) :
    RecyclerView.Adapter<ParaderosDetalleAdapter.ParaderoViewHolder>() {

    class ParaderoViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val stopSequence: TextView = view.findViewById(R.id.stop_sequence)
        val stopName: TextView = view.findViewById(R.id.stop_name)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ParaderoViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_paradero_detalle, parent, false)
        return ParaderoViewHolder(view)
    }

    override fun onBindViewHolder(holder: ParaderoViewHolder, position: Int) {
        val paradero = paraderos[position]

        holder.stopSequence.text = paradero.stopId
        holder.stopName.text = paradero.name

        holder.itemView.setOnClickListener {
            onItemClick(paradero)
        }
    }

    override fun getItemCount() = paraderos.size
}
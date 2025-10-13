package cl.example.turisnuble

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class DetalleRutaFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflamos el layout que creamos en el paso anterior
        val view = inflater.inflate(R.layout.fragment_detalle_ruta, container, false)

        // Obtenemos las referencias a las vistas del layout
        val recyclerView = view.findViewById<RecyclerView>(R.id.recycler_view_paraderos)
        val backButton = view.findViewById<ImageButton>(R.id.back_button)
        val headerTitle = view.findViewById<TextView>(R.id.route_name_header)

        recyclerView.layoutManager = LinearLayoutManager(context)

        // Obtenemos los argumentos que nos enviará MainActivity
        val routeId = arguments?.getString("routeId")
        val directionId = arguments?.getInt("directionId", -1)

        if (routeId != null && directionId != null && directionId != -1) {
            // Buscamos la información de la ruta en el GtfsDataManager
            val route = GtfsDataManager.routes[routeId]
            val directionText = if (directionId == 0) "Ida" else "Vuelta"
            headerTitle.text = "Línea ${route?.shortName} - $directionText"

            // Obtenemos la lista ordenada de paraderos
            val paraderos = GtfsDataManager.getStopsForRoute(routeId, directionId)
            recyclerView.adapter = ParaderosDetalleAdapter(paraderos)
        }

        // Lógica del botón para volver atrás
        backButton.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        return view
    }

    // Un método estático para crear instancias del fragmento con argumentos
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

// --- ADAPTADOR PARA LA LISTA DE PARADEROS ---

class ParaderosDetalleAdapter(private val paraderos: List<GtfsStop>) :
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
        holder.stopSequence.text = (position + 1).toString() // Mostramos el número de secuencia
        holder.stopName.text = paradero.name
    }

    override fun getItemCount() = paraderos.size
}
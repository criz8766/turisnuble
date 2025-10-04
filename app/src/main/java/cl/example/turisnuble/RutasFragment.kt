package cl.example.turisnuble

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

// --- INTERFAZ ACTUALIZADA ---
// Ahora pasamos el objeto RutaInfo completo
interface RouteDrawer {
    fun drawRoute(routeInfo: RutaInfo)
    fun clearRoutes()
}

data class RutaInfo(
    val linea: String,
    val recorrido: String,
    val fileName: String,
    val color: String,
    val routeId: String,
    val directionId: Int
)

class RutasFragment : Fragment() {

    private var routeDrawer: RouteDrawer? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is RouteDrawer) {
            routeDrawer = context
        } else {
            throw RuntimeException("$context must implement RouteDrawer")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_rutas, container, false)
        val recyclerView = view.findViewById<RecyclerView>(R.id.recycler_view_rutas)
        recyclerView.layoutManager = LinearLayoutManager(context)

        val listaDeRutas = listOf(
            RutaInfo(
                linea = "Línea 3",
                recorrido = "Ida: Agronomía - El Tejar",
                fileName = "linea_3_ida.kml",
                color = "#FF0000",
                routeId = "468",
                directionId = 0
            ),
            RutaInfo(
                linea = "Línea 3",
                recorrido = "Vuelta: El Tejar - Agronomía",
                fileName = "linea_3_vuelta.kml",
                color = "#CC0000",
                routeId = "468",
                directionId = 1
            )
        )

        // --- LLAMADA ACTUALIZADA ---
        // Pasamos el objeto completo al hacer clic
        val adapter = RutasAdapter(listaDeRutas) { rutaSeleccionada ->
            routeDrawer?.clearRoutes()
            routeDrawer?.drawRoute(rutaSeleccionada)
        }
        recyclerView.adapter = adapter

        return view
    }

    override fun onDetach() {
        super.onDetach()
        routeDrawer = null
    }
}

class RutasAdapter(
    private val rutas: List<RutaInfo>,
    private val onItemClick: (RutaInfo) -> Unit
) : RecyclerView.Adapter<RutasAdapter.RutaViewHolder>() {

    class RutaViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val nombreLinea: TextView = view.findViewById(R.id.nombre_linea)
        val nombreRecorrido: TextView = view.findViewById(R.id.nombre_recorrido)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RutaViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_ruta, parent, false)
        return RutaViewHolder(view)
    }

    override fun onBindViewHolder(holder: RutaViewHolder, position: Int) {
        val ruta = rutas[position]
        holder.nombreLinea.text = ruta.linea
        holder.nombreRecorrido.text = ruta.recorrido
        holder.itemView.setOnClickListener {
            onItemClick(ruta)
        }
    }

    override fun getItemCount() = rutas.size
}
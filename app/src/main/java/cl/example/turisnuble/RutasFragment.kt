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

// Interfaz para comunicar al MainActivity qué ruta debe dibujar
interface RouteDrawer {
    fun drawRoute(fileName: String, color: String)
    fun clearRoutes()
}

// --- CLASE DE DATOS ACTUALIZADA ---
// Ahora tiene campos separados para el título y el subtítulo
data class RutaInfo(
    val linea: String,
    val recorrido: String,
    val fileName: String,
    val color: String
)

// --- Fragmento que muestra la lista de rutas ---
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

        // --- LISTA DE RUTAS ACTUALIZADA con el nuevo formato ---
        val listaDeRutas = listOf(
            RutaInfo("Línea 3", "Agronomía - El Tejar", "linea_3_ida.kml", "#FF0000")
            // Puedes añadir más rutas aquí, siguiendo el mismo formato
            // Ejemplo: RutaInfo("Línea 4", "Río Viejo - lansa", "linea_4_ida.kml", "#0000FF")
        )

        val adapter = RutasAdapter(listaDeRutas) { rutaSeleccionada ->
            routeDrawer?.clearRoutes()
            routeDrawer?.drawRoute(rutaSeleccionada.fileName, rutaSeleccionada.color)
        }
        recyclerView.adapter = adapter

        return view
    }

    override fun onDetach() {
        super.onDetach()
        routeDrawer = null
    }
}


// --- ADAPTADOR ACTUALIZADO ---
class RutasAdapter(
    private val rutas: List<RutaInfo>,
    private val onItemClick: (RutaInfo) -> Unit
) : RecyclerView.Adapter<RutasAdapter.RutaViewHolder>() {

    // ViewHolder ahora obtiene referencias a los dos TextViews
    class RutaViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val nombreLinea: TextView = view.findViewById(R.id.nombre_linea)
        val nombreRecorrido: TextView = view.findViewById(R.id.nombre_recorrido)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RutaViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_ruta, parent, false)
        return RutaViewHolder(view)
    }

    // onBindViewHolder ahora asigna texto a ambos TextViews
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
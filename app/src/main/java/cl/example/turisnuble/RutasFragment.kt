package cl.example.turisnuble

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

// La interfaz no cambia
interface RouteDrawer {
    fun drawRoute(routeInfo: RutaInfo)
    fun clearRoutes()
}

// --- CLASE DE DATOS ACTUALIZADA ---
data class RutaInfo(
    val linea: String,
    val recorrido: String,
    val fileName: String,
    val color: String,
    val routeId: String,
    val directionId: Int,
    val iconResId: Int // <-- NUEVO CAMPO
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

        // --- LISTA DE RUTAS ACTUALIZADA con el nuevo ícono ---
        val listaDeRutas = listOf(
            RutaInfo(
                linea = "Línea 3",
                recorrido = "Ida: Agronomía - El Tejar",
                fileName = "linea_3_ida.kml",
                color = "#FF0000",
                routeId = "468",
                directionId = 0,
                iconResId = R.drawable.ic_bus_3 // <-- Ícono específico
            ),
            RutaInfo(
                linea = "Línea 3",
                recorrido = "Vuelta: El Tejar - Agronomía",
                fileName = "linea_3_vuelta.kml",
                color = "#CC0000",
                routeId = "468",
                directionId = 1,
                iconResId = R.drawable.ic_bus_3 // <-- Ícono específico
            ),
            // Ejemplo con ícono por defecto:
            // RutaInfo(
            //     linea = "Línea 4",
            //     recorrido = "Río Viejo - Lansa",
            //     fileName = "linea_4_ida.kml",
            //     color = "#0000FF",
            //     routeId = "469",
            //     directionId = 0,
            //     iconResId = R.drawable.ic_bus // <-- Ícono por defecto
            // )
        )

        val adapter = RutasAdapter(listaDeRutas,
            onItemClick = { rutaSeleccionada ->
                routeDrawer?.drawRoute(rutaSeleccionada)
            },
            onClearClick = {
                routeDrawer?.clearRoutes()
            }
        )
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
    private val onItemClick: (RutaInfo) -> Unit,
    private val onClearClick: () -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_ITEM = 1
    }

    class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val clearButton: TextView = view.findViewById(R.id.clear_selection_text)
    }

    class RutaViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val iconoRuta: ImageView = view.findViewById(R.id.icono_ruta) // <-- Obtenemos el ImageView
        val nombreLinea: TextView = view.findViewById(R.id.nombre_linea)
        val nombreRecorrido: TextView = view.findViewById(R.id.nombre_recorrido)
    }

    override fun getItemViewType(position: Int): Int {
        return if (position == 0) VIEW_TYPE_HEADER else VIEW_TYPE_ITEM
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == VIEW_TYPE_HEADER) {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_clear_selection, parent, false)
            HeaderViewHolder(view)
        } else {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_ruta, parent, false)
            RutaViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is HeaderViewHolder) {
            holder.clearButton.setOnClickListener {
                onClearClick()
            }
        } else if (holder is RutaViewHolder) {
            val ruta = rutas[position - 1]
            holder.nombreLinea.text = ruta.linea
            holder.nombreRecorrido.text = ruta.recorrido
            holder.iconoRuta.setImageResource(ruta.iconResId) // <-- Asignamos el ícono dinámicamente
            holder.itemView.setOnClickListener {
                onItemClick(ruta)
            }
        }
    }

    override fun getItemCount() = rutas.size + 1
}
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
    fun drawRoute(route: GtfsRoute, directionId: Int)
    fun clearRoutes()
}

// La clase de datos no cambia
data class DisplayRouteInfo(
    val route: GtfsRoute,
    val directionId: Int,
    val directionName: String
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

        val allDisplayRoutes = mutableListOf<DisplayRouteInfo>()
        GtfsDataManager.trips.values
            .distinctBy { it.routeId to it.directionId }
            .forEach { trip ->
                GtfsDataManager.routes[trip.routeId]?.let { route ->
                    val directionName = if (trip.directionId == 0) "Ida" else "Vuelta"
                    allDisplayRoutes.add(DisplayRouteInfo(route, trip.directionId, directionName))
                }
            }

        // --- LÓGICA DE FILTRADO CORREGIDA ---
        val mainRoutes = allDisplayRoutes.filter {
            val shortName = it.route.shortName
            // Una ruta principal es numérica O es específicamente "13A" o "13B"
            shortName.all { char -> char.isDigit() } || shortName == "13A" || shortName == "13B"
        }.sortedWith(
            compareBy(
                { it.route.shortName.filter { c -> c.isDigit() }.toIntOrNull() ?: Int.MAX_VALUE },
                { it.route.shortName }
            )
        )

        val variantRoutes = allDisplayRoutes.filterNot { mainRoutes.contains(it) }
            .sortedBy { it.route.shortName }

        val adapter = RutasAdapter(mainRoutes, variantRoutes,
            onItemClick = { displayRoute ->
                routeDrawer?.drawRoute(displayRoute.route, displayRoute.directionId)
                (activity as? MainActivity)?.showRouteDetail(displayRoute.route.routeId, displayRoute.directionId)
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

// El adaptador se mantiene exactamente igual, no necesita cambios.
class RutasAdapter(
    private val mainRoutes: List<DisplayRouteInfo>,
    private val variantRoutes: List<DisplayRouteInfo>,
    private val onItemClick: (DisplayRouteInfo) -> Unit,
    private val onClearClick: () -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_MAIN_ROUTE = 1
        private const val VIEW_TYPE_SUBHEADER = 2
        private const val VIEW_TYPE_VARIANT_ROUTE = 3
    }

    class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val clearButton: TextView = view.findViewById(R.id.clear_selection_text)
    }
    class RutaViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val iconoRuta: ImageView = view.findViewById(R.id.icono_ruta)
        val nombreLinea: TextView = view.findViewById(R.id.nombre_linea)
        val nombreRecorrido: TextView = view.findViewById(R.id.nombre_recorrido)
    }
    class SubheaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val headerText: TextView = view.findViewById(R.id.section_header_text)
    }

    override fun getItemViewType(position: Int): Int {
        return when {
            position == 0 -> VIEW_TYPE_HEADER
            position <= mainRoutes.size -> VIEW_TYPE_MAIN_ROUTE
            position == mainRoutes.size + 1 && variantRoutes.isNotEmpty() -> VIEW_TYPE_SUBHEADER
            else -> VIEW_TYPE_VARIANT_ROUTE
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_HEADER -> HeaderViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_clear_selection, parent, false))
            VIEW_TYPE_MAIN_ROUTE -> RutaViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_ruta, parent, false))
            VIEW_TYPE_SUBHEADER -> SubheaderViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_section_header, parent, false))
            VIEW_TYPE_VARIANT_ROUTE -> RutaViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_ruta, parent, false))
            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is HeaderViewHolder -> {
                holder.clearButton.setOnClickListener { onClearClick() }
            }
            is SubheaderViewHolder -> {
                holder.headerText.text = "Variantes"
            }
            is RutaViewHolder -> {
                val displayRoute = if (position <= mainRoutes.size) {
                    mainRoutes[position - 1]
                } else {
                    variantRoutes[position - mainRoutes.size - 2]
                }

                val shortName = displayRoute.route.shortName
                if (shortName == "13A" || shortName == "13B") {
                    holder.nombreLinea.text = "Línea 13"
                } else {
                    holder.nombreLinea.text = "Línea $shortName"
                }

                holder.nombreRecorrido.text = "${displayRoute.route.longName} (${displayRoute.directionName})"
                holder.iconoRuta.setImageResource(R.drawable.ic_bus)

                holder.itemView.setOnClickListener {
                    onItemClick(displayRoute)
                }
            }
        }
    }

    override fun getItemCount(): Int {
        var count = 1 + mainRoutes.size
        if (variantRoutes.isNotEmpty()) {
            count += 1 + variantRoutes.size
        }
        return count
    }
}
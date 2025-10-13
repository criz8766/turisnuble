package cl.example.turisnuble

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
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
    private val sharedViewModel: SharedViewModel by activityViewModels()
    private lateinit var adapter: RutasAdapter
    private var allDisplayRoutes: List<DisplayRouteInfo> = emptyList()

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

        prepareAllRoutes()

        adapter = RutasAdapter(emptyList(), emptyList(),
            onItemClick = { displayRoute ->
                routeDrawer?.drawRoute(displayRoute.route, displayRoute.directionId)
                (activity as? MainActivity)?.showRouteDetail(displayRoute.route.routeId, displayRoute.directionId)
            },
            onClearClick = {
                sharedViewModel.clearRouteFilter()
                routeDrawer?.clearRoutes()
            }
        )
        recyclerView.adapter = adapter

        sharedViewModel.routeFilter.observe(viewLifecycleOwner) { filter ->
            if (filter == null) {
                updateAdapterWithAllRoutes()
            } else {
                updateAdapterWithFilteredRoutes(filter)
            }
        }

        return view
    }

    // --- CAMBIO CLAVE: Añadimos onResume ---
    override fun onResume() {
        super.onResume()
        // Forzamos la actualización cada vez que la pestaña se vuelve visible.
        // Esto soluciona el problema de la lista vacía al inicio.
        if (sharedViewModel.routeFilter.value == null) {
            updateAdapterWithAllRoutes()
        } else {
            sharedViewModel.routeFilter.value?.let { updateAdapterWithFilteredRoutes(it) }
        }
    }

    private fun prepareAllRoutes() {
        val routes = mutableListOf<DisplayRouteInfo>()
        GtfsDataManager.trips.values
            .distinctBy { it.routeId to it.directionId }
            .forEach { trip ->
                GtfsDataManager.routes[trip.routeId]?.let { route ->
                    val directionName = if (trip.directionId == 0) "Ida" else "Vuelta"
                    routes.add(DisplayRouteInfo(route, trip.directionId, directionName))
                }
            }
        allDisplayRoutes = routes
    }

    private fun updateAdapterWithAllRoutes() {
        val mainRoutes = allDisplayRoutes.filter {
            val shortName = it.route.shortName
            shortName.all { char -> char.isDigit() } || shortName == "13A" || shortName == "13B"
        }.sortedWith(
            compareBy(
                { it.route.shortName.filter { c -> c.isDigit() }.toIntOrNull() ?: Int.MAX_VALUE },
                { it.route.shortName }
            )
        )
        val variantRoutes = allDisplayRoutes.filterNot { mainRoutes.contains(it) }
            .sortedBy { it.route.shortName }

        adapter.updateData(mainRoutes, variantRoutes)
    }

    private fun updateAdapterWithFilteredRoutes(filteredRoutes: List<DisplayRouteInfo>) {
        val sortedFilter = filteredRoutes.sortedWith(
            compareBy(
                { it.route.shortName.filter { c -> c.isDigit() }.toIntOrNull() ?: Int.MAX_VALUE },
                { it.route.shortName }
            )
        )
        adapter.updateData(sortedFilter, emptyList())
    }

    override fun onDetach() {
        super.onDetach()
        routeDrawer = null
    }
}

// El adaptador se mantiene exactamente igual, no necesita cambios.
class RutasAdapter(
    private var mainRoutes: List<DisplayRouteInfo>,
    private var variantRoutes: List<DisplayRouteInfo>,
    private val onItemClick: (DisplayRouteInfo) -> Unit,
    private val onClearClick: () -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_MAIN_ROUTE = 1
        private const val VIEW_TYPE_SUBHEADER = 2
        private const val VIEW_TYPE_VARIANT_ROUTE = 3
    }

    fun updateData(newMainRoutes: List<DisplayRouteInfo>, newVariantRoutes: List<DisplayRouteInfo>) {
        this.mainRoutes = newMainRoutes
        this.variantRoutes = newVariantRoutes
        notifyDataSetChanged()
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
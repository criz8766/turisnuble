package cl.example.turisnuble

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.google.firebase.ktx.Firebase

data class DisplayRouteInfo(
    val route: GtfsRoute,
    val directionId: Int,
    val directionName: String
)

class RutasFragment : Fragment() {

    private var routeDrawer: RouteDrawer? = null
    private val sharedViewModel: SharedViewModel by activityViewModels()

    private lateinit var recyclerView: RecyclerView
    private lateinit var btnVolver: LinearLayout
    private lateinit var txtTitulo: TextView

    // Listas generales
    private var urbanRoutes: List<DisplayRouteInfo> = emptyList() // Micros de Chillán
    private var ruralRoutes: List<DisplayRouteInfo> = emptyList() // Buses Rurales

    private var isShowingList = false

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

        recyclerView = view.findViewById(R.id.recycler_view_rutas)
        btnVolver = view.findViewById(R.id.btn_volver_categorias)
        txtTitulo = view.findViewById(R.id.txt_titulo_categoria_actual)

        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.setHasFixedSize(true)

        prepareClassifiedRoutes()

        btnVolver.setOnClickListener {
            showCategories()
        }

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isShowingList && sharedViewModel.routeFilter.value == null) {
                    showCategories()
                } else {
                    isEnabled = false
                    requireActivity().onBackPressed()
                }
            }
        })

        sharedViewModel.routeFilter.observe(viewLifecycleOwner) { filter ->
            if (filter == null) {
                if (!isShowingList) showCategories()
            } else {
                showFilteredList(filter)
            }
        }

        if (sharedViewModel.routeFilter.value == null) {
            showCategories()
        }

        return view
    }

    private fun prepareClassifiedRoutes() {
        val allRoutes = mutableListOf<DisplayRouteInfo>()
        GtfsDataManager.trips.values
            .distinctBy { it.routeId to it.directionId }
            .forEach { trip ->
                GtfsDataManager.routes[trip.routeId]?.let { route ->
                    val directionName = if (trip.directionId == 0) "Ida" else "Vuelta"
                    allRoutes.add(DisplayRouteInfo(route, trip.directionId, directionName))
                }
            }

        // 1. Asignamos TODO lo actual a "Micros de Chillán"
        urbanRoutes = allRoutes

        // 2. "Buses Rurales" queda vacío esperando el nuevo JSON
        ruralRoutes = emptyList()
    }

    private fun showCategories() {
        isShowingList = false
        btnVolver.visibility = View.GONE

        val categories = listOf(
            RouteCategory(
                name = "Micros de Chillán",
                count = urbanRoutes.size,
                imageResId = R.drawable.buses_chillan,
                routes = urbanRoutes
            ),
            RouteCategory(
                name = "Buses Rurales",
                count = ruralRoutes.size,
                imageResId = R.drawable.buses_rurales_chillan,
                routes = ruralRoutes
            )
        )

        recyclerView.adapter = RutaCategoriaAdapter(categories) { selectedCategory ->
            showRouteList(selectedCategory.name, selectedCategory.routes)
        }
    }

    private fun showRouteList(title: String, routes: List<DisplayRouteInfo>) {
        isShowingList = true
        btnVolver.visibility = View.VISIBLE
        txtTitulo.text = title

        // Recuperamos la lógica de división (Main vs Variantes)
        val mainRoutes = routes.filter {
            val shortName = it.route.shortName
            shortName.all { char -> char.isDigit() } || shortName == "13A" || shortName == "13B"
        }.sortedWith(
            compareBy(
                { it.route.shortName.filter { c -> c.isDigit() }.toIntOrNull() ?: Int.MAX_VALUE },
                { it.route.shortName }
            )
        )

        val variantRoutes = routes.filterNot { mainRoutes.contains(it) }
            .sortedBy { it.route.shortName }

        // --- CAMBIO: Eliminado onClearClick ---
        val adapter = RutasAdapter(
            mainRoutes = mainRoutes,
            variantRoutes = variantRoutes,
            onItemClick = { displayRoute ->
                routeDrawer?.drawRoute(displayRoute.route, displayRoute.directionId)
                (activity as? MainActivity)?.showRouteDetail(displayRoute.route.routeId, displayRoute.directionId)
            }
        )
        recyclerView.adapter = adapter
    }

    private fun showFilteredList(filteredRoutes: List<DisplayRouteInfo>) {
        isShowingList = true
        btnVolver.visibility = View.VISIBLE
        txtTitulo.text = "Resultados"

        val sortedFilter = filteredRoutes.sortedWith(
            compareBy(
                { it.route.shortName.filter { c -> c.isDigit() }.toIntOrNull() ?: Int.MAX_VALUE },
                { it.route.shortName }
            )
        )

        // --- CAMBIO: Eliminado onClearClick ---
        val adapter = RutasAdapter(
            mainRoutes = sortedFilter,
            variantRoutes = emptyList(),
            onItemClick = { displayRoute ->
                routeDrawer?.drawRoute(displayRoute.route, displayRoute.directionId)
                (activity as? MainActivity)?.showRouteDetail(displayRoute.route.routeId, displayRoute.directionId)
            }
        )
        recyclerView.adapter = adapter
    }

    override fun onDetach() {
        super.onDetach()
        routeDrawer = null
    }
}

// --- ADAPTADOR MODIFICADO (Sin Header) ---
class RutasAdapter(
    private var mainRoutes: List<DisplayRouteInfo>,
    private var variantRoutes: List<DisplayRouteInfo>,
    private val onItemClick: (DisplayRouteInfo) -> Unit
    // --- CAMBIO: Eliminado onClearClick del constructor ---
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val auth: FirebaseAuth = Firebase.auth
    private val currentUser = auth.currentUser
    private var favoriteRutaIds = setOf<String>()

    init {
        currentUser?.uid?.let { userId ->
            FavoritesManager.getRutaFavRef(userId, "").addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    favoriteRutaIds = snapshot.children.mapNotNull { it.key }.toSet()
                    notifyDataSetChanged()
                }
                override fun onCancelled(error: DatabaseError) {
                    Log.w("RutasAdapter", "Error al cargar rutas favoritas", error.toException())
                }
            })
        }
    }

    companion object {
        // --- CAMBIO: Eliminado VIEW_TYPE_HEADER ---
        private const val VIEW_TYPE_MAIN_ROUTE = 1
        private const val VIEW_TYPE_SUBHEADER = 2
        private const val VIEW_TYPE_VARIANT_ROUTE = 3
    }

    fun updateData(newMainRoutes: List<DisplayRouteInfo>, newVariantRoutes: List<DisplayRouteInfo>) {
        this.mainRoutes = newMainRoutes
        this.variantRoutes = newVariantRoutes
        notifyDataSetChanged()
    }

    // --- CAMBIO: Eliminado HeaderViewHolder ---

    class RutaViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val iconoRuta: ImageView = view.findViewById(R.id.icono_ruta)
        val nombreLinea: TextView = view.findViewById(R.id.nombre_linea)
        val nombreRecorrido: TextView = view.findViewById(R.id.nombre_recorrido)
        val favoriteButton: ImageButton = view.findViewById(R.id.btn_favorite_paradero)
    }
    class SubheaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val headerText: TextView = view.findViewById(R.id.section_header_text)
    }

    override fun getItemViewType(position: Int): Int {
        // --- CAMBIO: Lógica ajustada para empezar en 0 ---
        return when {
            position < mainRoutes.size -> VIEW_TYPE_MAIN_ROUTE
            position == mainRoutes.size && variantRoutes.isNotEmpty() -> VIEW_TYPE_SUBHEADER
            else -> VIEW_TYPE_VARIANT_ROUTE
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            // --- CAMBIO: Eliminado caso HEADER ---
            VIEW_TYPE_MAIN_ROUTE -> RutaViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_ruta, parent, false))
            VIEW_TYPE_SUBHEADER -> SubheaderViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_section_header, parent, false))
            VIEW_TYPE_VARIANT_ROUTE -> RutaViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_ruta, parent, false))
            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            // --- CAMBIO: Eliminado caso HeaderViewHolder ---
            is SubheaderViewHolder -> {
                holder.headerText.text = "Variantes"
            }
            is RutaViewHolder -> {
                // --- CAMBIO: Ajuste de índices (ya no restamos 1 al principio) ---
                val displayRoute = if (position < mainRoutes.size) {
                    mainRoutes[position]
                } else {
                    // Restamos el tamaño de mainRoutes y 1 por el subheader
                    variantRoutes[position - mainRoutes.size - 1]
                }

                val route = displayRoute.route

                val shortName = route.shortName
                if (shortName == "13A" || shortName == "13B") {
                    holder.nombreLinea.text = "Línea 13"
                } else {
                    holder.nombreLinea.text = "Línea $shortName"
                }

                holder.nombreRecorrido.text = "${route.longName} (${displayRoute.directionName})"

                val iconRes = when (route.routeId) {
                    "468" -> R.drawable.linea_3
                    "469" -> R.drawable.linea_4
                    "467" -> R.drawable.linea_2
                    "470" -> R.drawable.linea_6
                    "471" -> R.drawable.linea_7
                    "472" -> R.drawable.linea_8
                    "954" -> R.drawable.linea_7
                    "478" -> R.drawable.linea_14
                    "477" -> R.drawable.linea_14
                    "473" -> R.drawable.linea_10
                    "476" -> R.drawable.linea_13
                    "474" -> R.drawable.linea_13
                    "475" -> R.drawable.linea_13
                    "466" -> R.drawable.linea_1
                    else -> R.drawable.ic_bus
                }
                holder.iconoRuta.setImageResource(iconRes)

                holder.itemView.setOnClickListener {
                    onItemClick(displayRoute)
                }

                if (currentUser == null) {
                    holder.favoriteButton.visibility = View.GONE
                } else {
                    holder.favoriteButton.visibility = View.VISIBLE
                    val userId = currentUser.uid
                    val isFavorite = favoriteRutaIds.contains(route.routeId)

                    if (isFavorite) {
                        holder.favoriteButton.setImageResource(R.drawable.ic_star_filled)
                    } else {
                        holder.favoriteButton.setImageResource(R.drawable.ic_star_border)
                    }

                    holder.favoriteButton.setOnClickListener {
                        if (favoriteRutaIds.contains(route.routeId)) {
                            FavoritesManager.removeFavoriteRuta(userId, route.routeId)
                            Toast.makeText(holder.itemView.context, "Ruta eliminada de favoritos", Toast.LENGTH_SHORT).show()
                        } else {
                            FavoritesManager.addFavoriteRuta(userId, route)
                            Toast.makeText(holder.itemView.context, "Ruta añadida a favoritos", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    override fun getItemCount(): Int {
        // --- CAMBIO: Eliminado el +1 del header ---
        var count = mainRoutes.size
        if (variantRoutes.isNotEmpty()) {
            count += 1 + variantRoutes.size
        }
        return count
    }
}
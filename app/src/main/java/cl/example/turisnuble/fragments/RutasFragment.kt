package cl.example.turisnuble.fragments

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
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
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import cl.example.turisnuble.R
import cl.example.turisnuble.activities.MainActivity
import cl.example.turisnuble.adapters.RouteCategory
import cl.example.turisnuble.adapters.RutaCategoriaAdapter
import cl.example.turisnuble.data.FavoritesManager
import cl.example.turisnuble.data.GtfsDataManager
import cl.example.turisnuble.data.GtfsRoute
import cl.example.turisnuble.data.SharedViewModel
import cl.example.turisnuble.utils.RouteDrawer
import com.google.android.material.card.MaterialCardView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.launch

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

    private var urbanRoutes: List<DisplayRouteInfo> = emptyList()
    private var ruralRoutes: List<DisplayRouteInfo> = emptyList()

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

        viewLifecycleOwner.lifecycleScope.launch {
            GtfsDataManager.loadRuralData()
            prepareClassifiedRoutes()

            if (sharedViewModel.routeFilter.value == null) {
                showCategories()
            }
        }

        btnVolver.setOnClickListener { showCategories() }

        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
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

        ruralRoutes = allRoutes.filter { it.route.shortName == "Rural" }
            .sortedBy { it.route.longName }

        urbanRoutes = allRoutes.filterNot { it.route.shortName == "Rural" }
            .sortedWith(
                compareBy(
                    {
                        it.route.shortName.filter { c -> c.isDigit() }.toIntOrNull()
                            ?: Int.MAX_VALUE
                    },
                    { it.route.shortName }
                )
            )
    }

    private fun showCategories() {
        isShowingList = false
        btnVolver.visibility = View.GONE

        val categories = listOf(
            RouteCategory(
                "Micros de Chillán",
                urbanRoutes.size,
                R.drawable.buses_chillan,
                urbanRoutes
            ),
            RouteCategory(
                "Buses Rurales",
                ruralRoutes.size,
                R.drawable.buses_rurales_chillan,
                ruralRoutes
            )
        )

        recyclerView.adapter = RutaCategoriaAdapter(categories) { selected ->
            showRouteList(selected.name, selected.routes)
        }
    }

    private fun showRouteList(title: String, routes: List<DisplayRouteInfo>) {
        isShowingList = true
        btnVolver.visibility = View.VISIBLE
        txtTitulo.text = title

        if (title == "Buses Rurales") {
            val adapter = RutasAdapter(routes, emptyList(), onItemClick = { displayRoute ->
                routeDrawer?.drawRoute(displayRoute.route, displayRoute.directionId)
                Toast.makeText(
                    context,
                    "Mostrando: ${displayRoute.route.longName}",
                    Toast.LENGTH_SHORT
                ).show()

                (recyclerView.adapter as? RutasAdapter)?.let {
                    it.selectedRouteId = displayRoute.route.routeId
                    it.notifyDataSetChanged()
                }
            })
            recyclerView.adapter = adapter
        } else {
            val mainRoutes = routes.filter {
                val shortName = it.route.shortName
                shortName.all { c -> c.isDigit() } || shortName == "13A" || shortName == "13B"
            }.sortedWith(compareBy({
                it.route.shortName.filter { c -> c.isDigit() }.toIntOrNull() ?: Int.MAX_VALUE
            }, { it.route.shortName }))

            val variantRoutes = routes.filterNot { mainRoutes.contains(it) }
                .sortedBy { it.route.shortName }

            val adapter = RutasAdapter(mainRoutes, variantRoutes, onItemClick = { displayRoute ->
                routeDrawer?.drawRoute(displayRoute.route, displayRoute.directionId)
                (activity as? MainActivity)?.showRouteDetail(
                    displayRoute.route.routeId,
                    displayRoute.directionId
                )

                (recyclerView.adapter as? RutasAdapter)?.let {
                    it.selectedRouteId = displayRoute.route.routeId
                    it.notifyDataSetChanged()
                }
            })
            recyclerView.adapter = adapter
        }
    }

    private fun showFilteredList(filteredRoutes: List<DisplayRouteInfo>) {
        isShowingList = true
        btnVolver.visibility = View.VISIBLE
        txtTitulo.text = "Resultados"

        val sorted = filteredRoutes.sortedWith(compareBy({
            it.route.shortName.filter { c -> c.isDigit() }.toIntOrNull() ?: Int.MAX_VALUE
        }, { it.route.shortName }))

        val adapter = RutasAdapter(sorted, emptyList(), onItemClick = {
            routeDrawer?.drawRoute(it.route, it.directionId)
            (activity as? MainActivity)?.showRouteDetail(it.route.routeId, it.directionId)
        })
        recyclerView.adapter = adapter
    }

    override fun onDetach() {
        super.onDetach()
        routeDrawer = null
    }
}

class RutasAdapter(
    private var mainRoutes: List<DisplayRouteInfo>,
    private var variantRoutes: List<DisplayRouteInfo>,
    private val onItemClick: (DisplayRouteInfo) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val auth: FirebaseAuth = Firebase.auth
    private val currentUser = auth.currentUser
    private var favoriteRutaIds = setOf<String>()

    var selectedRouteId: String? = null

    init {
        currentUser?.uid?.let { userId ->
            FavoritesManager.getRutaFavRef(userId, "")
                .addValueEventListener(object : ValueEventListener {
                    override fun onDataChange(snap: DataSnapshot) {
                        favoriteRutaIds = snap.children.mapNotNull { it.key }.toSet()
                        notifyDataSetChanged()
                    }

                    override fun onCancelled(e: DatabaseError) {}
                })
        }
    }

    companion object {
        private const val VIEW_TYPE_MAIN = 1
        private const val VIEW_TYPE_SUBHEADER = 2
        private const val VIEW_TYPE_VARIANT = 3
    }

    fun updateData(newMain: List<DisplayRouteInfo>, newVar: List<DisplayRouteInfo>) {
        mainRoutes = newMain
        variantRoutes = newVar
        notifyDataSetChanged()
    }

    class RutaVH(v: View) : RecyclerView.ViewHolder(v) {
        val icono: ImageView = v.findViewById(R.id.icono_ruta)
        val linea: TextView = v.findViewById(R.id.nombre_linea)
        val recorrido: TextView = v.findViewById(R.id.nombre_recorrido)
        val favBtn: ImageButton = v.findViewById(R.id.btn_favorite_paradero)

        val cardView = v as? MaterialCardView
        val defaultColor: ColorStateList? = cardView?.cardBackgroundColor
    }

    class HeaderVH(v: View) : RecyclerView.ViewHolder(v) {
        val text: TextView = v.findViewById(R.id.section_header_text)
    }

    override fun getItemViewType(position: Int): Int {
        return when {
            position < mainRoutes.size -> VIEW_TYPE_MAIN
            position == mainRoutes.size && variantRoutes.isNotEmpty() -> VIEW_TYPE_SUBHEADER
            else -> VIEW_TYPE_VARIANT
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_MAIN -> RutaVH(
                LayoutInflater.from(parent.context).inflate(R.layout.item_ruta, parent, false)
            )

            VIEW_TYPE_SUBHEADER -> HeaderVH(
                LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_section_header, parent, false)
            )

            else -> RutaVH(
                LayoutInflater.from(parent.context).inflate(R.layout.item_ruta, parent, false)
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is HeaderVH) {
            holder.text.text = "Variantes"
            return
        }

        if (holder is RutaVH) {
            val item =
                if (position < mainRoutes.size) mainRoutes[position] else variantRoutes[position - mainRoutes.size - 1]
            val route = item.route

            // --- LÓGICA DE SELECCIÓN ---
            if (holder.cardView != null) {
                if (route.routeId == selectedRouteId) {
                    // CAMBIO: Color VERDE Claro (#C8E6C9)
                    holder.cardView.setCardBackgroundColor(Color.parseColor("#C8E6C9"))
                } else {
                    holder.cardView.setCardBackgroundColor(holder.defaultColor)
                }
            } else {
                if (route.routeId == selectedRouteId) holder.itemView.setBackgroundColor(
                    Color.parseColor(
                        "#C8E6C9"
                    )
                )
                else holder.itemView.setBackgroundColor(Color.TRANSPARENT)
            }
            // ---------------------------

            if (route.shortName == "Rural") {
                holder.linea.text = "Rural"
                holder.icono.setImageResource(R.drawable.ic_bus)
            } else {
                holder.linea.text =
                    if (route.shortName.startsWith("13")) "Línea 13" else "Línea ${route.shortName}"
                holder.icono.setImageResource(getIconForRoute(route.routeId))
            }
            holder.recorrido.text = route.longName

            holder.itemView.setOnClickListener { onItemClick(item) }

            if (currentUser == null) {
                holder.favBtn.visibility = View.GONE
            } else {
                holder.favBtn.visibility = View.VISIBLE
                holder.favBtn.setImageResource(if (favoriteRutaIds.contains(route.routeId)) R.drawable.ic_star_filled else R.drawable.ic_star_border)
                holder.favBtn.setOnClickListener {
                    if (favoriteRutaIds.contains(route.routeId)) FavoritesManager.removeFavoriteRuta(
                        currentUser.uid,
                        route.routeId
                    )
                    else FavoritesManager.addFavoriteRuta(currentUser.uid, route)
                }
            }
        }
    }

    override fun getItemCount(): Int =
        mainRoutes.size + if (variantRoutes.isNotEmpty()) variantRoutes.size + 1 else 0

    private fun getIconForRoute(id: String): Int {
        return when (id) {
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
    }
}
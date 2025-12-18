package cl.example.turisnuble.fragments

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.location.Location
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
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
import com.google.transit.realtime.GtfsRealtime
import kotlinx.coroutines.launch
import org.maplibre.android.geometry.LatLng

// Clase de datos auxiliar
data class DisplayRouteInfo(
    val route: GtfsRoute,
    val directionId: Int,
    val directionName: String
)

class RutasFragment : Fragment() {

    private var routeDrawer: RouteDrawer? = null
    private val sharedViewModel: SharedViewModel by activityViewModels()

    private lateinit var recyclerView: RecyclerView

    // UI Cabecera (Tu diseño personalizado)
    private lateinit var headerContainer: View
    private lateinit var btnVolver: View
    private lateinit var txtTitulo: TextView
    private lateinit var btnFavHeader: ImageButton

    private var currentParaderoFavListener: ValueEventListener? = null
    private var currentParaderoRefString: String? = null
    private val auth: FirebaseAuth = Firebase.auth

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

        // Binding de Vistas (Con tus IDs originales y la estrella agregada)
        recyclerView = view.findViewById(R.id.recycler_view_rutas)
        headerContainer = view.findViewById(R.id.layout_header_container)
        btnVolver = view.findViewById(R.id.btn_volver_categorias)
        txtTitulo = view.findViewById(R.id.txt_titulo_categoria_actual)
        btnFavHeader = view.findViewById(R.id.btn_fav_header)

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

        sharedViewModel.feedMessage.observe(viewLifecycleOwner) { feedMessage ->
            if (isShowingList && recyclerView.adapter is RutasAdapter) {
                (recyclerView.adapter as RutasAdapter).updateRealtimeData(feedMessage?.entityList)
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
                    { it.route.shortName.filter { c -> c.isDigit() }.toIntOrNull() ?: Int.MAX_VALUE },
                    { it.route.shortName }
                )
            )
    }

    private fun showCategories() {
        isShowingList = false
        headerContainer.visibility = View.GONE
        removeFavListener()

        val categories = listOf(
            RouteCategory("Micros de Chillán", urbanRoutes.size, R.drawable.buses_chillan, urbanRoutes),
            RouteCategory("Buses Rurales", ruralRoutes.size, R.drawable.buses_rurales_chillan, ruralRoutes)
        )

        recyclerView.adapter = RutaCategoriaAdapter(categories) { selected ->
            showRouteList(selected.name, selected.routes)
        }
    }

    private fun showRouteList(title: String, routes: List<DisplayRouteInfo>) {
        isShowingList = true
        headerContainer.visibility = View.VISIBLE
        btnFavHeader.visibility = View.GONE
        removeFavListener()

        txtTitulo.text = title

        // Pasamos null para no calcular en vista general
        val currentStopId: String? = null
        val currentStopLocation: LatLng? = null
        val currentEntities: List<GtfsRealtime.FeedEntity>? = null

        if (title == "Buses Rurales") {
            val adapter = RutasAdapter(routes, emptyList(), null, null, null, onItemClick = { displayRoute ->
                routeDrawer?.drawRoute(displayRoute.route, displayRoute.directionId)
                Toast.makeText(context, "Mostrando: ${displayRoute.route.longName}", Toast.LENGTH_SHORT).show()
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

            val adapter = RutasAdapter(mainRoutes, variantRoutes, currentStopId, currentStopLocation, currentEntities, onItemClick = { displayRoute ->
                routeDrawer?.drawRoute(displayRoute.route, displayRoute.directionId)
                (activity as? MainActivity)?.showRouteDetail(displayRoute.route.routeId, displayRoute.directionId)
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
        headerContainer.visibility = View.VISIBLE

        val currentStopId = sharedViewModel.selectedStopId.value
        val stop = if (currentStopId != null) GtfsDataManager.stops[currentStopId] else null
        val currentStopLocation = stop?.location

        if (stop != null) {
            txtTitulo.text = stop.name // Nombre real del paradero
            btnFavHeader.visibility = View.VISIBLE
            setupFavButton(stop.stopId)
        } else {
            txtTitulo.text = "Resultados"
            btnFavHeader.visibility = View.GONE
        }

        val currentEntities = sharedViewModel.feedMessage.value?.entityList

        val adapter = RutasAdapter(
            filteredRoutes.sortedWith(compareBy({
                it.route.shortName.filter { c -> c.isDigit() }.toIntOrNull() ?: Int.MAX_VALUE
            }, { it.route.shortName })),
            emptyList(),
            currentStopId,
            currentStopLocation,
            currentEntities,
            onItemClick = {
                routeDrawer?.drawRoute(it.route, it.directionId)
                (activity as? MainActivity)?.showRouteDetail(it.route.routeId, it.directionId)
            }
        )
        recyclerView.adapter = adapter
    }

    // --- FAVORITOS DEL PARADERO ---
    private fun setupFavButton(stopId: String) {
        val user = auth.currentUser
        if (user == null) {
            btnFavHeader.visibility = View.GONE
            return
        }
        removeFavListener()
        val favRef = FavoritesManager.getParaderoFavRef(user.uid, stopId)
        currentParaderoRefString = stopId

        currentParaderoFavListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    btnFavHeader.setImageResource(R.drawable.ic_star_filled)
                    btnFavHeader.setColorFilter(Color.parseColor("#FFC107")) // Amarillo
                } else {
                    btnFavHeader.setImageResource(R.drawable.ic_star_border)
                    btnFavHeader.setColorFilter(Color.WHITE) // Blanco
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        favRef.addValueEventListener(currentParaderoFavListener!!)

        btnFavHeader.setOnClickListener {
            favRef.get().addOnSuccessListener { snapshot ->
                if (snapshot.exists()) {
                    FavoritesManager.removeFavoriteParadero(user.uid, stopId)
                    Toast.makeText(context, "Eliminado de favoritos", Toast.LENGTH_SHORT).show()
                } else {
                    val stop = GtfsDataManager.stops[stopId]
                    if (stop != null) {
                        FavoritesManager.addFavoriteParadero(user.uid, stop)
                        Toast.makeText(context, "Paradero guardado ⭐", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun removeFavListener() {
        val user = auth.currentUser
        if (user != null && currentParaderoRefString != null && currentParaderoFavListener != null) {
            FavoritesManager.getParaderoFavRef(user.uid, currentParaderoRefString!!)
                .removeEventListener(currentParaderoFavListener!!)
        }
        currentParaderoRefString = null
        currentParaderoFavListener = null
    }

    override fun onDestroyView() {
        super.onDestroyView()
        removeFavListener()
    }

    override fun onDetach() {
        super.onDetach()
        routeDrawer = null
    }
}

// =====================================================================
// === ADAPTADOR CORREGIDO (Acceso correcto a Patente) ===
// =====================================================================

class RutasAdapter(
    private var mainRoutes: List<DisplayRouteInfo>,
    private var variantRoutes: List<DisplayRouteInfo>,
    private val currentStopId: String?,
    private val currentStopLocation: LatLng?,
    private var feedEntities: List<GtfsRealtime.FeedEntity>?,
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

    fun updateRealtimeData(newEntities: List<GtfsRealtime.FeedEntity>?) {
        this.feedEntities = newEntities
        notifyDataSetChanged()
    }

    // ViewHolder con el nuevo campo Patente
    class RutaVH(v: View) : RecyclerView.ViewHolder(v) {
        val icono: ImageView = v.findViewById(R.id.icono_ruta)
        val linea: TextView = v.findViewById(R.id.nombre_linea)
        val recorrido: TextView = v.findViewById(R.id.nombre_recorrido)
        val txtPatente: TextView = v.findViewById(R.id.txt_patente) // <--- NUEVO
        val favBtn: ImageButton = v.findViewById(R.id.btn_favorite_paradero)
        val txtTiempo: TextView = v.findViewById(R.id.txt_tiempo_espera)
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
            VIEW_TYPE_MAIN -> RutaVH(LayoutInflater.from(parent.context).inflate(R.layout.item_ruta, parent, false))
            VIEW_TYPE_SUBHEADER -> HeaderVH(LayoutInflater.from(parent.context).inflate(R.layout.item_section_header, parent, false))
            else -> RutaVH(LayoutInflater.from(parent.context).inflate(R.layout.item_ruta, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is HeaderVH) {
            holder.text.text = "Variantes"
            return
        }

        if (holder is RutaVH) {
            val item = if (position < mainRoutes.size) mainRoutes[position] else variantRoutes[position - mainRoutes.size - 1]
            val route = item.route

            // Selección Visual
            if (holder.cardView != null) {
                if (route.routeId == selectedRouteId) holder.cardView.setCardBackgroundColor(Color.parseColor("#C8E6C9"))
                else holder.cardView.setCardBackgroundColor(holder.defaultColor)
            } else {
                if (route.routeId == selectedRouteId) holder.itemView.setBackgroundColor(Color.parseColor("#C8E6C9"))
                else holder.itemView.setBackgroundColor(Color.TRANSPARENT)
            }

            if (route.shortName == "Rural") {
                holder.linea.text = "Rural"
                holder.icono.setImageResource(R.drawable.ic_bus)
                holder.txtTiempo.visibility = View.GONE
                holder.txtPatente.visibility = View.GONE
            } else {
                holder.linea.text = if (route.shortName.startsWith("13")) "Línea 13" else "Línea ${route.shortName}"
                holder.icono.setImageResource(getIconForRoute(route.routeId))

                // === LÓGICA DE DATOS (TIEMPO + PATENTE) ===
                val datosBus = obtenerDatosBusMasCercano(route.routeId, item.directionId)

                if (datosBus != null) {
                    // 1. Mostrar Tiempo
                    val tiempoStr = datosBus.first
                    holder.txtTiempo.visibility = View.VISIBLE
                    holder.txtTiempo.text = tiempoStr

                    if (tiempoStr == "Llegando" || (tiempoStr.contains("min") && (tiempoStr.split(" ")[0].toIntOrNull() ?: 99) <= 5)) {
                        holder.txtTiempo.setTextColor(Color.parseColor("#4CAF50"))
                    } else if (tiempoStr.contains("min") && (tiempoStr.split(" ")[0].toIntOrNull() ?: 99) <= 15) {
                        holder.txtTiempo.setTextColor(Color.parseColor("#FF9800"))
                    } else {
                        holder.txtTiempo.setTextColor(Color.GRAY)
                    }

                    // 2. Mostrar Patente (Si existe)
                    val patente = datosBus.second
                    if (!patente.isNullOrBlank()) {
                        holder.txtPatente.visibility = View.VISIBLE
                        holder.txtPatente.text = patente
                    } else {
                        holder.txtPatente.visibility = View.GONE
                    }

                } else {
                    // No hay datos -> Ocultar todo
                    holder.txtTiempo.visibility = View.GONE
                    holder.txtPatente.visibility = View.GONE
                }
            }
            holder.recorrido.text = route.longName
            holder.itemView.setOnClickListener { onItemClick(item) }

            // Botón Favorito de Ruta
            if (currentUser == null) {
                holder.favBtn.visibility = View.GONE
            } else {
                holder.favBtn.visibility = View.VISIBLE
                holder.favBtn.setImageResource(if (favoriteRutaIds.contains(route.routeId)) R.drawable.ic_star_filled else R.drawable.ic_star_border)
                holder.favBtn.setOnClickListener {
                    if (favoriteRutaIds.contains(route.routeId)) FavoritesManager.removeFavoriteRuta(currentUser.uid, route.routeId)
                    else FavoritesManager.addFavoriteRuta(currentUser.uid, route)
                }
            }
        }
    }

    // --- CEREBRO: Encuentra el bus y devuelve (Tiempo, Patente) ---
    private fun obtenerDatosBusMasCercano(routeId: String, directionId: Int): Pair<String, String?>? {
        if (currentStopLocation == null || feedEntities == null) return null

        val busesDeLaLinea = feedEntities!!.filter {
            it.hasVehicle() && it.vehicle.hasTrip() &&
                    it.vehicle.trip.routeId == routeId && it.vehicle.trip.directionId == directionId
        }
        if (busesDeLaLinea.isEmpty()) return null

        val trip = GtfsDataManager.trips.values.find { it.routeId == routeId && it.directionId == directionId }
        val shapePoints = GtfsDataManager.shapes[trip?.shapeId]

        // --- CASO 1: SIN SHAPE (Respaldo) ---
        if (shapePoints.isNullOrEmpty()) {
            val busMasCercano = busesDeLaLinea.minByOrNull {
                val pos = it.vehicle.position
                val res = FloatArray(1)
                Location.distanceBetween(pos.latitude.toDouble(), pos.longitude.toDouble(), currentStopLocation.latitude, currentStopLocation.longitude, res)
                res[0]
            } ?: return null

            val tiempoStr = calcularTiempoSimple(busMasCercano, currentStopLocation)

            // CORRECCIÓN: Acceso correcto a patente -> vehicle.vehicle.licensePlate
            val patente = if (busMasCercano.vehicle.hasVehicle() && busMasCercano.vehicle.vehicle.hasLicensePlate()) {
                busMasCercano.vehicle.vehicle.licensePlate
            } else null

            return if (tiempoStr != null) Pair(tiempoStr, patente) else null
        }

        // --- CASO 2: CON SHAPE (Inteligente) ---
        val indiceParadero = encontrarIndiceMasCercano(shapePoints, currentStopLocation)

        // Encontrar el bus más cercano que NO haya pasado
        val busGanador = busesDeLaLinea.filter { bus ->
            val pos = bus.vehicle.position
            val indiceBus = encontrarIndiceMasCercano(shapePoints, LatLng(pos.latitude.toDouble(), pos.longitude.toDouble()))
            indiceBus <= indiceParadero
        }.maxByOrNull { bus ->
            val pos = bus.vehicle.position
            encontrarIndiceMasCercano(shapePoints, LatLng(pos.latitude.toDouble(), pos.longitude.toDouble()))
        } ?: return null

        val minutosGPS = calcularMinutosGPSParaBus(busGanador, shapePoints, indiceParadero)
        val minutosAPI = if (currentStopId != null) obtenerMinutosAPIParaBus(busGanador, currentStopId) else null

        // Decisión Híbrida
        val minutosFinal = if (minutosGPS != null && minutosGPS < 10) minutosGPS else (minutosAPI ?: minutosGPS)

        if (minutosFinal == null) return null

        val tiempoStr = if (minutosFinal <= 0) "Llegando" else "$minutosFinal min"

        // CORRECCIÓN: Acceso correcto a patente
        val patente = if (busGanador.vehicle.hasVehicle() && busGanador.vehicle.vehicle.hasLicensePlate()) {
            busGanador.vehicle.vehicle.licensePlate
        } else null

        return Pair(tiempoStr, patente)
    }

    private fun calcularMinutosGPSParaBus(bus: GtfsRealtime.FeedEntity, shapePoints: List<LatLng>, indiceParadero: Int): Int? {
        val pos = bus.vehicle.position
        val indiceBus = encontrarIndiceMasCercano(shapePoints, LatLng(pos.latitude.toDouble(), pos.longitude.toDouble()))

        var distanciaRealMetros = 0f
        val results = FloatArray(1)
        val paso = if ((indiceParadero - indiceBus) > 100) 5 else 1

        for (i in indiceBus until indiceParadero step paso) {
            if (i + paso < shapePoints.size) {
                Location.distanceBetween(
                    shapePoints[i].latitude, shapePoints[i].longitude,
                    shapePoints[i + paso].latitude, shapePoints[i + paso].longitude,
                    results
                )
                distanciaRealMetros += results[0]
            }
        }
        if (distanciaRealMetros > 20000) return null
        return (distanciaRealMetros / 416).toInt()
    }

    private fun obtenerMinutosAPIParaBus(bus: GtfsRealtime.FeedEntity, stopId: String): Int? {
        if (!bus.hasTripUpdate()) return null

        val stopUpdates = bus.tripUpdate.stopTimeUpdateList
        val match = stopUpdates.find { it.stopId == stopId && it.hasArrival() }

        if (match != null) {
            val nowSeconds = System.currentTimeMillis() / 1000
            val diffMinutes = (match.arrival.time - nowSeconds) / 60
            if (diffMinutes < -2) return null
            return diffMinutes.toInt()
        }
        return null
    }

    private fun calcularTiempoSimple(bus: GtfsRealtime.FeedEntity, stopLocation: LatLng): String? {
        val pos = bus.vehicle.position
        val results = FloatArray(1)
        Location.distanceBetween(pos.latitude.toDouble(), pos.longitude.toDouble(), stopLocation.latitude, stopLocation.longitude, results)
        val distancia = results[0]
        if (distancia > 20000) return null
        val t = (distancia / 416).toInt()
        return if (t <= 0) "Llegando" else "$t min"
    }

    private fun encontrarIndiceMasCercano(puntos: List<LatLng>, objetivo: LatLng): Int {
        var minDist = Float.MAX_VALUE
        var index = 0
        val results = FloatArray(1)
        for (i in puntos.indices) {
            Location.distanceBetween(objetivo.latitude, objetivo.longitude, puntos[i].latitude, puntos[i].longitude, results)
            if (results[0] < minDist) {
                minDist = results[0]
                index = i
            }
        }
        return index
    }

    override fun getItemCount(): Int = mainRoutes.size + if (variantRoutes.isNotEmpty()) variantRoutes.size + 1 else 0

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
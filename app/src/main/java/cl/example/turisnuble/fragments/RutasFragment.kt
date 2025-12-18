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
import com.google.transit.realtime.GtfsRealtime
import kotlinx.coroutines.launch
import org.maplibre.android.geometry.LatLng

// Clase de datos auxiliar para la lista
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

        // Observar cambios en tiempo real de los buses (Posición y Predicciones)
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

    // LISTADO GENERAL (Sin tiempos, solo nombres)
    private fun showRouteList(title: String, routes: List<DisplayRouteInfo>) {
        isShowingList = true
        btnVolver.visibility = View.VISIBLE
        txtTitulo.text = title

        // Pasamos null en datos de paradero para que NO calcule tiempos en la vista general
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

    // RESULTADOS DE PARADERO (Con tiempos inteligentes)
    private fun showFilteredList(filteredRoutes: List<DisplayRouteInfo>) {
        isShowingList = true
        btnVolver.visibility = View.VISIBLE
        txtTitulo.text = "Resultados"

        // Recuperamos ID, Ubicación y Datos en vivo para calcular tiempos
        val currentStopId = sharedViewModel.selectedStopId.value
        val currentStopLocation = if (currentStopId != null) GtfsDataManager.stops[currentStopId]?.location else null
        val currentEntities = sharedViewModel.feedMessage.value?.entityList

        val adapter = RutasAdapter(
            filteredRoutes.sortedWith(compareBy({
                it.route.shortName.filter { c -> c.isDigit() }.toIntOrNull() ?: Int.MAX_VALUE
            }, { it.route.shortName })),
            emptyList(),
            currentStopId,       // ID del paradero (para predicción API)
            currentStopLocation, // Ubicación (para cálculo GPS inteligente)
            currentEntities,     // Datos en vivo
            onItemClick = {
                routeDrawer?.drawRoute(it.route, it.directionId)
                (activity as? MainActivity)?.showRouteDetail(it.route.routeId, it.directionId)
            }
        )
        recyclerView.adapter = adapter
    }

    override fun onDetach() {
        super.onDetach()
        routeDrawer = null
    }
}

// =====================================================================
// === ADAPTADOR INTELIGENTE V3 (Híbrido: Prioridad Visual/GPS Cercano) ===
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

    class RutaVH(v: View) : RecyclerView.ViewHolder(v) {
        val icono: ImageView = v.findViewById(R.id.icono_ruta)
        val linea: TextView = v.findViewById(R.id.nombre_linea)
        val recorrido: TextView = v.findViewById(R.id.nombre_recorrido)
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

            if (holder.cardView != null) {
                if (route.routeId == selectedRouteId) {
                    holder.cardView.setCardBackgroundColor(Color.parseColor("#C8E6C9"))
                } else {
                    holder.cardView.setCardBackgroundColor(holder.defaultColor)
                }
            } else {
                if (route.routeId == selectedRouteId) holder.itemView.setBackgroundColor(Color.parseColor("#C8E6C9"))
                else holder.itemView.setBackgroundColor(Color.TRANSPARENT)
            }

            if (route.shortName == "Rural") {
                holder.linea.text = "Rural"
                holder.icono.setImageResource(R.drawable.ic_bus)
                holder.txtTiempo.visibility = View.GONE
            } else {
                holder.linea.text = if (route.shortName.startsWith("13")) "Línea 13" else "Línea ${route.shortName}"
                holder.icono.setImageResource(getIconForRoute(route.routeId))

                // === LÓGICA DE TIEMPO INTELIGENTE ===
                val tiempoStr = decidirTiempoAMostrar(route.routeId, item.directionId)

                if (tiempoStr != null) {
                    holder.txtTiempo.visibility = View.VISIBLE
                    holder.txtTiempo.text = tiempoStr

                    // Colores según urgencia
                    if (tiempoStr == "Llegando" || (tiempoStr.contains("min") && (tiempoStr.split(" ")[0].toIntOrNull() ?: 99) <= 5)) {
                        holder.txtTiempo.setTextColor(Color.parseColor("#4CAF50")) // Verde
                    } else if (tiempoStr.contains("min") && (tiempoStr.split(" ")[0].toIntOrNull() ?: 99) <= 15) {
                        holder.txtTiempo.setTextColor(Color.parseColor("#FF9800")) // Naranja
                    } else {
                        holder.txtTiempo.setTextColor(Color.GRAY) // Gris
                    }
                } else {
                    holder.txtTiempo.visibility = View.GONE
                }
            }
            holder.recorrido.text = route.longName
            holder.itemView.setOnClickListener { onItemClick(item) }

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

    // --- CEREBRO CENTRAL DE DECISIÓN DE TIEMPOS ---
    private fun decidirTiempoAMostrar(routeId: String, directionId: Int): String? {
        // Si no hay datos básicos (ej. lista general), no calculamos
        if (currentStopLocation == null && currentStopId == null) return null
        if (feedEntities == null) return null

        // 1. Calcular Minutos por GPS (Realidad Física sobre Trazado)
        val minutosGPS = calcularMinutosGPS(routeId, directionId, currentStopLocation!!)

        // 2. Obtener Minutos por API (Predicción del servidor)
        val minutosAPI = if (currentStopId != null) obtenerMinutosAPI(routeId, directionId, currentStopId) else null

        // 3. --- REGLA DE ORO: PRIORIDAD DE CERCANÍA ---
        // Si el GPS dice que está a menos de 10 minutos (aprox 4 km), confiamos en el GPS.
        // Esto evita que la API diga "18 min" cuando la micro está a 3 cuadras.
        if (minutosGPS != null && minutosGPS < 10) {
            return formatearMinutos(minutosGPS)
        }

        // 4. Si está lejos, usamos la API (sabe de tráfico), o el GPS si la API falla.
        val minutosFinal = minutosAPI ?: minutosGPS

        return if (minutosFinal != null) formatearMinutos(minutosFinal) else null
    }

    private fun formatearMinutos(minutos: Int): String {
        return if (minutos <= 0) "Llegando" else "$minutos min"
    }

    // --- CÁLCULO FÍSICO (GPS sobre Trazado/Shape) ---
    private fun calcularMinutosGPS(routeId: String, directionId: Int, stopLocation: LatLng): Int? {
        // Filtrar buses de esta línea
        val busesDeLaLinea = feedEntities!!.filter {
            it.hasVehicle() && it.vehicle.hasTrip() &&
                    it.vehicle.trip.routeId == routeId && it.vehicle.trip.directionId == directionId
        }
        if (busesDeLaLinea.isEmpty()) return null

        // Obtener Shape (camino real)
        val trip = GtfsDataManager.trips.values.find { it.routeId == routeId && it.directionId == directionId }
        val shapePoints = GtfsDataManager.shapes[trip?.shapeId]

        // Si no hay shape, usamos fallback simple (línea recta)
        if (shapePoints.isNullOrEmpty()) {
            return calcularMinutosGPSSimple(busesDeLaLinea, stopLocation)
        }

        val indiceParadero = encontrarIndiceMasCercano(shapePoints, stopLocation)
        var mejorTiempo: Int? = null

        for (busEntity in busesDeLaLinea) {
            val posBus = busEntity.vehicle.position
            val latLngBus = LatLng(posBus.latitude.toDouble(), posBus.longitude.toDouble())

            // ¿En qué parte del camino va este bus?
            val indiceBus = encontrarIndiceMasCercano(shapePoints, latLngBus)

            // --- FILTRO: SI EL BUS VA DESPUÉS DEL PARADERO, YA PASÓ ---
            if (indiceBus > indiceParadero) continue
            // --------------------------------------------------------

            // Calcular distancia real sumando tramos
            var distanciaRealMetros = 0f
            val results = FloatArray(1)
            // Optimización: Saltos de 5 si está lejos para no congelar la UI
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

            if (distanciaRealMetros > 20000) continue // Muy lejos

            // Velocidad promedio: 25 km/h = 416 m/min
            val tiempoBus = (distanciaRealMetros / 416).toInt()

            if (mejorTiempo == null || tiempoBus < mejorTiempo) {
                mejorTiempo = tiempoBus
            }
        }
        return mejorTiempo
    }

    // --- CÁLCULO API (Datos del servidor) ---
    private fun obtenerMinutosAPI(routeId: String, directionId: Int, stopId: String): Int? {
        val nowSeconds = System.currentTimeMillis() / 1000
        val tripUpdates = feedEntities!!.filter {
            it.hasTripUpdate() &&
                    it.tripUpdate.trip.routeId == routeId &&
                    it.tripUpdate.trip.directionId == directionId
        }

        for (entity in tripUpdates) {
            val stopUpdates = entity.tripUpdate.stopTimeUpdateList
            val match = stopUpdates.find { it.stopId == stopId && it.hasArrival() }

            if (match != null) {
                val diffMinutes = (match.arrival.time - nowSeconds) / 60
                // Si la predicción es del pasado (-2 min), asumimos que ya pasó
                if (diffMinutes < -2) return null
                return diffMinutes.toInt()
            }
        }
        return null
    }

    // --- UTILS ---
    private fun encontrarIndiceMasCercano(puntos: List<LatLng>, objetivo: LatLng): Int {
        var minDist = Float.MAX_VALUE
        var index = 0
        val results = FloatArray(1)
        for (i in puntos.indices) {
            Location.distanceBetween(
                objetivo.latitude, objetivo.longitude,
                puntos[i].latitude, puntos[i].longitude,
                results
            )
            if (results[0] < minDist) {
                minDist = results[0]
                index = i
            }
        }
        return index
    }

    private fun calcularMinutosGPSSimple(buses: List<GtfsRealtime.FeedEntity>, stopLocation: LatLng): Int? {
        val busMasCercano = buses.minByOrNull { entity ->
            val pos = entity.vehicle.position
            val results = FloatArray(1)
            Location.distanceBetween(pos.latitude.toDouble(), pos.longitude.toDouble(), stopLocation.latitude, stopLocation.longitude, results)
            results[0]
        } ?: return null

        val pos = busMasCercano.vehicle.position
        val results = FloatArray(1)
        Location.distanceBetween(pos.latitude.toDouble(), pos.longitude.toDouble(), stopLocation.latitude, stopLocation.longitude, results)
        val distancia = results[0]

        if (distancia > 20000) return null
        return (distancia / 416).toInt()
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
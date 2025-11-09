package cl.example.turisnuble

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Button
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.google.firebase.ktx.Firebase

class DetalleRutaFragment : Fragment() {

    private var mapMover: MapMover? = null
    private var paraderoActionHandler: ParaderoActionHandler? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is MapMover) {
            mapMover = context
        } else {
            throw RuntimeException("$context must implement MapMover")
        }

        if (context is ParaderoActionHandler) {
            paraderoActionHandler = context
        } else {
            throw RuntimeException("$context must implement ParaderoActionHandler")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_detalle_ruta, container, false)

        val recyclerView = view.findViewById<RecyclerView>(R.id.recycler_view_paraderos)
        val backButton = view.findViewById<ImageButton>(R.id.back_button)
        val headerTitle = view.findViewById<TextView>(R.id.route_name_header)

        recyclerView.layoutManager = LinearLayoutManager(context)

        val routeId = arguments?.getString("routeId")
        val directionId = arguments?.getInt("directionId", -1)

        if (routeId != null && directionId != null && directionId != -1) {
            val route = GtfsDataManager.routes[routeId]
            val directionText = if (directionId == 0) "Ida" else "Vuelta"

            val shortName = route?.shortName
            val lineaText = if (shortName == "13A" || shortName == "13B") "13" else shortName

            headerTitle.text = "Línea $lineaText - $directionText"

            val paraderos = GtfsDataManager.getStopsForRoute(routeId, directionId)

            recyclerView.adapter = ParaderosDetalleAdapter(
                paraderos,
                onItemClick = { paraderoSeleccionado ->
                    mapMover?.centerMapOnPoint(paraderoSeleccionado.location.latitude, paraderoSeleccionado.location.longitude) // Corregido: .lat .lon
                },
                onGetDirectionsClick = { paraderoSeleccionado ->
                    paraderoActionHandler?.onGetDirectionsToStop(paraderoSeleccionado)
                }
            )
        }

        backButton.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        return view
    }

    override fun onDetach() {
        super.onDetach()
        mapMover = null
        paraderoActionHandler = null
    }

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

class ParaderosDetalleAdapter(
    private val paraderos: List<GtfsStop>,
    private val onItemClick: (GtfsStop) -> Unit,
    private val onGetDirectionsClick: (GtfsStop) -> Unit
) :
    RecyclerView.Adapter<ParaderosDetalleAdapter.ParaderoViewHolder>() {

    // --- INICIO LÓGICA DE FAVORITOS (ADAPTER) ---
    private val auth: FirebaseAuth = Firebase.auth
    private val currentUser = auth.currentUser
    private var favoriteParaderoIds = setOf<String>()

    init {
        // Si el usuario está logueado, carga sus paraderos favoritos
        currentUser?.uid?.let { userId ->
            FavoritesManager.getParaderoFavRef(userId, "").addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    favoriteParaderoIds = snapshot.children.mapNotNull { it.key }.toSet()
                    notifyDataSetChanged() // Recarga la lista para mostrar estrellas
                }
                override fun onCancelled(error: DatabaseError) {
                    Log.w("ParaderosDetDetalle", "Error al cargar paraderos favoritos", error.toException())
                }
            })
        }
    }
    // --- FIN LÓGICA DE FAVORITOS (ADAPTER) ---

    class ParaderoViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val stopSequence: TextView = view.findViewById(R.id.stop_sequence)
        val stopName: TextView = view.findViewById(R.id.stop_name)
        val getDirectionsButton: Button = view.findViewById(R.id.btnGetDirectionsToStop)
        // --- AÑADIDO: El botón de favorito ---
        val favoriteButton: ImageButton = view.findViewById(R.id.btn_favorite_paradero)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ParaderoViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_paradero_detalle, parent, false)
        return ParaderoViewHolder(view)
    }

    override fun onBindViewHolder(holder: ParaderoViewHolder, position: Int) {
        val paradero = paraderos[position]

        holder.stopSequence.text = paradero.stopId
        holder.stopName.text = paradero.name

        // Clic en todo el item (sin cambios)
        holder.itemView.setOnClickListener {
            onItemClick(paradero)
        }

        // Clic solo en el botón "Cómo Llegar" (sin cambios)
        holder.getDirectionsButton.setOnClickListener {
            onGetDirectionsClick(paradero)
        }

        // --- INICIO LÓGICA DE FAVORITOS (PARADEROS) ---
        if (currentUser == null) {
            // Si es INVITADO, ocultar botón
            holder.favoriteButton.visibility = View.GONE
        } else {
            // Si es USUARIO, mostrar y configurar
            holder.favoriteButton.visibility = View.VISIBLE
            val userId = currentUser.uid
            val isFavorite = favoriteParaderoIds.contains(paradero.stopId)

            // 1. Poner el ícono correcto
            if (isFavorite) {
                holder.favoriteButton.setImageResource(R.drawable.ic_star_filled)
            } else {
                holder.favoriteButton.setImageResource(R.drawable.ic_star_border)
            }

            // 2. Configurar el click
            holder.favoriteButton.setOnClickListener {
                if (favoriteParaderoIds.contains(paradero.stopId)) {
                    // Ya es favorito -> Quitar
                    FavoritesManager.removeFavoriteParadero(userId, paradero.stopId)
                    Toast.makeText(holder.itemView.context, "Paradero eliminado de favoritos", Toast.LENGTH_SHORT).show()
                } else {
                    // No es favorito -> Añadir
                    FavoritesManager.addFavoriteParadero(userId, paradero)
                    Toast.makeText(holder.itemView.context, "Paradero añadido a favoritos", Toast.LENGTH_SHORT).show()
                }
                // El listener de la BD actualiza el ícono
            }
        }
        // --- FIN LÓGICA DE FAVORITOS (PARADEROS) ---
    }

    override fun getItemCount() = paraderos.size
}
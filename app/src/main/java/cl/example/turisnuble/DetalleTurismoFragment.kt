package cl.example.turisnuble

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
// --- INICIO LÓGICA DE FAVORITOS: IMPORTS ---
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
// --- FIN LÓGICA DE FAVORITOS: IMPORTS ---
import java.io.Serializable

class DetalleTurismoFragment : Fragment() {

    private var navigator: DetalleTurismoNavigator? = null

    // --- INICIO LÓGICA DE FAVORITOS: VARIABLES ---
    private lateinit var auth: FirebaseAuth
    private var esFavorito = false // Variable para rastrear el estado actual
    // --- FIN LÓGICA DE FAVORITOS: VARIABLES ---

    companion object {
        private const val ARG_PUNTO_TURISTICO = "punto_turistico"

        fun newInstance(punto: PuntoTuristico): DetalleTurismoFragment {
            val fragment = DetalleTurismoFragment()
            val args = Bundle().apply {
                putSerializable(ARG_PUNTO_TURISTICO, punto)
            }
            fragment.arguments = args
            return fragment
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is DetalleTurismoNavigator) {
            navigator = context
        } else {
            throw RuntimeException("$context must implement DetalleTurismoNavigator")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_detalle_turismo, container, false)

        @Suppress("DEPRECATION")
        val puntoTuristico = arguments?.getSerializable(ARG_PUNTO_TURISTICO) as? PuntoTuristico
            ?: return view

        // --- INICIO LÓGICA DE FAVORITOS: INICIALIZACIÓN ---
        auth = Firebase.auth
        val currentUser = auth.currentUser
        // --- FIN LÓGICA DE FAVORITOS: INICIALIZACIÓN ---

        // Configurar botón de volver: sale de la pantalla de detalle
        view.findViewById<ImageButton>(R.id.back_button).setOnClickListener {
            navigator?.hideDetailFragment()
        }

        // --- Encontrar el botón "Cómo Llegar" ---
        val btnComoLlegar: Button = view.findViewById(R.id.btn_como_llegar)

        // 1. Mostrar la información del punto turístico
        view.findViewById<ImageView>(R.id.imagen_punto_turistico).setImageResource(puntoTuristico.imagenId)
        view.findViewById<TextView>(R.id.nombre_punto_turistico).text = puntoTuristico.nombre
        view.findViewById<TextView>(R.id.direccion_punto_turistico).text = puntoTuristico.direccion

        // 2. Encontrar y mostrar los paraderos cercanos
        val nearbyStops = GtfsDataManager.getNearbyStops(puntoTuristico.latitud, puntoTuristico.longitud, 3)
        val recyclerView = view.findViewById<RecyclerView>(R.id.recycler_view_paraderos_cercanos)
        recyclerView.layoutManager = LinearLayoutManager(context)
        val noParaderosTextView = view.findViewById<TextView>(R.id.no_paraderos_cercanos)

        if (nearbyStops.isEmpty()) {
            noParaderosTextView.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        } else {
            noParaderosTextView.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE

            val adapter = ParaderosCercanosAdapter(nearbyStops) { stop ->
                navigator?.showRoutesForStop(stop.stopId)
            }
            recyclerView.adapter = adapter
        }

        // --- INICIO LÓGICA DE FAVORITOS: CÓDIGO PRINCIPAL ---

        // Asumo que tienes un ImageButton en tu XML 'fragment_detalle_turismo.xml'
        // con el id 'favorite_button'. Si no, cámbialo aquí.
        // TAMBIÉN: Asegúrate de tener los íconos 'ic_star_filled' y 'ic_star_border' en 'res/drawable'.
        val favoriteButton: ImageButton = view.findViewById(R.id.favorite_button)

        if (currentUser == null) {
            // Si es INVITADO, ocultamos el botón.
            favoriteButton.visibility = View.GONE
        } else {
            // Si es USUARIO REGISTRADO, mostramos el botón y configuramos.
            favoriteButton.visibility = View.VISIBLE
            val userId = currentUser.uid

            // Función interna para actualizar el ícono
            fun updateStarIcon(isFav: Boolean) {
                if (isFav) {
                    favoriteButton.setImageResource(R.drawable.ic_star_filled)
                } else {
                    favoriteButton.setImageResource(R.drawable.ic_star_border)
                }
                esFavorito = isFav
            }

            // Revisar el estado inicial del favorito en la base de datos
            FavoritesManager.checkFavoriteTurismoStatus(userId, puntoTuristico.id) { isFav ->
                updateStarIcon(isFav)
            }

            // Configurar el OnClickListener
            favoriteButton.setOnClickListener {
                if (esFavorito) {
                    // Ya es favorito -> Quitar
                    FavoritesManager.removeFavoriteTurismo(userId, puntoTuristico.id)
                    updateStarIcon(false) // Ícono a borde
                    Toast.makeText(context, "Eliminado de favoritos", Toast.LENGTH_SHORT).show()
                } else {
                    // No es favorito -> Añadir
                    FavoritesManager.addFavoriteTurismo(userId, puntoTuristico)
                    updateStarIcon(true) // Ícono a lleno
                    Toast.makeText(context, "Añadido a favoritos", Toast.LENGTH_SHORT).show()
                }
            }
        }
        // --- FIN LÓGICA DE FAVORITOS ---


        // --- Lógica del botón "Cómo Llegar" (sin cambios) ---
        btnComoLlegar.setOnClickListener {
            Log.d("DetalleTurismo", "Botón 'Cómo Llegar' presionado para ${puntoTuristico.nombre}")
            navigator?.onGetDirectionsClicked(puntoTuristico) // Llama a MainActivity
            navigator?.hideDetailFragment() // Cierra este fragmento
        }
        // --- FIN AÑADIDO ---

        return view
    }

    override fun onDetach() {
        super.onDetach()
        navigator = null
    }

    // Adaptador interno (sin cambios)
    private inner class ParaderosCercanosAdapter(
        private val stops: List<GtfsStop>,
        private val onItemClick: (GtfsStop) -> Unit
    ) : RecyclerView.Adapter<ParaderosCercanosAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val stopIdChip: TextView = view.findViewById(R.id.stop_id_chip)
            val nombreParadero: TextView = view.findViewById(R.id.nombre_paradero)
            val lineasBuses: TextView = view.findViewById(R.id.llegadas_buses)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_paradero_cercano, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val stop = stops[position]
            holder.stopIdChip.text = stop.stopId
            holder.nombreParadero.text = stop.name

            val routesInfo = GtfsDataManager.getRoutesForStop(stop.stopId)
            val routesText = if (routesInfo.isNotEmpty()) {
                routesInfo
                    .distinctBy { it.route.shortName }
                    .sortedBy { it.route.shortName }
                    .joinToString(" | ") { displayRoute ->
                        val shortName = displayRoute.route.shortName
                        if (shortName == "13A" || shortName == "13B") "Línea 13" else "Línea $shortName"
                    }
            } else {
                "No hay rutas disponibles"
            }
            holder.lineasBuses.text = "Rutas: $routesText"
            holder.itemView.setOnClickListener {
                onItemClick(stop)
            }
        }

        override fun getItemCount() = stops.size
    }
}
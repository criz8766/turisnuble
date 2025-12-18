package cl.example.turisnuble.fragments

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
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels // <--- IMPORTANTE
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import cl.example.turisnuble.R
import cl.example.turisnuble.data.FavoritesManager
import cl.example.turisnuble.data.GtfsDataManager
import cl.example.turisnuble.data.GtfsStop
import cl.example.turisnuble.data.SharedViewModel // <--- IMPORTANTE
import cl.example.turisnuble.models.PuntoTuristico
import cl.example.turisnuble.utils.DetalleTurismoNavigator
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase

class DetalleTurismoFragment : Fragment() {

    // 1. INYECTAMOS EL VIEWMODEL COMPARTIDO
    private val sharedViewModel: SharedViewModel by activityViewModels()

    private var navigator: DetalleTurismoNavigator? = null
    private lateinit var auth: FirebaseAuth
    private var esFavorito = false

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

        auth = Firebase.auth
        val currentUser = auth.currentUser

        view.findViewById<ImageButton>(R.id.back_button).setOnClickListener {
            navigator?.hideDetailFragment()
        }

        val btnComoLlegar: Button = view.findViewById(R.id.btn_como_llegar)
        val imageView = view.findViewById<ImageView>(R.id.imagen_punto_turistico)

        Glide.with(this)
            .load(puntoTuristico.imagenUrl)
            .placeholder(R.drawable.ic_launcher_background)
            .error(R.drawable.ic_close)
            .transform(CenterCrop())
            .into(imageView)

        view.findViewById<TextView>(R.id.nombre_punto_turistico).text = puntoTuristico.nombre
        view.findViewById<TextView>(R.id.direccion_punto_turistico).text = puntoTuristico.direccion
        view.findViewById<TextView>(R.id.categoria_punto_turistico).text =
            puntoTuristico.categoria.uppercase()

        // --- LÓGICA DE PARADEROS CERCANOS ---
        val nearbyStops =
            GtfsDataManager.getNearbyStops(puntoTuristico.latitud, puntoTuristico.longitud, 3)
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
                // 2. GUARDAMOS EL PUNTO EN LA CAJA FUERTE (VIEWMODEL)
                sharedViewModel.puntoTuristicoRetorno = puntoTuristico

                // 3. NAVEGAMOS NORMALMENTE
                navigator?.showRoutesForStop(stop.stopId)
            }
            recyclerView.adapter = adapter
        }

        val favoriteButton: ImageButton = view.findViewById(R.id.favorite_button)

        if (currentUser == null) {
            favoriteButton.visibility = View.GONE
        } else {
            favoriteButton.visibility = View.VISIBLE
            val userId = currentUser.uid

            fun updateStarIcon(isFav: Boolean) {
                if (isFav) {
                    favoriteButton.setImageResource(R.drawable.ic_star_filled)
                } else {
                    favoriteButton.setImageResource(R.drawable.ic_star_border)
                }
                esFavorito = isFav
            }

            FavoritesManager.checkFavoriteTurismoStatus(userId, puntoTuristico.id) { isFav ->
                updateStarIcon(isFav)
            }

            favoriteButton.setOnClickListener {
                if (esFavorito) {
                    FavoritesManager.removeFavoriteTurismo(userId, puntoTuristico.id)
                    updateStarIcon(false)
                    Toast.makeText(context, "Eliminado de favoritos", Toast.LENGTH_SHORT).show()
                } else {
                    FavoritesManager.addFavoriteTurismo(userId, puntoTuristico)
                    updateStarIcon(true)
                    Toast.makeText(context, "Añadido a favoritos", Toast.LENGTH_SHORT).show()
                }
            }
        }

        btnComoLlegar.setOnClickListener {
            navigator?.onGetDirectionsClicked(puntoTuristico)
            navigator?.hideDetailFragment()
        }

        return view
    }

    override fun onDetach() {
        super.onDetach()
        navigator = null
    }

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
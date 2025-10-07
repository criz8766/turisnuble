package cl.example.turisnuble

import android.content.Context
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

// Interfaz para ordenar al MainActivity que mueva el mapa
interface MapMover {
    fun centerMapOnPoint(lat: Double, lon: Double)
}

class TurismoFragment : Fragment() {

    private var mapMover: MapMover? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is MapMover) {
            mapMover = context
        } else {
            throw RuntimeException("$context must implement MapMover")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_turismo, container, false)
        val recyclerView = view.findViewById<RecyclerView>(R.id.recycler_view_turismo)
        recyclerView.layoutManager = LinearLayoutManager(context)

        // Creamos el adaptador y le pasamos la acción a realizar al hacer clic
        val adapter = TurismoAdapter(DatosTurismo.puntosTuristicos) { puntoSeleccionado ->
            mapMover?.centerMapOnPoint(puntoSeleccionado.latitud, puntoSeleccionado.longitud)
        }
        recyclerView.adapter = adapter

        return view
    }

    override fun onDetach() {
        super.onDetach()
        mapMover = null
    }
}
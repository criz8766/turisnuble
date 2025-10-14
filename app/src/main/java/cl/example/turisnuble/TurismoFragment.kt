package cl.example.turisnuble

import android.content.Context
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

// Interfaz para manejar las acciones del fragmento de turismo en MainActivity
interface TurismoActionHandler {
    fun centerMapOnPoint(lat: Double, lon: Double)
    fun showTurismoDetail(punto: PuntoTuristico)
}

class TurismoFragment : Fragment() {

    private var actionHandler: TurismoActionHandler? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is TurismoActionHandler) {
            actionHandler = context
        } else {
            throw RuntimeException("$context must implement TurismoActionHandler")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_turismo, container, false)
        val recyclerView = view.findViewById<RecyclerView>(R.id.recycler_view_turismo)
        recyclerView.layoutManager = LinearLayoutManager(context)

        // FIX PARA FLUIDEZ: Indica que el tamaño de los elementos es fijo para optimizar el rendimiento.
        recyclerView.setHasFixedSize(true)

        // Creamos el adaptador y le pasamos la acción a realizar al hacer clic
        val adapter = TurismoAdapter(DatosTurismo.puntosTuristicos) { puntoSeleccionado ->
            // 1. Centrar mapa (Comportamiento existente)
            actionHandler?.centerMapOnPoint(puntoSeleccionado.latitud, puntoSeleccionado.longitud)
            // 2. Mostrar el nuevo fragmento de detalle
            actionHandler?.showTurismoDetail(puntoSeleccionado)
        }
        recyclerView.adapter = adapter

        return view
    }

    override fun onDetach() {
        super.onDetach()
        actionHandler = null
    }
}
package cl.example.turisnuble

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class TurismoFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var turismoAdapter: TurismoAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_turismo, container, false)

        recyclerView = view.findViewById(R.id.recycler_view_turismo)
        turismoAdapter = TurismoAdapter(DatosTurismo.puntosTuristicos)
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = turismoAdapter

        // --- OPTIMIZACIONES CLAVE ---
        // 1. Mejora el rendimiento si los items no cambian de tamaño
        recyclerView.setHasFixedSize(true)

        // 2. Le dice al RecyclerView que no gestione el scroll, el NestedScrollView lo hará.
        // Esto es crucial para que funcione correctamente.
        recyclerView.isNestedScrollingEnabled = false

        return view
    }
}
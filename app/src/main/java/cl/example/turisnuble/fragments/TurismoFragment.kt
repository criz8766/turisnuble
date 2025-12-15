package cl.example.turisnuble.fragments

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import cl.example.turisnuble.R
import cl.example.turisnuble.adapters.CategoriaAdapter
import cl.example.turisnuble.adapters.TurismoAdapter
import cl.example.turisnuble.data.TurismoDataManager
import cl.example.turisnuble.utils.TurismoActionHandler

class TurismoFragment : Fragment() {

    private var actionHandler: TurismoActionHandler? = null
    private lateinit var recyclerView: RecyclerView
    private lateinit var btnVolver: LinearLayout
    private lateinit var txtTitulo: TextView

    private var isShowingPoints = false // Estado para saber qué estamos viendo

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

        recyclerView = view.findViewById(R.id.recycler_view_turismo)
        btnVolver = view.findViewById(R.id.btn_volver_categorias)
        txtTitulo = view.findViewById(R.id.txt_titulo_categoria_actual)

        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.setHasFixedSize(true)

        // Cargar vista inicial (Categorías)
        showCategories()

        // Configurar botón volver
        btnVolver.setOnClickListener {
            showCategories()
        }

        // Manejar el botón "Atrás" del teléfono para que vuelva a categorías en vez de salir
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (isShowingPoints) {
                        showCategories()
                    } else {
                        isEnabled = false
                        requireActivity().onBackPressed()
                    }
                }
            })

        return view
    }

    private fun showCategories() {
        isShowingPoints = false
        btnVolver.visibility = View.GONE // Ocultar botón volver

        // 1. Obtener lista completa
        val todosLosPuntos = TurismoDataManager.puntosTuristicos

        // 2. Extraer categorías únicas
        val categorias = todosLosPuntos.map { it.categoria }.distinct().sorted()

        // 3. Configurar adaptador de categorías
        val adapter = CategoriaAdapter(categorias, todosLosPuntos) { categoriaSeleccionada ->
            showPointsOfCategory(categoriaSeleccionada)
        }
        recyclerView.adapter = adapter
    }

    private fun showPointsOfCategory(categoria: String) {
        isShowingPoints = true

        // 1. Configurar botón volver y título
        btnVolver.visibility = View.VISIBLE
        txtTitulo.text = "Categoría: $categoria" // O simplemente el nombre de la cat

        // 2. Filtrar puntos
        val puntosFiltrados =
            TurismoDataManager.puntosTuristicos.filter { it.categoria == categoria }

        // 3. Configurar adaptador de puntos turísticos (el normal)
        val adapter = TurismoAdapter(puntosFiltrados) { puntoSeleccionado ->
            actionHandler?.centerMapOnPoint(puntoSeleccionado.latitud, puntoSeleccionado.longitud)
            actionHandler?.showTurismoDetail(puntoSeleccionado)
        }
        recyclerView.adapter = adapter
    }

    override fun onDetach() {
        super.onDetach()
        actionHandler = null
    }

    // Método público para refrescar si los datos cambian (opcional)
    fun refreshData() {
        if (!isShowingPoints) {
            showCategories()
        }
    }
}
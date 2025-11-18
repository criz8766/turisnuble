package cl.example.turisnuble

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase

class FavoritosActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    // Referencias de la UI
    private lateinit var rvFavoritos: RecyclerView
    private lateinit var pbLoading: ProgressBar
    private lateinit var tvEmpty: TextView
    private lateinit var btnBack: ImageView

    private lateinit var adapter: FavoritosAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_favoritos)

        // Inicializar Firebase Auth
        auth = Firebase.auth

        // Encontrar vistas
        btnBack = findViewById(R.id.btn_favoritos_back)
        rvFavoritos = findViewById(R.id.rv_favoritos)
        pbLoading = findViewById(R.id.pb_favoritos_loading)
        tvEmpty = findViewById(R.id.tv_favoritos_empty)

        btnBack.setOnClickListener {
            finish() // Cierra esta actividad y vuelve a MainActivity
        }

        // Configurar RecyclerView
        rvFavoritos.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)

        // Cargar los favoritos
        loadFavorites()
    }

    private fun loadFavorites() {
        val userId = auth.currentUser?.uid

        if (userId == null) {
            Toast.makeText(this, "Error: Usuario no encontrado", Toast.LENGTH_SHORT).show()
            showEmptyState()
            return
        }

        showLoadingState()

        FavoritesManager.getAllFavorites(userId) { favoritesList ->

            // --- INICIO DE LA LÓGICA DE VALIDACIÓN ---
            val listaValidada = favoritesList.filter { item ->
                // CORRECCIÓN 1: Usar FavoritesManager.FavoriteType
                if (item.type == FavoritesManager.FavoriteType.TURISMO) {
                    val idNumerico = item.id.toIntOrNull()
                    if (idNumerico != null) {
                        TurismoDataManager.puntosTuristicos.any { it.id == idNumerico }
                    } else {
                        false
                    }
                } else {
                    // Es Paradero
                    GtfsDataManager.stops.containsKey(item.id)
                }
            }
            // --- FIN DE LA LÓGICA DE VALIDACIÓN ---

            runOnUiThread {
                if (listaValidada.isEmpty()) {
                    showEmptyState()
                } else {
                    showListState()
                    adapter = FavoritosAdapter(listaValidada) { selectedItem ->

                        // CORRECCIÓN 2: Usar FavoritesManager.FavoriteType
                        if (selectedItem.type == FavoritesManager.FavoriteType.TURISMO) {
                            val idNumerico = selectedItem.id.toIntOrNull()
                            val puntoReal = TurismoDataManager.puntosTuristicos.find { it.id == idNumerico }

                            if (puntoReal != null) {
                                Toast.makeText(this, "Seleccionaste: ${puntoReal.nombre}", Toast.LENGTH_SHORT).show()
                                // Aquí podrías lanzar el DetalleTurismoFragment si estuvieras en MainActivity
                            }
                        } else {
                            // Es un paradero
                            Toast.makeText(this, "Paradero: ${selectedItem.name}", Toast.LENGTH_SHORT).show()
                        }
                    }
                    rvFavoritos.adapter = adapter
                }
            }
        }
    }

    // Funciones auxiliares para manejar la visibilidad
    private fun showLoadingState() {
        pbLoading.visibility = View.VISIBLE
        rvFavoritos.visibility = View.GONE
        tvEmpty.visibility = View.GONE
    }

    private fun showEmptyState() {
        pbLoading.visibility = View.GONE
        rvFavoritos.visibility = View.GONE
        tvEmpty.visibility = View.VISIBLE
    }

    private fun showListState() {
        pbLoading.visibility = View.GONE
        rvFavoritos.visibility = View.VISIBLE
        tvEmpty.visibility = View.GONE
    }
}
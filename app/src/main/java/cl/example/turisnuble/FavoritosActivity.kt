package cl.example.turisnuble

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
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
        rvFavoritos.layoutManager = LinearLayoutManager(this)

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
                when (item.type) {
                    FavoritesManager.FavoriteType.TURISMO -> {
                        val idNumerico = item.id.toIntOrNull()
                        if (idNumerico != null) {
                            TurismoDataManager.puntosTuristicos.any { it.id == idNumerico }
                        } else {
                            false
                        }
                    }
                    FavoritesManager.FavoriteType.PARADERO -> {
                        GtfsDataManager.stops.containsKey(item.id)
                    }
                    FavoritesManager.FavoriteType.RUTA -> {
                        GtfsDataManager.routes.containsKey(item.id)
                    }
                }
            }
            // --- FIN DE LA LÓGICA DE VALIDACIÓN ---

            runOnUiThread {
                if (listaValidada.isEmpty()) {
                    showEmptyState()
                } else {
                    showListState()

                    // Configuramos el adaptador con la acción de navegación
                    adapter = FavoritosAdapter(listaValidada) { selectedItem ->

                        // Preparamos el Intent para ir a MainActivity
                        val intent = Intent(this, MainActivity::class.java)

                        // Estas banderas aseguran que si MainActivity ya está abierta,
                        // se reutilice y se limpie lo que esté encima, en lugar de crear una nueva instancia.
                        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP

                        // Enviamos los datos necesarios según el tipo
                        when (selectedItem.type) {
                            FavoritesManager.FavoriteType.TURISMO -> {
                                intent.putExtra("EXTRA_ACTION_TYPE", "TURISMO")
                                intent.putExtra("EXTRA_ID", selectedItem.id)
                            }
                            FavoritesManager.FavoriteType.PARADERO -> {
                                intent.putExtra("EXTRA_ACTION_TYPE", "PARADERO")
                                intent.putExtra("EXTRA_ID", selectedItem.id)
                            }
                            FavoritesManager.FavoriteType.RUTA -> {
                                intent.putExtra("EXTRA_ACTION_TYPE", "RUTA")
                                intent.putExtra("EXTRA_ID", selectedItem.id)
                            }
                        }

                        startActivity(intent)
                        // finish() // Descomenta si quieres cerrar FavoritosActivity inmediatamente
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
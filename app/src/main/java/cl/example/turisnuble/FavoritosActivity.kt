// app/src/main/java/cl/example/turisnuble/FavoritosActivity.kt

package cl.example.turisnuble

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

    // --- INICIO DE MODIFICACIONES ---

    private lateinit var auth: FirebaseAuth

    // Referencias de la UI
    private lateinit var rvFavoritos: RecyclerView
    private lateinit var pbLoading: ProgressBar
    private lateinit var tvEmpty: TextView
    private lateinit var btnBack: ImageView

    private lateinit var adapter: FavoritosAdapter

    // --- FIN DE MODIFICACIONES ---

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

        // Configurar RecyclerView (solo necesita el LayoutManager aquí)
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
            // Asegurarse de que el código se ejecute en el hilo principal
            runOnUiThread {
                if (favoritesList.isEmpty()) {
                    showEmptyState()
                } else {
                    showListState()
                    // Inicializar el adapter con la lista y el manejador de clics
                    adapter = FavoritosAdapter(favoritesList) { selectedItem ->
                        // Acción simple al hacer clic (puedes expandir esto)
                        Toast.makeText(this, "Clic en: ${selectedItem.name}", Toast.LENGTH_SHORT).show()

                        // NOTA: Aquí podrías implementar una lógica más compleja, como
                        // volver al mapa y centrarlo en el paradero/turismo,
                        // pero por simplicidad, solo mostramos un Toast.
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
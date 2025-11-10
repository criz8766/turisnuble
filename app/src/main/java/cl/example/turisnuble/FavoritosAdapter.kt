// app/src/main/java/cl/example/turisnuble/FavoritosAdapter.kt

package cl.example.turisnuble

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class FavoritosAdapter(
    private val favorites: List<FavoritesManager.FavoriteItem>,
    private val onClick: (FavoritesManager.FavoriteItem) -> Unit
) : RecyclerView.Adapter<FavoritosAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_favorito, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount(): Int = favorites.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = favorites[position]
        holder.bind(item)
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val icon: ImageView = itemView.findViewById(R.id.iv_favorito_icon)
        private val nombre: TextView = itemView.findViewById(R.id.tv_favorito_nombre)
        private val tipo: TextView = itemView.findViewById(R.id.tv_favorito_tipo)

        fun bind(item: FavoritesManager.FavoriteItem) {
            nombre.text = item.name

            when (item.type) {
                FavoritesManager.FavoriteType.TURISMO -> {
                    icon.setImageResource(R.drawable.ic_turismo) // Asumo que tienes este drawable
                    tipo.text = "Punto Turístico"
                }
                FavoritesManager.FavoriteType.PARADERO -> {
                    icon.setImageResource(R.drawable.ic_paradero) // Asumo que tienes este drawable
                    tipo.text = "Paradero"
                }
                FavoritesManager.FavoriteType.RUTA -> {
                    icon.setImageResource(R.drawable.ic_bus) // Asumo que tienes este drawable
                    tipo.text = "Ruta"
                }
            }

            itemView.setOnClickListener { onClick(item) }
        }
    }
}
package cl.example.turisnuble

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CenterCrop

data class RouteCategory(
    val name: String,
    val count: Int,
    val imageResId: Int,
    val routes: List<DisplayRouteInfo>
)

class RutaCategoriaAdapter(
    private val categories: List<RouteCategory>,
    private val onCategoryClick: (RouteCategory) -> Unit
) : RecyclerView.Adapter<RutaCategoriaAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imagenBg: ImageView = view.findViewById(R.id.img_categoria_bg)
        val nombre: TextView = view.findViewById(R.id.txt_nombre_categoria)
        val cantidad: TextView = view.findViewById(R.id.txt_cantidad_lugares)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        // Reutilizamos el layout existente item_categoria.xml
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_categoria, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val category = categories[position]

        holder.nombre.text = category.name
        holder.cantidad.text = "${category.count} Recorridos"

        // Cargamos la imagen de recurso local
        Glide.with(holder.itemView.context)
            .load(category.imageResId)
            .transform(CenterCrop())
            .into(holder.imagenBg)

        holder.itemView.setOnClickListener {
            onCategoryClick(category)
        }
    }

    override fun getItemCount() = categories.size
}
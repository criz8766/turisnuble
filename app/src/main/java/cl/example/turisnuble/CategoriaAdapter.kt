package cl.example.turisnuble

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CenterCrop

class CategoriaAdapter(
    private val categorias: List<String>,
    private val allPuntos: List<PuntoTuristico>, // Necesitamos todos los puntos para sacar la foto de portada
    private val onCategoriaClick: (String) -> Unit
) : RecyclerView.Adapter<CategoriaAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imagenBg: ImageView = view.findViewById(R.id.img_categoria_bg)
        val nombre: TextView = view.findViewById(R.id.txt_nombre_categoria)
        val cantidad: TextView = view.findViewById(R.id.txt_cantidad_lugares)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_categoria, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val categoria = categorias[position]

        // Filtrar puntos de esta categoría para obtener datos
        val puntosDeCategoria = allPuntos.filter { it.categoria == categoria }
        val imagenPortada = puntosDeCategoria.firstOrNull()?.imagenUrl

        holder.nombre.text = categoria
        holder.cantidad.text = "${puntosDeCategoria.size} Lugares"

        // Cargar imagen de portada usando Glide
        if (imagenPortada != null) {
            Glide.with(holder.itemView.context)
                .load(imagenPortada)
                .transform(CenterCrop())
                .into(holder.imagenBg)
        } else {
            holder.imagenBg.setImageResource(R.drawable.ic_launcher_background) // Fallback
        }

        holder.itemView.setOnClickListener {
            onCategoriaClick(categoria)
        }
    }

    override fun getItemCount() = categorias.size
}
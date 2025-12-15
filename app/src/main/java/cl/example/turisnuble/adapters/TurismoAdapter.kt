package cl.example.turisnuble.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import cl.example.turisnuble.R
import cl.example.turisnuble.models.PuntoTuristico
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CenterCrop

class TurismoAdapter(
    private val puntosTuristicos: List<PuntoTuristico>,
    private val onItemClick: (PuntoTuristico) -> Unit
) : RecyclerView.Adapter<TurismoAdapter.PuntoTuristicoViewHolder>() {

    class PuntoTuristicoViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imagen: ImageView = view.findViewById(R.id.imagen_turismo)
        val nombre: TextView = view.findViewById(R.id.nombre_turismo)
        val direccion: TextView = view.findViewById(R.id.direccion_turismo)
        val categoria: TextView = view.findViewById(R.id.categoria_turismo) // <-- NUEVO
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PuntoTuristicoViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_punto_turistico, parent, false)
        return PuntoTuristicoViewHolder(view)
    }

    override fun onBindViewHolder(holder: PuntoTuristicoViewHolder, position: Int) {
        val punto = puntosTuristicos[position]

        // 1. Cargar Imagen desde URL (Internet)
        Glide.with(holder.itemView.context)
            .load(punto.imagenUrl) // <-- CAMBIO: Usamos la URL del objeto
            .placeholder(R.drawable.ic_launcher_background) // Imagen mientras carga (puedes cambiarla por tu logo)
            .error(R.drawable.ic_close) // Imagen si falla la carga
            .transform(CenterCrop()) // Ajuste para que llene el espacio correctamente
            .into(holder.imagen)

        // 2. Asignar textos
        holder.nombre.text = punto.nombre
        holder.direccion.text = punto.direccion
        holder.categoria.text =
            punto.categoria.uppercase() // <-- NUEVO: Mostramos la categoría en mayúsculas

        // 3. Click Listener
        holder.itemView.setOnClickListener {
            onItemClick(punto)
        }
    }

    override fun getItemCount() = puntosTuristicos.size
}
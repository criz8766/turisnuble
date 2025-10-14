package cl.example.turisnuble

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide // <-- NUEVA IMPORTACIÓN

// El constructor ahora incluye la acción de clic
class TurismoAdapter(
    private val puntosTuristicos: List<PuntoTuristico>,
    private val onItemClick: (PuntoTuristico) -> Unit
) : RecyclerView.Adapter<TurismoAdapter.PuntoTuristicoViewHolder>() {

    class PuntoTuristicoViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imagen: ImageView = view.findViewById(R.id.imagen_turismo)
        val nombre: TextView = view.findViewById(R.id.nombre_turismo)
        val direccion: TextView = view.findViewById(R.id.direccion_turismo)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PuntoTuristicoViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_punto_turistico, parent, false)
        return PuntoTuristicoViewHolder(view)
    }

    override fun onBindViewHolder(holder: PuntoTuristicoViewHolder, position: Int) {
        val punto = puntosTuristicos[position]

        // FIX PARA FLUIDEZ: Usar Glide para cargar la imagen asíncronamente, decodificarla
        // en un hilo secundario y cachearla, mejorando drásticamente el scrolling.
        Glide.with(holder.itemView.context)
            .load(punto.imagenId) // Carga la imagen desde los recursos (R.drawable.*)
            .centerCrop()
            .into(holder.imagen)

        holder.nombre.text = punto.nombre
        holder.direccion.text = punto.direccion

        // Asignamos la acción de clic a toda la fila
        holder.itemView.setOnClickListener {
            onItemClick(punto)
        }
    }

    override fun getItemCount() = puntosTuristicos.size
}
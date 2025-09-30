package cl.example.turisnuble

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide // <-- NUEVO IMPORT

class TurismoAdapter(private val puntosTuristicos: List<PuntoTuristico>) :
    RecyclerView.Adapter<TurismoAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imagen: ImageView = view.findViewById(R.id.imagen_turismo)
        val nombre: TextView = view.findViewById(R.id.nombre_turismo)
        val direccion: TextView = view.findViewById(R.id.direccion_turismo)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_punto_turistico, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val punto = puntosTuristicos[position]

        // --- LÍNEA MODIFICADA ---
        // En lugar de: holder.imagen.setImageResource(punto.imagenId)
        // Usamos Glide para cargar la imagen de forma eficiente.
        Glide.with(holder.itemView.context)
            .load(punto.imagenId)
            .into(holder.imagen)

        holder.nombre.text = punto.nombre
        holder.direccion.text = punto.direccion
    }

    override fun getItemCount(): Int {
        return puntosTuristicos.size
    }
}
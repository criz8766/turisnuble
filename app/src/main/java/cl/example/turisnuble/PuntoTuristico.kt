package cl.example.turisnuble

import androidx.annotation.DrawableRes

data class PuntoTuristico(
    val id: Int,
    val nombre: String,
    val direccion: String,
    val latitud: Double,
    val longitud: Double,
    @DrawableRes val imagenId: Int // Usaremos una imagen de nuestros recursos
)
package cl.example.turisnuble

import androidx.annotation.DrawableRes
import java.io.Serializable // <-- NUEVA IMPORTACIÓN

data class PuntoTuristico(
    val id: Int,
    val nombre: String,
    val direccion: String,
    val latitud: Double,
    val longitud: Double,
    @DrawableRes val imagenId: Int // Usaremos una imagen de nuestros recursos
) : Serializable // <-- IMPLEMENTACIÓN AGREGADA
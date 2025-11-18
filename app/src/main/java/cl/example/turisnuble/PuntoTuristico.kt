package cl.example.turisnuble

import java.io.Serializable

data class PuntoTuristico(
    val id: Int,
    val nombre: String,
    val direccion: String,
    val latitud: Double,
    val longitud: Double,
    val categoria: String, // <-- NUEVO: Categoría del lugar
    val imagenUrl: String  // <-- CAMBIO: Ahora es una URL (String), no un ID (Int)
) : Serializable
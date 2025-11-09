package cl.example.turisnuble

import android.util.Log
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase

object FavoritesManager {

    private val db = Firebase.database.reference
    private const val TAG = "FavoritesManager"

    // --- 1. FAVORITOS DE TURISMO (Refactorizado) ---

    // La ruta ahora es /usuarios/{userId}/favoritos_turismo/{puntoId}
    private fun getTurismoFavRef(userId: String, puntoId: Int) =
        db.child("usuarios").child(userId).child("favoritos_turismo").child(puntoId.toString())

    fun addFavoriteTurismo(userId: String, punto: PuntoTuristico) {
        val favoriteData = hashMapOf(
            "id" to punto.id,
            "nombre" to punto.nombre
        )
        getTurismoFavRef(userId, punto.id).setValue(favoriteData)
            .addOnSuccessListener { Log.d(TAG, "Favorito Turismo añadido: ${punto.nombre}") }
            .addOnFailureListener { e -> Log.w(TAG, "Error al añadir favorito turismo", e) }
    }

    fun removeFavoriteTurismo(userId: String, puntoId: Int) {
        getTurismoFavRef(userId, puntoId).removeValue()
            .addOnSuccessListener { Log.d(TAG, "Favorito Turismo eliminado: $puntoId") }
            .addOnFailureListener { e -> Log.w(TAG, "Error al eliminar favorito turismo", e) }
    }

    fun checkFavoriteTurismoStatus(userId: String, puntoId: Int, onResult: (Boolean) -> Unit) {
        getTurismoFavRef(userId, puntoId).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) { onResult(snapshot.exists()) }
            override fun onCancelled(error: DatabaseError) {
                Log.w(TAG, "Error al revisar favorito turismo", error.toException())
                onResult(false)
            }
        })
    }

    // --- 2. FAVORITOS DE PARADEROS (Nuevo) ---

    // La ruta es /usuarios/{userId}/favoritos_paraderos/{stopId}
    // Nota: stopId es un String, no un Int.
    internal fun getParaderoFavRef(userId: String, stopId: String) =
        db.child("usuarios").child(userId).child("favoritos_paraderos").child(stopId)

    fun addFavoriteParadero(userId: String, stop: GtfsStop) {
        // --- CORRECCIÓN AQUÍ ---
        // Cambiamos 'stop.lat' y 'stop.lon' por 'stop.location.latitude' y 'stop.location.longitude'
        val favoriteData = hashMapOf(
            "stopId" to stop.stopId,
            "name" to stop.name,
            "lat" to stop.location.latitude,
            "lon" to stop.location.longitude
        )
        getParaderoFavRef(userId, stop.stopId).setValue(favoriteData)
            .addOnSuccessListener { Log.d(TAG, "Favorito Paradero añadido: ${stop.name}") }
            .addOnFailureListener { e -> Log.w(TAG, "Error al añadir favorito paradero", e) }
    }

    fun removeFavoriteParadero(userId: String, stopId: String) {
        getParaderoFavRef(userId, stopId).removeValue()
            .addOnSuccessListener { Log.d(TAG, "Favorito Paradero eliminado: $stopId") }
            .addOnFailureListener { e -> Log.w(TAG, "Error al eliminar favorito paradero", e) }
    }

    fun checkFavoriteParaderoStatus(userId: String, stopId: String, onResult: (Boolean) -> Unit) {
        getParaderoFavRef(userId, stopId).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) { onResult(snapshot.exists()) }
            override fun onCancelled(error: DatabaseError) {
                Log.w(TAG, "Error al revisar favorito paradero", error.toException())
                onResult(false)
            }
        })
    }

    // --- 3. FAVORITOS DE RUTAS (Nuevo) ---

    // La ruta es /usuarios/{userId}/favoritos_rutas/{routeId}
    // Nota: routeId es un String (ej. "3", "13A").
    internal fun getRutaFavRef(userId: String, routeId: String) =
        db.child("usuarios").child(userId).child("favoritos_rutas").child(routeId)

    fun addFavoriteRuta(userId: String, route: GtfsRoute) {
        val favoriteData = hashMapOf(
            "id" to route.routeId,
            "shortName" to route.shortName,
            "longName" to route.longName
        )
        getRutaFavRef(userId, route.routeId).setValue(favoriteData)
            .addOnSuccessListener { Log.d(TAG, "Favorito Ruta añadido: ${route.shortName}") }
            .addOnFailureListener { e -> Log.w(TAG, "Error al añadir favorito ruta", e) }
    }

    fun removeFavoriteRuta(userId: String, routeId: String) {
        getRutaFavRef(userId, routeId).removeValue()
            .addOnSuccessListener { Log.d(TAG, "Favorito Ruta eliminado: $routeId") }
            .addOnFailureListener { e -> Log.w(TAG, "Error al eliminar favorito ruta", e) }
    }

    fun checkFavoriteRutaStatus(userId: String, routeId: String, onResult: (Boolean) -> Unit) {
        getRutaFavRef(userId, routeId).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) { onResult(snapshot.exists()) }
            override fun onCancelled(error: DatabaseError) {
                Log.w(TAG, "Error al revisar favorito ruta", error.toException())
                onResult(false)
            }
        })
    }

    data class FavoriteItem(
        val id: String,
        val name: String,
        val type: FavoriteType,
        val data: Map<String, Any> // Datos adicionales (lat/lon, etc.)
    )

    enum class FavoriteType { TURISMO, PARADERO, RUTA }

    fun getAllFavorites(userId: String, onResult: (List<FavoriteItem>) -> Unit) {
        val allFavorites = mutableListOf<FavoriteItem>()
        val userRef = db.child("usuarios").child(userId)

        userRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                // 1. Turismo
                snapshot.child("favoritos_turismo").children.forEach {
                    val id = it.key ?: return@forEach
                    val name = it.child("nombre").value as? String ?: "Desconocido"
                    allFavorites.add(FavoriteItem(id, name, FavoriteType.TURISMO, it.value as Map<String, Any>))
                }
                // 2. Paraderos
                snapshot.child("favoritos_paraderos").children.forEach {
                    val id = it.key ?: return@forEach
                    val name = it.child("name").value as? String ?: "Paradero"
                    allFavorites.add(FavoriteItem(id, name, FavoriteType.PARADERO, it.value as Map<String, Any>))
                }
                // 3. Rutas
                snapshot.child("favoritos_rutas").children.forEach {
                    val id = it.key ?: return@forEach
                    // Usamos shortName para mostrar "Línea X"
                    val shortName = it.child("shortName").value as? String ?: ""
                    val displayName = if (shortName.isNotEmpty()) "Línea $shortName" else "Ruta"
                    allFavorites.add(FavoriteItem(id, displayName, FavoriteType.RUTA, it.value as Map<String, Any>))
                }

                onResult(allFavorites)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.w(TAG, "Error obteniendo todos los favoritos", error.toException())
                onResult(emptyList())
            }
        })
    }
}
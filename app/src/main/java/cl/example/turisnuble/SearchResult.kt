package cl.example.turisnuble

// Enum para saber qué tipo de resultado es
enum class SearchResultType {
    PARADERO,
    RUTA,
    TURISMO
}

/**
 * Clase de datos unificada para mostrar en la lista de resultados de búsqueda.
 * @param originalObject Mantenemos una referencia al objeto original (GtfsStop, GtfsRoute, etc.)
 * para saber qué acción tomar cuando el usuario le haga click.
 */
data class SearchResult(
    val type: SearchResultType,
    val title: String,
    val subtitle: String,
    val originalObject: Any
)
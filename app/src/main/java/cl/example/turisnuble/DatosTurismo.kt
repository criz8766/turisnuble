package cl.example.turisnuble

object DatosTurismo {

    // Lista de ejemplo con puntos turísticos de Chillán
    val puntosTuristicos = listOf(
        PuntoTuristico(
            id = 1,
            nombre = "Catedral de Chillán",
            direccion = "Arauco 505, Chillán",
            latitud = -36.606944,
            longitud = -72.102222,
            imagenId = R.drawable.catedral_chillan // Crearemos esta imagen
        ),
        PuntoTuristico(
            id = 2,
            nombre = "Mercado Municipal de Chillán",
            direccion = "Maipón 773, Chillán",
            latitud = -36.610278,
            longitud = -72.101389,
            imagenId = R.drawable.mercado_chillan // Crearemos esta imagen
        ),
        PuntoTuristico(
            id = 3,
            nombre = "Teatro Municipal de Chillán",
            direccion = "18 de Septiembre 590, Chillán",
            latitud = -36.606944,
            longitud = -72.104444,
            imagenId = R.drawable.teatro_chillan // Crearemos esta imagen
        ),
        PuntoTuristico(
            id = 4,
            nombre = "Museo Claudio Arrau",
            direccion = "Claudio Arrau 558, Chillán",
            latitud = -36.605833,
            longitud = -72.1075,
            imagenId = R.drawable.museo_arrau // Crearemos esta imagen
        )
    )
}
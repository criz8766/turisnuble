// --- 1. Importar Librerías ---
const express = require('express');
const admin = require('firebase-admin');
const axios = require('axios');
const GtfsRealtimeBindings = require('gtfs-realtime-bindings');

// --- 2. Configuración ---
const app = express();
// Render nos dará un puerto automáticamente, o usamos el 3000 si probamos localmente
const PORT = process.env.PORT || 3000;

// Obtenemos nuestros "secretos" de las variables de entorno que configuraremos en Render
const serviceAccount = JSON.parse(process.env.FIREBASE_SERVICE_ACCOUNT);
const apiToken = process.env.GTFS_RT_TOKEN;

const GTFS_RT_URL = `https://datamanager.dtpr.transapp.cl/data/gtfs-rt/chillan.proto?apikey=${apiToken}`;

// --- 3. Inicializar Firebase ---
try {
  admin.initializeApp({
    credential: admin.credential.cert(serviceAccount)
  });
} catch (error) {
  console.error("Error al inicializar Firebase:", error.message);
}

const db = admin.firestore();

// --- 4. La Función que hace el Trabajo (¡Es la misma de antes!) ---
async function fetchGtfsRealtimeData() {
  console.log("Iniciando la obtención de datos GTFS-RT...");
  try {
    const response = await axios.get(GTFS_RT_URL, {
      responseType: "arraybuffer",
    });

    const feed = GtfsRealtimeBindings.transit_realtime.FeedMessage.decode(
      new Uint8Array(response.data)
    );

    const vehiclePositions = {};
    feed.entity.forEach((entity) => {
      if (entity.vehicle) {
        vehiclePositions[entity.id] = {
          latitude: entity.vehicle.position.latitude,
          longitude: entity.vehicle.position.longitude,
          tripId: entity.vehicle.trip.tripId,
          routeId: entity.vehicle.trip.routeId,
        };
      }
    });

    const docRef = db.collection("realtime").doc("vehiclePositions");
    await docRef.set({
      positions: vehiclePositions,
      lastUpdate: admin.firestore.FieldValue.serverTimestamp(),
    });

    console.log(`Datos actualizados. ${Object.keys(vehiclePositions).length} vehículos encontrados.`);
    return `OK - ${Object.keys(vehiclePositions).length} vehículos actualizados.`;

  } catch (error) {
    console.error("Error al obtener o procesar datos GTFS-RT:", error.message);
    return `ERROR: ${error.message}`;
  }
}

// --- 5. El Servidor Web ---
// Creamos una "ruta" (endpoint) principal.
// UptimeRobot visitará esta ruta cada minuto.
app.get('/', async (req, res) => {
  console.log("¡Recibido ping de UptimeRobot!");

  // Cuando UptimeRobot nos visite, ejecutamos la función
  const result = await fetchGtfsRealtimeData();

  // Le respondemos a UptimeRobot para que sepa que todo salió bien
  res.status(200).send(`Proceso completado: ${result}`);
});

// Le decimos al servidor que empiece a escuchar visitas
app.listen(PORT, () => {
  console.log(`Servidor escuchando en el puerto ${PORT}`);
});
// --- 1. Importar Librerías ---
const express = require('express');
const admin = require('firebase-admin');
const axios = require('axios');
const GtfsRealtimeBindings = require('gtfs-realtime-bindings');

// --- 2. Configuración ---
const app = express();
const PORT = process.env.PORT || 3000;

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

// --- ¡INICIO DE LA CORRECCIÓN! ---
// Añadimos esta línea para decirle a Firestore
// que ignore campos 'undefined' si se nos escapa alguno.
db.settings({ ignoreUndefinedProperties: true });
// --- FIN DE LA CORRECCIÓN ---


// --- 4. La Función que hace el Trabajo ---
async function fetchGtfsRealtimeData() {
  console.log("Iniciando la obtención de datos GTFS-RT...");

  const response = await axios.get(GTFS_RT_URL, {
    responseType: "arraybuffer",
  });

  const feed = GtfsRealtimeBindings.transit_realtime.FeedMessage.decode(
    new Uint8Array(response.data)
  );

  const vehiclePositions = {};
  feed.entity.forEach((entity) => {

    if (entity.vehicle && entity.vehicle.position) {
      vehiclePositions[entity.id] = {
        latitude: entity.vehicle.position.latitude,
        longitude: entity.vehicle.position.longitude,

        // --- ¡INICIO DE LA CORRECCIÓN! ---
        // Si 'tripId' es 'undefined', guárdalo como 'null'
        tripId: entity.vehicle.trip?.tripId || null,
        routeId: entity.vehicle.trip?.routeId || null
        // --- FIN DE LA CORRECCIÓN ---
      };
    }
  });

  const docRef = db.collection("realtime").doc("vehiclePositions");
  await docRef.set({
    positions: vehiclePositions,
    lastUpdate: admin.firestore.FieldValue.serverTimestamp(),
  });

  const message = `Datos actualizados. ${Object.keys(vehiclePositions).length} vehículos encontrados.`;
  console.log(message);
  return message;
}

// --- 5. El Servidor Web ---
app.get('/', async (req, res) => {
  console.log("¡Recibido ping de UptimeRobot!");

  try {
    const result = await fetchGtfsRealtimeData();
    res.status(200).send(`Proceso completado: ${result}`);

  } catch (error) {
    console.error("Error en el ciclo de fetch:", error.message);
    res.status(500).send(`ERROR: ${error.message}`);
  }
});

app.listen(PORT, () => {
  console.log(`Servidor escuchando en el puerto ${PORT}`);
});
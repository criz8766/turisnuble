// --- 1. Importar Librerías ---
const express = require('express');
const admin = require('firebase-admin');
const axios = require('axios');
const GtfsRealtimeBindings = require('gtfs-realtime-bindings');

// --- 2. Configuración ---
const app = express();
const PORT = process.env.PORT || 3000;

// Obtenemos nuestros "secretos" de las variables de entorno
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

// --- 4. La Función que hace el Trabajo (VERSIÓN CORREGIDA) ---
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

    // --- INICIO DE LA CORRECIÓN (Error 1) ---
    // Hacemos el código "defensivo".
    // Solo procesamos la entidad si tiene:
    // 1. Un objeto 'vehicle'
    // 2. Un objeto 'position' dentro de 'vehicle'
    if (entity.vehicle && entity.vehicle.position) {
      vehiclePositions[entity.id] = {
        // Usamos "optional chaining" (?.)
        // Si 'trip' no existe, guardará 'null', lo cual está bien.
        latitude: entity.vehicle.position.latitude,
        longitude: entity.vehicle.position.longitude,
        tripId: entity.vehicle.trip?.tripId,
        routeId: entity.vehicle.trip?.routeId,
      };
    }
    // --- FIN DE LA CORRECIÓN ---
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

// --- 5. El Servidor Web (VERSIÓN CORREGIDA) ---
// Esta ruta es la que visita UptimeRobot
app.get('/', async (req, res) => {
  console.log("¡Recibido ping de UptimeRobot!");

  try {
    // Ejecutamos la función
    const result = await fetchGtfsRealtimeData();

    // Le respondemos a UptimeRobot que todo salió bien
    res.status(200).send(`Proceso completado: ${result}`);

  } catch (error) {
    // --- INICIO DE LA CORRECIÓN (Error 2) ---
    // Si CUALQUIER cosa falla (la API 503, el parseo, etc.)...
    console.error("Error en el ciclo de fetch:", error.message);

    // Le respondemos a UptimeRobot con un error, pero el servidor no se cae.
    // UptimeRobot lo marcará como "down" pero lo volverá a intentar en 1 min.
    res.status(500).send(`ERROR: ${error.message}`);
    // --- FIN DE LA CORRECIÓN ---
  }
});

// Le decimos al servidor que empiece a escuchar visitas
app.listen(PORT, () => {
  console.log(`Servidor escuchando en el puerto ${PORT}`);
});
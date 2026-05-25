const express = require('express');
const mysql = require('mysql2/promise');
const { MongoClient } = require('mongodb');

const app = express();
const PORT = 3000;

// CORS middleware
app.use((req, res, next) => {
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET, POST, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization');
  if (req.method === 'OPTIONS') return res.sendStatus(200);
  next();
});

// Parse raw binary bodies up to 50MB
app.use('/api/images', express.raw({ type: 'application/octet-stream', limit: '50mb' }));
app.use(express.json());

// Database connections
let mysqlPool;
let mongoDb;

async function initDatabases() {
  // MySQL connection pool
  mysqlPool = mysql.createPool({
    host: 'localhost',
    user: 'root',
    password: 'root',
    database: 'imageencrypter',
    waitForConnections: true,
    connectionLimit: 10,
  });

  // MongoDB connection
  const mongoClient = new MongoClient('mongodb://localhost:27017');
  await mongoClient.connect();
  mongoDb = mongoClient.db('imageencrypter');
  console.log('Connected to MySQL and MongoDB');
}

// ─── Image Endpoints ───────────────────────────────────────────────────────────

// POST /api/images - store an image (raw BMP body, metadata in query params)
app.post('/api/images', async (req, res) => {
  try {
    const { jobId, operation, mode, originalName, userEmail } = req.query;
    const imageData = req.body;

    if (!imageData || imageData.length === 0) {
      return res.status(400).json({ error: 'No image data received' });
    }

    const [result] = await mysqlPool.execute(
      'INSERT INTO images (job_id, original_name, operation, mode, image_data, user_email) VALUES (?, ?, ?, ?, ?, ?)',
      [jobId || null, originalName || 'image.bmp', operation || 'encrypt', mode || 'ecb', imageData, userEmail || null]
    );

    console.log(`Stored image id=${result.insertId} jobId=${jobId} (${imageData.length} bytes)`);
    res.json({ id: result.insertId });
  } catch (err) {
    console.error('Error storing image:', err);
    res.status(500).json({ error: err.message });
  }
});

// GET /api/images - list stored images (optionally filtered by userEmail)
app.get('/api/images', async (req, res) => {
  try {
    const { userEmail } = req.query;
    let query = 'SELECT id, job_id, original_name, operation, mode, created_at, LENGTH(image_data) as size FROM images';
    const params = [];
    if (userEmail) {
      query += ' WHERE user_email = ?';
      params.push(userEmail);
    }
    query += ' ORDER BY created_at DESC';
    const [rows] = await mysqlPool.execute(query, params);
    res.json(rows);
  } catch (err) {
    console.error('Error listing images:', err);
    res.status(500).json({ error: err.message });
  }
});

// GET /api/images/:id - download an image
app.get('/api/images/:id', async (req, res) => {
  try {
    const [rows] = await mysqlPool.execute(
      'SELECT image_data, original_name, operation FROM images WHERE id = ?',
      [req.params.id]
    );

    if (rows.length === 0) {
      return res.status(404).json({ error: 'Image not found' });
    }

    const { image_data, original_name, operation } = rows[0];
    const filename = `${operation}_${original_name || 'image.bmp'}`;

    res.setHeader('Content-Type', 'application/octet-stream');
    res.setHeader('Content-Disposition', `attachment; filename="${filename}"`);
    res.setHeader('Content-Length', image_data.length);
    res.send(image_data);
  } catch (err) {
    console.error('Error retrieving image:', err);
    res.status(500).json({ error: err.message });
  }
});

// ─── Monitoring Endpoints ──────────────────────────────────────────────────────

// POST /api/monitoring - store SNMP stats (called by collector)
app.post('/api/monitoring', async (req, res) => {
  try {
    const stats = req.body;
    if (Array.isArray(stats)) {
      await mongoDb.collection('monitoring').insertMany(
        stats.map(s => ({ ...s, timestamp: new Date() }))
      );
    } else {
      await mongoDb.collection('monitoring').insertOne({
        ...stats,
        timestamp: new Date(),
      });
    }
    res.json({ ok: true });
  } catch (err) {
    console.error('Error storing monitoring data:', err);
    res.status(500).json({ error: err.message });
  }
});

// GET /api/monitoring - get latest stats for all containers
app.get('/api/monitoring', async (req, res) => {
  try {
    // Get the most recent entry for each container
    const stats = await mongoDb.collection('monitoring').aggregate([
      { $sort: { timestamp: -1 } },
      { $group: {
        _id: '$hostname',
        hostname: { $first: '$hostname' },
        sysName: { $first: '$sysName' },
        cpuLoad1: { $first: '$cpuLoad1' },
        cpuLoad5: { $first: '$cpuLoad5' },
        cpuLoad15: { $first: '$cpuLoad15' },
        ramTotal: { $first: '$ramTotal' },
        ramAvailable: { $first: '$ramAvailable' },
        uptime: { $first: '$uptime' },
        timestamp: { $first: '$timestamp' },
      }},
    ]).toArray();

    res.json(stats);
  } catch (err) {
    console.error('Error fetching monitoring data:', err);
    res.status(500).json({ error: err.message });
  }
});

// ─── Start ─────────────────────────────────────────────────────────────────────

async function start() {
  await initDatabases();

  // Start SNMP collector
  try {
    require('./snmp-collector');
    console.log('SNMP collector started');
  } catch (err) {
    console.warn('SNMP collector not available:', err.message);
  }

  app.listen(PORT, '0.0.0.0', () => {
    console.log(`Storage API listening on port ${PORT}`);
  });
}

start().catch(err => {
  console.error('Failed to start:', err);
  process.exit(1);
});

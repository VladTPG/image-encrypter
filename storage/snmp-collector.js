const snmp = require('net-snmp');
const { MongoClient } = require('mongodb');

const POLL_INTERVAL = 30000; // 30 seconds

const CONTAINERS = [
  { hostname: 'app', address: 'app' },
  { hostname: 'broker', address: 'broker' },
  { hostname: 'subscriber', address: 'subscriber' },
  { hostname: 'mpi-worker', address: 'mpi-worker' },
  { hostname: 'storage', address: 'localhost' },
];

// SNMP OIDs
const OIDS = {
  sysName:      '1.3.6.1.2.1.1.5.0',
  uptime:       '1.3.6.1.2.1.1.3.0',
  cpuLoad1:     '1.3.6.1.4.1.2021.10.1.3.1',
  cpuLoad5:     '1.3.6.1.4.1.2021.10.1.3.2',
  cpuLoad15:    '1.3.6.1.4.1.2021.10.1.3.3',
  ramTotal:     '1.3.6.1.4.1.2021.4.5.0',
  ramAvailable: '1.3.6.1.4.1.2021.4.6.0',
};

const OID_LIST = Object.values(OIDS);
const OID_KEYS = Object.keys(OIDS);

let mongoDb;

async function initMongo() {
  const client = new MongoClient('mongodb://localhost:27017');
  await client.connect();
  mongoDb = client.db('imageencrypter');
  // Create TTL index to auto-expire old monitoring data (keep 1 hour)
  await mongoDb.collection('monitoring').createIndex(
    { timestamp: 1 },
    { expireAfterSeconds: 3600 }
  );
}

function pollContainer(container) {
  return new Promise((resolve) => {
    const session = snmp.createSession(container.address, 'public', {
      timeout: 5000,
      retries: 1,
    });

    session.get(OID_LIST, (error, varbinds) => {
      session.close();

      if (error) {
        resolve({
          hostname: container.hostname,
          error: error.message,
          online: false,
        });
        return;
      }

      const result = { hostname: container.hostname, online: true };
      varbinds.forEach((vb, i) => {
        if (snmp.isVarbindError(vb)) {
          result[OID_KEYS[i]] = null;
        } else {
          result[OID_KEYS[i]] = vb.value.toString();
        }
      });

      resolve(result);
    });
  });
}

async function collectAll() {
  const results = await Promise.all(CONTAINERS.map(pollContainer));
  if (mongoDb) {
    try {
      await mongoDb.collection('monitoring').insertMany(
        results.map(r => ({ ...r, timestamp: new Date() }))
      );
    } catch (err) {
      console.error('Error storing SNMP data:', err.message);
    }
  }
}

async function start() {
  await initMongo();
  console.log('SNMP collector: polling every', POLL_INTERVAL / 1000, 'seconds');
  collectAll(); // initial poll
  setInterval(collectAll, POLL_INTERVAL);
}

start().catch(err => console.error('SNMP collector error:', err));

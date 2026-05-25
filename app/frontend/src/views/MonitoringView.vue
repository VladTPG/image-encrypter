<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'

interface ContainerStats {
  _id: string
  hostname: string
  sysName: string | null
  cpuLoad1: string | null
  cpuLoad5: string | null
  cpuLoad15: string | null
  ramTotal: string | null
  ramAvailable: string | null
  uptime: string | null
  online?: boolean
  error?: string
  timestamp: string
}

interface ImageRecord {
  id: number
  job_id: string
  original_name: string
  operation: string
  mode: string
  size: number
  created_at: string
}

const stats = ref<ContainerStats[]>([])
const images = ref<ImageRecord[]>([])
const loading = ref(true)
let interval: ReturnType<typeof setInterval>

async function fetchStats() {
  try {
    const res = await fetch('http://localhost:3000/api/monitoring')
    if (res.ok) stats.value = await res.json()
  } catch {
    /* ignore fetch errors */
  }
}

async function fetchImages() {
  try {
    const res = await fetch('http://localhost:3000/api/images')
    if (res.ok) images.value = await res.json()
  } catch {
    /* ignore fetch errors */
  }
}

async function refresh() {
  await Promise.all([fetchStats(), fetchImages()])
  loading.value = false
}

function isOnline(s: ContainerStats): boolean {
  return s.online !== false && !s.error
}

function formatBytes(bytes: number): string {
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
}

function formatKbToMb(kb: string | null): string {
  if (!kb) return 'N/A'
  return (parseInt(kb) / 1024).toFixed(0)
}

function formatUptime(ticks: string | null): string {
  if (!ticks) return 'N/A'
  const seconds = parseInt(ticks) / 100
  const hours = Math.floor(seconds / 3600)
  const mins = Math.floor((seconds % 3600) / 60)
  if (hours > 0) return `${hours}h ${mins}m`
  return `${mins}m`
}

function timeAgo(timestamp: string): string {
  const diff = Date.now() - new Date(timestamp).getTime()
  const secs = Math.floor(diff / 1000)
  if (secs < 60) return `${secs}s ago`
  return `${Math.floor(secs / 60)}m ago`
}

onMounted(() => {
  refresh()
  interval = setInterval(refresh, 10000)
})

onUnmounted(() => {
  clearInterval(interval)
})
</script>

<template>
  <div class="monitoring-page">
    <h1>System Monitoring</h1>

    <div v-if="loading" class="loading">Loading...</div>

    <section v-else>
      <h2>Container Status</h2>
      <p class="subtitle">Auto-refreshes every 10 seconds</p>
      <div class="cards">
        <div v-for="s in stats" :key="s._id" class="card" :class="{ offline: !isOnline(s) }">
          <div class="card-header">
            <span class="hostname">{{ s.hostname }}</span>
            <span class="status-dot" :class="isOnline(s) ? 'online' : 'offline'"></span>
          </div>
          <div v-if="isOnline(s)" class="card-body">
            <div class="stat">
              <span class="label">CPU Load (1/5/15m)</span>
              <span class="value">{{ s.cpuLoad1 ?? 'N/A' }} / {{ s.cpuLoad5 ?? 'N/A' }} / {{ s.cpuLoad15 ?? 'N/A' }}</span>
            </div>
            <div class="stat">
              <span class="label">RAM</span>
              <span class="value">
                {{ formatKbToMb(s.ramAvailable) }} MB free
                / {{ formatKbToMb(s.ramTotal) }} MB total
              </span>
            </div>
            <div class="stat">
              <span class="label">Uptime</span>
              <span class="value">{{ formatUptime(s.uptime) }}</span>
            </div>
            <div class="stat">
              <span class="label">Last updated</span>
              <span class="value muted">{{ timeAgo(s.timestamp) }}</span>
            </div>
          </div>
          <div v-else class="card-body">
            <span class="offline-text">{{ s.error || 'Container unreachable' }}</span>
          </div>
        </div>
      </div>

      <div v-if="stats.length === 0" class="empty">
        No monitoring data yet. SNMP collector polls every 30 seconds.
      </div>

      <h2>Job History</h2>
      <table v-if="images.length > 0" class="images-table">
        <thead>
          <tr>
            <th>ID</th>
            <th>Name</th>
            <th>Operation</th>
            <th>Mode</th>
            <th>Size</th>
            <th>Date</th>
            <th>Download</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="img in images" :key="img.id">
            <td>{{ img.id }}</td>
            <td>{{ img.original_name }}</td>
            <td>
              <span class="badge" :class="img.operation">{{ img.operation }}</span>
            </td>
            <td>{{ img.mode?.toUpperCase() }}</td>
            <td>{{ formatBytes(img.size) }}</td>
            <td>{{ new Date(img.created_at).toLocaleString() }}</td>
            <td>
              <a :href="`http://localhost:3000/api/images/${img.id}`" target="_blank">Download</a>
            </td>
          </tr>
        </tbody>
      </table>
      <p v-else class="empty">No images processed yet.</p>
    </section>
  </div>
</template>

<style scoped>
.monitoring-page {
  max-width: 900px;
}

h1 {
  color: #e94560;
  margin-bottom: 0.3rem;
  font-size: 1.4rem;
}

h2 {
  color: #ccc;
  font-size: 1.1rem;
  margin: 1.5rem 0 0.5rem;
}

.subtitle {
  color: #555;
  font-size: 0.8rem;
  margin-bottom: 1rem;
}

.loading {
  color: #888;
}

.cards {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 1rem;
}

.card {
  background: #16213e;
  border-radius: 8px;
  padding: 1rem;
  border: 1px solid #1a1a2e;
}

.card.offline {
  opacity: 0.6;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 0.8rem;
}

.hostname {
  font-weight: bold;
  color: #6db3f8;
}

.status-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
}

.status-dot.online {
  background: #4caf50;
  box-shadow: 0 0 6px #4caf5088;
}

.status-dot.offline {
  background: #e94560;
}

.stat {
  display: flex;
  justify-content: space-between;
  margin-bottom: 0.3rem;
  font-size: 0.85rem;
}

.stat .label {
  color: #888;
}

.stat .value {
  color: #ddd;
}

.stat .value.muted {
  color: #666;
}

.offline-text {
  color: #e94560;
  font-size: 0.85rem;
}

.images-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.9rem;
}

.images-table th,
.images-table td {
  padding: 0.5rem 0.8rem;
  text-align: left;
  border-bottom: 1px solid #1a1a2e;
}

.images-table th {
  color: #888;
  font-weight: normal;
  font-size: 0.8rem;
  text-transform: uppercase;
}

.images-table td {
  color: #ddd;
}

.images-table a {
  color: #6db3f8;
}

.badge {
  display: inline-block;
  padding: 0.15rem 0.5rem;
  border-radius: 3px;
  font-size: 0.8rem;
  font-weight: bold;
  text-transform: uppercase;
}

.badge.encrypt {
  background: #1a3a2a;
  color: #4caf50;
}

.badge.decrypt {
  background: #3a2a1a;
  color: #f8a846;
}

.empty {
  color: #666;
  font-size: 0.9rem;
}
</style>

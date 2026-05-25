<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useEncryptionStore } from '@/stores/encryption'
import { useAuthStore } from '@/stores/auth'

const store = useEncryptionStore()
const auth = useAuthStore()

const file = ref<File | null>(null)
const operation = ref('encrypt')
const mode = ref('ecb')
const userKey = ref('')

interface HistoryItem {
  id: number
  job_id: string
  original_name: string
  operation: string
  mode: string
  size: number
  created_at: string
}

const history = ref<HistoryItem[]>([])

const isDecrypt = computed(() => operation.value === 'decrypt')

function onFileChange(e: Event) {
  const input = e.target as HTMLInputElement
  file.value = input.files?.[0] ?? null
}

async function handleSubmit() {
  if (!file.value) return
  await store.upload(file.value, operation.value, mode.value, userKey.value)
}

function copyKey() {
  navigator.clipboard.writeText(store.key)
}

function formatBytes(bytes: number): string {
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
}

async function fetchHistory() {
  try {
    const email = encodeURIComponent(auth.email)
    const res = await fetch(`http://localhost:3000/api/images?userEmail=${email}`)
    if (res.ok) history.value = await res.json()
  } catch {
    /* ignore */
  }
}

onMounted(fetchHistory)

// Refresh history when a job completes
const originalUpload = store.upload
</script>

<template>
  <div class="upload-page">
    <h1>Image Encryption / Decryption</h1>
    <p class="page-desc">
      Upload a <strong>BMP image</strong> to encrypt or decrypt it using AES-256.
      The processing is distributed across multiple containers using OpenMPI and OpenMP.
    </p>

    <form @submit.prevent="handleSubmit" class="upload-form">
      <div class="field">
        <label>BMP Image</label>
        <input type="file" accept=".bmp" @change="onFileChange" required />
        <small v-if="isDecrypt" class="hint-decrypt">
          Select the encrypted BMP file you want to decrypt.
        </small>
        <small v-else>
          Select a BMP image to encrypt. Only <code>.bmp</code> format is supported.
        </small>
      </div>

      <div class="field-row">
        <div class="field">
          <label>Operation</label>
          <div class="radio-group">
            <label class="radio-label">
              <input type="radio" v-model="operation" value="encrypt" />
              <span class="radio-text">Encrypt</span>
            </label>
            <label class="radio-label">
              <input type="radio" v-model="operation" value="decrypt" />
              <span class="radio-text">Decrypt</span>
            </label>
          </div>
        </div>

        <div class="field">
          <label>AES Mode</label>
          <div class="radio-group">
            <label class="radio-label">
              <input type="radio" v-model="mode" value="ecb" />
              <span class="radio-text">ECB</span>
            </label>
            <label class="radio-label">
              <input type="radio" v-model="mode" value="cbc" />
              <span class="radio-text">CBC</span>
            </label>
          </div>
          <small v-if="isDecrypt" class="hint-decrypt">
            Must match the mode used during encryption.
          </small>
        </div>
      </div>

      <div class="field">
        <label>
          AES-256 Key (64 hex characters)
          <span v-if="isDecrypt" class="required-badge">Required</span>
          <span v-else class="optional-badge">Optional</span>
        </label>
        <input
          v-model="userKey"
          type="text"
          :placeholder="isDecrypt
            ? 'Paste the key you received during encryption'
            : 'Leave empty to auto-generate a new key'"
          maxlength="64"
          pattern="[0-9a-fA-F]*"
          class="key-input"
          :class="{ 'key-required': isDecrypt }"
        />
        <small v-if="isDecrypt" class="hint-decrypt">
          You must provide the exact key that was used to encrypt this image.
          Using a wrong key or wrong mode will produce a corrupted output.
        </small>
        <small v-else>
          Leave blank to generate a new key automatically. Save the key to decrypt later.
        </small>
      </div>

      <div v-if="isDecrypt && !userKey.trim()" class="warning-box">
        You are in <strong>decrypt mode</strong> without a key.
        A random key will be generated, which will not correctly decrypt the image.
        Please paste the original encryption key.
      </div>

      <button
        type="submit"
        class="submit-btn"
        :class="{ 'btn-decrypt': isDecrypt }"
        :disabled="!file || store.status === 'uploading' || store.status === 'processing'"
      >
        {{
          store.status === 'uploading' ? 'Uploading...'
          : store.status === 'processing' ? 'Processing...'
          : isDecrypt ? 'Decrypt Image' : 'Encrypt Image'
        }}
      </button>
    </form>

    <!-- Status -->
    <div v-if="store.status !== 'idle'" class="status-section">
      <div v-if="store.status === 'uploading'" class="status uploading">
        Uploading image...
      </div>

      <div v-if="store.status === 'processing'" class="status processing">
        {{ isDecrypt ? 'Decrypting' : 'Encrypting' }} with MPI across containers. This may take a moment...
      </div>

      <div v-if="store.status === 'done'" class="status done">
        <p>{{ isDecrypt ? 'Decryption' : 'Encryption' }} complete!</p>
        <a :href="store.downloadUrl" class="download-btn" target="_blank" @click="fetchHistory">
          Download {{ isDecrypt ? 'Decrypted' : 'Encrypted' }} Image
        </a>
      </div>

      <div v-if="store.status === 'error'" class="status error">
        Error: {{ store.error }}
      </div>

      <!-- Show key -->
      <div v-if="store.key" class="key-display">
        <label>
          {{ isDecrypt ? 'Key used for decryption:' : 'AES Key — save this to decrypt later:' }}
        </label>
        <div class="key-row">
          <code>{{ store.key }}</code>
          <button type="button" class="copy-btn" @click="copyKey">Copy</button>
        </div>
      </div>
    </div>

    <!-- History -->
    <div class="history-section">
      <h2>Job History</h2>
      <table v-if="history.length > 0" class="history-table">
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
          <tr v-for="img in history" :key="img.id">
            <td>{{ img.id }}</td>
            <td class="name-cell">{{ img.original_name }}</td>
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
      <p v-else class="empty">No processed images yet. Upload a BMP to get started.</p>
    </div>
  </div>
</template>

<style scoped>
.upload-page {
  max-width: 650px;
}

h1 {
  color: #e94560;
  margin-bottom: 0.3rem;
  font-size: 1.4rem;
}

h2 {
  color: #ccc;
  font-size: 1.1rem;
  margin-bottom: 0.8rem;
}

.page-desc {
  color: #888;
  font-size: 0.9rem;
  margin-bottom: 1.5rem;
  line-height: 1.5;
}

.upload-form {
  background: #16213e;
  padding: 1.5rem;
  border-radius: 8px;
}

.field {
  margin-bottom: 1rem;
}

.field > label {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin-bottom: 0.3rem;
  color: #ccc;
  font-size: 0.9rem;
}

.field small {
  color: #666;
  font-size: 0.8rem;
  display: block;
  margin-top: 0.2rem;
}

.field small code {
  background: #0f3460;
  padding: 0.1rem 0.3rem;
  border-radius: 2px;
  font-size: 0.75rem;
}

.hint-decrypt {
  color: #f8a846 !important;
}

.required-badge {
  background: #e94560;
  color: white;
  padding: 0.1rem 0.4rem;
  border-radius: 3px;
  font-size: 0.7rem;
  font-weight: bold;
  text-transform: uppercase;
}

.optional-badge {
  background: #333;
  color: #888;
  padding: 0.1rem 0.4rem;
  border-radius: 3px;
  font-size: 0.7rem;
  text-transform: uppercase;
}

.field-row {
  display: flex;
  gap: 2rem;
}

.radio-group {
  display: flex;
  gap: 1rem;
}

.radio-label {
  display: flex;
  align-items: center;
  gap: 0.3rem;
  color: #ddd;
  cursor: pointer;
}

.radio-text {
  font-size: 0.95rem;
}

input[type='file'] {
  color: #ccc;
}

.key-input {
  width: 100%;
  padding: 0.5rem;
  border: 1px solid #333;
  border-radius: 4px;
  background: #0f3460;
  color: #eee;
  font-family: monospace;
  font-size: 0.9rem;
  box-sizing: border-box;
}

.key-input:focus {
  outline: none;
  border-color: #e94560;
}

.key-input.key-required {
  border-color: #f8a846;
}

.warning-box {
  background: #3a2a1a;
  border: 1px solid #f8a846;
  color: #f8a846;
  padding: 0.7rem 1rem;
  border-radius: 6px;
  font-size: 0.85rem;
  margin-bottom: 1rem;
  line-height: 1.5;
}

.submit-btn {
  width: 100%;
  padding: 0.7rem;
  background: #e94560;
  color: white;
  border: none;
  border-radius: 4px;
  font-size: 1rem;
  cursor: pointer;
  margin-top: 0.5rem;
}

.submit-btn.btn-decrypt {
  background: #f8a846;
  color: #1a1a2e;
}

.submit-btn:hover:not(:disabled) {
  filter: brightness(0.9);
}

.submit-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.status-section {
  margin-top: 1.5rem;
}

.status {
  padding: 1rem;
  border-radius: 6px;
  margin-bottom: 1rem;
}

.uploading {
  background: #1a3a5c;
  color: #6db3f8;
}

.processing {
  background: #3a2a1a;
  color: #f8a846;
}

.done {
  background: #1a3a2a;
  color: #4caf50;
}

.error {
  background: #3a1a1a;
  color: #e94560;
}

.download-btn {
  display: inline-block;
  margin-top: 0.5rem;
  padding: 0.5rem 1.5rem;
  background: #4caf50;
  color: white;
  text-decoration: none;
  border-radius: 4px;
}

.download-btn:hover {
  background: #43a047;
}

.key-display {
  background: #16213e;
  padding: 1rem;
  border-radius: 6px;
  margin-top: 1rem;
}

.key-display label {
  color: #ccc;
  font-size: 0.85rem;
  display: block;
  margin-bottom: 0.3rem;
}

.key-row {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.key-row code {
  flex: 1;
  background: #0f3460;
  padding: 0.4rem;
  border-radius: 4px;
  font-size: 0.8rem;
  color: #f8a846;
  word-break: break-all;
}

.copy-btn {
  padding: 0.3rem 0.8rem;
  background: #0f3460;
  color: #6db3f8;
  border: 1px solid #333;
  border-radius: 4px;
  cursor: pointer;
  font-size: 0.8rem;
}

.copy-btn:hover {
  background: #1a4a7c;
}

.history-section {
  margin-top: 2rem;
}

.history-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.85rem;
}

.history-table th,
.history-table td {
  padding: 0.5rem 0.6rem;
  text-align: left;
  border-bottom: 1px solid #1a1a2e;
}

.history-table th {
  color: #888;
  font-weight: normal;
  font-size: 0.75rem;
  text-transform: uppercase;
}

.history-table td {
  color: #ddd;
}

.history-table a {
  color: #6db3f8;
}

.name-cell {
  max-width: 150px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.badge {
  display: inline-block;
  padding: 0.1rem 0.45rem;
  border-radius: 3px;
  font-size: 0.75rem;
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

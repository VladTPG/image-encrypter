import { ref } from 'vue'
import { defineStore } from 'pinia'
import { useAuthStore } from './auth'

export const useEncryptionStore = defineStore('encryption', () => {
  const jobId = ref('')
  const key = ref('')
  const status = ref<'idle' | 'uploading' | 'processing' | 'done' | 'error'>('idle')
  const downloadUrl = ref('')
  const error = ref('')

  function reset() {
    jobId.value = ''
    key.value = ''
    status.value = 'idle'
    downloadUrl.value = ''
    error.value = ''
  }

  async function upload(file: File, operation: string, mode: string, userKey: string) {
    reset()
    status.value = 'uploading'
    error.value = ''

    const auth = useAuthStore()

    try {
      const formData = new FormData()
      formData.append('file', file)
      formData.append('operation', operation)
      formData.append('mode', mode)
      if (userKey.trim()) {
        formData.append('key', userKey.trim())
      }

      const res = await fetch('/api/upload', {
        method: 'POST',
        headers: { Authorization: `Bearer ${auth.token}` },
        body: formData,
      })

      const data = await res.json()
      if (!res.ok) throw new Error(data.error || 'Upload failed')

      jobId.value = data.jobId
      key.value = data.key
      status.value = 'processing'

      // Open SSE for notifications
      listenForCompletion(data.jobId, auth.token)
    } catch (e: any) {
      status.value = 'error'
      error.value = e.message
    }
  }

  function listenForCompletion(jid: string, token: string) {
    // EventSource doesn't support custom headers, so we pass token as query param
    const eventSource = new EventSource(`/api/notifications/${jid}?token=${token}`)

    eventSource.addEventListener('done', (event: MessageEvent) => {
      const data = JSON.parse(event.data)
      downloadUrl.value = `http://localhost:3000/api/images/${data.imageId}`
      status.value = 'done'
      eventSource.close()
    })

    eventSource.onerror = () => {
      // SSE connection lost - might be server restart or timeout
      if (status.value === 'processing') {
        error.value = 'Connection lost. Check if processing completed.'
      }
      eventSource.close()
    }
  }

  return { jobId, key, status, downloadUrl, error, upload, reset }
})

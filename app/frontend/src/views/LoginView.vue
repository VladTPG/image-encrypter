<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const router = useRouter()

const email = ref('')
const password = ref('')
const isRegister = ref(false)
const error = ref('')
const loading = ref(false)

async function handleSubmit() {
  error.value = ''
  loading.value = true

  try {
    if (isRegister.value) {
      await auth.register(email.value, password.value)
    } else {
      await auth.login(email.value, password.value)
    }
    router.push('/')
  } catch (e: any) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-container">
    <div class="login-card">
      <h1>{{ isRegister ? 'Register' : 'Login' }}</h1>

      <form @submit.prevent="handleSubmit">
        <div class="field">
          <label for="email">Email</label>
          <input
            id="email"
            v-model="email"
            type="email"
            required
            placeholder="your@email.com"
          />
        </div>

        <div class="field">
          <label for="password">Password</label>
          <input
            id="password"
            v-model="password"
            type="password"
            required
            placeholder="Password"
            minlength="6"
          />
        </div>

        <div v-if="error" class="error">{{ error }}</div>

        <button type="submit" :disabled="loading" class="submit-btn">
          {{ loading ? 'Please wait...' : isRegister ? 'Register' : 'Login' }}
        </button>
      </form>

      <p class="toggle">
        {{ isRegister ? 'Already have an account?' : "Don't have an account?" }}
        <a href="#" @click.prevent="isRegister = !isRegister">
          {{ isRegister ? 'Login' : 'Register' }}
        </a>
      </p>
    </div>
  </div>
</template>

<style scoped>
.login-container {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 60vh;
}

.login-card {
  background: #16213e;
  padding: 2.5rem;
  border-radius: 8px;
  width: 100%;
  max-width: 400px;
}

h1 {
  margin: 0 0 1.5rem;
  color: #e94560;
  font-size: 1.5rem;
}

.field {
  margin-bottom: 1rem;
}

label {
  display: block;
  margin-bottom: 0.3rem;
  color: #ccc;
  font-size: 0.9rem;
}

input {
  width: 100%;
  padding: 0.6rem;
  border: 1px solid #333;
  border-radius: 4px;
  background: #0f3460;
  color: #eee;
  font-size: 1rem;
  box-sizing: border-box;
}

input:focus {
  outline: none;
  border-color: #e94560;
}

.error {
  color: #e94560;
  margin-bottom: 1rem;
  font-size: 0.9rem;
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
}

.submit-btn:hover {
  background: #c73e54;
}

.submit-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.toggle {
  margin-top: 1rem;
  text-align: center;
  color: #888;
  font-size: 0.9rem;
}

.toggle a {
  color: #e94560;
}
</style>

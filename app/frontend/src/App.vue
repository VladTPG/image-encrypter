<script setup lang="ts">
import { RouterLink, RouterView, useRouter } from 'vue-router'
import { useAuthStore } from './stores/auth'

const auth = useAuthStore()
const router = useRouter()

function handleLogout() {
  auth.logout()
  router.push('/login')
}
</script>

<template>
  <header>
    <nav>
      <div class="nav-brand">Image Encrypter</div>
      <div class="nav-links">
        <template v-if="auth.isLoggedIn">
          <RouterLink to="/">Upload</RouterLink>
          <RouterLink v-if="auth.isAdmin" to="/monitoring">Monitoring</RouterLink>
          <span class="nav-user">{{ auth.email }}</span>
          <button class="nav-btn" @click="handleLogout">Logout</button>
        </template>
        <template v-else>
          <RouterLink to="/login">Login</RouterLink>
        </template>
      </div>
    </nav>
  </header>

  <main>
    <RouterView />
  </main>
</template>

<style scoped>
header {
  background: #1a1a2e;
  padding: 0 2rem;
}

nav {
  display: flex;
  align-items: center;
  justify-content: space-between;
  max-width: 1200px;
  margin: 0 auto;
  height: 60px;
}

.nav-brand {
  font-size: 1.2rem;
  font-weight: bold;
  color: #e94560;
}

.nav-links {
  display: flex;
  align-items: center;
  gap: 1.5rem;
}

nav a {
  color: #eee;
  text-decoration: none;
  font-size: 0.95rem;
}

nav a.router-link-exact-active {
  color: #e94560;
}

.nav-user {
  color: #888;
  font-size: 0.85rem;
}

.nav-btn {
  background: none;
  border: 1px solid #e94560;
  color: #e94560;
  padding: 0.3rem 0.8rem;
  border-radius: 4px;
  cursor: pointer;
  font-size: 0.85rem;
}

.nav-btn:hover {
  background: #e94560;
  color: white;
}

main {
  max-width: 1200px;
  margin: 2rem auto;
  padding: 0 2rem;
}
</style>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const authStore = useAuthStore()

const username = ref('')
const password = ref('')
const confirmPassword = ref('')

onMounted(() => {
  authStore.error = null
})

async function handleSubmit() {
  if (password.value !== confirmPassword.value) {
    authStore.error = '两次输入的密码不一致'
    return
  }
  try {
    await authStore.register(username.value, password.value)
    router.push('/')
  } catch {
    // error handled in store
  }
}
</script>

<template>
  <div class="auth-container">
    <h1>注册</h1>
    <form @submit.prevent="handleSubmit">
      <div class="form-group">
        <label for="username">用户名</label>
        <input id="username" v-model="username" type="text" required />
      </div>
      <div class="form-group">
        <label for="password">密码</label>
        <input id="password" v-model="password" type="password" required />
      </div>
      <div class="form-group">
        <label for="confirmPassword">确认密码</label>
        <input id="confirmPassword" v-model="confirmPassword" type="password" required />
      </div>
      <p v-if="authStore.error" class="error">{{ authStore.error }}</p>
      <button type="submit" :disabled="authStore.loading">
        {{ authStore.loading ? '注册中...' : '注册' }}
      </button>
    </form>
    <p class="switch">
      已有账号？<RouterLink to="/login">去登录</RouterLink>
    </p>
  </div>
</template>

<style scoped>
.auth-container {
  max-width: 360px;
  margin: 4rem auto;
  padding: 2rem;
  border: 1px solid var(--color-border);
  border-radius: 8px;
}

h1 {
  text-align: center;
  margin-bottom: 1.5rem;
}

.form-group {
  margin-bottom: 1rem;
}

label {
  display: block;
  margin-bottom: 0.25rem;
  font-weight: 600;
}

input {
  width: 100%;
  padding: 0.5rem;
  border: 1px solid var(--color-border);
  border-radius: 4px;
  background: var(--color-background);
  color: var(--color-text);
}

button {
  width: 100%;
  padding: 0.6rem;
  border: none;
  border-radius: 4px;
  background: #42b883;
  color: white;
  font-size: 1rem;
  cursor: pointer;
}

button:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.error {
  color: #c0392b;
  font-size: 0.9rem;
  margin-bottom: 0.75rem;
}

.switch {
  text-align: center;
  margin-top: 1rem;
  font-size: 0.9rem;
}
</style>

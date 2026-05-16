<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { getCoords, fetchWeather, getSceneType, type CurrentWeather, type SceneType } from '@/api/weather'
import SkyCanvas from '@/components/sky/SkyCanvas.vue'

const weather = ref<CurrentWeather | null>(null)
const scene = ref<SceneType>('sunny')
const coordsText = ref('39.9°N 116.4°E')
let timer: ReturnType<typeof setInterval> | null = null

async function loadWeather() {
  try {
    const coords = await getCoords()
    coordsText.value = `${coords.lat.toFixed(1)}°N ${coords.lon.toFixed(1)}°E`
    const data = await fetchWeather(coords.lat, coords.lon)
    weather.value = data
    scene.value = getSceneType(data.weatherCode, data.isDay)
  } catch {
    // 天气获取失败，保持默认晴天场景
  }
}

onMounted(() => {
  loadWeather()
  timer = setInterval(loadWeather, 30 * 60 * 1000)
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
})
</script>

<template>
  <div class="home">
    <!-- Hero 区域 -->
    <section class="hero">
      <div class="hero-content">
        <h1 class="hero-title">
          <span class="hero-icon">🎆</span>
          Fireworks
        </h1>
        <p class="hero-subtitle">
          你的私人密码保险箱 &amp; 族谱管理工具
        </p>
        <p class="hero-tagline">
          安全 · 私密 · 永远属于你
        </p>
        <div class="hero-actions">
          <router-link to="/vault" class="fw-btn fw-btn-primary hero-btn">
            🔐 打开密码本
          </router-link>
          <router-link to="/family-trees" class="fw-btn hero-btn">
            🌳 查看族谱
          </router-link>
        </div>
      </div>
      <div class="hero-divider"></div>
    </section>

    <!-- 功能卡片网格 -->
    <section class="features">
      <div class="features-inner">
        <article class="feature-card">
          <div class="feature-icon">🔐</div>
          <h3 class="feature-title">密码保险箱</h3>
          <p class="feature-desc">
            端到端加密存储，仅你能解锁。支持主密码 + PBKDF2 密钥派生，
            AES-256-GCM 加密，浏览器端加解密确保数据安全。
          </p>
        </article>

        <article class="feature-card">
          <div class="feature-icon">🌳</div>
          <h3 class="feature-title">族谱管理</h3>
          <p class="feature-desc">
            可视化家族关系图谱，自由编辑成员信息。
            支持父母、配偶、子女等多种关系类型，灵活构建家族树。
          </p>
        </article>

        <article class="feature-card">
          <div class="feature-icon">☁️</div>
          <h3 class="feature-title">自托管部署</h3>
          <p class="feature-desc">
            数据完全自主掌控。Spring Boot + Vue 3 全栈架构，
            一键部署到你的服务器，无需依赖第三方云服务。
          </p>
        </article>
      </div>
    </section>

    <!-- 天气小挂件 — 右下角固定 -->
    <div class="weather-widget">
      <div class="weather-canvas">
        <SkyCanvas :scene="scene" />
      </div>
      <div class="weather-overlay" v-if="weather">
        <span class="weather-temp mono">{{ weather.temperature }}°</span>
        <span class="weather-coords mono">{{ coordsText }}</span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.home {
  min-height: calc(100vh - 56px);
}

/* ---- Hero ---- */
.hero {
  padding: 4rem 1.5rem 2rem;
  text-align: center;
}

.hero-content {
  max-width: 640px;
  margin: 0 auto;
}

.hero-icon {
  display: inline-block;
  font-size: 3rem;
  margin-bottom: 0.5rem;
}

.hero-title {
  font-size: 3rem;
  font-weight: 700;
  color: var(--fw-accent);
  letter-spacing: 0.03em;
  margin-bottom: 0.75rem;
}

.hero-subtitle {
  font-size: 1.2rem;
  color: var(--fw-text);
  margin-bottom: 0.5rem;
  line-height: 1.7;
}

.hero-tagline {
  font-size: 0.95rem;
  color: var(--fw-secondary);
  margin-bottom: 2rem;
}

.hero-actions {
  display: flex;
  justify-content: center;
  gap: 1rem;
  flex-wrap: wrap;
}

.hero-btn {
  font-size: 1rem;
  padding: 0.6rem 1.5rem;
}

.hero-divider {
  width: 80px;
  height: 2px;
  background: var(--fw-border);
  margin: 3rem auto 0;
}

/* ---- 功能卡片 ---- */
.features {
  padding: 2rem 1.5rem 4rem;
}

.features-inner {
  max-width: 1000px;
  margin: 0 auto;
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 1.5rem;
}

.feature-card {
  background: var(--fw-bg-soft);
  border: 1px solid var(--fw-border);
  border-radius: var(--fw-radius-lg);
  padding: 1.75rem;
  transition: border-color 0.3s, transform 0.3s;
}

.feature-card:hover {
  border-color: var(--fw-accent);
  transform: translateY(-3px);
}

.feature-icon {
  font-size: 2rem;
  margin-bottom: 0.75rem;
}

.feature-title {
  font-size: 1.1rem;
  font-weight: 700;
  color: var(--fw-text);
  margin-bottom: 0.5rem;
}

.feature-desc {
  font-size: 0.9rem;
  color: var(--fw-text-dim);
  line-height: 1.7;
}

/* ---- 天气小挂件 ---- */
.weather-widget {
  position: fixed;
  right: 20px;
  bottom: 20px;
  width: 140px;
  height: 140px;
  border-radius: var(--fw-radius-lg);
  overflow: hidden;
  border: 1px solid var(--fw-border);
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.4);
  z-index: 50;
  cursor: default;
}

.weather-canvas {
  position: absolute;
  inset: 0;
}

/* SkyCanvas 内部元素缩放适配 — 通过 scale 缩小 */
.weather-canvas :deep(.sky-canvas) {
  transform: scale(0.45);
  transform-origin: center center;
}

.weather-overlay {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2px;
  pointer-events: none;
}

.weather-temp {
  font-size: 1.6rem;
  font-weight: 300;
  color: var(--fw-text);
  text-shadow: 0 1px 4px rgba(0, 0, 0, 0.5);
  line-height: 1;
}

.weather-coords {
  font-size: 0.6rem;
  color: var(--fw-text-dim);
  text-shadow: 0 1px 3px rgba(0, 0, 0, 0.5);
}
</style>

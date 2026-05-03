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
  <div class="home-canvas">
    <SkyCanvas :scene="scene" />

    <div class="weather-info" v-if="weather">
      <span class="temperature">{{ weather.temperature }}°C</span>
      <span class="coords">{{ coordsText }}</span>
    </div>
  </div>
</template>

<style scoped>
.home-canvas {
  position: fixed;
  inset: 0;
  overflow: hidden;
}

.weather-info {
  position: absolute;
  right: 24px;
  bottom: 24px;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 4px;
  z-index: 10;
}

.temperature {
  font-size: 1.5rem;
  font-weight: 300;
  color: rgba(255, 255, 255, 0.9);
  text-shadow: 0 1px 4px rgba(0, 0, 0, 0.3);
}

.coords {
  font-size: 0.8rem;
  color: rgba(255, 255, 255, 0.6);
  text-shadow: 0 1px 3px rgba(0, 0, 0, 0.3);
  font-family: 'Courier New', monospace;
}
</style>

# 主页天气动效场景 — 实现方案

> **For agentic workers:** 使用 superpowers:subagent-driven-development 或 superpowers:executing-plans 按任务逐步实现。步骤使用 checkbox（`- [ ]`）语法跟踪。

**目标：** 将 HomeView 替换为根据实时天气和时间展示的纯 CSS SVG 动效全屏画布。

**架构：** 新增 `api/weather.ts` 封装 Open-Meteo 调用，新增 5 个 sky 子组件（SunElement / CloudElement / RainElement / SnowElement / MoonStars），由 SkyCanvas 根据场景类型组合渲染。HomeView 作为全屏容器，onMounted 时获取天气并 30 分钟轮询。

**技术栈：** Vue 3 + TypeScript + 内联 SVG + CSS @keyframes，无需额外依赖。

---

## 文件结构

```
src/
├── api/weather.ts                    ← 创建：天气 API 封装
├── components/sky/
│   ├── SkyCanvas.vue                 ← 创建：场景调度 + 背景渐变
│   ├── SunElement.vue                ← 创建：太阳 + 旋转光芒
│   ├── CloudElement.vue              ← 创建：可复用漂移云朵
│   ├── RainElement.vue               ← 创建：雨线
│   ├── SnowElement.vue               ← 创建：雪花
│   └── MoonStars.vue                 ← 创建：月亮 + 闪烁星星
└── views/HomeView.vue                ← 修改：全屏容器 + 天气轮询
```

---

### Task 1: 创建天气 API 模块

**文件：** 创建 `src/api/weather.ts`

- [ ] **Step 1: 写入天气 API 封装**

```ts
// src/api/weather.ts
export interface CurrentWeather {
  temperature: number
  weatherCode: number
  isDay: 0 | 1
  lat: number
  lon: number
}

interface CoordsResult {
  lat: number
  lon: number
  source: 'browser' | 'default'
}

export type SceneType = 'sunny' | 'cloudy' | 'rainy' | 'snowy' | 'night'

/**
 * 静默获取地理位置：已有授权才使用浏览器坐标，否则降级为默认北京坐标
 */
export async function getCoords(): Promise<CoordsResult> {
  try {
    const ps = await navigator.permissions.query({ name: 'geolocation' as PermissionName })
    if (ps.state === 'granted') {
      const pos = await new Promise<GeolocationPosition>((resolve, reject) => {
        navigator.geolocation.getCurrentPosition(resolve, reject, { timeout: 5000 })
      })
      return { lat: pos.coords.latitude, lon: pos.coords.longitude, source: 'browser' }
    }
  } catch {
    // 权限不可用或拒绝，降级
  }
  return { lat: 39.9, lon: 116.4, source: 'default' }
}

/**
 * 调用 Open-Meteo 获取当前天气
 */
export async function fetchWeather(lat: number, lon: number): Promise<CurrentWeather> {
  const url = `https://api.open-meteo.com/v1/forecast?latitude=${lat}&longitude=${lon}&current_weather=true`
  const res = await fetch(url)
  if (!res.ok) throw new Error(`天气服务不可用 (${res.status})`)
  const data = await res.json()
  const cw = data.current_weather
  return {
    temperature: Math.round(cw.temperature),
    weatherCode: cw.weathercode,
    isDay: cw.is_day ? 1 : 0,
    lat,
    lon,
  }
}

/**
 * 根据天气码和昼夜判定场景类型
 */
export function getSceneType(weatherCode: number, isDay: 0 | 1): SceneType {
  if (isDay === 0) return 'night'
  if (weatherCode === 0) return 'sunny'
  if (weatherCode >= 1 && weatherCode <= 3) return 'cloudy'
  if (weatherCode >= 45 && weatherCode <= 57) return 'rainy'
  if (weatherCode >= 71 && weatherCode <= 77) return 'snowy'
  return 'sunny'
}
```

- [ ] **Step 2: 验证 TypeScript 编译**

```bash
cd fireworks-web && npx vue-tsc --noEmit --project tsconfig.app.json src/api/weather.ts 2>&1 | tail -5
```

- [ ] **Step 3: 提交**

```bash
git add fireworks-web/src/api/weather.ts
git commit -m "feat: 新增天气 API 模块（Open-Meteo）"
```

---

### Task 2: 创建 SunElement 组件

**文件：** 创建 `src/components/sky/SunElement.vue`

- [ ] **Step 1: 写入 SunElement**

```vue
<script setup lang="ts">
</script>

<template>
  <div class="sun-container">
    <svg viewBox="0 0 200 200" xmlns="http://www.w3.org/2000/svg">
      <g class="sun-rays">
        <line
          v-for="i in 12"
          :key="i"
          x1="100" y1="25" x2="100" y2="12"
          :transform="`rotate(${i * 30}, 100, 100)`"
          stroke="#FFD700" stroke-width="5" stroke-linecap="round"
        />
      </g>
      <circle cx="100" cy="100" r="42" fill="#FFD700" />
      <circle cx="100" cy="100" r="48" fill="#FFD700" opacity="0.15" />
    </svg>
  </div>
</template>

<style scoped>
.sun-container {
  position: absolute;
  top: 8%;
  right: 12%;
  width: 180px;
  height: 180px;
  filter: drop-shadow(0 0 30px rgba(255, 200, 50, 0.5));
}

.sun-rays {
  transform-origin: 100px 100px;
  animation: sun-rotate 60s linear infinite;
}

@keyframes sun-rotate {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}
</style>
```

- [ ] **Step 2: 提交**

```bash
git add fireworks-web/src/components/sky/SunElement.vue
git commit -m "feat: 新增太阳 SVG 组件（旋转光芒动画）"
```

---

### Task 3: 创建 CloudElement 组件

**文件：** 创建 `src/components/sky/CloudElement.vue`

- [ ] **Step 1: 写入 CloudElement**

```vue
<script setup lang="ts">
defineProps<{
  size?: number
  speed?: number
  top?: number
  delay?: number
  opacity?: number
}>()
</script>

<template>
  <div
    class="cloud-wrapper"
    :style="{
      top: `${top ?? 15}%`,
      width: `${size ?? 160}px`,
      opacity: opacity ?? 0.85,
      animationDuration: `${speed ?? 30}s`,
      animationDelay: `${delay ?? 0}s`,
    }"
  >
    <svg viewBox="0 0 220 100" xmlns="http://www.w3.org/2000/svg">
      <ellipse cx="75" cy="65" rx="60" ry="30" fill="white" />
      <ellipse cx="125" cy="50" rx="55" ry="38" fill="white" />
      <ellipse cx="160" cy="60" rx="45" ry="28" fill="white" />
      <ellipse cx="100" cy="55" rx="50" ry="30" fill="white" />
    </svg>
  </div>
</template>

<style scoped>
.cloud-wrapper {
  position: absolute;
  left: 0;
  animation: cloud-drift linear infinite;
}

@keyframes cloud-drift {
  from { transform: translateX(-240px); }
  to { transform: translateX(calc(100vw + 240px)); }
}
</style>
```

- [ ] **Step 2: 提交**

```bash
git add fireworks-web/src/components/sky/CloudElement.vue
git commit -m "feat: 新增云朵 SVG 组件（漂移动画）"
```

---

### Task 4: 创建 RainElement 组件

**文件：** 创建 `src/components/sky/RainElement.vue`

- [ ] **Step 1: 写入 RainElement**

```vue
<script setup lang="ts">
interface Drop {
  left: number
  delay: number
  duration: number
}

const drops: Drop[] = Array.from({ length: 50 }, () => ({
  left: Math.random() * 100,
  delay: Math.random() * 1.5,
  duration: 0.45 + Math.random() * 0.4,
}))
</script>

<template>
  <div class="rain-layer">
    <div
      v-for="(d, i) in drops"
      :key="i"
      class="drop"
      :style="{
        left: `${d.left}%`,
        animationDelay: `${d.delay}s`,
        animationDuration: `${d.duration}s`,
      }"
    />
  </div>
</template>

<style scoped>
.rain-layer {
  position: absolute;
  inset: 0;
  overflow: hidden;
  pointer-events: none;
}

.drop {
  position: absolute;
  top: -30px;
  width: 2px;
  height: 18px;
  background: linear-gradient(to bottom, transparent, rgba(255, 255, 255, 0.5));
  border-radius: 0 0 2px 2px;
  animation: rain-fall linear infinite;
}

@keyframes rain-fall {
  from { transform: translateY(-30px) translateX(0); }
  to { transform: translateY(100vh) translateX(-8px); }
}
</style>
```

- [ ] **Step 2: 提交**

```bash
git add fireworks-web/src/components/sky/RainElement.vue
git commit -m "feat: 新增雨滴 SVG 组件（下落动画）"
```

---

### Task 5: 创建 SnowElement 组件

**文件：** 创建 `src/components/sky/SnowElement.vue`

- [ ] **Step 1: 写入 SnowElement**

```vue
<script setup lang="ts">
interface Flake {
  left: number
  delay: number
  duration: number
  size: number
  sway: number
}

const flakes: Flake[] = Array.from({ length: 35 }, () => ({
  left: Math.random() * 100,
  delay: Math.random() * 6,
  duration: 3 + Math.random() * 5,
  size: 4 + Math.random() * 8,
  sway: -30 + Math.random() * 60,
}))
</script>

<template>
  <div class="snow-layer">
    <div
      v-for="(f, i) in flakes"
      :key="i"
      class="flake"
      :style="{
        left: `${f.left}%`,
        width: `${f.size}px`,
        height: `${f.size}px`,
        animationDelay: `${f.delay}s`,
        animationDuration: `${f.duration}s`,
        '--sway': `${f.sway}px`,
      }"
    />
  </div>
</template>

<style scoped>
.snow-layer {
  position: absolute;
  inset: 0;
  overflow: hidden;
  pointer-events: none;
}

.flake {
  position: absolute;
  top: -20px;
  background: white;
  border-radius: 50%;
  opacity: 0.85;
  animation: snow-fall linear infinite;
}

@keyframes snow-fall {
  0%   { transform: translateY(-20px) translateX(0); opacity: 0.9; }
  50%  { transform: translateY(50vh) translateX(var(--sway)); opacity: 0.7; }
  100% { transform: translateY(100vh) translateX(calc(var(--sway) * -0.5)); opacity: 0.4; }
}
</style>
```

- [ ] **Step 2: 提交**

```bash
git add fireworks-web/src/components/sky/SnowElement.vue
git commit -m "feat: 新增雪花 SVG 组件（飘落摇摆动画）"
```

---

### Task 6: 创建 MoonStars 组件

**文件：** 创建 `src/components/sky/MoonStars.vue`

- [ ] **Step 1: 写入 MoonStars**

```vue
<script setup lang="ts">
interface Star {
  cx: number
  cy: number
  r: number
  delay: number
}

const stars: Star[] = Array.from({ length: 40 }, () => ({
  cx: Math.random() * 100,
  cy: Math.random() * 70,
  r: 0.8 + Math.random() * 1.8,
  delay: Math.random() * 3,
}))
</script>

<template>
  <div class="moon-layer">
    <svg viewBox="0 0 100 100" xmlns="http://www.w3.org/2000/svg" class="stars-svg">
      <!-- 星星 -->
      <circle
        v-for="(s, i) in stars"
        :key="i"
        :cx="s.cx" :cy="s.cy" :r="s.r"
        fill="white"
        :style="{ animationDelay: `${s.delay}s` }"
        class="star"
      />
      <!-- 月亮 -->
      <g transform="translate(70, 20)">
        <circle cx="0" cy="0" r="18" fill="#F5E56C" opacity="0.15" />
        <circle cx="0" cy="0" r="14" fill="#F5E56C" />
        <circle cx="6" cy="-4" r="11" fill="#0a0e27" />
      </g>
    </svg>
  </div>
</template>

<style scoped>
.moon-layer {
  position: absolute;
  inset: 0;
  pointer-events: none;
}

.stars-svg {
  width: 100%;
  height: 100%;
}

.star {
  animation: twinkle 2.5s ease-in-out infinite;
}

@keyframes twinkle {
  0%, 100% { opacity: 0.3; transform: scale(1); }
  50%      { opacity: 1;   transform: scale(1.3); }
}
</style>
```

- [ ] **Step 2: 提交**

```bash
git add fireworks-web/src/components/sky/MoonStars.vue
git commit -m "feat: 新增月亮星星 SVG 组件（闪烁动画）"
```

---

### Task 7: 创建 SkyCanvas 调度组件

**文件：** 创建 `src/components/sky/SkyCanvas.vue`

- [ ] **Step 1: 写入 SkyCanvas**

```vue
<script setup lang="ts">
import { computed } from 'vue'
import type { SceneType } from '@/api/weather'
import SunElement from './SunElement.vue'
import CloudElement from './CloudElement.vue'
import RainElement from './RainElement.vue'
import SnowElement from './SnowElement.vue'
import MoonStars from './MoonStars.vue'

const props = defineProps<{
  scene: SceneType
}>()

const bgGradient = computed(() => {
  switch (props.scene) {
    case 'sunny':
      return 'linear-gradient(180deg, #1e88e5 0%, #64b5f6 40%, #90caf9 70%, #bbdefb 100%)'
    case 'cloudy':
      return 'linear-gradient(180deg, #546e7a 0%, #78909c 40%, #90a4ae 70%, #b0bec5 100%)'
    case 'rainy':
      return 'linear-gradient(180deg, #37474f 0%, #455a64 40%, #546e7a 70%, #607d8b 100%)'
    case 'snowy':
      return 'linear-gradient(180deg, #90a4ae 0%, #b0bec5 40%, #cfd8dc 70%, #eceff1 100%)'
    case 'night':
      return 'linear-gradient(180deg, #0d0f1a 0%, #151a30 40%, #1c2541 70%, #1e2d4a 100%)'
    default:
      return 'linear-gradient(180deg, #1e88e5 0%, #64b5f6 40%, #90caf9 100%)'
  }
})
</script>

<template>
  <div class="sky-canvas" :style="{ background: bgGradient }">
    <Transition name="fade" mode="out-in">
      <!-- 晴天 -->
      <template v-if="scene === 'sunny'">
        <SunElement key="sunny-sun" />
        <CloudElement key="sunny-c1" :size="100" :top="22" :speed="35" :delay="0" :opacity="0.5" />
        <CloudElement key="sunny-c2" :size="70" :top="35" :speed="28" :delay="8" :opacity="0.35" />
      </template>

      <!-- 多云 -->
      <template v-else-if="scene === 'cloudy'">
        <CloudElement key="cld-c1" :size="200" :top="10" :speed="22" :delay="0" :opacity="0.9" />
        <CloudElement key="cld-c2" :size="170" :top="28" :speed="28" :delay="6" :opacity="0.75" />
        <CloudElement key="cld-c3" :size="140" :top="42" :speed="32" :delay="12" :opacity="0.6" />
        <CloudElement key="cld-c4" :size="180" :top="55" :speed="25" :delay="4" :opacity="0.5" />
      </template>

      <!-- 雨天 -->
      <template v-else-if="scene === 'rainy'">
        <CloudElement key="rain-c1" :size="200" :top="8" :speed="22" :delay="0" :opacity="0.8" />
        <CloudElement key="rain-c2" :size="170" :top="18" :speed="28" :delay="5" :opacity="0.65" />
        <RainElement key="rain-drops" />
      </template>

      <!-- 雪天 -->
      <template v-else-if="scene === 'snowy'">
        <CloudElement key="snow-c1" :size="200" :top="8" :speed="25" :delay="0" :opacity="0.7" />
        <CloudElement key="snow-c2" :size="160" :top="20" :speed="30" :delay="7" :opacity="0.55" />
        <SnowElement key="snow-flakes" />
      </template>

      <!-- 夜晚 -->
      <template v-else-if="scene === 'night'">
        <MoonStars key="night-moon" />
      </template>
    </Transition>
  </div>
</template>

<style scoped>
.sky-canvas {
  position: absolute;
  inset: 0;
  overflow: hidden;
  transition: background 1.5s ease;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 1.5s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
```

- [ ] **Step 2: 提交**

```bash
git add fireworks-web/src/components/sky/SkyCanvas.vue
git commit -m "feat: 新增 SkyCanvas 天气场景调度组件"
```

---

### Task 8: 重写 HomeView 为全屏画布

**文件：** 修改 `src/views/HomeView.vue`

- [ ] **Step 1: 重写 HomeView**

```vue
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
```

- [ ] **Step 2: 验证 TypeScript 编译**

```bash
cd fireworks-web && npx vue-tsc --noEmit 2>&1 | tail -20
```

- [ ] **Step 3: 提交**

```bash
git add fireworks-web/src/views/HomeView.vue
git commit -m "feat: 重写主页为全屏天气动效画布"
```

---

### Task 9: 浏览器验证

- [ ] **Step 1: 启动开发服务器（如未运行）**

```bash
./gradlew :fireworks-server:bootRunDev
```

- [ ] **Step 2: 打开 Chrome DevTools 导航并截图**

```
navigate → http://localhost:5173/
take screenshot → 确认太阳、云朵、雨天/雪天/夜晚场景动画正常
```

- [ ] **Step 3: 确认右下角温度坐标显示**

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

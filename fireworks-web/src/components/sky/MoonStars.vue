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

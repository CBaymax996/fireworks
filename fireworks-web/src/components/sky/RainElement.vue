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

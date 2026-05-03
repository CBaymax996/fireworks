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

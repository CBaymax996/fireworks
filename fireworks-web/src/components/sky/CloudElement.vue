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

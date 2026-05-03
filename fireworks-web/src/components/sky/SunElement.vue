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

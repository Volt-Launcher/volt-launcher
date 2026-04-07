<script setup lang="ts">
import { ref, onMounted, onUnmounted } from "vue";
import { useLauncher } from "@/composables/useLauncher";

const { showFps } = useLauncher();

const fps = ref(0);
let frameCount = 0;
let lastTime = performance.now();
let rafId = 0;

const tick = (now: number) => {
  frameCount++;
  if (now - lastTime >= 1000) {
    fps.value = frameCount;
    frameCount = 0;
    lastTime = now;
  }
  rafId = requestAnimationFrame(tick);
};

onMounted(() => { rafId = requestAnimationFrame(tick); });
onUnmounted(() => { cancelAnimationFrame(rafId); });
</script>

<template>
  <div class="h-full w-full overflow-hidden bg-[var(--app-bg)] text-white antialiased">
    <RouterView />
    <div
      v-if="showFps"
      class="pointer-events-none fixed right-2 bottom-2 z-[9999] rounded-md bg-black/60 px-2 py-0.5 font-mono text-[11px] tabular-nums text-white/70"
    >
      {{ fps }} FPS
    </div>
  </div>
</template>

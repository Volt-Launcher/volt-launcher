<script setup lang="ts"></script>

<template>
  <div class="pointer-events-none fixed inset-0 z-0 overflow-hidden">
    <div class="absolute -left-20 -top-44 h-[700px] w-[700px] rounded-full bg-[image:var(--aurora-primary)] blur-[120px] animate-pulse"></div>
    <div class="absolute right-[-130px] top-[35%] h-[580px] w-[580px] rounded-full bg-[image:var(--aurora-secondary)] blur-[120px] animate-pulse [animation-delay:-1s]"></div>
    <div class="absolute bottom-[-80px] left-[28%] h-[480px] w-[480px] rounded-full bg-[image:var(--aurora-violet)] blur-[120px] animate-pulse [animation-delay:-2s]"></div>
    <div class="absolute left-[45%] top-[40%] h-[320px] w-[320px] rounded-full bg-[image:var(--aurora-accent)] blur-[120px] animate-pulse [animation-delay:-3s]"></div>
  </div>
</template>

<script setup lang="ts"></script>

<template>
  <div class="pointer-events-none fixed inset-0 z-0 overflow-hidden">
    <div class="absolute -left-20 -top-44 h-[700px] w-[700px] rounded-full bg-[radial-gradient(circle,rgba(0,178,255,.2),transparent_70%)] blur-[120px] animate-pulse"></div>
    <div class="absolute right-[-130px] top-[35%] h-[580px] w-[580px] rounded-full bg-[radial-gradient(circle,rgba(108,99,255,.18),transparent_70%)] blur-[120px] animate-pulse [animation-delay:-1s]"></div>
    <div class="absolute bottom-[-80px] left-[28%] h-[480px] w-[480px] rounded-full bg-[radial-gradient(circle,rgba(139,92,246,.15),transparent_70%)] blur-[120px] animate-pulse [animation-delay:-2s]"></div>
    <div class="absolute left-[45%] top-[40%] h-[320px] w-[320px] rounded-full bg-[radial-gradient(circle,rgba(0,255,204,.1),transparent_70%)] blur-[120px] animate-pulse [animation-delay:-3s]"></div>
  </div>
</template>

<script setup lang="ts"></script>
<template>
  <div class="aurora"><div class="orb a"></div><div class="orb b"></div><div class="orb c"></div><div class="orb d"></div></div>
</template>
<style scoped>
.aurora { position: fixed; inset: 0; z-index: 0; pointer-events: none; }
.orb { position: absolute; border-radius: 50%; filter: blur(120px); animation: drift linear infinite alternate; }
.orb.a { width: 700px; height: 700px; background: radial-gradient(circle,rgba(0,178,255,.2),transparent 70%); top: -180px; left: -80px; animation-duration: 22s; }
.orb.b { width: 580px; height: 580px; background: radial-gradient(circle,rgba(108,99,255,.18),transparent 70%); top: 35%; right: -130px; animation-duration: 28s; animation-delay: -8s; }
.orb.c { width: 480px; height: 480px; background: radial-gradient(circle,rgba(139,92,246,.15),transparent 70%); bottom: -80px; left: 28%; animation-duration: 20s; animation-delay: -13s; }
.orb.d { width: 320px; height: 320px; background: radial-gradient(circle,rgba(0,255,204,.1),transparent 70%); top: 40%; left: 45%; animation-duration: 34s; animation-delay: -5s; }
@keyframes drift { 0% { transform:translate(0,0) scale(1); } 30% { transform:translate(35px,-28px) scale(1.06); } 60% { transform:translate(-20px,45px) scale(.94); } 100% { transform:translate(15px,-35px) scale(1.02); } }
</style>